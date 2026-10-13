package pe.edu.virgenmaria.cuentasclaras.operacion.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.ResolucionRespaldo;

/** Solo inserción y lectura (correcciones del sprint 7, QA-S7-1): sin {@code delete*} ni actualizaciones. */
public interface ResolucionRespaldoRepository extends Repository<ResolucionRespaldo, Long> {

	ResolucionRespaldo saveAndFlush(ResolucionRespaldo resolucion);

	/** Sprint 8, tanda 3 (V30): ¿ese colegio ya resolvió ese respaldo? (una resolución por respaldo y colegio). */
	boolean existsByRespaldoIdAndColegioId(Long respaldoId, Long colegioId);
}
