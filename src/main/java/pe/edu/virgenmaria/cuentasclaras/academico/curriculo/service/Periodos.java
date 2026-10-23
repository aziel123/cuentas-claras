package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.OpcionAnio;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.PeriodoVista;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.PeriodosVista;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.ConfiguracionEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 10, tanda 1 (sección 3.3; E9, E10, E41 y M7): los periodos de evaluación del año. Todo lo hace SOLO Dirección, con
 * la firma de su sesión en cada acción:
 * <ul>
 *   <li><b>programar</b> el siguiente periodo (en orden, dentro del año y sin solaparse), que nace PROGRAMADO
 *       ({@code periodo_evaluacion:{anio}:{numero}:PROGRAMADO});</li>
 *   <li><b>mover las fechas</b> ({@code periodo_evaluacion:{id}:FECHAS:{version}}): en PROGRAMADO, las dos; en ABIERTO, solo
 *       extender el fin (M7); en CERRADO, nada;</li>
 *   <li><b>abrir antes de tiempo</b> ({@code …:ABIERTO}), con el anterior ya abierto; en su fecha lo abre
 *       {@code sistema.academico} ({@code AperturaPeriodos});</li>
 *   <li><b>cerrar</b> ({@code …:CERRADO}) desde {@code fin − dias_cierre_anticipado} (hoy en Lima; en MySQL decide la hora
 *       de la base, E9), con el anterior CERRADO. Un periodo cerrado no se reabre (E10).</li>
 * </ul>
 * Cada acción queda en la bitácora. En MySQL lo exigen además trg_periodo_evaluacion_nace y _cambio.
 */
@Service
@PreAuthorize("hasRole('DIRECTOR')")
public class Periodos {

	static final String ENTIDAD = "periodo_evaluacion";

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final PeriodoEvaluacionRepository periodos;

	private final ConfiguracionEvaluacionRepository configuraciones;

