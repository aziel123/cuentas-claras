package pe.edu.virgenmaria.cuentasclaras.comun.muestreo;

import org.springframework.data.repository.Repository;

import java.time.LocalDate;
import java.util.Optional;

/** Semillas del colegio actual ({@code @TenantId}). Solo inserción. */
public interface SemillaMuestreoRepository extends Repository<SemillaMuestreo, Long> {

	SemillaMuestreo saveAndFlush(SemillaMuestreo semilla);

	Optional<SemillaMuestreo> findByAmbitoAndFecha(SemillaMuestreo.Ambito ambito, LocalDate fecha);
}
