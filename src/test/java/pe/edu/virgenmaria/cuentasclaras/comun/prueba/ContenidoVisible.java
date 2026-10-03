package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Lo que una vista (record) le muestra a quien la recibe: sus montos y sus textos, recorriendo records, listas y mapas
 * anidados. Hallazgo 12 de QA: las pruebas comparan valores concretos, no {@code toString()} (que incluye ids y fallaba
 * cuando un id contenía «450»).
 */
public final class ContenidoVisible {

	private ContenidoVisible() {
	}

	public static List<BigDecimal> montos(Object vista) {
		List<BigDecimal> montos = new ArrayList<>();
		recorrer(vista, montos, new ArrayList<>());
		return montos;
	}

	public static List<String> textos(Object vista) {
		List<String> textos = new ArrayList<>();
		recorrer(vista, new ArrayList<>(), textos);
		return textos;
	}

	/** Si algún monto de la vista es igual (por valor) a {@code monto}. */
	public static boolean muestraMonto(Object vista, String monto) {
		BigDecimal buscado = new BigDecimal(monto);
		return montos(vista).stream().anyMatch(m -> m.compareTo(buscado) == 0);
	}

	private static void recorrer(Object valor, List<BigDecimal> montos, List<String> textos) {
		if (valor == null) {
			return;
		}
		if (valor instanceof BigDecimal monto) {
			montos.add(monto);
		}
		else if (valor instanceof String texto) {
			textos.add(texto);
		}
		else if (valor instanceof Collection<?> coleccion) {
			coleccion.forEach(v -> recorrer(v, montos, textos));
		}
		else if (valor instanceof Map<?, ?> mapa) {
			mapa.values().forEach(v -> recorrer(v, montos, textos));
		}
		else if (valor.getClass().isRecord()) {
			for (RecordComponent componente : valor.getClass().getRecordComponents()) {
				try {
					recorrer(componente.getAccessor().invoke(valor), montos, textos);
				}
				catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			}
		}
	}
}
