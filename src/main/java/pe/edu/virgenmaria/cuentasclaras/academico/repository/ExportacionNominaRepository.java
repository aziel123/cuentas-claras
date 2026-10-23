package pe.edu.virgenmaria.cuentasclaras.academico.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ExportacionNomina;

import java.time.LocalDateTime;

/** Sprint 9, tanda 3: las descargas de la nómina. SOLO INSERCIÓN: sin cambios ni {@code delete*}. */
public interface ExportacionNominaRepository extends Repository<ExportacionNomina, Long> {

	ExportacionNomina saveAndFlush(ExportacionNomina exportacion);

	boolean existsByCodigo(String codigo);

	/** Cuántas secciones exportó una persona desde una hora (la alerta de nómina masiva a Promotoría, B10). */
	long countByCreadoPorAndCreadoEnGreaterThanEqual(String creadoPor, LocalDateTime desde);

	long countByCreadoEnGreaterThanEqual(LocalDateTime desde);
}