	private final AnioEscolarRepository anios;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public Periodos(PeriodoEvaluacionRepository periodos, ConfiguracionEvaluacionRepository configuraciones,
			AnioEscolarRepository anios, FirmaSesion firma, AuditoriaService auditoria, Clock reloj) {
		this.periodos = periodos;
		this.configuraciones = configuraciones;
		this.anios = anios;
		this.firma = firma;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** La pantalla del año (por defecto, el en curso o el último que no está cerrado). */
	@Transactional(readOnly = true)
	public PeriodosVista vista(Long anioId) {
		List<AnioEscolar> todos = anios.findAllByOrderByAnioDesc();
		List<OpcionAnio> opciones = AniosDeEvaluacion.opciones(todos);
		Optional<AnioEscolar> elegido = AniosDeEvaluacion.elegir(todos, anioId);
		LocalDate hoy = LocalDate.now(reloj);
		if (elegido.isEmpty()) {
			return new PeriodosVista(opciones, null, null, false, null, null, false, null, 0, 0, List.of(), null, null, hoy);
		}
		AnioEscolar anio = elegido.get();
		Optional<ConfiguracionEvaluacion> configuracion = configuraciones.findByAnioEscolarId(anio.getId());
		List<PeriodoEvaluacion> delAnio = periodos.findByAnioEscolarIdOrderByNumeroAsc(anio.getId());
		int dias = configuracion.map(ConfiguracionEvaluacion::getDiasCierreAnticipado).orElse(0);
		List<PeriodoVista> filas = delAnio.stream().map(p -> vista(p, delAnio, dias, hoy, anio.cerrado())).toList();
		Integer siguiente = configuracion.filter(c -> !anio.cerrado() && delAnio.size() < c.getNumeroPeriodos())
				.map(c -> delAnio.size() + 1).orElse(null);
		return new PeriodosVista(opciones, anio.getId(), anio.getAnio(), anio.cerrado(), anio.getInicioClases(),
				anio.getFinClases(), configuracion.isPresent(), configuracion.map(c -> c.getTipoPeriodo().etiqueta())
						.orElse(null), configuracion.map(ConfiguracionEvaluacion::getNumeroPeriodos).orElse(0), dias, filas,
				siguiente, siguiente == null ? null : configuracion.get().getTipoPeriodo().nombreDe(siguiente), hoy);
	}

	/** Programa el siguiente periodo del año, PROGRAMADO, con la firma de Dirección. @return su id */
	@Transactional
	public Long programar(Long anioId, LocalDate inicio, LocalDate fin) {
		UsuarioAutenticado persona = QuienEnSesion.actual();
		AnioEscolar anio = anios.bloquear(anioId).orElseThrow(() -> new RecursoNoEncontradoException(
				"Año escolar no encontrado"));
		if (anio.cerrado()) {
			throw new ReglaNegocioException("El año " + anio.getAnio() + " está cerrado: ya no se programan periodos.");
		}
		ConfiguracionEvaluacion configuracion = configuraciones.findByAnioEscolarId(anio.getId())
				.orElseThrow(() -> new ReglaNegocioException("Primero guarda la evaluación del año " + anio.getAnio()
						+ " (cómo se divide el año): los periodos salen de ahí."));
		List<PeriodoEvaluacion> delAnio = periodos.findByAnioEscolarIdOrderByNumeroAsc(anio.getId());
		int numero = delAnio.size() + 1;
		if (numero > configuracion.getNumeroPeriodos()) {
			throw new ReglaNegocioException("El año ya tiene sus " + configuracion.getNumeroPeriodos() + " periodos.");
		}
		String nombre = configuracion.getTipoPeriodo().nombreDe(numero);
		PeriodoEvaluacion nuevo = PeriodoEvaluacion.programado(anio.getId(), numero, nombre, inicio, fin,
				persona.getUsername());
		exigirDentroDelAnio(anio, inicio, fin);
		if (numero > 1) {
			PeriodoEvaluacion anterior = delAnio.get(numero - 2);
			if (!anterior.getFin().isBefore(inicio)) {
				throw new ReglaNegocioException("El " + nombre + " empieza después de que termine el " + anterior.getNombre()
						+ " (el " + anterior.getFin().format(FECHA) + "): los periodos no se solapan.");
			}
		}
		firma.firmar(ClaveFirma.periodoProgramado(anio.getId(), numero));
		PeriodoEvaluacion guardado = periodos.saveAndFlush(nuevo);
		auditoria.registrar(AccionAuditoria.PERIODO_PROGRAMADO, ENTIDAD, guardado.getId().toString(), null,
				EstadoPeriodo.PROGRAMADO.name(), persona.nombreCompleto() + " programó el " + nombre + " de "
						+ anio.getAnio() + ": del " + inicio.format(FECHA) + " al " + fin.format(FECHA) + ".");
		return guardado.getId();
	}

	/** Mueve las fechas de un periodo con la firma de Dirección (en ABIERTO, el fin solo se extiende: M7). */
	@Transactional
	public void moverFechas(Long periodoId, LocalDate inicio, LocalDate fin) {
		UsuarioAutenticado persona = QuienEnSesion.actual();
		PeriodoEvaluacion periodo = bloquear(periodoId);
		AnioEscolar anio = anioDe(periodo);
		String antes = rango(periodo.getInicio(), periodo.getFin());
		if (periodo.getInicio().equals(inicio) && periodo.getFin().equals(fin)) {
			return;
		}
		// Los demás periodos se leen ANTES de cambiar este: la consulta vaciaría el cambio a la base antes de la firma.
		List<PeriodoEvaluacion> delAnio = periodos.findByAnioEscolarIdOrderByNumeroAsc(anio.getId());
		long versionNueva = periodo.getVersion() + 1;
		periodo.moverFechas(inicio, fin, persona.getUsername());
		exigirDentroDelAnio(anio, inicio, fin);
		for (PeriodoEvaluacion otro : delAnio) {
			if (otro.getId().equals(periodo.getId())) {
				continue;
			}
			if (otro.getNumero() == periodo.getNumero() - 1 && !otro.getFin().isBefore(inicio)) {
				throw new ReglaNegocioException("El " + periodo.getNombre() + " empieza después de que termine el "
						+ otro.getNombre() + " (el " + otro.getFin().format(FECHA) + ").");
			}
			if (otro.getNumero() == periodo.getNumero() + 1 && !otro.getInicio().isAfter(fin)) {
				throw new ReglaNegocioException("El " + periodo.getNombre() + " termina antes de que empiece el "
						+ otro.getNombre() + " (el " + otro.getInicio().format(FECHA) + "): mueve primero ese periodo.");
			}
		}
		// La firma de Dirección con la versión que tendrá la fila (trg_periodo_evaluacion_cambio la exige).
		firma.firmar(ClaveFirma.periodoFechas(periodo.getId(), versionNueva));
		periodos.saveAndFlush(periodo);
		auditoria.registrar(AccionAuditoria.PERIODO_FECHAS_CAMBIADAS, ENTIDAD, periodo.getId().toString(), antes,
				rango(inicio, fin), persona.nombreCompleto() + " cambió las fechas del " + periodo.getNombre() + " de "
						+ anio.getAnio() + " (" + periodo.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT)
						+ "). Antes: " + antes + ". Ahora: " + rango(inicio, fin) + ".");
	}

