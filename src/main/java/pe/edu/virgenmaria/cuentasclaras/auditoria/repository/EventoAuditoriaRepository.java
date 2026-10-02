package pe.edu.virgenmaria.cuentasclaras.auditoria.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bitácora de auditoría. Extiende {@link Repository} (no {@code JpaRepository}) para exponer
 * solo inserción y lectura: no hay métodos de borrado ni edición (regla ArchUnit).
 * {@link EventoAuditoria} no tiene {@code @TenantId}: toda consulta filtra por colegio de forma explícita.
 */
public interface EventoAuditoriaRepository extends Repository<EventoAuditoria, Long> {

	EventoAuditoria save(EventoAuditoria evento);

	Page<EventoAuditoria> findByColegioIdAndOcurridoEnBetweenOrderBySecuenciaDesc(Long colegioId,
			LocalDateTime desde, LocalDateTime hasta, Pageable pagina);

	/** Lote de la cadena en orden, para verificar la integridad. */
	List<EventoAuditoria> findBySecuenciaGreaterThanOrderBySecuenciaAsc(long secuencia, Limit limite);
}
