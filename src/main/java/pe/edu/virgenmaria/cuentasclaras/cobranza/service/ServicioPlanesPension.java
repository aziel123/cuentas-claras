package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanDetalle;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanResumen;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.ResultadoGeneracion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.ResumenPensiones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.TarjetaNivel;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.VencimientoVista;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConfiguracionPlan;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoPlan;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.LoteSaldoInicialRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.PlanPensionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ControlParticipantes;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Planes de pensiones por año y nivel, con aprobación de otra persona:
 * <ul>
 *   <li>Promotoría, Dirección y Administración los ven;</li>
 *   <li>Administración propone, edita, crea versiones nuevas («Cambiar montos») y descarta borradores;</li>
 *   <li>Promotoría o Dirección aprueban, siempre que no hayan creado ni editado ese plan. El intento queda en la
 *       bitácora ({@code AUTOAPROBACION_RECHAZADA}) aunque se rechace: por eso {@code noRollbackFor}.</li>
 * </ul>
 * Aprobar reemplaza la versión anterior (sin tocar las cuotas ya generadas) y genera los cronogramas pendientes del
 * nivel.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioPlanesPension {

	private final PlanPensionRepository planes;

	private final AnioEscolarRepository anios;

	private final CuotaRepository cuotas;

	private final GeneradorCronograma generador;

	private final LoteSaldoInicialRepository lotes;

	private final ControlParticipantes participantes;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	/** Sprint 7, tanda 2: la firma de la sesión de quien resuelve (sección 3.4). */
	private final FirmaSesion firmaSesion;

	public ServicioPlanesPension(PlanPensionRepository planes, AnioEscolarRepository anios, CuotaRepository cuotas,
			GeneradorCronograma generador, LoteSaldoInicialRepository lotes, ControlParticipantes participantes,
			AuditoriaService auditoria, Clock reloj, FirmaSesion firmaSesion) {
		this.firmaSesion = firmaSesion;
		this.lotes = lotes;
		this.participantes = participantes;
		this.planes = planes;
		this.anios = anios;
		this.cuotas = cuotas;
		this.generador = generador;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** Resumen de un año (por defecto, el año en curso o el más reciente). */
	public ResumenPensiones resumen(Long anioId) {
		List<AnioEscolar> todos = anios.findAllByOrderByAnioDesc();
		List<AnioOpcion> opciones = todos.stream().map(ServicioPlanesPension::opcion).toList();
		Optional<AnioEscolar> elegido = anioId != null
				? Optional.of(anios.findById(anioId).orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado")))
				: todos.stream().filter(AnioEscolar::enCurso).findFirst().or(() -> todos.stream().findFirst());
		if (elegido.isEmpty()) {
			return new ResumenPensiones(null, opciones, List.of(), 0, 0, false);
		}
		AnioEscolar anio = elegido.get();
		List<PlanPension> delAnio = planes.findByAnioEscolarIdOrderByNivelAscNumeroVersionDesc(anio.getId());
		boolean puedeProponer = SesionActual.tieneAlgunRol("ADMINISTRACION") && !anio.cerrado();
		long totalSinCronograma = 0;
		List<TarjetaNivel> tarjetas = new java.util.ArrayList<>();
		for (Nivel nivel : Nivel.values()) {
			List<PlanPension> delNivel = delAnio.stream().filter(p -> p.getNivel() == nivel).toList();
			PlanResumen vigente = delNivel.stream().filter(PlanPension::aprobado).findFirst()
					.map(ServicioPlanesPension::resumenDe).orElse(null);
			List<PlanResumen> borradores = delNivel.stream().filter(PlanPension::pendiente)
					.map(ServicioPlanesPension::resumenDe).toList();
			long sinCronograma = cuotas.matriculasSinCronograma(anio.getId(), grados(nivel)).size();
			totalSinCronograma += sinCronograma;
			tarjetas.add(new TarjetaNivel(nivel.name(), nivel.etiqueta(), vigente, borradores, sinCronograma,
					puedeProponer && borradores.isEmpty()));
		}
		return new ResumenPensiones(opcion(anio), opciones, tarjetas, totalSinCronograma,
				cuotas.countByAnioEscolarId(anio.getId()), SesionActual.tieneAlgunRol("DIRECTOR", "ADMINISTRACION"));
	}

	public PlanDetalle obtener(Long id) {
		PlanPension plan = buscar(id);
		String usuario = SesionActual.usuario();
		boolean aprobador = SesionActual.tieneAlgunRol("PROMOTOR", "DIRECTOR");
		boolean administracion = SesionActual.tieneAlgunRol("ADMINISTRACION");
		boolean autor = participantes.ampliar(plan.participantes()).contains(usuario);
		boolean hayBorrador = hayPendiente(plan.getAnioEscolar(), plan.getNivel());
		String avisoAutor = plan.pendiente() && aprobador && autor
				? "Tú creaste, editaste o enviaste este plan (o preparaste la cuenta de quien lo hizo): debe aprobarlo "
						+ "otra persona de Promotoría o Dirección." : null;
		List<PlanResumen> historial = planes
				.findByAnioEscolarIdAndNivelOrderByNumeroVersionDesc(plan.getAnioEscolar().getId(), plan.getNivel())
				.stream().map(ServicioPlanesPension::resumenDe).toList();
		List<VencimientoVista> vencimientos = plan.getVencimientos().stream()
				.map(f -> new VencimientoVista(f, Calendario.nombreMes(f.getMonthValue()))).toList();
		return new PlanDetalle(plan.getId(), plan.getAnioEscolar().getId(), plan.getAnioEscolar().getAnio(),
				plan.getNivel().name(), plan.getNivel().etiqueta(), plan.nombre(), plan.getNumeroVersion(),
				plan.getEstado().name(), plan.getEstado().etiqueta(), plan.getEstado().variante(),
				plan.getMontoMatricula(), plan.getVencimientoMatricula(), plan.getMontoPension(), vencimientos,
				plan.getCobroDesde(), plan.getMotivoCambio(), plan.getCreadoPor(), plan.getCreadoEn(),
				plan.getEditadoPor(), plan.getAprobadoPor(), plan.getAprobadoEn(), plan.getCerradoPor(),
				plan.getCerradoEn(), plan.borrador() && administracion, plan.enviado() && aprobador && !autor,
				plan.aprobado() && administracion && !hayBorrador && !plan.getAnioEscolar().cerrado(),
				plan.borrador() && administracion, avisoAutor, historial, plan.borrador() && administracion,
				plan.enviado() && aprobador, plan.getEnviadoPor(), plan.getEnviadoEn(), plan.getDevueltoPor(),
				plan.getMotivoDevolucion(), String.join(", ", plan.participantes()), plan.getVersion());
	}

	/** Valores del borrador para su formulario de edición. */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public PlanRequest datosParaEditar(Long id) {
		PlanPension plan = buscar(id);
		if (!plan.borrador()) {
			throw new ReglaNegocioException("El plan está " + plan.getEstado().etiqueta().toLowerCase()
					+ ": no se edita. Usa «Cambiar montos» para proponer una versión nueva.");
		}
		return PlanRequest.de(plan.configuracion());
	}

	/** Datos del año para el formulario. */
	public AnioOpcion anio(Long anioId) {
		return opcion(buscarAnio(anioId));
	}

	/**
	 * Propuesta para un plan nuevo: los montos del último plan aprobado del nivel en años anteriores (si hay) y los
	 * vencimientos por defecto (último día de marzo a diciembre; matrícula, último día de febrero).
	 */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public PlanRequest propuestaPorDefecto(Long anioId, Nivel nivel) {
		AnioEscolar anio = buscarAnio(anioId);
		Optional<PlanPension> anterior = planes.vigentesAnteriores(nivel, anio.getAnio()).stream().findFirst();
		BigDecimal matricula = anterior.map(PlanPension::getMontoMatricula).orElse(null);
		BigDecimal pension = anterior.map(PlanPension::getMontoPension).orElse(null);
		return PlanRequest.de(ConfiguracionPlan.porDefecto(anio.getAnio(), matricula, pension));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public Long crearBorrador(Long anioId, Nivel nivel, PlanRequest solicitud) {
		AnioEscolar anio = buscarAnio(anioId);
		exigirAbierto(anio);
		exigirSinBorrador(anio, nivel);
		if (planes.findByAnioEscolarIdAndNivelAndVigenteTrue(anio.getId(), nivel).isPresent()) {
			throw new ReglaNegocioException("Ya hay un plan aprobado de " + nivel.etiqueta() + " " + anio.getAnio()
					+ ": usa «Cambiar montos» para proponer una versión nueva.");
		}
		ConfiguracionPlan configuracion = solicitud.configuracion().validar(anio);
		int numero = planes.ultimaVersion(anio.getId(), nivel) + 1;
		// Si hubo versiones descartadas, esta es la siguiente (los números no se reutilizan).
		PlanPension plan = planes.save(PlanPension.borrador(anio, nivel, numero,
				"Nueva propuesta: la versión " + (numero - 1) + " se descartó.", configuracion, SesionActual.usuario()));
		auditoria.registrar(AccionAuditoria.PLAN_PENSION_CREADO, "plan_pension", plan.getId().toString(), null,
				DescripcionCobranza.plan(plan), plan.nombre() + " (borrador). Falta la aprobación de Promotoría o Dirección.");
		return plan.getId();
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void editarBorrador(Long id, PlanRequest solicitud) {
		PlanPension plan = buscar(id);
		String anterior = DescripcionCobranza.plan(plan);
		ConfiguracionPlan configuracion = solicitud.configuracion().validar(plan.getAnioEscolar());
		plan.editar(configuracion, SesionActual.usuario());
		auditoria.registrar(AccionAuditoria.PLAN_PENSION_EDITADO, "plan_pension", plan.getId().toString(), anterior,
				DescripcionCobranza.plan(plan), plan.nombre() + " (borrador).");
	}

	/** «Cambiar montos»: un borrador nuevo copiado del plan vigente, con motivo. Las cuotas ya generadas no cambian. */
	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public Long nuevaVersion(Long planId, String motivo) {
		PlanPension vigente = buscar(planId);
		exigirAbierto(vigente.getAnioEscolar());
		exigirSinBorrador(vigente.getAnioEscolar(), vigente.getNivel());
		int numero = planes.ultimaVersion(vigente.getAnioEscolar().getId(), vigente.getNivel()) + 1;
		PlanPension nueva = planes.save(vigente.nuevaVersion(numero, motivo, SesionActual.usuario()));
		auditoria.registrar(AccionAuditoria.PLAN_PENSION_CREADO, "plan_pension", nueva.getId().toString(),
				DescripcionCobranza.plan(vigente), DescripcionCobranza.plan(nueva), nueva.nombre() + " a partir de "
						+ vigente.nombre() + ". Motivo: " + nueva.getMotivoCambio());
		return nueva.getId();
	}

	/** Lo bloquea para que otra persona lo revise: desde aquí nadie lo edita (auditoría A1, «cebo y cambio»). */
	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void enviar(Long planId) {
		PlanPension plan = buscar(planId);
		plan.configuracion().validar(plan.getAnioEscolar());
		plan.enviar(SesionActual.usuario(), ahora());
		auditoria.registrar(AccionAuditoria.PLAN_PENSION_ENVIADO, "plan_pension", plan.getId().toString(), null,
				DescripcionCobranza.plan(plan), plan.nombre() + " enviado. Ya no se puede editar; lo aprueba otra persona.");
	}

	/** Quien revisa lo devuelve con motivo (vuelve a BORRADOR para corregirlo). */
	@Transactional
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public void devolver(Long planId, Long version, String motivo) {
		PlanPension plan = bloquearConAnio(planId);
		exigirVersion(plan, version);
		plan.devolver(SesionActual.usuario(), motivo, ahora());
		auditoria.registrar(AccionAuditoria.PLAN_PENSION_DEVUELTO, "plan_pension", plan.getId().toString(),
				EstadoPlan.ENVIADO.name(), EstadoPlan.BORRADOR.name(), plan.nombre() + ". Motivo: "
						+ plan.getMotivoDevolucion());
	}

	/**
	 * Aprueba el plan enviado: reemplaza la versión vigente (sus cuotas no cambian) y genera los cronogramas pendientes
	 * del nivel. Exige la versión que vio el aprobador (si el plan cambió, se rechaza). No aprueba quien lo creó, lo
	 * editó alguna vez, lo envió o preparó la cuenta de alguno de ellos: el intento queda auditado. Bloquea primero el
	 * año escolar (mismo orden que matricular y confirmar lotes) y después el plan.
	 */
	@Transactional(noRollbackFor = AutoaprobacionException.class)
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public ResultadoGeneracion aprobar(Long planId, Long version) {
		PlanPension plan = bloquearConAnio(planId);
		String usuario = SesionActual.usuario();
		if (!plan.enviado()) {
			throw new ReglaNegocioException("Solo se aprueba un plan enviado (este está "
					+ plan.getEstado().etiqueta().toLowerCase() + ").");
		}
		exigirVersion(plan, version);
		if (participantes.ampliar(plan.participantes()).contains(usuario)) {
			auditoria.registrar(AccionAuditoria.AUTOAPROBACION_RECHAZADA, "plan_pension", plan.getId().toString(),
					null, DescripcionCobranza.plan(plan), "Intentó aprobar " + plan.nombre()
							+ ", en el que participó (lo creó, lo editó, lo envió o preparó la cuenta de quien lo hizo). "
							+ "Se rechazó: debe aprobarlo otra persona.");
			throw new AutoaprobacionException("No puedes aprobar un plan en el que participaste: debe aprobarlo "
					+ "otra persona de Promotoría o Dirección.");
		}
		LocalDate corte = lotes.ultimoCorteConfirmado(plan.getAnioEscolar().getId());
		if (corte != null && (plan.getCobroDesde() == null || !plan.getCobroDesde().isAfter(corte))) {
			throw new ReglaNegocioException("El año " + plan.getAnioEscolar().getAnio() + " tiene saldo inicial "
					+ "confirmado hasta el " + Calendario.formatear(corte) + ": el plan debe cobrar desde después de esa "
					+ "fecha (por ejemplo el 01/" + String.format("%02d", corte.plusMonths(1).getMonthValue()) + "/"
					+ corte.plusMonths(1).getYear() + "). Devuélvelo para corregirlo.");
		}
		LocalDateTime ahora = ahora();
		Optional<PlanPension> anterior = planes.findByAnioEscolarIdAndNivelAndVigenteTrue(plan.getAnioEscolar().getId(),
				plan.getNivel());
		anterior.ifPresent(a -> {
			a.reemplazar(usuario, ahora);
			// Primero sale de vigente: el UNIQUE (año, nivel, vigente) no admite dos versiones vigentes a la vez.
			planes.saveAndFlush(a);
		});
		firmaSesion.firmar(ClaveFirma.planPension(plan.getId()));
		plan.aprobar(usuario, ahora);
		planes.saveAndFlush(plan);
		auditoria.registrar(AccionAuditoria.PLAN_PENSION_APROBADO, "plan_pension", plan.getId().toString(),
				anterior.map(DescripcionCobranza::plan).orElse(null), DescripcionCobranza.plan(plan),
				plan.nombre() + " aprobado." + anterior.map(a -> " Reemplaza a " + a.nombre()
						+ " (sus cuotas ya generadas no cambian).").orElse(""));
		return generador.generarPendientesDelNivel(plan.getAnioEscolar().getId(), plan.getNivel());
	}

	/** Bloquea primero el año escolar y después el plan (lectura con bloqueo: ve el estado más reciente). */
	private PlanPension bloquearConAnio(Long planId) {
		Long anioId = planes.anioDe(planId).orElseThrow(() -> new RecursoNoEncontradoException("Plan no encontrado"));
		anios.bloquear(anioId);
		return planes.bloquear(planId).orElseThrow(() -> new RecursoNoEncontradoException("Plan no encontrado"));
	}

	private static void exigirVersion(PlanPension plan, Long version) {
		if (version == null || !version.equals(plan.getVersion())) {
			throw new ReglaNegocioException("El plan cambió desde que lo abriste; revísalo de nuevo.");
		}
	}

	private boolean hayPendiente(AnioEscolar anio, Nivel nivel) {
		return planes.existsByAnioEscolarIdAndNivelAndEstado(anio.getId(), nivel, EstadoPlan.BORRADOR)
				|| planes.existsByAnioEscolarIdAndNivelAndEstado(anio.getId(), nivel, EstadoPlan.ENVIADO);
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void descartar(Long id, String motivo) {
		PlanPension plan = buscar(id);
		String texto = Motivo.exigir(motivo);
		plan.descartar(SesionActual.usuario(), ahora());
		auditoria.registrar(AccionAuditoria.PLAN_PENSION_DESCARTADO, "plan_pension", plan.getId().toString(),
				DescripcionCobranza.plan(plan), null, plan.nombre() + " descartado. Motivo: " + texto);
	}

	private void exigirSinBorrador(AnioEscolar anio, Nivel nivel) {
		if (hayPendiente(anio, nivel)) {
			throw new ReglaNegocioException("Ya hay un borrador de " + nivel.etiqueta() + " " + anio.getAnio()
					+ " por aprobar: edítalo o descártalo antes de proponer otro.");
		}
	}

	private static void exigirAbierto(AnioEscolar anio) {
		if (anio.cerrado()) {
			throw new ReglaNegocioException("El año " + anio.getAnio() + " está cerrado.");
		}
	}

	private PlanPension buscar(Long id) {
		return planes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Plan no encontrado"));
	}

	private AnioEscolar buscarAnio(Long id) {
		return anios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	static List<Grado> grados(Nivel nivel) {
		return Arrays.stream(Grado.values()).filter(g -> g.nivel() == nivel).toList();
	}

	static AnioOpcion opcion(AnioEscolar anio) {
		return new AnioOpcion(anio.getId(), anio.getAnio(), etiqueta(anio.getEstado()));
	}

	private static String etiqueta(EstadoAnioEscolar estado) {
		return estado.etiqueta();
	}

	static PlanResumen resumenDe(PlanPension p) {
		List<LocalDate> fechas = p.getVencimientos();
		return new PlanResumen(p.getId(), p.nombre(), p.getNumeroVersion(), p.getEstado().name(),
				p.getEstado().etiqueta(), p.getEstado().variante(), p.getMontoMatricula(), p.getMontoPension(),
				fechas.size(), fechas.isEmpty() ? null : fechas.get(0), fechas.isEmpty() ? null : fechas.get(fechas.size() - 1),
				p.configuracion().descripcionCobroDesde(), p.getEditadoPor(), p.getAprobadoPor(), p.getAprobadoEn(),
				p.getMotivoCambio());
	}
}