	/** Dirección abre un periodo antes de su fecha, con su firma (el anterior debe estar abierto o cerrado). */
	@Transactional
	public void abrir(Long periodoId) {
		UsuarioAutenticado persona = QuienEnSesion.actual();
		PeriodoEvaluacion periodo = bloquear(periodoId);
		AnioEscolar anio = anioDe(periodo);
		if (anio.cerrado()) {
			throw new ReglaNegocioException("El año " + anio.getAnio() + " está cerrado.");
		}
		exigirAnteriorAbierto(periodo);
		firma.firmar(ClaveFirma.periodoAbierto(periodo.getId()));
		periodo.abrir(persona.getUsername(), ahora());
		periodos.saveAndFlush(periodo);
		LocalDate hoy = LocalDate.now(reloj);
		auditoria.registrar(AccionAuditoria.PERIODO_ABIERTO, ENTIDAD, periodo.getId().toString(),
				EstadoPeriodo.PROGRAMADO.name(), EstadoPeriodo.ABIERTO.name(), persona.nombreCompleto() + " abrió el "
						+ periodo.getNombre() + " de " + anio.getAnio() + (periodo.getInicio().isAfter(hoy)
								? " antes de su fecha (empezaba el " + periodo.getInicio().format(FECHA) + ")" : "")
						+ ". Desde ahora los docentes registran sus notas.");
	}

