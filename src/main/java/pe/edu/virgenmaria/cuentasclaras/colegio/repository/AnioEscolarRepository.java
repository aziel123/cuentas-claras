package pe.edu.virgenmaria.cuentasclaras.colegio.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;

import java.util.List;
import java.util.Optional;

/** Años escolares del colegio actual (los filtra {@code @TenantId}). */
public interface AnioEscolarRepository extends JpaRepository<AnioEscolar, Long> {

	List<AnioEscolar> findAllByOrderByAnioDesc();

	Optional<AnioEscolar> findByEstado(EstadoAnioEscolar estado);

	boolean existsByAnio(int anio);

	Optional<AnioEscolar> findByAnio(int anio);

	/** Lee y bloquea el año: serializa operaciones que dependen de él (generación de cuotas, importación). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AnioEscolar a where a.id = :id")
	Optional<AnioEscolar> bloquear(@Param("id") Long id);
}
