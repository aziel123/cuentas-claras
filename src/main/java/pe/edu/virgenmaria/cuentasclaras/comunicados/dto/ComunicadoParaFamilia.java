package pe.edu.virgenmaria.cuentasclaras.comunicados.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 9, tanda 3 (sección 3.10): un comunicado en el portal de la familia. Si está retirado, solo la constancia (sin el
 * texto, E28). {@code feDeErratas}: las correcciones que tiene (id y fecha).
 */
public record ComunicadoParaFamilia(Long id, String titulo, String emisor, LocalDateTime enviadoEn, List<String> parrafos,
		String pie, boolean urgente, boolean requiereConfirmacion, boolean confirmado, boolean nuevo, boolean retirado,
		String constanciaRetiro, boolean conAdjunto, Long corrigeId, List<Long> feDeErratas) {
}
