package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.OpcionSeccion;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ReporteSeccion;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MarcaAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.MarcaAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.RegistroAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 2 (sección 3.4): el reporte mensual de asistencia por sección, con una fila por alumno. Lo ven el
 * tutor(a) de la sección y Dirección (Promotoría no, decisión 143; un docente que no es tutor, 404). Cada consulta queda en
 * «Quién vio datos personales» como {@code REPORTE_ASISTENCIA}, con la cantidad. Sin exportación en este sprint.
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','DOCENTE')")
public class ReportesAsistencia {

	private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy",
			Locale.forLanguageTag("es-PE"));

	private final SeccionesDelDocente seccionesDelDocente;

	private final SeccionRepository secciones;

	private final AnioEscolarRepository anios;

	private final RegistroAsistenciaRepository registros;

	private final MarcaAsistenciaRepository marcas;

	private final AlumnoRepository alumnos;

	private final Clock reloj;

	public ReportesAsistencia(SeccionesDelDocente seccionesDelDocente, SeccionRepository secciones,
			AnioEscolarRepository anios, RegistroAsistenciaRepository registros, MarcaAsistenciaRepository marcas,
			AlumnoRepository alumnos, Clock reloj) {
		this.seccionesDelDocente = seccionesDelDocente;
		this.secciones = secciones;
		this.anios = anios;
		this.registros = registros;
		this.marcas = marcas;
		this.alumnos = alumnos;
		this.reloj = reloj;
	}

	/** Las secciones que puede consultar: Dirección, todas las del año en curso; el docente, las suyas de tutor. */
	@Transactional(readOnly = true)
	public List<OpcionSeccion> seccionesDisponibles() {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		if (persona.roles().contains(Rol.DIRECTOR)) {
			return anios.findByEstado(EstadoAnioEscolar.EN_CURSO).map(a -> secciones
					.findByAnioEscolarIdOrderByGradoAscNombreAsc(a.getId()).stream()
					.map(s -> new OpcionSeccion(s.getId(), s.etiqueta(), false)).toList()).orElse(List.of());
		}
		return seccionesDelDocente.mias().stream().filter(SeccionDocente::tutor)
				.map(s -> new OpcionSeccion(s.seccionId(), s.seccion(), false)).toList();
	}

	/** El reporte de una sección en un mes (404 si no es de Dirección ni del tutor de la sección). */
	@Transactional(readOnly = true)
	public ReporteSeccion seccion(long seccionId, YearMonth mes) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		Seccion seccion;
		if (persona.roles().contains(Rol.DIRECTOR)) {
			seccion = secciones.findById(seccionId).orElseThrow(SeccionesDelDocente::noEncontrada);
		}
		else {
			if (!seccionesDelDocente.tutorDe(seccionId)) {
				throw SeccionesDelDocente.noEncontrada();
			}
			seccion = seccionesDelDocente.vigentesEn(seccionId, true).orElseThrow(SeccionesDelDocente::noEncontrada)
					.getKey();
		}
		YearMonth elegido = mes == null ? YearMonth.now(reloj) : mes;
		int dias = registros.findBySeccionIdAndFechaBetweenOrderByFechaAsc(seccionId, elegido.atDay(1),
				elegido.atEndOfMonth()).size();
		Map<Long, List<MarcaAsistencia>> porAlumno = marcas.vigentesDeSeccion(seccionId, elegido.atDay(1),
				elegido.atEndOfMonth()).stream().collect(Collectors.groupingBy(MarcaAsistencia::getAlumnoId,
						LinkedHashMap::new, Collectors.toList()));
		Map<Long, Alumno> nombres = alumnos.findAllById(porAlumno.keySet()).stream()
				.collect(Collectors.toMap(Alumno::getId, Function.identity()));
		List<ReporteSeccion.FilaReporte> filas = new ArrayList<>();
		porAlumno.keySet().stream().sorted(Comparator.comparing(id -> nombre(nombres.get(id)))).forEach(id -> {
			List<MarcaAsistencia> suyas = porAlumno.get(id);
			int presentes = contar(suyas, EstadoAsistencia.PRESENTE);
			int tardes = contar(suyas, EstadoAsistencia.TARDE);
			int faltas = contar(suyas, EstadoAsistencia.FALTA);
			int justificadas = contar(suyas, EstadoAsistencia.FALTA_JUSTIFICADA);
			String porcentaje = suyas.isEmpty() ? "0.0 %" : BigDecimal.valueOf(faltas * 100L)
					.divide(BigDecimal.valueOf(suyas.size()), 1, RoundingMode.HALF_UP).toPlainString() + " %";
			filas.add(new ReporteSeccion.FilaReporte(filas.size() + 1, id, nombre(nombres.get(id)), presentes, tardes,
					faltas, justificadas, porcentaje));
		});
		AccesoMostrado.filas(filas.size());
		return new ReporteSeccion(seccion.getId(), seccion.etiqueta(), elegido.format(MES), dias, filas);
	}

	private static int contar(List<MarcaAsistencia> marcas, EstadoAsistencia estado) {
		return (int) marcas.stream().filter(m -> m.getEstado() == estado).count();
	}

	private static String nombre(Alumno a) {
		return a == null ? "—" : ServicioAsistencia.apellidos(a) + ", " + a.getNombres();
	}
}
