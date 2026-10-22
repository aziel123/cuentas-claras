package pe.edu.virgenmaria.cuentasclaras.comunicados.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 9, tanda 3 (sección 9): lo que {@code comunicacion} necesita para avisar los comunicados a las familias, SIN el
 * texto (solo ids, la etiqueta del emisor y, en los urgentes, el título). Y dónde anotar qué mensaje avisó y cuál recordó
 * cada comunicado (lo escribe sistema.comunicados).
 */
public interface ComunicadosPorAvisar {

	/** Un destinatario por avisar: el comunicado, la familia, el emisor y, si es urgente, su título. */
	record Pendiente(Long destinatarioId, Long comunicadoId, Long familiaId, String emisor, boolean urgente,
			String tituloUrgente, LocalDateTime enviadoEn) {
	}

	/** Un destinatario avisado hace más de 48 horas, que pide confirmación y todavía no la tiene (el recordatorio). */
	record PorRecordar(Long destinatarioId, Long comunicadoId, Long familiaId, String emisor) {
	}

	/** Los destinatarios de comunicados ENVIADOS desde {@code desde} que todavía no se avisaron. */
	List<Pendiente> sinAvisar(LocalDateTime desde);

	/** Los destinatarios avisados antes de {@code antesDe}, sin confirmar y sin recordatorio. */
	List<PorRecordar> porRecordar(LocalDateTime antesDe);

	/** Anota el mensaje que avisó a ese destinatario (una sola vez). */
	void avisado(Long destinatarioId, Long mensajeId);

	/** Anota el mensaje que llevó el recordatorio (una sola vez). */
	void recordado(Long destinatarioId, Long mensajeId);
}
