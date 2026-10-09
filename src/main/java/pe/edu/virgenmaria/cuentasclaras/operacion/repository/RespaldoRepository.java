package pe.edu.virgenmaria.cuentasclaras.operacion.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.Respaldo;

import java.util.Optional;

/** Solo lectura: los respaldos los registra {@code cc_respaldo} (la aplicación no tiene GRANT de escritura). */
public interface RespaldoRepository extends Repository<Respaldo, Long> {

	Optional<Respaldo> findFirstByOrderByIdDesc();

	/** El último respaldo a un destino real (en prod el simulado no cuenta). */
	Optional<Respaldo> findFirstByDestinoNotOrderByIdDesc(String destino);
}
