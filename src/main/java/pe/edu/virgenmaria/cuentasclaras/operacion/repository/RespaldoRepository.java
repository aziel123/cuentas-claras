package pe.edu.virgenmaria.cuentasclaras.operacion.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.ComparacionRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.Respaldo;

import java.util.List;
import java.util.Optional;

/** Solo lectura: los respaldos los registra {@code cc_respaldo} (la aplicación no tiene GRANT de escritura). */
public interface RespaldoRepository extends Repository<Respaldo, Long> {

	Optional<Respaldo> findFirstByOrderByIdDesc();

	/** El último respaldo a un destino real (en prod el simulado no cuenta). */
	Optional<Respaldo> findFirstByDestinoNotOrderByIdDesc(String destino);

	/** El último respaldo con esa comparación (para resolver: el último con FALTAN_FILAS). */
	Optional<Respaldo> findFirstByComparacionOrderByIdDesc(ComparacionRespaldo comparacion);

	/**
	 * Correcciones del sprint 7 (QA-S7-1): los respaldos con FALTAN_FILAS que siguen sin resolver (ninguna resolución en
	 * ellos ni en uno posterior), del más nuevo al más viejo. Con {@code simulados = false}, solo los de un destino real.
	 */
	@Query("select r from Respaldo r where r.comparacion = :faltan and (:simulados = true or r.destino <> :simulado) "
			+ "and not exists (select s from ResolucionRespaldo s where s.respaldoId >= r.id) order by r.id desc")
	List<Respaldo> sinResolver(@Param("faltan") ComparacionRespaldo faltan, @Param("simulados") boolean simulados,
			@Param("simulado") String simulado, Limit limite);
}