	/**
	 * Dirección cierra un periodo con su firma, desde {@code fin − dias_cierre_anticipado} y con el anterior CERRADO. Un
	 * periodo cerrado no se reabre: lo que cambie después va por solicitud, nota por nota (tanda 2).
	 */
	@Transactional
	public void cerrar(Long periodoId) {
		UsuarioAutenticado persona = QuienEnSesion.actual();
		PeriodoEvaluacion periodo = bloquear(periodoId);
		AnioEscolar anio = anioDe(periodo);
		ConfiguracionEvaluacion configuracion = configuraciones.findByAnioEscolarId(anio.getId())
				.orElseThrow(() -> new IllegalStateException("Un periodo sin la configuración de su año"));
		if (periodo.getEstado() != EstadoPeriodo.ABIERTO) {
			throw new ReglaNegocioException("Solo se cierra un periodo abierto (el " + periodo.getNombre() + " está "
					+ periodo.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT) + ").");
		}
		LocalDate desde = periodo.cierreDesde(configuracion.getDiasCierreAnticipado());
		if (LocalDate.now(reloj).isBefore(desde)) {
			throw new ReglaNegocioException("El " + periodo.getNombre() + " se puede cerrar desde el " + desde.format(FECHA)
					+ " (" + configuracion.getDiasCierreAnticipado() + " días antes de su fin, el "
					+ periodo.getFin().format(FECHA) + ").");
		}
		if (periodo.getNumero() > 1) {
			PeriodoEvaluacion anterior = periodos.findByAnioEscolarIdAndNumero(anio.getId(), periodo.getNumero() - 1)
					.orElseThrow(() -> new IllegalStateException("Falta el periodo anterior"));
			if (anterior.getEstado() != EstadoPeriodo.CERRADO) {
				throw new ReglaNegocioException("Primero cierra el " + anterior.getNombre() + ".");
			}
		}
		firma.firmar(ClaveFirma.periodoCerrado(periodo.getId()));
		periodo.cerrar(persona.getUsername(), ahora());
		periodos.saveAndFlush(periodo);
		auditoria.registrar(AccionAuditoria.PERIODO_CERRADO, ENTIDAD, periodo.getId().toString(),
				EstadoPeriodo.ABIERTO.name(), EstadoPeriodo.CERRADO.name(), persona.nombreCompleto() + " cerró el "
						+ periodo.getNombre() + " de " + anio.getAnio() + ". No se reabre: un cambio de nota después del "
						+ "cierre va por solicitud y lo aprueba otra persona.");
	}

	private PeriodoEvaluacion bloquear(Long periodoId) {
		return periodos.bloquear(periodoId).orElseThrow(() -> new RecursoNoEncontradoException("Periodo no encontrado"));
	}

	private AnioEscolar anioDe(PeriodoEvaluacion periodo) {
		return anios.findById(periodo.getAnioEscolarId()).orElseThrow(() -> new IllegalStateException("Periodo sin año"));
	}

	private void exigirAnteriorAbierto(PeriodoEvaluacion periodo) {
		if (periodo.getEstado() != EstadoPeriodo.PROGRAMADO) {
			throw new ReglaNegocioException("El " + periodo.getNombre() + " ya está "
					+ periodo.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT) + ".");
		}
		if (periodo.getNumero() > 1) {
			PeriodoEvaluacion anterior = periodos.findByAnioEscolarIdAndNumero(periodo.getAnioEscolarId(),
					periodo.getNumero() - 1).orElseThrow(() -> new IllegalStateException("Falta el periodo anterior"));
			if (anterior.getEstado() == EstadoPeriodo.PROGRAMADO) {
				throw new ReglaNegocioException("Primero abre el " + anterior.getNombre() + ".");
			}
		}
	}

	private static void exigirDentroDelAnio(AnioEscolar anio, LocalDate inicio, LocalDate fin) {
		if (inicio.isBefore(anio.getInicioClases()) || fin.isAfter(anio.getFinClases())) {
			throw new ReglaNegocioException("El periodo va dentro de las clases de " + anio.getAnio() + ": del "
					+ anio.getInicioClases().format(FECHA) + " al " + anio.getFinClases().format(FECHA) + ".");
		}
	}

	private static PeriodoVista vista(PeriodoEvaluacion p, List<PeriodoEvaluacion> delAnio, int dias, LocalDate hoy,
			boolean anioCerrado) {
		Optional<PeriodoEvaluacion> anterior = delAnio.stream().filter(o -> o.getNumero() == p.getNumero() - 1).findFirst();
		boolean puedeAbrir = !anioCerrado && p.getEstado() == EstadoPeriodo.PROGRAMADO
				&& anterior.map(a -> a.getEstado() != EstadoPeriodo.PROGRAMADO).orElse(true);
		LocalDate desde = p.cierreDesde(dias);
		boolean puedeCerrar = p.getEstado() == EstadoPeriodo.ABIERTO && !hoy.isBefore(desde)
				&& anterior.map(a -> a.getEstado() == EstadoPeriodo.CERRADO).orElse(true);
		return new PeriodoVista(p.getId(), p.getNumero(), p.getNombre(), p.getInicio(), p.getFin(), p.getEstado().name(),
				p.getEstado().etiqueta(), p.getEstado().variante(), p.getAbiertoPor(), p.getAbiertoEn(), p.getCerradoPor(),
				p.getCerradoEn(), desde, puedeAbrir, puedeCerrar, p.getEstado() == EstadoPeriodo.PROGRAMADO,
				p.getEstado() != EstadoPeriodo.CERRADO);
	}

	private static String rango(LocalDate inicio, LocalDate fin) {
		return "del " + inicio.format(FECHA) + " al " + fin.format(FECHA);
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
