package pe.edu.virgenmaria.cuentasclaras.comunicados.dto;

import java.time.LocalDateTime;

/** Sprint 9, tanda 3: un comunicado en la lista del personal (sin el texto). */
public record ComunicadoVista(Long id, String titulo, String emisor, String alcance, String estado, String estadoTexto,
		LocalDateTime enviadoEn, Integer destinatarias, long confirmadas, boolean feDeErratas, boolean urgente,
		boolean conAdjunto) {
}
