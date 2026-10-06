package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

import java.util.List;

/**
 * Puerto: cifras del día para el inicio de Promotoría (por ejemplo, caja: cobrado hoy, efectivo, digital y cajas
 * abiertas o cerradas). Mismas reglas que {@link AlertasRevision}: solo lectura, baratas y con Promotoría en sesión.
 */
public interface IndicadoresInicio {

	/** Título del bloque («Hoy en caja · 02/10/2026»). */
	String titulo();

	List<Indicador> indicadores();

	/** Enlace al detalle o {@code null}. */
	String enlace();

	/** Texto del enlace al detalle. */
	default String textoEnlace() {
		return "Ver detalle";
	}

	record Indicador(String etiqueta, String valor, String detalle) {
	}
}
