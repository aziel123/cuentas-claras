package pe.edu.virgenmaria.cuentasclaras.comunicados.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 9, tanda 3: el detalle de un comunicado para el personal (el emisor y Dirección o Promotoría): el texto, el pie,
 * sus estadísticas, sus fe de erratas y si se puede retirar o corregir.
 */
public record DetalleComunicado(ComunicadoVista resumen, List<String> parrafos, String pie, boolean requiereConfirmacion,
		Long corrigeId, List<ComunicadoVista> feDeErratas, Estadisticas estadisticas, boolean puedeRetirar,
		boolean puedeCorregir, String retiradoPor, LocalDateTime retiradoEn, String motivoRetiro, boolean adjuntoDescargable,
		Long solicitudId) {
}
