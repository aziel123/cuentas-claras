package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.DiaDelMes;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.JustificarRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.MesAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.MesDeHijo;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConfiguracionAcademica;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MarcaAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.OrigenMarca;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.MarcaAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 2 (sección 3.5, decisión 150; E11, E12, E14, E53): la asistencia en el portal de la familia.
 * <ul>
 *   <li>{@link #delMes}: el mes de CADA hijo de la familia del apoderado en sesión (nunca de otra familia).</li>
 *   <li>{@link #justificar}: una FALTA de los últimos días hábiles del colegio (5 por defecto; la base acepta 15
 *       calendario) queda FALTA_JUSTIFICADA al instante, con un motivo de la lista, una nota opcional corta y la firma de
 *       la sesión del apoderado (A5). Nunca una falta que Dirección ya revirtió (A3). Un alumno de otra familia: 404.</li>
 * </ul>
 * Lo mismo para una familia con deuda que para una al día (INDECOPI: este servicio no puede saber quién debe).
 */
@Service
@PreAuthorize("hasRole('APODERADO')")
public class JustificacionesFamilia {

	private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy",
			Locale.forLanguageTag("es-PE"));

	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEE d", Locale.forLanguageTag("es-PE"));

	private final MarcaAsistenciaRepository marcas;

	private final AlumnoRepository alumnos;

	private final ApoderadoRepository apoderados;

	private final ConfiguracionesAcademicas configuraciones;

	private final CalendarioHabil calendario;

	private final SesionDeQuienEscribe sesiones;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public JustificacionesFamilia(MarcaAsistenciaRepository marcas, AlumnoRepository alumnos,
			ApoderadoRepository apoderados, ConfiguracionesAcademicas configuraciones, CalendarioHabil calendario,
			SesionDeQuienEscribe sesiones, FirmaSesion firma, AuditoriaService auditoria, Clock reloj) {
		this.marcas = marcas;
		this.alumnos = alumnos;
		this.apoderados = apoderados;
		this.configuraciones = configuraciones;
		this.calendario = calendario;
		this.sesiones = sesiones;
		this.firma = firma;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** El mes de los hijos de la familia (por defecto, el actual). */
	@Transactional(readOnly = true)
	public MesAsistencia delMes(YearMonth mes) {
		YearMonth elegido = mes == null ? YearMonth.now(reloj) : mes;
		Apoderado apoderado = apoderadoEnSesion();
		List<Alumno> hijos = alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(apoderado.getFamilia().getId());
		ConfiguracionAcademica configuracion = configuraciones.vigente();
		LocalDate hoy = LocalDate.now(reloj);
		Map<Long, List<MarcaAsistencia>> porAlumno = hijos.isEmpty() ? Map.of()
				: marcas.vigentesDeAlumnos(hijos.stream().map(Alumno::getId).toList(), elegido.atDay(1),
						elegido.atEndOfMonth()).stream().collect(Collectors.groupingBy(MarcaAsistencia::getAlumnoId));
		List<MesDeHijo> resultado = new ArrayList<>();
		for (Alumno hijo : hijos) {
			List<DiaDelMes> dias = new ArrayList<>();
			int faltas = 0;
			int tardes = 0;
			int justificadas = 0;
			for (MarcaAsistencia m : porAlumno.getOrDefault(hijo.getId(), List.of())) {
				faltas += m.getEstado() == EstadoAsistencia.FALTA ? 1 : 0;
				tardes += m.getEstado() == EstadoAsistencia.TARDE ? 1 : 0;
				justificadas += m.getEstado() == EstadoAsistencia.FALTA_JUSTIFICADA ? 1 : 0;
				dias.add(new DiaDelMes(m.getId(), m.getFecha(), m.getFecha().format(DIA), m.getEstado().name(),
						m.getEstado().etiqueta(), m.getEstado().variante(), m.getHoraLlegada(), m.getOrigen().etiqueta(),
						justificable(m, hoy, configuracion),
						m.getMotivoJustificacion() == null ? null : m.getMotivoJustificacion().etiqueta()));
			}
			resultado.add(new MesDeHijo(hijo.getId(), hijo.getNombres(), faltas, tardes, justificadas, dias));
		}
		return new MesAsistencia(elegido.toString(), elegido.format(MES), elegido.minusMonths(1).toString(),
				elegido.plusMonths(1).isAfter(YearMonth.from(hoy)) ? null : elegido.plusMonths(1).toString(),
				configuracion.getDiasJustificacion(), resultado);
	}

	/** Justifica una falta de su hijo (E11: de otra familia, 404; E14: fuera de plazo, se rechaza; E53: revertida, no). */
	@Transactional
	public void justificar(long marcaId, JustificarRequest pedido) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		Apoderado apoderado = apoderadoEnSesion();
		MarcaAsistencia vigente = marcas.findById(marcaId)
				.filter(m -> alumnos.findById(m.getAlumnoId()).map(a -> a.getFamilia().getId()
						.equals(apoderado.getFamilia().getId())).orElse(false))
				.orElseThrow(ServicioAsistencia::marcaNoEncontrada);
		if (marcas.existsByReemplazaId(vigente.getId())) {
			throw new ReglaNegocioException("Esa marca ya cambió: vuelve a abrir la asistencia del mes.");
		}
		ConfiguracionAcademica configuracion = configuraciones.vigente();
		if (vigente.getOrigen() == OrigenMarca.REVERSION_JUSTIFICACION) {
			throw new ReglaNegocioException("Dirección revirtió la justificación de esta falta: ya no se puede justificar "
					+ "desde el portal. Si es un error, comuníquese con el colegio.");
		}
		if (vigente.getEstado() != EstadoAsistencia.FALTA) {
			throw new ReglaNegocioException("Solo se justifica una falta.");
		}
		if (!dentroDelPlazo(vigente.getFecha(), LocalDate.now(reloj), configuracion)) {
			throw new ReglaNegocioException("Solo se justifican las faltas de los últimos "
					+ configuracion.getDiasJustificacion() + " días hábiles. Para una más antigua, comuníquese con el "
					+ "colegio.");
		}
		MarcaAsistencia nueva = MarcaAsistencia.justificacion(vigente, pedido.motivo(), pedido.nota(), apoderado.getId(),
				apoderado.getFamilia().getId(), sesiones.de(persona));
		firma.firmar(ClaveFirma.marcaJustificada(vigente.getId()));
		marcas.saveAndFlush(nueva);
		// La nota puede tener datos de salud: no va a la bitácora (solo el motivo de la lista).
		auditoria.registrar(AccionAuditoria.ASISTENCIA_JUSTIFICADA, ServicioAsistencia.ENTIDAD_MARCA,
				nueva.getId().toString(), vigente.getEstado().name(), nueva.getEstado().name(), "El apoderado "
						+ apoderado.getId() + " justificó la falta del " + vigente.getFecha() + " (marca " + vigente.getId()
						+ "). Motivo: " + pedido.motivo().etiqueta() + ".");
	}

	private boolean justificable(MarcaAsistencia m, LocalDate hoy, ConfiguracionAcademica configuracion) {
		return m.getEstado() == EstadoAsistencia.FALTA && m.getOrigen() != OrigenMarca.REVERSION_JUSTIFICACION
				&& dentroDelPlazo(m.getFecha(), hoy, configuracion);
	}

	/** Los últimos N días hábiles, contando hoy (y nunca más de 15 calendario: el tope de la base). */
	private boolean dentroDelPlazo(LocalDate fecha, LocalDate hoy, ConfiguracionAcademica configuracion) {
		return !fecha.isAfter(hoy) && !fecha.isBefore(hoy.minusDays(ServicioAsistencia.MAX_DIAS_TARDIO))
				&& calendario.habilesEntre(fecha, hoy) < configuracion.getDiasJustificacion();
	}

	private Apoderado apoderadoEnSesion() {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		Long id = persona.apoderadoId();
		if (id == null) {
			throw new RecursoNoEncontradoException("Apoderado no encontrado");
		}
		return apoderados.findById(id).filter(Apoderado::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("Apoderado no encontrado"));
	}
}
