package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

import java.util.Comparator;
import java.util.Objects;

/**
 * Una alerta de «Para revisar»: su gravedad (las críticas, como un faltante de caja, van primero y en rojo), el módulo
 * que la da, el texto en lenguaje claro (sin datos sensibles) y, si hay, un enlace a donde se revisa.
 * <p>
 * Sprint 6, tanda 2: {@code aviso} (opcional) le da identidad para salir UNA vez al celular de Promotoría con un texto
 * fijo. Sin aviso (por ejemplo, un conteo sin un registro estable detrás) la alerta se ve en el panel y cuenta en el
 * resumen, pero no se difunde.
 */
public record AlertaRevision(Gravedad gravedad, String modulo, String texto, String enlace, Aviso aviso) {

	/** Críticas primero; dentro de cada gravedad, en el orden en que llegaron. */
	public static final Comparator<AlertaRevision> POR_GRAVEDAD = Comparator.comparing(AlertaRevision::gravedad);

	public AlertaRevision {
		Objects.requireNonNull(gravedad, "gravedad");
		Objects.requireNonNull(modulo, "modulo");
		Objects.requireNonNull(texto, "texto");
	}

	/** Una alerta que no sale al celular (todas las anteriores al sprint 6). */
	public AlertaRevision(Gravedad gravedad, String modulo, String texto, String enlace) {
		this(gravedad, modulo, texto, enlace, null);
	}

	/**
	 * Si sale al celular (decisión 70): tiene aviso y es CRÍTICA, o es una de las ATENCIÓN que también salen (caja sin
	 * cerrar a la hora límite y anulación de pago por aprobar).
	 */
	public boolean difundible() {
		return aviso != null && (gravedad == Gravedad.CRITICA || aviso.tipo().saleAunqueNoSeaCritica());
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
