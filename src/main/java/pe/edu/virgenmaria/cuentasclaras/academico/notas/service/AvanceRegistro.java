package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Periodos;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.AvanceVista;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.HojaNotas;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 10, tanda 2 (secciones 2 y 4 del brief): el avance del registro de notas (Dirección y Promotoría, sin alumnos) y
 * el CIERRE con resumen: antes de cerrar, Dirección ve por sección y área las competencias sin nota y las asignaciones sin
 * áreas del catálogo, y confirma. No bloquea: lo que no se registró va por nota tardía (con aprobación).
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','PROMOTOR')")
public class AvanceRegistro {

	private final CalculoAvance calculo;

	private final PeriodoEvaluacionRepository periodos;

	private final AnioEscolarRepository anios;

	private final Periodos cierre;

	public AvanceRegistro(CalculoAvance calculo, PeriodoEvaluacionRepository periodos, AnioEscolarRepository anios,
			Periodos cierre) {
		this.calculo = calculo;
		this.periodos = periodos;
		this.anios = anios;
		this.cierre = cierre;
	}

	/** El avance del periodo pedido o, sin pedirlo, del abierto (o el último cerrado) del año en curso. */
	@Transactional(readOnly = true)
	public AvanceVista avance(Long periodoId) {
		Optional<AnioEscolar> anio = periodoId != null ? Optional.empty() : anios.findByEstado(EstadoAnioEscolar.EN_CURSO);
		PeriodoEvaluacion periodo;
		if (periodoId != null) {
			periodo = periodos.findById(periodoId).orElseThrow(() -> new RecursoNoEncontradoException("Periodo no encontrado"));
		}
		else {
			List<PeriodoEvaluacion> delAnio = anio.map(a -> periodos.findByAnioEscolarIdOrderByNumeroAsc(a.getId()))
					.orElse(List.of());
			Optional<PeriodoEvaluacion> elegido = delAnio.stream().filter(p -> p.getEstado() == EstadoPeriodo.ABIERTO)
					.max(Comparator.comparingInt(PeriodoEvaluacion::getNumero))
					.or(() -> delAnio.stream().filter(p -> p.getEstado() == EstadoPeriodo.CERRADO)
							.max(Comparator.comparingInt(PeriodoEvaluacion::getNumero)));
			if (elegido.isEmpty()) {
				return new AvanceVista(opciones(delAnio), null, null, null, List.of(),
						anio.map(a -> calculo.sinAreas(a.getId())).orElse(List.of()), 0, 0);
			}
			periodo = elegido.get();
		}
		return vista(periodo);
	}

	/** El resumen para confirmar el cierre (solo Dirección, que es quien cierra). */
	@PreAuthorize("hasRole('DIRECTOR')")
	@Transactional(readOnly = true)
	public AvanceVista resumenCierre(long periodoId) {
		return vista(periodos.findById(periodoId).orElseThrow(() -> new RecursoNoEncontradoException("Periodo no encontrado")));
	}

	/**
	 * Cierra el periodo con la firma de Dirección (Periodos.cerrar, con las reglas de la tanda 1) y deja en la bitácora lo
	 * que faltaba al cerrar. Las pendientes no lo bloquean.
	 */
	@PreAuthorize("hasRole('DIRECTOR')")
	@Transactional
	public void cerrar(long periodoId) {
		AvanceVista resumen = resumenCierre(periodoId);
		long areas = resumen.pendientes().size();
		cierre.cerrar(periodoId, resumen.faltan() == 0 && resumen.sinAreas().isEmpty() ? "Al cerrar no faltaba ninguna nota."
				: "Al cerrar faltaban " + resumen.faltan() + " notas en " + areas + " áreas de sección y había "
						+ resumen.sinAreas().size() + " asignaciones sin áreas del catálogo (van por nota tardía).");
	}

	private AvanceVista vista(PeriodoEvaluacion periodo) {
		List<AvanceVista.FilaAvance> filas = calculo.filas(periodo);
		return new AvanceVista(opciones(periodos.findByAnioEscolarIdOrderByNumeroAsc(periodo.getAnioEscolarId())),
				periodo.getId(), periodo.getNombre(), periodo.getEstado().etiqueta(), filas,
				calculo.sinAreas(periodo.getAnioEscolarId()), filas.stream().mapToInt(AvanceVista.FilaAvance::esperadas).sum(),
				filas.stream().mapToInt(AvanceVista.FilaAvance::conNota).sum());
	}

	private static List<HojaNotas.OpcionPeriodo> opciones(List<PeriodoEvaluacion> delAnio) {
		return delAnio.stream().map(p -> new HojaNotas.OpcionPeriodo(p.getId(), p.getNombre(), p.getEstado().etiqueta()))
				.toList();
	}
}
