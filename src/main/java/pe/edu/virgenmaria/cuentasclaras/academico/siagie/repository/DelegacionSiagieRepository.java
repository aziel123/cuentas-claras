package pe.edu.virgenmaria.cuentasclaras.academico.siagie.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.model.DelegacionSiagie;

import java.time.LocalDate;
import java.util.List;

/** Sprint 10, tanda 3 (M12): las delegaciones de la exportación para el SIAGIE. SOLO INSERCIÓN. */
public interface DelegacionSiagieRepository extends Repository<DelegacionSiagie, Long> {

	DelegacionSiagie saveAndFlush(DelegacionSiagie delegacion);

	/** Las que siguen vigentes o vienen (hasta hoy o después), las más nuevas primero. */
	List<DelegacionSiagie> findByHastaGreaterThanEqualOrderByIdDesc(LocalDate dia);

	List<DelegacionSiagie> findByUsuarioIdAndHastaGreaterThanEqualOrderByIdDesc(Long usuarioId, LocalDate dia);
}
