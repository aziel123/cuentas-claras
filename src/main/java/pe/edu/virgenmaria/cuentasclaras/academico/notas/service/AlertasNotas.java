package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.AvanceVista;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.Calificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.NivelLogro;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.OrigenCalificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.CalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 2 (secciones 3.6 y 4 del brief; A5, E26): las alertas de las notas.
 * <ul>
 *   <li>{@code CORRECCIONES_NOTAS} a Promotoría: TODA corrección aprobada (últimos 7 días) que sube el nivel o deja la
 *       competencia sin nota (A5, sin umbral), y quien aprueba más de {@code alerta-correcciones-por-persona} en un
 *       periodo (E26). Quien aprobó no la recibe.</li>
 *   <li>{@code NOTAS_PENDIENTES} a Dirección, desde 3 días antes del fin de un periodo abierto: cuántas notas faltan y en
 *       qué secciones (sin alumnos).</li>
 *   <li>{@code ASIGNACIONES_SIN_AREAS} a Dirección, la semana en que abre un periodo y desde 7 días antes de su fin: cuántas
 *       asignaciones vigentes no tienen áreas del catálogo y en qué secciones (esos docentes no pueden poner notas).</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class AlertasNotas implements AlertasRevision {

	static final String MODULO = "Notas";

	static final int DIAS_PENDIENTES = 3;

	static final int DIAS_SIN_AREAS = 7;

	static final int DIAS_CORRECCIONES = 7;

	private final CalificacionRepository calificaciones;

	private final PeriodoEvaluacionRepository periodos;

	private final AnioEscolarRepository anios;

	private final CalculoAvance calculo;

	private final Clock reloj;

	private final int maxPorPersona;

	private final pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository usuarios;

	public AlertasNotas(CalificacionRepository calificaciones, PeriodoEvaluacionRepository periodos,
			AnioEscolarRepository anios, CalculoAvance calculo, Clock reloj,
			@Value("${cuentasclaras.notas.alerta-correcciones-por-persona:5}") int maxPorPersona,
			pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository usuarios) {
		this.usuarios = usuarios;
		this.calificaciones = calificaciones;
		this.periodos = periodos;
		this.anios = anios;
		this.calculo = calculo;
		this.reloj = reloj;
		this.maxPorPersona = maxPorPersona;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AlertaRevision> alertas() {
		List<AlertaRevision> alertas = new ArrayList<>(correcciones());
		LocalDate hoy = LocalDate.now(reloj);
		anios.findByEstado(EstadoAnioEscolar.EN_CURSO).ifPresent(anio -> {
			for (PeriodoEvaluacion p : periodos.findByAnioEscolarIdOrderByNumeroAsc(anio.getId())) {
				if (p.getEstado() != EstadoPeriodo.ABIERTO) {
					continue;
				}
				boolean cercaDelFin = !hoy.isBefore(p.getFin().minusDays(DIAS_PENDIENTES)) && !hoy.isAfter(p.getFin());
				if (cercaDelFin) {
					pendientes(p).ifPresent(alertas::add);
				}
				boolean recienAbierto = p.getAbiertoEn() != null
						&& !p.getAbiertoEn().toLocalDate().isBefore(hoy.minusDays(DIAS_SIN_AREAS));
				boolean semanaFinal = !hoy.isBefore(p.getFin().minusDays(DIAS_SIN_AREAS)) && !hoy.isAfter(p.getFin());
				if (recienAbierto || semanaFinal) {
					sinAreas(p, anio.getId(), recienAbierto ? "A" : "F").ifPresent(alertas::add);
				}
			}
		});
		return alertas;
	}

	/** A5 y E26: las correcciones aprobadas de los últimos días que suben el nivel o dejan sin nota, y las de más. */
	List<AlertaRevision> correcciones() {
		List<AlertaRevision> alertas = new ArrayList<>();
		LocalDateTime desde = LocalDateTime.now(reloj).minusDays(DIAS_CORRECCIONES);
		List<Calificacion> recientes = calificaciones.findByOrigenAndCreadoEnGreaterThanEqualOrderByIdAsc(
				OrigenCalificacion.CORRECCION_APROBADA, desde);
		for (Calificacion c : recientes) {
			NivelLogro antes = calificaciones.findById(c.getReemplazaId()).map(Calificacion::getNivelLogro).orElse(null);
			boolean sube = NivelLogro.sube(antes, c.getNivelLogro());
			boolean sinNota = c.getNivelLogro() == null;
			if (sube || sinNota) {
				String de = antes == null ? "sin nota" : antes.name();
				String a = sinNota ? "sin nota" : c.getNivelLogro().name();
				alertas.add(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, MODULO, "Se corrigió una nota después del "
						+ "cierre: " + de + " → " + a + " (nota " + c.getId() + ", aprobó " + c.getCreadoPor() + "). "
						+ (sinNota ? "Quedó sin nota." : "Subió el nivel.") + " Revisa que tenga sustento.",
						"/auditoria?accion=CALIFICACION_CORREGIDA",
						new Aviso(TipoAviso.CORRECCIONES_NOTAS, "NOTA:" + c.getId(), null, Set.of(c.getCreadoPor()))));
				// Correcciones del sprint 10 (hallazgo 3): si aprobó Promotoría, la alerta de Promotoría no le llega a ella
				// (quien aprobó no la recibe); va también a Dirección.
				if (aprobadaPorPromotoria(c.getCreadoPor())) {
					alertas.add(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, MODULO, "Promotoría (" + c.getCreadoPor()
							+ ") aprobó una corrección de nota después del cierre: " + de + " → " + a + " (nota " + c.getId()
							+ "). Revisa que tenga sustento.", "/auditoria?accion=CALIFICACION_CORREGIDA",
							new Aviso(TipoAviso.CORRECCION_NOTA_POR_PROMOTORIA, "NOTAPROM:" + c.getId(), null,
									Set.of(c.getCreadoPor()))));
				}
			}
		}
		Map<String, Map<Long, Long>> porPersona = recientes.stream().collect(Collectors.groupingBy(
				Calificacion::getCreadoPor, Collectors.groupingBy(Calificacion::getPeriodoId, Collectors.counting())));
		porPersona.forEach((quien, porPeriodo) -> porPeriodo.forEach((periodo, cuantas) -> {
			long enElPeriodo = calificaciones.findByPeriodoIdAndOrigenOrderByIdAsc(periodo,
					OrigenCalificacion.CORRECCION_APROBADA).stream().filter(c -> quien.equals(c.getCreadoPor())).count();
			if (enElPeriodo > maxPorPersona) {
				alertas.add(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, MODULO, quien + " aprobó " + enElPeriodo
						+ " correcciones de notas en un mismo periodo (más de " + maxPorPersona + "). Revisa por qué.",
						"/auditoria?accion=CALIFICACION_CORREGIDA",
						new Aviso(TipoAviso.CORRECCIONES_NOTAS, "NOTAS:" + periodo + ":" + Math.abs(quien.hashCode()),
								String.valueOf(enElPeriodo), Set.of(quien))));
			}
		}));
		return alertas;
	}

	private boolean aprobadaPorPromotoria(String aprobador) {
		return usuarios.findByNombreUsuario(aprobador).filter(pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario::isActivo)
				.map(u -> u.getRoles().contains(pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.PROMOTOR))
				.orElse(false);
	}

	private java.util.Optional<AlertaRevision> pendientes(PeriodoEvaluacion p) {
		List<AvanceVista.FilaAvance> faltan = calculo.filas(p).stream().filter(f -> f.faltan() > 0).toList();
		if (faltan.isEmpty()) {
			return java.util.Optional.empty();
		}
		int total = faltan.stream().mapToInt(AvanceVista.FilaAvance::faltan).sum();
		Set<String> secciones = faltan.stream().map(AvanceVista.FilaAvance::seccion)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		return java.util.Optional.of(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, MODULO, "El " + p.getNombre()
				+ " cierra el " + p.getFin() + " y faltan " + total + " notas en: " + String.join(", ", secciones)
				+ ". Revisa el avance del registro.", "/academico/notas/avance?periodo=" + p.getId(),
				new Aviso(TipoAviso.NOTAS_PENDIENTES, "NOTPEND:" + p.getId(), String.valueOf(total))));
	}

	private java.util.Optional<AlertaRevision> sinAreas(PeriodoEvaluacion p, Long anioId, String momento) {
		List<AvanceVista.SinAreas> lista = calculo.sinAreas(anioId);
		if (lista.isEmpty()) {
			return java.util.Optional.empty();
		}
		Set<String> secciones = lista.stream().map(AvanceVista.SinAreas::seccion)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		return java.util.Optional.of(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, MODULO, lista.size()
				+ " asignaciones todavía no tienen áreas del catálogo y esos docentes no pueden poner notas del "
				+ p.getNombre() + ". Secciones: " + String.join(", ", secciones) + ". Usa «Completar áreas» en Docentes y "
				+ "secciones.", "/academico/asignaciones", new Aviso(TipoAviso.ASIGNACIONES_SIN_AREAS,
						"SINAREAS:" + p.getId() + ":" + momento, String.valueOf(lista.size()))));
	}
}
