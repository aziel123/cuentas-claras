package pe.edu.virgenmaria.cuentasclaras.colegio.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.LogoColegio;

import java.util.Optional;

/** Logos del colegio en contexto ({@code @TenantId}): se insertan y se reemplazan, nunca se borran. */
public interface LogoColegioRepository extends Repository<LogoColegio, Long> {

	LogoColegio saveAndFlush(LogoColegio logo);

	Optional<LogoColegio> findByVigenteTrue();

	boolean existsByVigenteTrue();

	/** Correcciones del sprint 8 (S8-B4): un logo por su id (el de la foto de un comprobante), del colegio en contexto. */
	Optional<LogoColegio> findById(Long id);
}
