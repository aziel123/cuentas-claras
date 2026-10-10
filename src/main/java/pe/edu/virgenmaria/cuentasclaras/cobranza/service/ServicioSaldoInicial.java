package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.config.PropiedadesSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaVista;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteDetalle;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteResumen;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.CalculadoraCronograma;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoLote;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.LineaSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.LoteSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TotalNoCoincideException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.LineaSaldoInicialRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.LoteSaldoInicialRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.PlanPensionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ControlParticipantes;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Saldo inicial (D7) con doble control y total de control:
 * <ul>
 *   <li>Administración crea el lote (referencia al informe del contador y total declarado), agrega y quita líneas
 *       (nunca se borran) y lo envía solo si la suma cuadra exactamente;</li>
 *   <li>Promotoría o Dirección lo confirman o lo devuelven con motivo. No puede confirmar quien lo creó, lo envió o
 *       le agregó líneas: el intento queda auditado ({@code noRollbackFor}). Al confirmar se crean las cuotas
 *       SALDO_INICIAL, todo o nada.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioSaldoInicial {

	private final LoteSaldoInicialRepository lotes;

	private final LineaSaldoInicialRepository lineas;

	private final CuotaRepository cuotas;

	private final AnioEscolarRepository anios;

	private final AlumnoRepository alumnos;

	private final MatriculaRepository matriculas;

	private final AuditoriaService auditoria;

	private final PlanPensionRepository planes;

	private final ControlParticipantes participantes;

	private final PropiedadesSaldoInicial propiedades;

	private final Clock reloj;

	/** Sprint 7, tanda 2: la firma de la sesión de quien resuelve (sección 3.4). */
	private final FirmaSesion firmaSesion;

	public ServicioSaldoInicial(LoteSaldoInicialRepository lotes, LineaSaldoInicialRepository lineas,
			CuotaRepository cuotas, AnioEscolarRepository anios,
			AlumnoRepository alumnos, MatriculaRepository matriculas, AuditoriaService auditoria,
			PlanPensionRepository planes, ControlParticipantes participantes, PropiedadesSaldoInicial propiedades,
			Clock reloj, FirmaSesion firmaSesion) {
		this.firmaSesion = firmaSesion;
		this.planes = planes;
		this.participantes = participantes;
		this.propiedades = propiedades;
		this.lotes = lotes;
		this.lineas = lineas;
		this.cuotas = cuotas;
		this.anios = anios;
		this.alumnos = alumnos;
		this.matriculas = matriculas;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	public List<LoteResumen> listar() {
		return lotes.findAllByOrderByCreadoEnDescIdDesc().stream()
				.map(l -> new LoteResumen(l.getId(), l.getAnioEscolar().getAnio(), l.getFechaCorte(),
						l.getDocumentoReferencia(), l.getTotalDeclarado(), l.totalLineas(), l.cuadra(),
						l.lineasVigentes().size(), l.getEstado().name(), l.getEstado().etiqueta(),
						l.getEstado().variante(), l.getCreadoPor(), l.getCreadoEn()))
				.toList();
	}

	/** Solo Administración arma lotes: decide si se muestra el formulario. */
	public boolean puedeArmarLotes() {
		return SesionActual.tieneAlgunRol("ADMINISTRACION");
	}

	/** Años para el formulario del lote (los no cerrados). */
	public List<AnioOpcion> aniosAbiertos() {
		return anios.findAllByOrderByAnioDesc().stream().filter(a -> !a.cerrado()).map(ServicioPlanesPension::opcion)
				.toList();
	}

	public LoteDetalle obtener(Long id) {
		LoteSaldoInicial lote = buscar(id);
		String usuario = SesionActual.usuario();
		boolean administracion = SesionActual.tieneAlgunRol("ADMINISTRACION");
		boolean confirmador = SesionActual.tieneAlgunRol("PROMOTOR", "DIRECTOR");
		boolean participo = participantes.ampliar(lote.participantes()).contains(usuario);
		boolean borrador = lote.getEstado() == EstadoLote.BORRADOR;
		boolean enviado = lote.getEstado() == EstadoLote.ENVIADO;
		Map<Long, Long> cuotaPorLinea = lote.getLineas().isEmpty() ? Map.of()
				: cuotas.findByLineaSaldoInicialIdIn(lote.getLineas().stream().map(LineaSaldoInicial::getId).toList())
						.stream().collect(Collectors.toMap(Cuota::getLineaSaldoInicialId, Cuota::getId));
		Map<Long, List<Cuota>> cronogramas = new HashMap<>();
		Long anioId = lote.getAnioEscolar().getId();
		List<LineaVista> vistas = lote.getLineas().stream()
				.map(l -> {
					List<Cuota> delAlumno = cronogramas.computeIfAbsent(l.getAlumno().getId(),
							a -> cuotas.findByAlumnoIdAndAnioEscolarId(a, anioId));
					return new LineaVista(l.getId(), l.getAlumno().getId(), l.getAlumno().nombreCompleto(),
							l.getAlumno().getDocumento().texto(), l.getConcepto().etiqueta(), l.getDescripcion(),
							l.getMonto(), l.getFechaVencimiento(), l.isQuitada(), l.getCreadoPor(),
							cuotaPorLinea.get(l.getId()), resumenCronograma(delAlumno, lote.getAnioEscolar().getAnio()),
							l.isQuitada() ? null : alerta(l, lote, delAlumno, cuotaPorLinea.get(l.getId())));
				})
				.toList();
		boolean aciegas = enviado && confirmador && !participo;
		String aviso = enviado && confirmador && participo
				? "Tú participaste en este lote (lo creaste, lo enviaste, le agregaste líneas o preparaste la cuenta de "
						+ "quien lo hizo): debe confirmarlo otra persona de Promotoría o Dirección." : null;
		List<String> bloqueos = enviado || borrador ? bloqueos(lote) : List.of();
		BigDecimal suma = lote.totalLineas();
		return new LoteDetalle(lote.getId(), anioId, lote.getAnioEscolar().getAnio(),
				lote.getFechaCorte(), lote.getDocumentoReferencia(), aciegas ? null : lote.getTotalDeclarado(),
				aciegas ? null : suma, aciegas ? null : lote.getTotalDeclarado().subtract(suma), lote.cuadra(),
				lote.getEstado().name(), lote.getEstado().etiqueta(), lote.getEstado().variante(), lote.getCreadoPor(),
				lote.getCreadoEn(), lote.getEnviadoPor(), lote.getEnviadoEn(), lote.getConfirmadoPor(),
				lote.getConfirmadoEn(), lote.getDevueltoPor(), lote.getMotivoDevolucion(), lote.getDescartadoPor(),
				lote.getMotivoDescarte(), vistas, borrador && administracion, borrador && administracion && lote.cuadra()
						&& !lote.lineasVigentes().isEmpty() && bloqueos.isEmpty(),
				aciegas && bloqueos.isEmpty(), enviado && confirmador, borrador && administracion, aviso, !aciegas,
				bloqueos, propiedades.conceptosOtros(), lote.getVersion());
	}

	/** «2026: 3 cuotas, saldo S/ 1,350.00» (lo que el alumno ya debe ese año). */
	private static String resumenCronograma(List<Cuota> delAlumno, int anio) {
		List<Cuota> vigentes = delAlumno.stream().filter(c -> !c.anulada()).toList();
		if (vigentes.isEmpty()) {
			return anio + ": sin cuotas";
		}
		return anio + ": " + vigentes.size() + (vigentes.size() == 1 ? " cuota" : " cuotas") + ", saldo "
				+ Dinero.formatear(Dinero.sumar(vigentes.stream().map(Cuota::saldo).toList()));
	}

	/** Duplicada en el lote o ya en el cronograma del alumno (misma deuda o misma descripción). */
	private static String alerta(LineaSaldoInicial linea, LoteSaldoInicial lote, List<Cuota> delAlumno, Long propia) {
		boolean duplicadaEnLote = lote.lineasVigentes().stream().anyMatch(o -> !o.getId().equals(linea.getId())
				&& o.getAlumno().getId().equals(linea.getAlumno().getId())
				&& (o.obligacion().equals(linea.obligacion()) || o.getDescripcion().equalsIgnoreCase(linea.getDescripcion())));
		if (duplicadaEnLote) {
			return "Duplicada en el lote";
		}
		boolean enCronograma = delAlumno.stream().anyMatch(c -> !c.anulada() && !c.getId().equals(propia)
				&& (linea.obligacion().equals(c.getObligacion()) || c.getDescripcion().equalsIgnoreCase(linea.getDescripcion())));
		return enCronograma ? "Ya está en su cronograma" : null;
	}

	/**
	 * Cuotas que esta confirmación bloquearía (auditoría C1): la deuda coincide con una cuota que el alumno ya tiene o
	 * que el plan vigente de su nivel le generaría. Si hay alguna, el lote no se confirma.
	 */
	private List<String> bloqueos(LoteSaldoInicial lote) {
		List<String> bloqueos = new ArrayList<>();
		for (LineaSaldoInicial linea : lote.lineasVigentes()) {
			String obligacion = linea.obligacion();
			String quien = linea.getAlumno().nombreCompleto() + ": " + linea.getDescripcion();
			if (cuotas.existsByClave(linea.clave())
					|| !cuotas.findByAlumnoIdAndObligacionIn(linea.getAlumno().getId(), List.of(obligacion)).isEmpty()) {
				bloqueos.add(quien + " ya está en su cronograma");
				continue;
			}
			if (linea.getConcepto() == ConceptoSaldo.OTRO) {
				continue;
			}
			Optional<Matricula> matricula = anios.findByAnio(linea.getAnioDeuda()).flatMap(a -> matriculas
					.findByAlumnoIdAndAnioEscolarId(linea.getAlumno().getId(), a.getId()));
			matricula.flatMap(m -> planes.findByAnioEscolarIdAndNivelAndVigenteTrue(m.getAnioEscolar().getId(), m.nivel())
					.filter(plan -> CalculadoraCronograma.calcular(plan, m.getId(), m.getFechaMatricula()).stream()
							.anyMatch(c -> c.obligacion().equals(obligacion)))
					.map(PlanPension::nombre))
					.ifPresent(plan -> bloqueos.add(quien + " la cobra también el " + plan
							+ ": confirmarla bloquearía esa cuota"));
		}
		return bloqueos;
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public Long crearLote(LoteRequest solicitud) {
		AnioEscolar anio = anios.findById(solicitud.anioId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
		if (anio.cerrado()) {
			throw new ReglaNegocioException("El año " + anio.getAnio() + " está cerrado.");
		}
		LoteSaldoInicial lote = lotes.save(LoteSaldoInicial.nuevo(anio, solicitud.fechaCorte(),
				solicitud.documentoReferencia(), solicitud.totalDeclarado(), LocalDate.now(reloj)));
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_LOTE_CREADO, "lote_saldo_inicial", lote.getId().toString(),
				null, cabecera(lote), "Lote " + lote.getId() + " del año " + anio.getAnio() + " (en preparación).");
		return lote.getId();
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public Long agregarLinea(Long loteId, LineaSaldoRequest solicitud) {
		LoteSaldoInicial lote = bloquear(loteId);
		Alumno alumno = alumnoPorDocumento(solicitud.documentoAlumno());
		// Primero las reglas de la deuda (año, mes, corte, concepto); luego, que el alumno esté matriculado ese año
		// (que es el del lote: la entidad ya exigió que coincidan).
		LineaSaldoInicial linea = lote.agregarLinea(alumno, solicitud.concepto(), solicitud.anio(), solicitud.mes(),
				solicitud.descripcion(), solicitud.monto(), solicitud.vencimiento(), propiedades.conceptosOtros());
		exigirMatriculaActiva(alumno, lote.getAnioEscolar().getAnio());
		String obligacion = linea.obligacion();
		if (!cuotas.findByAlumnoIdAndObligacionIn(alumno.getId(), List.of(obligacion)).isEmpty()) {
			throw new ReglaNegocioException(alumno.nombreCompleto() + " ya tiene la cuota «" + linea.getDescripcion()
					+ "» en su cronograma: no la cargues también como saldo inicial.");
		}
		lineas.saveAndFlush(linea);
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_LINEA_AGREGADA, "lote_saldo_inicial", loteId.toString(), null,
				linea(linea), "Línea " + linea.getId() + " del lote " + loteId + ". Suma de líneas: "
						+ Dinero.formatear(lote.totalLineas()) + " de " + Dinero.formatear(lote.getTotalDeclarado())
						+ " declarados.");
		return linea.getId();
	}

	/** Auditoría A2 (c): solo alumnos activos y matriculados (activos) en el año de la deuda. */
	private void exigirMatriculaActiva(Alumno alumno, int anio) {
		if (!alumno.activo()) {
			throw new ReglaNegocioException(alumno.nombreCompleto() + " está retirado: no se le carga saldo inicial.");
		}
		boolean matriculado = anios.findByAnio(anio)
				.flatMap(a -> matriculas.findByAlumnoIdAndAnioEscolarId(alumno.getId(), a.getId()))
				.filter(Matricula::activa).isPresent();
		if (!matriculado) {
			throw new ReglaNegocioException(alumno.nombreCompleto() + " no tiene una matrícula activa en " + anio
					+ ": no se le carga una deuda de ese año.");
		}
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void quitarLinea(Long loteId, Long lineaId, String motivo) {
		String texto = Motivo.exigir(motivo);
		LoteSaldoInicial lote = bloquear(loteId);
		LineaSaldoInicial linea = lote.quitarLinea(lineaId);
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_LINEA_QUITADA, "lote_saldo_inicial", loteId.toString(),
				linea(linea), null, "Línea " + lineaId + " quitada (no se borra). Motivo: " + texto);
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void enviar(Long loteId) {
		LoteSaldoInicial lote = bloquear(loteId);
		lote.enviar(SesionActual.usuario(), ahora());
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_ENVIADO, "lote_saldo_inicial", loteId.toString(), null,
				cabecera(lote) + " · " + lote.lineasVigentes().size() + " líneas que suman "
						+ Dinero.formatear(lote.totalLineas()),
				"Cuadra con el total declarado. Falta la confirmación de Promotoría o Dirección.");
	}

	/**
	 * Confirma el lote y crea las cuotas SALDO_INICIAL, todo o nada. Exige: la versión que vio quien confirma (el lote
	 * no cambió), el total del informe del contador escrito a ciegas (igual al declarado), que quien confirma no haya
	 * participado (ni preparado la cuenta de quien participó) y que ninguna deuda bloquee una cuota existente o futura.
	 * Los intentos de autoaprobación y de total distinto quedan auditados aunque se rechacen.
	 */
	@Transactional(noRollbackFor = { AutoaprobacionException.class, TotalNoCoincideException.class })
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public int confirmar(Long loteId, Long version, BigDecimal totalInforme) {
		LoteSaldoInicial lote = bloquearConAnio(loteId);
		String usuario = SesionActual.usuario();
		if (lote.getEstado() != EstadoLote.ENVIADO) {
			throw new ReglaNegocioException("Solo se confirma un lote enviado (este está "
					+ lote.getEstado().etiqueta().toLowerCase() + ").");
		}
		exigirVersion(lote, version);
		if (participantes.ampliar(lote.participantes()).contains(usuario)) {
			auditoria.registrar(AccionAuditoria.AUTOAPROBACION_RECHAZADA, "lote_saldo_inicial", loteId.toString(), null,
					cabecera(lote), "Intentó confirmar el lote " + loteId + ", en el que participó (lo creó, lo envió, "
							+ "le agregó líneas o preparó la cuenta de quien lo hizo). Se rechazó.");
			throw new AutoaprobacionException("No puedes confirmar un lote en el que participaste: debe confirmarlo "
					+ "otra persona de Promotoría o Dirección.");
		}
		List<String> bloqueos = bloqueos(lote);
		if (!bloqueos.isEmpty()) {
			throw new ReglaNegocioException("No se confirmó nada: " + String.join("; ", bloqueos)
					+ ". Devuelve el lote para que Administración quite esas deudas.");
		}
		try {
			// Sprint 7, tanda 2: firma solo si el total coincide (un intento fallido no confirma nada).
			if (totalInforme != null && pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero.normalizar(totalInforme)
					.compareTo(lote.getTotalDeclarado()) == 0) {
				firmaSesion.firmar(ClaveFirma.loteSaldoInicialConfirmado(lote.getId()));
			}
			lote.confirmar(usuario, totalInforme, ahora());
		}
		catch (TotalNoCoincideException e) {
			auditoria.registrar(AccionAuditoria.SALDO_INICIAL_TOTAL_NO_COINCIDE, "lote_saldo_inicial", loteId.toString(),
					null, "Total escrito: " + (totalInforme == null ? "—" : Dinero.formatear(totalInforme)),
					cabecera(lote) + ". Quien confirmaba escribió otro total: no se confirmó.");
			throw e;
		}
		List<LineaSaldoInicial> vigentes = lote.lineasVigentes();
		Long anioId = lote.getAnioEscolar().getId();
		for (LineaSaldoInicial linea : vigentes) {
			cuotas.save(Cuota.deSaldoInicial(linea,
					matriculas.findByAlumnoIdAndAnioEscolarId(linea.getAlumno().getId(), anioId).orElse(null)));
		}
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_CONFIRMADO, "lote_saldo_inicial", loteId.toString(),
				EstadoLote.ENVIADO.name(), cabecera(lote) + " · " + vigentes.size() + " cuotas creadas por "
						+ Dinero.formatear(lote.totalLineas()) + " · total del informe confirmado a ciegas",
				"Lote creado por " + lote.getCreadoPor() + " y enviado por " + lote.getEnviadoPor() + ".");
		return vigentes.size();
	}

	/** Bloquea primero el año y luego el lote (mismo orden que matricular y aprobar planes: sin bloqueo mutuo). */
	private LoteSaldoInicial bloquearConAnio(Long loteId) {
		Long anioId = lotes.anioDe(loteId).orElseThrow(() -> new RecursoNoEncontradoException("Lote no encontrado"));
		anios.bloquear(anioId);
		return bloquear(loteId);
	}

	private static void exigirVersion(LoteSaldoInicial lote, Long version) {
		if (version == null || !version.equals(lote.getVersion())) {
			throw new ReglaNegocioException("El lote cambió desde que lo abriste; revísalo de nuevo.");
		}
	}

	@Transactional
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public void devolver(Long loteId, Long version, String motivo) {
		LoteSaldoInicial lote = bloquear(loteId);
		exigirVersion(lote, version);
		LocalDateTime cuando = ahora();
		firmaSesion.firmar(ClaveFirma.loteSaldoInicialDevuelto(lote.getId(), cuando));
		lote.devolver(SesionActual.usuario(), motivo, cuando);
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_DEVUELTO, "lote_saldo_inicial", loteId.toString(),
				EstadoLote.ENVIADO.name(), EstadoLote.BORRADOR.name(), "Motivo: " + lote.getMotivoDevolucion());
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void descartar(Long loteId, String motivo) {
		LoteSaldoInicial lote = bloquear(loteId);
		lote.descartar(SesionActual.usuario(), motivo, ahora());
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_DESCARTADO, "lote_saldo_inicial", loteId.toString(),
				cabecera(lote), null, "Motivo: " + lote.getMotivoDescarte());
	}

	private Alumno alumnoPorDocumento(String documento) {
		String numero = Normalizador.sinEspacios(documento);
		if (numero == null) {
			throw new ReglaNegocioException("Escribe el DNI o documento del alumno.");
		}
		List<Alumno> encontrados = alumnos.findByDocumentoNumeroIn(List.of(numero.toUpperCase(Locale.ROOT)));
		if (encontrados.isEmpty()) {
			throw new ReglaNegocioException("No hay un alumno con el documento «" + numero + "». Regístralo o "
					+ "impórtalo primero.");
		}
		if (encontrados.size() > 1) {
			throw new ReglaNegocioException("Hay más de un alumno con el número «" + numero + "» (con distinto tipo de "
					+ "documento). Revisa sus fichas.");
		}
		return encontrados.get(0);
	}

	private LoteSaldoInicial buscar(Long id) {
		return lotes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Lote no encontrado"));
	}

	private LoteSaldoInicial bloquear(Long id) {
		return lotes.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Lote no encontrado"));
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	private static String cabecera(LoteSaldoInicial lote) {
		return "Corte " + Calendario.formatear(lote.getFechaCorte()) + " · Informe: " + lote.getDocumentoReferencia()
				+ " · Total declarado " + Dinero.formatear(lote.getTotalDeclarado());
	}

	private static String linea(LineaSaldoInicial l) {
		return l.getAlumno().nombreCompleto() + " (" + l.getAlumno().getDocumento().enmascarado() + "): "
				+ l.getDescripcion() + " " + Dinero.formatear(l.getMonto()) + ", vence "
				+ Calendario.formatear(l.getFechaVencimiento());
	}
}
