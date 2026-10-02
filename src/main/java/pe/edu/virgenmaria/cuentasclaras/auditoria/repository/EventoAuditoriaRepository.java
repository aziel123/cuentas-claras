package pe.edu.virgenmaria.cuentasclaras.auditoria.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;

import java.time.LocalDateTime;
import java.util.Collection;
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

	/**
	 * Bitácora de un colegio con filtros opcionales ({@code null} = sin filtro). "Solo revisar" sigue la misma
	 * regla que {@link pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria#requiereRevision}.
	 */
	@Query("""
			select e from EventoAuditoria e
			where e.colegioId = :colegioId and e.ocurridoEn between :desde and :hasta
			  and (:nombreUsuario is null or e.nombreUsuario = :nombreUsuario)
			  and (:accion is null or e.accion = :accion)
			  and (:soloRevisar = false
			       or e.accion in :siempreRevisar
			       or (e.accion in :conRoles and (e.valorNuevo like '%PROMOTOR%' or e.valorNuevo like '%DIRECTOR%'
			           or e.valorNuevo like '%ADMINISTRACION%' or e.valorNuevo like '%CAJA%')))
			order by e.secuencia desc""")
	Page<EventoAuditoria> buscar(@Param("colegioId") Long colegioId, @Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta, @Param("nombreUsuario") String nombreUsuario,
			@Param("accion") AccionAuditoria accion, @Param("soloRevisar") boolean soloRevisar,
			@Param("siempreRevisar") Collection<AccionAuditoria> siempreRevisar,
			@Param("conRoles") Collection<AccionAuditoria> conRoles, Pageable pagina);

	/** Eventos recientes de ciertas acciones en un colegio (tarjeta "Para revisar"). */
	List<EventoAuditoria> findByColegioIdAndAccionInAndOcurridoEnGreaterThanEqualOrderBySecuenciaDesc(Long colegioId,
			Collection<AccionAuditoria> acciones, LocalDateTime desde, Limit limite);

	/** Lote de la cadena en orden, para verificar la integridad. */
	List<EventoAuditoria> findBySecuenciaGreaterThanOrderBySecuenciaAsc(long secuencia, Limit limite);
}
