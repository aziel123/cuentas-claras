package pe.edu.virgenmaria.cuentasclaras.academico.notas.dto;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 10, tanda 2 (sección 3.5): la hoja de notas en un solo POST (un lote, una firma). En el formulario cada celda manda
 * {@code nivel-{matricula}-{competencia}} (AD, A, B, C o SIN para «sin nota en este periodo»; vacío: sin elegir) y
 * {@code conclusion-{matricula}-{competencia}}: un toque por nivel, sin JavaScript.
 */
public record NotasRequest(List<NotaPedida> notas, Long solicitudId) {

	public static final String NIVEL = "nivel-";

	public static final String CONCLUSION = "conclusion-";

	public static final String SIN_NOTA = "SIN";

	public NotasRequest {
		notas = notas == null ? List.of() : List.copyOf(notas);
	}

	/** Lee la hoja de los parámetros del formulario (lo que no es {@code nivel-} se ignora). */
	public static NotasRequest deFormulario(Map<String, String> parametros, Long solicitudId) {
		Map<String, NotaPedida> celdas = new LinkedHashMap<>();
		for (Map.Entry<String, String> p : parametros.entrySet()) {
			if (!p.getKey().startsWith(NIVEL)) {
				continue;
			}
			String clave = p.getKey().substring(NIVEL.length());
			String[] partes = clave.split("-");
			if (partes.length != 2) {
				throw ilegible();
			}
			try {
				Long matricula = Long.valueOf(partes[0]);
				Long competencia = Long.valueOf(partes[1]);
				String valor = p.getValue() == null ? "" : p.getValue().strip();
				if (valor.isEmpty()) {
					continue;
				}
				String nivel = SIN_NOTA.equals(valor) ? null : valor;
				if (nivel != null && !List.of("AD", "A", "B", "C").contains(nivel)) {
					throw ilegible();
				}
				String conclusion = parametros.get(CONCLUSION + clave);
				conclusion = conclusion == null || conclusion.isBlank() ? null : conclusion.strip();
				celdas.put(clave, new NotaPedida(matricula, competencia, nivel, conclusion));
			}
			catch (NumberFormatException e) {
				throw ilegible();
			}
		}
		return new NotasRequest(new ArrayList<>(celdas.values()), solicitudId);
	}

	private static ReglaNegocioException ilegible() {
		return new ReglaNegocioException("La hoja tiene un dato que no se entiende: vuelve a abrirla.");
	}
}
