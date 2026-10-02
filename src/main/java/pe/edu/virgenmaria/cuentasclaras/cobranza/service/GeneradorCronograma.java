package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.MatriculaRegistrada;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.ResultadoGeneracion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.CalculadoraCronograma;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.CuotaPlanificada;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.PlanPensionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Genera los cronogramas de cuotas con el plan aprobado del nivel:
 * <ul>
 *   <li>al registrar una matrícula ({@link MatriculaRegistrada}, síncrono y en la misma transacción: si la
 *       generación falla, la matrícula tampoco queda);</li>
 *   <li>al aprobar un plan, para las matrículas del nivel que aún no tienen cronograma;</li>
 *   <li>con el botón «Generar pendientes», que es solo una red de seguridad.</li>
 * </ul>
 * Es idempotente: una matrícula que ya tiene cuotas de matrícula o pensión nunca se vuelve a generar, cada cuota
 * tiene una clave única y se omite la deuda que el alumno ya tiene (por ejemplo, cargada como saldo inicial). Las
 * generaciones masivas se serializan con {@code SELECT ... FOR UPDATE} sobre el año escolar.
 */
@Service
@Transactional
public class GeneradorCronograma {

	private static final Set<TipoCuota> DEL_PLAN = EnumSet.of(TipoCuota.MATRICULA, TipoCuota.PENSION);

	private final AnioEscolarRepository anios;

	private final MatriculaRepository matriculas;

	private final PlanPensionRepository planes;

	private final CuotaRepository cuotas;

	private final AuditoriaService auditoria;

	public GeneradorCronograma(AnioEscolarRepository anios, MatriculaRepository matriculas,
			PlanPensionRepository planes, CuotaRepository cuotas, AuditoriaService auditoria) {
		this.anios = anios;
		this.matriculas = matriculas;
		this.planes = planes;
		this.cuotas = cuotas;
		this.auditoria = auditoria;
	}

	/** «Generar pendientes»: todas las matrículas activas del año sin cronograma, en los niveles con plan aprobado. */
	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	public ResultadoGeneracion generarPendientes(Long anioId) {
		AnioEscolar anio = anios.bloquear(anioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
		ResultadoGeneracion resultado = vacio();
		for (PlanPension plan : planes.findByAnioEscolarIdAndVigenteTrue(anio.getId())) {
			resultado = resultado.sumar(generarConPlan(plan));
		}
		return resultado;
	}

	/**
	 * Lo llama {@code ServicioPlanesPension.aprobar} (que ya exigió Promotoría o Dirección) dentro de su transacción.
	 * Bloquea el año antes de leer las matrículas pendientes.
	 */
	public ResultadoGeneracion generarPendientesDelNivel(Long anioId, Nivel nivel) {
		anios.bloquear(anioId).orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
		return planes.findByAnioEscolarIdAndNivelAndVigenteTrue(anioId, nivel).map(this::generarConPlan)
				.orElseGet(GeneradorCronograma::vacio);
	}

	/** Matrícula nueva: si su nivel ya tiene plan aprobado, genera su cronograma al instante. */
	@EventListener
	public void alRegistrarMatricula(MatriculaRegistrada evento) {
		Matricula matricula = matriculas.findById(evento.matriculaId())
				.orElseThrow(() -> new IllegalStateException("La matrícula " + evento.matriculaId() + " no existe"));
		planes.findByAnioEscolarIdAndNivelAndVigenteTrue(matricula.getAnioEscolar().getId(), matricula.nivel())
				.ifPresent(plan -> generarPara(matricula, plan));
	}

	private ResultadoGeneracion generarConPlan(PlanPension plan) {
		List<Grado> grados = Arrays.stream(Grado.values()).filter(g -> g.nivel() == plan.getNivel()).toList();
		ResultadoGeneracion resultado = vacio();
		for (Matricula matricula : cuotas.matriculasSinCronograma(plan.getAnioEscolar().getId(), grados)) {
			resultado = resultado.sumar(generarPara(matricula, plan));
		}
		return resultado;
	}

	/** Genera el cronograma de una matrícula. Idempotente; cada cronograma queda en la bitácora. */
	ResultadoGeneracion generarPara(Matricula matricula, PlanPension plan) {
		if (!matricula.activa() || !matricula.getAlumno().activo() || !plan.aprobado()
				|| matricula.nivel() != plan.getNivel()
				|| !matricula.getAnioEscolar().getId().equals(plan.getAnioEscolar().getId())
				|| cuotas.existsByMatriculaIdAndTipoIn(matricula.getId(), DEL_PLAN)) {
			return vacio();
		}
		List<CuotaPlanificada> planificadas = CalculadoraCronograma.calcular(plan, matricula.getId(),
				matricula.getFechaMatricula());
		Map<String, Cuota> existentes = cuotas
				.findByAlumnoIdAndObligacionIn(matricula.getAlumno().getId(),
						planificadas.stream().map(CuotaPlanificada::obligacion).toList())
				.stream().collect(Collectors.toMap(Cuota::getObligacion, c -> c, (a, b) -> a));
		List<CuotaPlanificada> generadas = new ArrayList<>();
		List<String> omitidas = new ArrayList<>();
		for (CuotaPlanificada planificada : planificadas) {
			Cuota existente = existentes.get(planificada.obligacion());
			if (existente != null) {
				omitidas.add(planificada.descripcion() + " de " + matricula.getAlumno().nombreCompleto()
						+ ": ya existía (por ejemplo, como saldo inicial)");
				// Nunca en silencio (auditoría C1): cada omisión queda resaltada, por alumno.
				auditoria.registrar(AccionAuditoria.CUOTA_OMITIDA_DEUDA_EXISTENTE, "matricula",
						matricula.getId().toString(), null, planificada.descripcion() + " "
								+ Dinero.formatear(planificada.monto()),
						"Alumno " + matricula.getAlumno().nombreCompleto() + ": " + plan.nombre() + " no generó «"
								+ planificada.descripcion() + "» porque ya existe la cuota " + existente.getId() + " ("
								+ existente.getTipo().etiqueta() + ", " + existente.getDescripcion() + ", "
								+ Dinero.formatear(existente.getMonto()) + ").");
				continue;
			}
			if (cuotas.existsByClave(planificada.clave())) {
				continue;
			}
			cuotas.save(Cuota.generada(matricula, plan, planificada));
			generadas.add(planificada);
		}
		if (generadas.isEmpty()) {
			return new ResultadoGeneracion(0, 0, Dinero.CERO, List.copyOf(omitidas));
		}
		BigDecimal total = Dinero.sumar(generadas.stream().map(CuotaPlanificada::monto).toList());
		auditoria.registrar(AccionAuditoria.CRONOGRAMA_GENERADO, "matricula", matricula.getId().toString(), null,
				DescripcionCobranza.cuotas(generadas),
				"Alumno " + matricula.getAlumno().nombreCompleto() + ", " + matricula.getSeccion().etiqueta() + " "
						+ matricula.getAnioEscolar().getAnio() + ". " + plan.nombre() + ": " + generadas.size()
						+ " cuotas por " + Dinero.formatear(total)
						+ (omitidas.isEmpty() ? "." : ". Omitidas por ya existir: " + omitidas.size() + "."));
		return new ResultadoGeneracion(1, generadas.size(), total, List.copyOf(omitidas));
	}

	private static ResultadoGeneracion vacio() {
		return new ResultadoGeneracion(0, 0, Dinero.CERO, List.of());
	}
}
