package pe.edu.virgenmaria.cuentasclaras.colegio.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.LogoColegio;

import java.util.Optional;

/** Logos del colegio en contexto ({@code @TenantId}): se insertan y se reemplazan, nunca se borran. */
public interface LogoColegioRepository extends Repository<LogoColegio, Long> {

	LogoColegio saveAndFlush(LogoColegio logo);

	Optional<LogoColegio> findByVigenteTrue();

	boolean existsByVigenteTrue();
}
