package pe.edu.virgenmaria.cuentasclaras.colegio.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.ActaSupresion;

import java.util.List;
import java.util.Optional;

/** Sprint 11, tanda 2: el acta de supresión del colegio en contexto (una por colegio). */
public interface ActaSupresionRepository extends JpaRepository<ActaSupresion, Long> {

	Optional<ActaSupresion> findFirstByIdNotNull();

	List<ActaSupresion> findByEstado(ActaSupresion.Estado estado);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from ActaSupresion a where a.id = :id")
	Optional<ActaSupresion> bloquear(@Param("id") Long id);
}
