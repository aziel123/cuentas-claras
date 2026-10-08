package pe.edu.virgenmaria.cuentasclaras.alumnos.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.VerificacionContacto;

import java.util.List;
import java.util.Optional;

/** Verificaciones de contacto del colegio actual ({@code @TenantId}). Sin borrados ni ediciones por consulta. */
public interface VerificacionContactoRepository extends Repository<VerificacionContacto, Long> {

	VerificacionContacto save(VerificacionContacto verificacion);

	VerificacionContacto saveAndFlush(VerificacionContacto verificacion);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from VerificacionContacto v where v.hashToken = :hash")
	Optional<VerificacionContacto> bloquearPorHash(@Param("hash") String hash);

	List<VerificacionContacto> findByApoderadoIdOrderByIdDesc(Long apoderadoId);
}
