package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConfiguracionAcademica;
import pe.edu.virgenmaria.cuentasclaras.academico.model.RegistroAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.MarcaAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.RegistroAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 9, tanda 2 (decisión 149; A4, A7, E47): a la hora de alerta del colegio (09:30 por defecto), un día de clases,
 * las secciones activas del año en curso que no registraron la asistencia o cuyo registro tiene MENOS marcas iniciales
 * que matrículas ACTIVAS. Una alerta por día, a DIRECCIÓN (al celular, por el camino de las alertas del sprint 6 con
 * {@link TipoAviso#SECCION_SIN_ASISTENCIA}), con el número; el panel la lista con los nombres de las secciones (para saber
 * a quién cubrir) y nunca con nombres de alumnos.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class AlertasAcademicas implements AlertasRevision {

	private static final DateTimeFormatter DIA = DateTimeFormatter.BASIC_ISO_DATE;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final MatriculaRepository matriculas;

	private final RegistroAsistenciaRepository registros;

	private final MarcaAsistenciaRepository marcas;

	private final ConfiguracionesAcademicas configuraciones;

	private final DiaDeClases diaDeClases;

	private final Clock reloj;

	private final pe.edu.virgenmaria.cuentasclaras.academico.repository.ConsentimientoAvisoRepository consentimientos;

	public AlertasAcademicas(AnioEscolarRepository anios, SeccionRepository secciones, MatriculaRepository matriculas,
			RegistroAsistenciaRepository registros, MarcaAsistenciaRepository marcas,
			ConfiguracionesAcademicas configuraciones, DiaDeClases diaDeClases, Clock reloj,
			pe.edu.virgenmaria.cuentasclaras.academico.repository.ConsentimientoAvisoRepository consentimientos) {
		this.consentimientos = consentimientos;
		this.anios = anios;
		this.secciones = secciones;
		this.matriculas = matriculas;
		this.registros = registros;
		this.marcas = marcas;
		this.configuraciones = configuraciones;
		this.diaDeClases = diaDeClases;
		this.reloj = reloj;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AlertaRevision> alertas() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		List<AlertaRevision> alertas = new ArrayList<>(consentimientosDesdeFicha(ahora));
		ConfiguracionAcademica configuracion = configuraciones.vigente();
		if (ahora.toLocalTime().isBefore(configuracion.getHoraAlerta())) {
			return alertas;
		}
		List<String> faltan = seccionesSinAsistencia(ahora.toLocalDate());
		if (faltan.isEmpty()) {
			return alertas;
		}
		alertas.add(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, "Asistencia", "Secciones sin asistencia "
				+ "registrada (o incompleta) hoy: " + String.join(", ", faltan) + ". Dirección decide quién la cubre.", null,
				new Aviso(TipoAviso.SECCION_SIN_ASISTENCIA, "ASISTENCIA:" + ahora.toLocalDate().format(DIA),
						String.valueOf(faltan.size()))));
		return alertas;
	}

	/** La referencia de un aviso admite letras, cifras, «:» y «-» (hasta 40). */
	private static String referencia(String texto) {
		String limpia = texto.replaceAll("[^A-Za-z0-9:-]", "-");
		return limpia.length() > 40 ? limpia.substring(0, 40) : limpia;
	}

	/** Más de esto en un día por la misma persona desde la ficha: alerta a Dirección (correcciones del sprint 9). */
	static final int MAX_FICHAS_DIA = 30;

	/**
	 * Correcciones del sprint 9 (auditoría, 6): quien registra más de {@value #MAX_FICHAS_DIA} consentimientos desde la
	 * ficha firmada en un día. Va a Dirección (TipoAviso.soloDireccion), con el usuario y la cantidad.
	 */
	List<AlertaRevision> consentimientosDesdeFicha(LocalDateTime ahora) {
		Map<String, Long> porPersona = new java.util.TreeMap<>();
		consentimientos.findByOrigenAndCreadoEnGreaterThanEqual(
				pe.edu.virgenmaria.cuentasclaras.academico.model.OrigenConsentimiento.FICHA, ahora.toLocalDate().atStartOfDay())
				.forEach(c -> porPersona.merge(c.getCreadoPor(), 1L, Long::sum));
		List<AlertaRevision> alertas = new ArrayList<>();
		porPersona.forEach((quien, cuantos) -> {
			if (cuantos > MAX_FICHAS_DIA) {
				alertas.add(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, "Consentimientos", quien + " registró hoy "
						+ cuantos + " consentimientos desde la ficha firmada: revisa las fichas en papel.", "/auditoria",
						new Aviso(TipoAviso.CONSENTIMIENTOS_DESDE_FICHA, referencia("FICHAS:" + ahora.toLocalDate().format(DIA)
								+ ":" + quien), String.valueOf(cuantos))));
			}
		});
		return alertas;
	}

	/** Las etiquetas de las secciones activas con clases ese día sin registro o con el registro incompleto (A4). */
	@Transactional(readOnly = true)
	public List<String> seccionesSinAsistencia(LocalDate fecha) {
		Optional<AnioEscolar> anio = anios.findByEstado(EstadoAnioEscolar.EN_CURSO);
		if (anio.isEmpty()) {
			return List.of();
		}
		// QA-S9-6: se comparan ALUMNOS (matrículas activas sin su marca inicial), no cantidades: si el mismo día se retira un
		// alumno ya marcado y se matricula otro, el nuevo sin marca deja el registro incompleto.
		Map<Long, java.util.Set<Long>> activas = new HashMap<>();
		for (Object[] fila : matriculas.alumnosActivosPorSeccion(anio.get().getId())) {
			activas.computeIfAbsent((Long) fila[0], k -> new java.util.HashSet<>()).add((Long) fila[1]);
		}
		Map<Long, RegistroAsistencia> delDia = new HashMap<>();
		registros.findByFecha(fecha).forEach(r -> delDia.put(r.getSeccionId(), r));
		Map<Long, java.util.Set<Long>> iniciales = new HashMap<>();
		if (!delDia.isEmpty()) {
			for (Object[] fila : marcas.alumnosInicialesPorRegistro(delDia.values().stream().map(RegistroAsistencia::getId)
					.toList())) {
				iniciales.computeIfAbsent((Long) fila[0], k -> new java.util.HashSet<>()).add((Long) fila[1]);
			}
		}
		List<String> faltan = new ArrayList<>();
		for (Seccion s : secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(anio.get().getId())) {
			java.util.Set<Long> matriculados = activas.getOrDefault(s.getId(), java.util.Set.of());
			if (!s.isActiva() || matriculados.isEmpty() || diaDeClases.sinClases(s, fecha).isPresent()) {
				continue;
			}
			RegistroAsistencia registro = delDia.get(s.getId());
			if (registro == null || !iniciales.getOrDefault(registro.getId(), java.util.Set.of()).containsAll(matriculados)) {
				faltan.add(s.etiqueta());
			}
		}
		return faltan;
	}
}
