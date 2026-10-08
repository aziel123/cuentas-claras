package pe.edu.virgenmaria.cuentasclaras.panel.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.panel.model.MuestraLlamada;

import java.time.LocalDate;
import java.util.List;

/**
 * Muestra congelada de la llamada de control del colegio actual ({@code @TenantId}). SOLO inserción y lectura: sin
 * borrados, sin {@code @Modifying} y sin SQL nativo (reglas ArchUnit).
 */
public interface MuestraLlamadaRepository extends Repository<MuestraLlamada, Long> {

	MuestraLlamada saveAndFlush(MuestraLlamada fila);

	/** La muestra de una semana, en el orden en que se eligió (los reemplazos al final). */
	List<MuestraLlamada> findBySemanaOrderByIdAsc(LocalDate semana);

	/** Los reemplazos desde un momento (la alerta ATENCIÓN de S6-M2). */
	List<MuestraLlamada> findByMotivoAndCreadoEnGreaterThanEqualOrderByIdAsc(
			pe.edu.virgenmaria.cuentasclaras.panel.model.MotivoMuestra motivo, java.time.LocalDateTime desde);
}
