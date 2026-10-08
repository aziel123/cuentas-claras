package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.PlanPensionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Red de seguridad para Promotoría (auditoría y QA del sprint 2):
 * <ul>
 *   <li>matrículas activas sin cronograma en un nivel con plan aprobado (no debería pasar: la generación es
 *       automática y serializada; si pasa, algo falló);</li>
 *   <li>matrículas retiradas sin ninguna cuota en un nivel con plan aprobado (un retiro que evitó todo el cobro).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class AlertasCobranza implements AlertasRevision {

	private static final int MAX_NOMBRES = 5;

	private final AnioEscolarRepository anios;

	private final PlanPensionRepository planes;

	private final CuotaRepository cuotas;

	public AlertasCobranza(AnioEscolarRepository anios, PlanPensionRepository planes, CuotaRepository cuotas) {
		this.anios = anios;
		this.planes = planes;
		this.cuotas = cuotas;
	}

	@Override
	public List<AlertaRevision> alertas() {
		List<AlertaRevision> alertas = new ArrayList<>();
		for (AnioEscolar anio : anios.findAllByOrderByAnioDesc()) {
			if (anio.cerrado()) {
				continue;
			}
			for (PlanPension plan : planes.findByAnioEscolarIdAndVigenteTrue(anio.getId())) {
				var grados = ServicioPlanesPension.grados(plan.getNivel());
				List<Matricula> sinCronograma = cuotas.matriculasSinCronograma(anio.getId(), grados);
				if (!sinCronograma.isEmpty()) {
					alertas.add(new AlertaRevision(Gravedad.ATENCION, "Cobranza", sinCronograma.size() + " matrícula(s) de "
							+ plan.getNivel().etiqueta() + " " + anio.getAnio() + " sin cronograma aunque hay plan aprobado: "
							+ nombres(sinCronograma) + ". Usa «Generar pendientes» y revisa por qué no se generó.",
							"/pensiones"));
				}
				List<Matricula> retiradas = cuotas.retiradasSinCuotas(anio.getId(), grados);
				if (!retiradas.isEmpty()) {
					alertas.add(new AlertaRevision(Gravedad.ATENCION, "Cobranza", retiradas.size() + " matrícula(s) de "
							+ plan.getNivel().etiqueta() + " " + anio.getAnio() + " retirada(s) sin ninguna cuota: "
							+ nombres(retiradas) + ". Revisa que el retiro sea real y desde cuándo.", "/alumnos"));
				}
			}
		}
		return alertas;
	}

	private static String nombres(List<Matricula> matriculas) {
		String nombres = matriculas.stream().limit(MAX_NOMBRES).map(m -> m.getAlumno().nombreCompleto())
				.collect(Collectors.joining(", "));
		return matriculas.size() > MAX_NOMBRES ? nombres + " y otros" : nombres;
	}
}
