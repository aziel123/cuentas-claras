package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ResultadoBusqueda;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Búsqueda de alumnos o apoderados (por nombre, hasta tres palabras sin tildes, o por el inicio del documento) con la
 * deuda de cada alumno. Solo lectura y sin rol propio: la usan los servicios de caja que ya exigieron el suyo.
 */
@Component
@Transactional(readOnly = true)
public class BusquedaFamilias {

	static final int MAX_RESULTADOS = 20;

	private static final int MAX_PALABRAS = 3;

	private final AlumnoRepository alumnos;

	private final ApoderadoRepository apoderados;

	private final MatriculaRepository matriculas;

	private final CuotaRepository cuotas;

	public BusquedaFamilias(AlumnoRepository alumnos, ApoderadoRepository apoderados, MatriculaRepository matriculas,
			CuotaRepository cuotas) {
		this.alumnos = alumnos;
		this.apoderados = apoderados;
		this.matriculas = matriculas;
		this.cuotas = cuotas;
	}

	/** @param limpio texto ya limpio, de 2 caracteres o más */
	public List<ResultadoBusqueda> buscar(String limpio, LocalDate hoy) {
		String[] palabras = { null, null, null };
		String documento = null;
		String compacto = limpio.replace(" ", "");
		if (compacto.matches("[0-9A-Za-z]+") && compacto.chars().anyMatch(Character::isDigit)) {
			documento = Normalizador.escaparLike(compacto.toUpperCase(Locale.ROOT)) + "%";
		}
		else {
			String[] partes = Normalizador.paraBusqueda(limpio).split(" ");
			for (int i = 0; i < Math.min(partes.length, MAX_PALABRAS); i++) {
				palabras[i] = "%" + Normalizador.escaparLike(partes[i]) + "%";
			}
		}
		Map<Long, Alumno> encontrados = new LinkedHashMap<>();
		alumnos.buscar(palabras[0], palabras[1], palabras[2], documento, null, null, null,
				PageRequest.of(0, MAX_RESULTADOS)).forEach(a -> encontrados.putIfAbsent(a.getId(), a));
		if (encontrados.size() < MAX_RESULTADOS) {
			Set<Long> familiasDeApoderados = new LinkedHashSet<>();
			apoderados.buscar(palabras[0], palabras[1], palabras[2], documento, PageRequest.of(0, MAX_RESULTADOS))
					.forEach(a -> familiasDeApoderados.add(a.getFamilia().getId()));
			for (Long familia : familiasDeApoderados) {
				alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(familia).forEach(a -> encontrados.putIfAbsent(a.getId(), a));
			}
		}
		List<Alumno> lista = encontrados.values().stream().limit(MAX_RESULTADOS).toList();
		Map<Long, List<Cuota>> deudas = lista.isEmpty() ? Map.of()
				: cuotas.porPagarDeAlumnos(lista.stream().map(Alumno::getId).toList()).stream()
						.collect(Collectors.groupingBy(c -> c.getAlumno().getId()));
		return lista.stream().map(a -> {
			List<Cuota> debe = deudas.getOrDefault(a.getId(), List.of());
			BigDecimal vencido = Dinero.sumar(debe.stream().filter(c -> c.vencidaAl(hoy)).map(Cuota::saldo).toList());
			BigDecimal porPagar = Dinero.sumar(debe.stream().map(Cuota::saldo).toList());
			return new ResultadoBusqueda(a.getFamilia().getId(), a.getId(), a.nombreCompleto(), a.getDocumento().texto(),
					grado(a), a.getFamilia().getNombre(), vencido, porPagar);
		}).toList();
	}

	/** «5.° Primaria A · 2026»: su matrícula activa más reciente. */
	public String grado(Alumno alumno) {
		return matriculas.findByAlumnoIdOrderByAnioEscolarAnioDesc(alumno.getId()).stream().filter(Matricula::activa)
				.findFirst().map(m -> m.getSeccion().etiqueta() + " · " + m.getAnioEscolar().getAnio()).orElse(null);
	}
}
