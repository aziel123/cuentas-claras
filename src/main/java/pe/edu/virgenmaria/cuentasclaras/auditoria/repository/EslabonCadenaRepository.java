package pe.edu.virgenmaria.cuentasclaras.auditoria.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EslabonCadena;

/**
 * Eslabón de la cadena de auditoría. Sin {@code save} ni borrado: el eslabón se modifica solo
 * dentro de la transacción que lo bloqueó, por el seguimiento de cambios de JPA.
 */
public interface EslabonCadenaRepository extends Repository<EslabonCadena, Long> {

	/** {@code SELECT ... FOR UPDATE}: serializa la asignación de secuencias entre transacciones. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from EslabonCadena e where e.id = 1")
	EslabonCadena bloquear();

	@Query("select e from EslabonCadena e where e.id = 1")
	EslabonCadena leer();
}
