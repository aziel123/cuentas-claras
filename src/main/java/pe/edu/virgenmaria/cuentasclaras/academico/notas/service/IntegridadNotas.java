package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.Calificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.HuellaConclusion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.LoteCalificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.CalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.repository.LoteCalificacionRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Correcciones del sprint 10 (auditoría del código, hallazgo 1, alto): la integridad de las notas. Cada guardado deja en
 * la bitácora la huella de sus filas ({@code NOTAS_GUARDADAS} o {@code CALIFICACION_TARDIA_REGISTRADA}: «N filas; huella
 * …», calculada por {@link RegistroNotas} con matrícula, competencia, nivel y huella de la conclusión de cada nota). Aquí se
 * recalcula la huella de cada lote con sus notas de hoy: si no coincide, o tiene más o menos notas que sus filas, o no tiene
 * su evento, alguien tocó la base por fuera de la aplicación. Entonces Promotoría recibe la alerta CRÍTICA
 * {@code INTEGRIDAD_NOTAS} y la emisión de libretas de esa sección se detiene ({@code EmisorLibretas}): ninguna libreta
 * sale con notas que nadie registró. En MySQL, además, trg_calificacion_nace exige que cada nota venga de la conexión que
 * guardó su lote y que no pase de sus filas.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class IntegridadNotas implements AlertasRevision {

	static final String MODULO = "Notas";

	static final List<AccionAuditoria> GUARDADOS = List.of(AccionAuditoria.NOTAS_GUARDADAS,
			AccionAuditoria.CALIFICACION_TARDIA_REGISTRADA);

	/** Un lote cuyas notas de hoy no son las que se guardaron. */
	public record LoteAlterado(Long loteId, Long periodoId, Long seccionId, int secuencia, String guardadoPor) {
	}

	private final LoteCalificacionRepository lotes;

	private final CalificacionRepository calificaciones;

	private final PeriodoEvaluacionRepository periodos;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final AuditoriaService auditoria;

	public IntegridadNotas(LoteCalificacionRepository lotes, CalificacionRepository calificaciones,
			PeriodoEvaluacionRepository periodos, AnioEscolarRepository anios, SeccionRepository secciones,
			AuditoriaService auditoria) {
		this.lotes = lotes;
		this.calificaciones = calificaciones;
		this.periodos = periodos;
		this.anios = anios;
		this.secciones = secciones;
		this.auditoria = auditoria;
	}

	/** Promotoría: un lote alterado de los periodos del año en curso es una alerta CRÍTICA (libretas detenidas). */
	@Override
	@Transactional(readOnly = true)
	public List<AlertaRevision> alertas() {
		List<AlertaRevision> alertas = new ArrayList<>();
		anios.findByEstado(EstadoAnioEscolar.EN_CURSO).ifPresent(anio -> {
			Map<Long, PeriodoEvaluacion> delAnio = periodos.findByAnioEscolarIdOrderByNumeroAsc(anio.getId()).stream()
					.collect(Collectors.toMap(PeriodoEvaluacion::getId, p -> p));
			if (delAnio.isEmpty()) {
				return;
			}
			for (LoteAlterado l : alterados(lotes.findByPeriodoIdInOrderByIdAsc(delAnio.keySet()))) {
				String seccion = secciones.findById(l.seccionId()).map(Seccion::etiqueta).orElse("la sección " + l.seccionId());
				String periodo = delAnio.get(l.periodoId()).getNombre();
				alertas.add(new AlertaRevision(AlertaRevision.Gravedad.CRITICA, MODULO, "Las notas del lote " + l.secuencia()
						+ " de " + seccion + " (" + periodo + ", guardado por " + l.guardadoPor() + ") no coinciden con lo "
						+ "que se guardó: alguien cambió la base por fuera de la aplicación. Las libretas de esa sección quedan "
						+ "detenidas hasta revisarlo con el responsable técnico.", "/auditoria?accion=NOTAS_GUARDADAS",
						new Aviso(TipoAviso.INTEGRIDAD_NOTAS, "NOTASALT:" + l.loteId(), null)));
			}
		});
		return alertas;
	}

	/** Si la sección tiene un lote alterado en esos periodos: entonces sus libretas no se emiten. */
	@PreAuthorize("hasAnyRole('SISTEMA_LIBRETAS','PROMOTOR','SISTEMA_PANEL')")
	@Transactional(readOnly = true)
	public boolean seccionAlterada(long seccionId, Collection<Long> periodoIds) {
		return !periodoIds.isEmpty()
				&& !alterados(lotes.findBySeccionIdAndPeriodoIdInOrderByIdAsc(seccionId, periodoIds)).isEmpty();
	}

	List<LoteAlterado> alterados(List<LoteCalificacion> deLosLotes) {
		if (deLosLotes.isEmpty()) {
			return List.of();
		}
		List<String> ids = deLosLotes.stream().map(l -> l.getId().toString()).toList();
		Map<String, String> guardados = auditoria.valoresNuevos(GUARDADOS, RegistroNotas.ENTIDAD_LOTE, ids);
		Map<Long, List<Calificacion>> notas = calificaciones.findByLoteIdInOrderByIdAsc(
				deLosLotes.stream().map(LoteCalificacion::getId).toList()).stream()
				.collect(Collectors.groupingBy(Calificacion::getLoteId));
		List<LoteAlterado> alterados = new ArrayList<>();
		for (LoteCalificacion l : deLosLotes) {
			List<Calificacion> suyas = notas.getOrDefault(l.getId(), List.of());
			String esperada = huellaGuardada(guardados.get(l.getId().toString()));
			if (esperada == null || suyas.size() != l.getFilas() || !esperada.equals(huella(suyas))) {
				alterados.add(new LoteAlterado(l.getId(), l.getPeriodoId(), l.getSeccionId(), l.getSecuencia(),
						l.getCreadoPor()));
			}
		}
		return alterados;
	}

	/** La huella de las notas de un lote, igual que la calcula {@link RegistroNotas} al guardarlo. */
	static String huella(List<Calificacion> delLote) {
		List<String> lineas = new ArrayList<>();
		for (Calificacion c : delLote) {
			lineas.add(c.getMatriculaId() + ":" + c.getCompetenciaId() + ":"
					+ (c.getNivelLogro() == null ? "-" : c.getNivelLogro().name()) + ":"
					+ (c.getConclusionSha256() == null ? "" : c.getConclusionSha256()));
		}
		return HuellaConclusion.de(String.join("\n", lineas));
	}

	/** «12 filas; huella abc…» → «abc…» (null si el evento no está o no tiene huella). */
	static String huellaGuardada(String valorNuevo) {
		if (valorNuevo == null) {
			return null;
		}
		int i = valorNuevo.lastIndexOf("huella ");
		return i < 0 ? null : valorNuevo.substring(i + "huella ".length()).strip();
	}
}
