package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.LibretasPorAvisar;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 3: las libretas por avisar ({@link LibretasPorAvisar}) de las emitidas desde una hora. Correcciones del
 * sprint 10 (hallazgo 4 y P-5): por familia y OLEADA, no por periodo ni por pasada. Una oleada son las libretas de la familia
 * emitidas con menos de {@code ventana-avisos-minutos} entre una y otra: los hermanos de secciones que se emiten en pasadas
 * distintas y las versiones de varios periodos por una misma corrección van en UN aviso, que sale cuando la oleada lleva la
 * ventana sin libretas nuevas. La clave del aviso es la libreta más nueva de la oleada (una pasada repetida no duplica).
 */
@Service
@PreAuthorize("hasRole('SISTEMA_LIBRETAS')")
public class LibretasPorAvisarDeLaBase implements LibretasPorAvisar {

	private final LibretaEmitidaRepository libretas;

	private final AlumnoRepository alumnos;

	private final PeriodoEvaluacionRepository periodos;

	private final Duration ventana;

	public LibretasPorAvisarDeLaBase(LibretaEmitidaRepository libretas, AlumnoRepository alumnos,
			PeriodoEvaluacionRepository periodos,
			@Value("${cuentasclaras.libretas.ventana-avisos-minutos:30}") long ventanaMinutos) {
		this.libretas = libretas;
		this.alumnos = alumnos;
		this.periodos = periodos;
		this.ventana = Duration.ofMinutes(Math.max(0, ventanaMinutos));
	}

	@Override
	@Transactional(readOnly = true)
	public List<PorAvisar> emitidasDesde(LocalDateTime desde, LocalDateTime ahora) {
		List<LibretaEmitida> recientes = libretas.findByCreadoEnGreaterThanEqualOrderByIdAsc(desde);
		if (recientes.isEmpty()) {
			return List.of();
		}
		Map<Long, Alumno> porAlumno = alumnos.findAllById(recientes.stream().map(LibretaEmitida::getAlumnoId).distinct()
				.toList()).stream().collect(Collectors.toMap(Alumno::getId, Function.identity()));
		Map<Long, List<LibretaEmitida>> porFamilia = new LinkedHashMap<>();
		for (LibretaEmitida l : recientes) {
			Alumno a = porAlumno.get(l.getAlumnoId());
			if (a != null) {
				porFamilia.computeIfAbsent(a.getFamilia().getId(), k -> new ArrayList<>()).add(l);
			}
		}
		Map<Long, Optional<PeriodoEvaluacion>> periodoDe = new LinkedHashMap<>();
		List<PorAvisar> resultado = new ArrayList<>();
		porFamilia.forEach((familia, deLaFamilia) -> {
			for (List<LibretaEmitida> oleada : oleadas(deLaFamilia, ventana)) {
				LibretaEmitida ultima = oleada.get(oleada.size() - 1);
				if (ultima.getCreadoEn().plus(ventana).isAfter(ahora)) {
					continue;
				}
				List<PeriodoEvaluacion> suyos = oleada.stream().map(LibretaEmitida::getPeriodoId).distinct()
						.map(id -> periodoDe.computeIfAbsent(id, periodos::findById)).flatMap(Optional::stream)
						.sorted(Comparator.comparingInt(PeriodoEvaluacion::getNumero)).toList();
				String nombrePeriodo = suyos.isEmpty() ? "periodo"
						: String.join(" y ", suyos.stream().map(PeriodoEvaluacion::getNombre).toList());
				List<String> nombres = oleada.stream().map(l -> primerNombre(porAlumno.get(l.getAlumnoId()))).distinct()
						.toList();
				boolean corregida = oleada.stream().anyMatch(l -> l.getNumeroVersion() > 1);
				long ultimaId = oleada.stream().mapToLong(LibretaEmitida::getId).max().orElse(0);
				LibretaEmitida masNueva = oleada.stream().max(Comparator.comparing(LibretaEmitida::getId)).orElseThrow();
				resultado.add(new PorAvisar(familia, masNueva.getPeriodoId(), nombrePeriodo, nombres, corregida, ultimaId));
			}
		});
		return resultado;
	}

	/**
	 * Separa las libretas de una familia en oleadas: una libreta emitida más de {@code ventana} después de la anterior
	 * empieza otra.
	 */
	static List<List<LibretaEmitida>> oleadas(List<LibretaEmitida> deLaFamilia, Duration ventana) {
		List<LibretaEmitida> enOrden = deLaFamilia.stream()
				.sorted(Comparator.comparing(LibretaEmitida::getCreadoEn).thenComparing(LibretaEmitida::getId)).toList();
		List<List<LibretaEmitida>> oleadas = new ArrayList<>();
		List<LibretaEmitida> actual = new ArrayList<>();
		for (LibretaEmitida l : enOrden) {
			if (!actual.isEmpty() && l.getCreadoEn().isAfter(actual.get(actual.size() - 1).getCreadoEn().plus(ventana))) {
				oleadas.add(actual);
				actual = new ArrayList<>();
			}
			actual.add(l);
		}
		if (!actual.isEmpty()) {
			oleadas.add(actual);
		}
		return oleadas;
	}

	private static String primerNombre(Alumno a) {
		String nombres = a.getNombres().strip();
		int espacio = nombres.indexOf(' ');
		return espacio > 0 ? nombres.substring(0, espacio) : nombres;
	}
}
