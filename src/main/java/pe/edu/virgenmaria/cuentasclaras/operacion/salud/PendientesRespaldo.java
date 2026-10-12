package pe.edu.virgenmaria.cuentasclaras.operacion.salud;

import pe.edu.virgenmaria.cuentasclaras.operacion.model.ResolucionRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.Respaldo;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 8, tanda 3 (sección 3.8, decisión 131): qué alertas «Faltan filas» siguen abiertas, por colegio. Es la misma regla
 * que exigen en MySQL {@code trg_respaldo_registro} y {@code trg_resolucion_respaldo_registro}:
 * <ul>
 *   <li>un respaldo con FALTAN_FILAS nombra en {@code colegios_faltan} a los colegios afectados; la falta de CADA uno sigue
 *       abierta hasta que su Promotoría la resuelve en ese respaldo o en uno posterior;</li>
 *   <li>un FALTAN_FILAS anterior a V30 (sin colegios) es de toda la base: lo cierra cualquier resolución posterior, como en
 *       el sprint 7.</li>
 * </ul>
 * Sin estado ni base: recibe los respaldos con FALTAN_FILAS (del más nuevo al más viejo) y las resoluciones.
 */
public final class PendientesRespaldo {

	private PendientesRespaldo() {
	}

	/** Si la falta de ese respaldo para ese colegio ya se resolvió (en él o en uno posterior). */
	public static boolean resuelto(Respaldo respaldo, Long colegioId, List<ResolucionRespaldo> resoluciones) {
		return resoluciones.stream().anyMatch(s -> s.getRespaldoId() >= respaldo.getId()
				&& (respaldo.deTodaLaBase() || s.getColegioId().equals(colegioId)));
	}

	/** Si al respaldo le queda algún colegio sin resolver (o, si es de toda la base, ninguna resolución posterior). */
	public static boolean abierto(Respaldo respaldo, List<ResolucionRespaldo> resoluciones) {
		if (respaldo.deTodaLaBase()) {
			return resoluciones.stream().noneMatch(s -> s.getRespaldoId() >= respaldo.getId());
		}
		return respaldo.colegiosConFaltantes().stream().anyMatch(c -> !resuelto(respaldo, c, resoluciones));
	}

	/** El respaldo abierto más nuevo, de cualquier colegio (para el vigilante y el correo al responsable técnico). */
	public static Optional<Respaldo> abiertoMasNuevo(List<Respaldo> faltantes, List<ResolucionRespaldo> resoluciones) {
		return faltantes.stream().sorted(Comparator.comparing(Respaldo::getId).reversed())
				.filter(r -> abierto(r, resoluciones)).findFirst();
	}

	/** El respaldo más nuevo con una falta de ESE colegio sin resolver (lo que ve su Promotoría). */
	public static Optional<Respaldo> abiertoDe(Long colegioId, List<Respaldo> faltantes,
			List<ResolucionRespaldo> resoluciones) {
		return faltantes.stream().sorted(Comparator.comparing(Respaldo::getId).reversed())
				.filter(r -> r.afectaA(colegioId) && !resuelto(r, colegioId, resoluciones)).findFirst();
	}

	/**
	 * El respaldo que resuelve la Promotoría de ese colegio: el ÚLTIMO con FALTAN_FILAS que lo nombra (o de toda la base),
	 * si sigue abierto para él. Resolverlo cierra también los anteriores de ese colegio.
	 */
	public static Optional<Respaldo> porResolverDe(Long colegioId, List<Respaldo> faltantes,
			List<ResolucionRespaldo> resoluciones) {
		return faltantes.stream().filter(r -> r.afectaA(colegioId)).max(Comparator.comparing(Respaldo::getId))
				.filter(r -> !resuelto(r, colegioId, resoluciones));
	}

	/**
	 * Las diferencias que le tocan a ese colegio: las de su colegio ({@code [colegio N]}) y las de toda la instalación (sin
	 * marca o {@code [plataforma]}). Las de otros colegios no se muestran (solo dicen que existen).
	 */
	public static String diferenciasDe(Long colegioId, String diferencias) {
		if (diferencias == null || diferencias.isBlank() || colegioId == null) {
			return diferencias;
		}
		String propia = "[colegio " + colegioId + "]";
		List<String> partes = List.of(diferencias.split(", (?=[a-z])"));
		List<String> mias = partes.stream().filter(p -> !p.contains("[colegio ") || p.contains(propia))
				.map(p -> p.replace(" " + propia, "")).toList();
		long otras = partes.size() - mias.size();
		String texto = String.join(", ", mias);
		if (otras > 0) {
			texto = (texto.isEmpty() ? "" : texto + "; ") + "además, faltas en " + otras + (otras == 1 ? " tabla" : " tablas")
					+ " de otros colegios de la instalación (las revisa su Promotoría)";
		}
		return texto;
	}
}
