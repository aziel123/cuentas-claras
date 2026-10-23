package pe.edu.virgenmaria.cuentasclaras.academico.siagie.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.model.ExportacionCalificaciones;

import java.time.LocalDateTime;
import java.util.List;

/** Sprint 10, tanda 3: las descargas de la exportación de calificaciones. SOLO INSERCIÓN: sin cambios ni {@code delete*}. */
public interface ExportacionCalificacionesRepository extends Repository<ExportacionCalificaciones, Long> {

	ExportacionCalificaciones saveAndFlush(ExportacionCalificaciones exportacion);

	boolean existsByCodigo(String codigo);

	/** Cuántas secciones exportó una persona desde una hora (la alerta a Promotoría de más de 10 por día). */
	long countByCreadoPorAndCreadoEnGreaterThanEqual(String creadoPor, LocalDateTime desde);

	/** La última de una sección y un periodo («exportación desactualizada»). */
	List<ExportacionCalificaciones> findByPeriodoIdAndSeccionIdOrderByIdDesc(Long periodoId, Long seccionId);
}
