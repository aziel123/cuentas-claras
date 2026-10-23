package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 3 (E29, E40; A3, decisión 188): la integridad de las libretas, como alerta CRÍTICA
 * {@code INTEGRIDAD_LIBRETAS} a Promotoría (sale al celular).
 * <ul>
 *   <li><b>Faltan libretas:</b> en un periodo CERRADO hace más de {@value #MINUTOS_GRACIA} minutos, una matrícula ACTIVA o
 *       RESERVADA del año sin libreta (la emisión se saltó a alguien; C1: la RESERVADA también cuenta).</li>
 *   <li><b>Huella que no coincide:</b> una libreta que alguien alteró en la base y todavía no se reemitió. Vencido el plazo
 *       de la reemisión (2 días hábiles), la alerta se repite cada día.</li>
 * </ul>
 * Sin nombres de alumnos: cantidades, periodos y códigos de libreta.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class IntegridadLibretas implements AlertasRevision {

	static final String MODULO = "Libretas";

	static final int MINUTOS_GRACIA = 30;

	static final int DIAS_HUELLA = 120;

	private final PeriodoEvaluacionRepository periodos;

	private final AnioEscolarRepository anios;

	private final MatriculaRepository matriculas;

	private final LibretaEmitidaRepository libretas;

	private final AuditoriaService auditoria;

	private final CalendarioHabil calendario;

	private final Clock reloj;

	private final int diasReemision;

	public IntegridadLibretas(PeriodoEvaluacionRepository periodos, AnioEscolarRepository anios,
			MatriculaRepository matriculas, LibretaEmitidaRepository libretas, AuditoriaService auditoria,
			CalendarioHabil calendario, Clock reloj,
			@Value("${cuentasclaras.libretas.reemision-dias-habiles:2}") int diasReemision) {
		this.periodos = periodos;
		this.anios = anios;
		this.matriculas = matriculas;
		this.libretas = libretas;
		this.auditoria = auditoria;
		this.calendario = calendario;
		this.reloj = reloj;
		this.diasReemision = diasReemision;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AlertaRevision> alertas() {
		List<AlertaRevision> alertas = new ArrayList<>(faltantes());
		alertas.addAll(huellas());
		return alertas;
	}

	/** E29: matrículas activas o reservadas sin libreta en los periodos cerrados del año en curso. */
	List<AlertaRevision> faltantes() {
		List<AlertaRevision> alertas = new ArrayList<>();
		LocalDateTime limite = LocalDateTime.now(reloj).minusMinutes(MINUTOS_GRACIA);
		anios.findByEstado(EstadoAnioEscolar.EN_CURSO).ifPresent(anio -> {
			List<Matricula> delAnio = new ArrayList<>(matriculas.findByAnioEscolarIdAndEstado(anio.getId(),
					EstadoMatricula.ACTIVA));
			delAnio.addAll(matriculas.findByAnioEscolarIdAndEstado(anio.getId(), EstadoMatricula.RESERVADA));
			for (PeriodoEvaluacion p : periodos.findByAnioEscolarIdOrderByNumeroAsc(anio.getId())) {
				if (p.getEstado() != EstadoPeriodo.CERRADO || p.getCerradoEn() == null || p.getCerradoEn().isAfter(limite)) {
					continue;
				}
				Set<Long> conLibreta = libretas.findByPeriodoIdOrderByIdAsc(p.getId()).stream()
						.map(LibretaEmitida::getMatriculaId).collect(Collectors.toSet());
				long faltan = delAnio.stream().filter(m -> !conLibreta.contains(m.getId())).count();
				if (faltan > 0) {
					alertas.add(new AlertaRevision(AlertaRevision.Gravedad.CRITICA, MODULO, faltan + " matrícula(s) activas "
							+ "o reservadas no tienen la libreta del " + p.getNombre() + ", cerrado el "
							+ p.getCerradoEn().toLocalDate() + ". Revisa la emisión de libretas: ninguna familia se queda "
							+ "sin libreta.", "/academico/libretas?periodo=" + p.getId(),
							new Aviso(TipoAviso.INTEGRIDAD_LIBRETAS, "LIBFALTAN:" + p.getId(), String.valueOf(faltan))));
				}
			}
		});
		return alertas;
	}

	/** E40 y A3: libretas que no coinciden con su huella y siguen sin reemitirse. */
	List<AlertaRevision> huellas() {
		List<AlertaRevision> alertas = new ArrayList<>();
		LocalDate hoy = LocalDate.now(reloj);
		Map<String, LocalDateTime> marcadas = auditoria.primerasDesde(AccionAuditoria.LIBRETA_HUELLA_NO_COINCIDE,
				LocalDateTime.now(reloj).minusDays(DIAS_HUELLA));
		marcadas.forEach((id, desde) -> {
			LibretaEmitida libreta = libretas.findById(Long.valueOf(id)).orElse(null);
			if (libreta == null) {
				return;
			}
			boolean sigueVigente = libretas.findFirstByPeriodoIdAndMatriculaIdOrderByNumeroVersionDesc(
					libreta.getPeriodoId(), libreta.getMatriculaId()).map(l -> l.getId().equals(libreta.getId()))
					.orElse(false);
			if (!sigueVigente) {
				return;
			}
			LocalDate plazo = calendario.sumarHabiles(desde.toLocalDate(), diasReemision);
			boolean vencido = hoy.isAfter(plazo);
			alertas.add(new AlertaRevision(AlertaRevision.Gravedad.CRITICA, MODULO, "La libreta "
					+ libreta.codigoVerificacion() + " no coincide con su huella: alguien cambió la base después de emitirla. "
					+ (vencido ? "Venció el plazo para entregarla (" + plazo + "): " : "El colegio debe entregarla a más tardar el "
							+ plazo + ": ") + "Dirección pide su reemisión y Promotoría la aprueba.",
					"/auditoria?accion=LIBRETA_HUELLA_NO_COINCIDE",
					new Aviso(TipoAviso.INTEGRIDAD_LIBRETAS, "LIBHUELLA:" + id + (vencido ? ":" + hoy : ""),
							vencido ? "vencido" : plazo.toString())));
		});
		return alertas;
	}
}
