package pe.edu.virgenmaria.cuentasclaras.conectores.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 8, tanda 2: conectores del colegio actual (los filtra {@code @TenantId}). Decide adónde entra el dinero: no
 * extiende CrudRepository (no hereda {@code delete*}) ni tiene {@code @Modifying}.
 */
public interface ConectorColegioRepository extends Repository<ConectorColegio, Long> {

	ConectorColegio save(ConectorColegio conector);

	ConectorColegio saveAndFlush(ConectorColegio conector);

	Optional<ConectorColegio> findById(Long id);

	/** Lee y bloquea el conector: dos aprobaciones (o una aprobación y un rechazo) a la vez se serializan. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ConectorColegio c where c.id = :id")
	Optional<ConectorColegio> bloquear(@Param("id") Long id);

	/** El vigente del tipo (uno como máximo: {@code uk_conector_colegio_vigente}). */
	Optional<ConectorColegio> findFirstByTipoAndEstado(TipoConector tipo, EstadoConector estado);

	/** El vigente del tipo, bloqueado (lo reemplaza la aprobación de otro). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ConectorColegio c where c.tipo = :tipo and c.estado = "
			+ "pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector.VIGENTE")
	Optional<ConectorColegio> bloquearVigente(@Param("tipo") TipoConector tipo);

	boolean existsByTipoAndEstado(TipoConector tipo, EstadoConector estado);

	List<ConectorColegio> findAllByOrderByIdDesc();

	List<ConectorColegio> findByTipoOrderByIdDesc(TipoConector tipo);

	/** Los que entraron en vigencia desde {@code desde} (la alerta CRÍTICA del cambio dura unos días). */
	List<ConectorColegio> findByResueltoEnGreaterThanEqualAndEstadoInOrderByResueltoEnDesc(LocalDateTime desde,
			List<EstadoConector> estados);

	/** El conector de esa clave pública opaca (la ruta del aviso de la pasarela), sea cual sea su estado. */
	Optional<ConectorColegio> findByClavePublica(String clavePublica);

	/** Los conectores con secreto, para verificar al arrancar que la clave maestra los descifra. */
	List<ConectorColegio> findBySecretoPistaIsNotNullOrClaveVersionIsNotNull();
}
