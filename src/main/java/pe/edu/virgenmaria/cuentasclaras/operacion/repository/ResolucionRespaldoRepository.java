package pe.edu.virgenmaria.cuentasclaras.operacion.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.ResolucionRespaldo;

import java.util.Optional;

/** Solo inserción y lectura (correcciones del sprint 7, QA-S7-1): sin {@code delete*} ni actualizaciones. */
public interface ResolucionRespaldoRepository extends Repository<ResolucionRespaldo, Long> {

	ResolucionRespaldo saveAndFlush(ResolucionRespaldo resolucion);

	/** La última resolución (cierra la alerta de su respaldo y de los anteriores). */
	Optional<ResolucionRespaldo> findFirstByOrderByRespaldoIdDesc();
}
