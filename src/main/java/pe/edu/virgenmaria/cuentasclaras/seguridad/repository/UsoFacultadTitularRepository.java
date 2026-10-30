package pe.edu.virgenmaria.cuentasclaras.seguridad.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.UsoFacultadTitular;

import java.util.List;

/**
 * Sprint 11, tanda 1 (sección 3.12): los usos de la facultad del titular del colegio actual ({@code @TenantId}). Solo
 * inserción: sin borrados ni ediciones (la tabla no tiene GRANT de UPDATE ni DELETE).
 */
public interface UsoFacultadTitularRepository extends Repository<UsoFacultadTitular, Long> {

	UsoFacultadTitular saveAndFlush(UsoFacultadTitular uso);

	/** Cuántos usos tuvo el colegio: el siguiente lleva el número siguiente (y su firma también). */
	long count();

	boolean existsByMensajeId(Long mensajeId);

	List<UsoFacultadTitular> findAllByOrderByNumeroDesc();
}
