package pe.edu.virgenmaria.cuentasclaras.comunicacion.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CambioCorreoControl;

import java.util.List;
import java.util.Optional;

/** Correcciones del sprint 8 (S8-A2): los cambios del correo externo de control del colegio actual ({@code @TenantId}). */
public interface CambioCorreoControlRepository extends Repository<CambioCorreoControl, Long> {

	CambioCorreoControl saveAndFlush(CambioCorreoControl cambio);

	Optional<CambioCorreoControl> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CambioCorreoControl c where c.id = :id")
	Optional<CambioCorreoControl> bloquear(@Param("id") Long id);

	Optional<CambioCorreoControl> findFirstByEstado(CambioCorreoControl.Estado estado);

	/** Cuántas propuestas tuvo el colegio: la firma del operador lleva el número del intento (única por clave). */
	long count();

	List<CambioCorreoControl> findTop10ByOrderByIdDesc();
}
