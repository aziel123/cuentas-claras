package pe.edu.virgenmaria.cuentasclaras.operacion.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.ComparacionRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.ResolucionRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.Respaldo;

import java.util.List;
import java.util.Optional;

/** Solo lectura: los respaldos los registra {@code cc_respaldo} (la aplicación no tiene GRANT de escritura). */
public interface RespaldoRepository extends Repository<Respaldo, Long> {

	Optional<Respaldo> findFirstByOrderByIdDesc();

	/** El último respaldo a un destino real (en prod el simulado no cuenta). */
	Optional<Respaldo> findFirstByDestinoNotOrderByIdDesc(String destino);

	/**
	 * Los respaldos con esa comparación (FALTAN_FILAS), del más nuevo al más viejo. Con {@code simulados = false}, solo los
	 * de un destino real. Sprint 8, tanda 3: cuáles siguen abiertos y para qué colegio lo decide {@code PendientesRespaldo}
	 * (son pocos: cada uno es un incidente).
	 */
	@Query("select r from Respaldo r where r.comparacion = :comparacion and (:simulados = true or r.destino <> :simulado) "
			+ "order by r.id desc")
	List<Respaldo> conComparacion(@Param("comparacion") ComparacionRespaldo comparacion,
			@Param("simulados") boolean simulados, @Param("simulado") String simulado);

	/** Las resoluciones de los respaldos desde ese id (las que pueden cerrar una alerta de ese respaldo o de uno posterior). */
	@Query("select s from ResolucionRespaldo s where s.respaldoId >= :desde order by s.respaldoId")
	List<ResolucionRespaldo> resolucionesDesde(@Param("desde") Long desde);
}
