package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AvanceMes;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DeudaVencida;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.FamiliaMorosa;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MorosidadGrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.RebajasPeriodo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.Tramos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.AjusteCuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Puerto de cifras de cobranza para el panel y los reportes (sprint 6, decisión 2). Solo lectura y JPQL agregado sobre
 * los libros (cuotas, ajustes de descuento); nada se guarda ni se escribe a mano. {@code @TenantId} filtra por el
 * colegio en sesión. La morosidad nunca se abre por sección (Ley 29733 e INDECOPI).
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class CifrasCobranza {

	private final CuotaRepository cuotas;

	private final AjusteCuotaRepository ajustes;

	private final AnioEscolarRepository anios;

	public CifrasCobranza(CuotaRepository cuotas, AjusteCuotaRepository ajustes, AnioEscolarRepository anios) {
		this.cuotas = cuotas;
		this.ajustes = ajustes;
		this.anios = anios;
	}

	/** Saldo vencido de un alumno en un año (una fila de {@link CuotaRepository#vencidasPorAlumno}). */
	private record Vencido(Long familiaId, String familia, Long alumnoId, Long anioId, LocalDate masAntigua,
			BigDecimal saldo, long cuotas) {
	}

	private List<Vencido> vencidos(LocalDate al) {
		Objects.requireNonNull(al, "al");
		List<Vencido> filas = new ArrayList<>();
		for (Object[] f : cuotas.vencidasPorAlumno(al)) {
			filas.add(new Vencido((Long) f[0], (String) f[1], (Long) f[2], (Long) f[3], (LocalDate) f[4], monto(f[5]),
					((Number) f[6]).longValue()));
		}
		return filas;
	}

	/** Deuda vencida al día {@code al}: monto, familias morosas, cuotas y familias por tramo de atraso. */
	public DeudaVencida deudaVencida(LocalDate al) {
		List<Vencido> filas = vencidos(al);
		Map<Long, LocalDate> masAntiguaPorFamilia = new HashMap<>();
		filas.forEach(v -> masAntiguaPorFamilia.merge(v.familiaId(), v.masAntigua(),
				(a, b) -> a.isBefore(b) ? a : b));
		Tramos tramos = Tramos.NINGUNO;
		for (LocalDate masAntigua : masAntiguaPorFamilia.values()) {
			tramos = tramos.sumar(ChronoUnit.DAYS.between(masAntigua, al));
		}
		return new DeudaVencida(al, Dinero.sumar(filas.stream().map(Vencido::saldo).toList()),
				masAntiguaPorFamilia.size(), filas.stream().mapToLong(Vencido::cuotas).sum(), tramos);
	}

	/** Familias con deuda vencida, de la que más debe a la que menos (solo en pantalla: no se exporta). */
	public List<FamiliaMorosa> familiasMorosas(LocalDate al) {
		record Acumulado(String familia, Set<Long> alumnos, BigDecimal[] monto, LocalDate[] masAntigua) {
		}
		Map<Long, Acumulado> porFamilia = new LinkedHashMap<>();
		for (Vencido v : vencidos(al)) {
			Acumulado a = porFamilia.computeIfAbsent(v.familiaId(), id -> new Acumulado(v.familia(), new HashSet<>(),
					new BigDecimal[] { Dinero.CERO }, new LocalDate[] { v.masAntigua() }));
			a.alumnos().add(v.alumnoId());
			a.monto()[0] = a.monto()[0].add(v.saldo());
			if (v.masAntigua().isBefore(a.masAntigua()[0])) {
				a.masAntigua()[0] = v.masAntigua();
			}
		}
		List<FamiliaMorosa> lista = new ArrayList<>();
		porFamilia.forEach((id, a) -> lista.add(new FamiliaMorosa(id, a.familia(), a.alumnos().size(), a.monto()[0],
				ChronoUnit.DAYS.between(a.masAntigua()[0], al))));
		lista.sort(Comparator.comparing(FamiliaMorosa::monto).reversed().thenComparing(FamiliaMorosa::familiaId));
		return lista;
	}

	/**
	 * Morosidad por grado de las cuotas de un año al día {@code al}. El grado sale de la matrícula del alumno en el año
	 * de la cuota (hallazgo 8); las cuotas sin matrícula en ese año van a la fila «Sin matrícula en ese año», al final.
	 * Matriculados = matrículas ACTIVAS del grado. Nunca por sección.
	 */
	public List<MorosidadGrado> morosidadPorGrado(Long anioEscolarId, LocalDate al) {
		Objects.requireNonNull(anioEscolarId, "anioEscolarId");
		Map<Long, Grado> gradoDe = new HashMap<>();
		Map<Grado, long[]> matriculados = new EnumMap<>(Grado.class);
		for (Object[] m : cuotas.gradosDelAnio(anioEscolarId)) {
			Grado grado = (Grado) m[1];
			gradoDe.put((Long) m[0], grado);
			if (m[2] == EstadoMatricula.ACTIVA) {
				matriculados.computeIfAbsent(grado, g -> new long[1])[0]++;
			}
		}
		Map<Long, Vencido> porAlumno = new HashMap<>();
		for (Vencido v : vencidos(al)) {
			if (anioEscolarId.equals(v.anioId())) {
				porAlumno.put(v.alumnoId(), v);
			}
		}
		Map<Grado, List<Vencido>> porGrado = new EnumMap<>(Grado.class);
		List<Vencido> sinMatricula = new ArrayList<>();
		porAlumno.values().forEach(v -> {
			Grado grado = gradoDe.get(v.alumnoId());
			if (grado == null) {
				sinMatricula.add(v);
			}
			else {
				porGrado.computeIfAbsent(grado, g -> new ArrayList<>()).add(v);
			}
		});
		List<MorosidadGrado> filas = new ArrayList<>();
		for (Grado grado : Grado.values()) {
			long enGrado = matriculados.containsKey(grado) ? matriculados.get(grado)[0] : 0;
			List<Vencido> deudores = porGrado.getOrDefault(grado, List.of());
			if (enGrado > 0 || !deudores.isEmpty()) {
				filas.add(fila(grado, enGrado, deudores, al));
			}
		}
		if (!sinMatricula.isEmpty()) {
			filas.add(fila(null, 0, sinMatricula, al));
		}
		return filas;
	}

	private static MorosidadGrado fila(Grado grado, long matriculados, List<Vencido> deudores, LocalDate al) {
		Tramos tramos = Tramos.NINGUNO;
		for (Vencido v : deudores) {
			tramos = tramos.sumar(ChronoUnit.DAYS.between(v.masAntigua(), al));
		}
		return new MorosidadGrado(grado, matriculados, deudores.size(),
				Dinero.sumar(deudores.stream().map(Vencido::saldo).toList()), tramos);
	}

	/** Descuentos aprobados y cuotas anuladas entre dos días (ambos incluidos), con quién aprobó. */
	public RebajasPeriodo rebajas(LocalDate desde, LocalDate hasta) {
		exigirRango(desde, hasta);
		List<RebajasPeriodo.PorAprobador> descuentos = porAprobador(ajustes.aprobadosEntre(desde.atStartOfDay(),
				hasta.plusDays(1).atStartOfDay()));
		List<RebajasPeriodo.PorAprobador> anuladas = porAprobador(cuotas.anuladasEntre(desde.atStartOfDay(),
				hasta.plusDays(1).atStartOfDay()));
		return new RebajasPeriodo(desde, hasta, Dinero.sumar(descuentos.stream().map(RebajasPeriodo.PorAprobador::monto)
				.toList()), descuentos.stream().mapToLong(RebajasPeriodo.PorAprobador::cantidad).sum(), descuentos,
				Dinero.sumar(anuladas.stream().map(RebajasPeriodo.PorAprobador::monto).toList()),
				anuladas.stream().mapToLong(RebajasPeriodo.PorAprobador::cantidad).sum(), anuladas);
	}

	private static List<RebajasPeriodo.PorAprobador> porAprobador(List<Object[]> filas) {
		return filas.stream().map(f -> new RebajasPeriodo.PorAprobador((String) f[0], ((Number) f[1]).longValue(),
				monto(f[2]))).sorted(Comparator.comparing(RebajasPeriodo.PorAprobador::aprobador)).toList();
	}

	/** Lo que vence en el mes y cuánto ya se pagó de eso. */
	public AvanceMes avanceDelMes(YearMonth mes) {
		List<Object[]> filas = cuotas.avanceEntre(mes.atDay(1), mes.atEndOfMonth());
		Object[] fila = filas.isEmpty() ? new Object[2] : filas.get(0);
		return new AvanceMes(mes, monto(fila[0]), monto(fila[1]));
	}

	/** Años escolares del colegio, del más reciente al más antiguo (selector de los reportes). */
	public List<AnioOpcion> anios() {
		return anios.findAllByOrderByAnioDesc().stream().map(ServicioPlanesPension::opcion).toList();
	}

	/** El año pedido o, si no se pide, el año en curso (o el más reciente). Vacío si el colegio no tiene años. */
	public Optional<AnioOpcion> anio(Long anioId) {
		if (anioId != null) {
			return Optional.of(ServicioPlanesPension.opcion(anios.findById(anioId)
					.orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"))));
		}
		List<AnioEscolar> todos = anios.findAllByOrderByAnioDesc();
		return todos.stream().filter(AnioEscolar::enCurso).findFirst().or(() -> todos.stream().findFirst())
				.map(ServicioPlanesPension::opcion);
	}

	private static void exigirRango(LocalDate desde, LocalDate hasta) {
		Objects.requireNonNull(desde, "desde");
		Objects.requireNonNull(hasta, "hasta");
		if (hasta.isBefore(desde)) {
			throw new IllegalArgumentException("El rango termina antes de empezar");
		}
	}

	/** Un SUM de JPQL vuelve {@code null} sin filas y puede volver con otra escala. */
	private static BigDecimal monto(Object valor) {
		return valor == null ? Dinero.CERO : Dinero.normalizar((BigDecimal) valor);
	}
}
