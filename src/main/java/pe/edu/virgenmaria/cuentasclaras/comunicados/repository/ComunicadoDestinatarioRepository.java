package pe.edu.virgenmaria.cuentasclaras.comunicados.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoDestinatario;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Sprint 9, tanda 3: las familias destinatarias, congeladas al enviarse. Sin {@code delete*}. */
public interface ComunicadoDestinatarioRepository extends Repository<ComunicadoDestinatario, Long> {

	ComunicadoDestinatario saveAndFlush(ComunicadoDestinatario destinatario);

	ComunicadoDestinatario save(ComunicadoDestinatario destinatario);

	java.util.Optional<ComunicadoDestinatario> findById(Long id);

	List<ComunicadoDestinatario> findByComunicadoIdOrderByIdAsc(Long comunicadoId);

	Optional<ComunicadoDestinatario> findByComunicadoIdAndFamiliaId(Long comunicadoId, Long familiaId);

	long countByComunicadoId(Long comunicadoId);

	/** Los comunicados de una familia (el portal), del más reciente al más antiguo. */
	List<ComunicadoDestinatario> findByFamiliaIdOrderByIdDesc(Long familiaId);

	/** Los que todavía no se avisaron (el resumen del día y los urgentes). */
	List<ComunicadoDestinatario> findByAvisadoMensajeIdIsNullAndCreadoEnGreaterThanEqualOrderByIdAsc(LocalDateTime desde);

	/** Los avisados antes de una hora que siguen sin recordar (el recordatorio de las 48 horas). */
	List<ComunicadoDestinatario> findByAvisadoMensajeIdIsNotNullAndRecordadoMensajeIdIsNullAndCreadoEnLessThanOrderByIdAsc(
			LocalDateTime antesDe);

	List<ComunicadoDestinatario> findByComunicadoIdIn(Collection<Long> comunicados);
}
