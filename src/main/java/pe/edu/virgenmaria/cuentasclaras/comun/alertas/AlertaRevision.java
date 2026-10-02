package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

import java.util.Comparator;
import java.util.Objects;

/**
 * Una alerta de «Para revisar»: su gravedad (las críticas, como un faltante de caja, van primero y en rojo), el módulo
 * que la da, el texto en lenguaje claro (sin datos sensibles) y, si hay, un enlace a donde se revisa.
 */
public record AlertaRevision(Gravedad gravedad, String modulo, String texto, String enlace) {

	/** Críticas primero; dentro de cada gravedad, en el orden en que llegaron. */
	public static final Comparator<AlertaRevision> POR_GRAVEDAD = Comparator.comparing(AlertaRevision::gravedad);

	public AlertaRevision {
		Objects.requireNonNull(gravedad, "gravedad");
		Objects.requireNonNull(modulo, "modulo");
		Objects.requireNonNull(texto, "texto");
	}

	public enum Gravedad {

		CRITICA("Crítica", "peligro"),
		ATENCION("Atención", "alerta"),
		INFORMATIVA("Para saber", "info");

		private final String etiqueta;

		private final String variante;

		Gravedad(String etiqueta, String variante) {
			this.etiqueta = etiqueta;
			this.variante = variante;
		}

		public String etiqueta() {
			return etiqueta;
		}

		public String variante() {
			return variante;
		}
	}
}
