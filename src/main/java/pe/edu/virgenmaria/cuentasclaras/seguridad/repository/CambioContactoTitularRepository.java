package pe.edu.virgenmaria.cuentasclaras.seguridad.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.CambioContactoTitular;

import java.util.List;
import java.util.Optional;

/** Correcciones del sprint 8 (S8-A1): los cambios del contacto del titular del colegio actual ({@code @TenantId}). Sin borrados. */
public interface CambioContactoTitularRepository extends Repository<CambioContactoTitular, Long> {

	CambioContactoTitular saveAndFlush(CambioContactoTitular cambio);

	Optional<CambioContactoTitular> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CambioContactoTitular c where c.id = :id")
	Optional<CambioContactoTitular> bloquear(@Param("id") Long id);

	/** El cambio pendiente de ese titular (uno a la vez). */
	Optional<CambioContactoTitular> findFirstByUsuarioIdAndEstado(Long usuarioId, CambioContactoTitular.Estado estado);

	/** Sprint 11, tanda 1c (A1c-1): el cambio por aplicar del colegio (de su titular o de otra cuenta directiva). */
	Optional<CambioContactoTitular> findFirstByEstadoOrderByIdAsc(CambioContactoTitular.Estado estado);

	/** Cuántos cambios tuvo ese titular: la firma de quien propone lleva el número del intento (única por clave). */
	long countByUsuarioId(Long usuarioId);

	List<CambioContactoTitular> findByUsuarioIdOrderByIdDesc(Long usuarioId);
}
