package pe.edu.virgenmaria.cuentasclaras.familias.dto;

import java.time.LocalDateTime;

/** Un aviso de la familia: lo que ve la propia familia y lo que ve Promotoría o Dirección en su bandeja. */
public record AvisoVista(Long id, LocalDateTime creadoEn, String familia, String apoderado, String tipo, boolean critico,
		String referencia, String texto, boolean abierto, String respuesta, LocalDateTime atendidoEn) {
}
