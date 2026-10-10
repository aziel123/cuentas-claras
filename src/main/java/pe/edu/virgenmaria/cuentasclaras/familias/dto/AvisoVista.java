package pe.edu.virgenmaria.cuentasclaras.familias.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un aviso de la familia: lo que ve la propia familia y lo que ve Promotoría o Dirección en su bandeja. Los pedidos sobre
 * datos personales (sprint 7, tanda 3) traen su derecho y el último día hábil para responder; los demás, {@code null}.
 */
public record AvisoVista(Long id, LocalDateTime creadoEn, String familia, String apoderado, String tipo, boolean critico,
		String referencia, String texto, boolean abierto, String respuesta, LocalDateTime atendidoEn, String derecho,
		LocalDate venceEl, boolean vencido) {

	public AvisoVista(Long id, LocalDateTime creadoEn, String familia, String apoderado, String tipo, boolean critico,
			String referencia, String texto, boolean abierto, String respuesta, LocalDateTime atendidoEn) {
		this(id, creadoEn, familia, apoderado, tipo, critico, referencia, texto, abierto, respuesta, atendidoEn, null, null,
				false);
	}
}
