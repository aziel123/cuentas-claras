package pe.edu.virgenmaria.cuentasclaras.panel.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.panel.model.LlamadaControl;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Llamadas de control del colegio actual ({@code @TenantId}). SOLO inserción y lectura: sin borrados, sin
 * {@code @Modifying} y sin SQL nativo (reglas ArchUnit).
 */
public interface LlamadaControlRepository extends Repository<LlamadaControl, Long> {

	LlamadaControl saveAndFlush(LlamadaControl llamada);

	/** Las llamadas registradas de una semana, en el orden en que se hicieron. */
	List<LlamadaControl> findBySemanaOrderByIdAsc(LocalDate semana);

	boolean existsBySemanaAndFamiliaId(LocalDate semana, Long familiaId);

	/** Las de un resultado desde un momento (las «No confirma» recientes para la alerta CRÍTICA). */
	List<LlamadaControl> findByResultadoAndCreadoEnGreaterThanEqualOrderByIdAsc(ResultadoLlamada resultado,
			LocalDateTime desde);
}
