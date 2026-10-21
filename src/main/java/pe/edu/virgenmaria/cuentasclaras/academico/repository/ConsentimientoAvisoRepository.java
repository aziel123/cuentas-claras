package pe.edu.virgenmaria.cuentasclaras.academico.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Canal;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConsentimientoAviso;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Finalidad;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 9, tanda 2: consentimientos del colegio actual (los filtra {@code @TenantId}). SOLO INSERCIÓN: vale la última
 * fila de cada apoderado, finalidad y canal. Sin {@code delete*} ni {@code @Modifying}.
 */
public interface ConsentimientoAvisoRepository extends Repository<ConsentimientoAviso, Long> {

	ConsentimientoAviso saveAndFlush(ConsentimientoAviso consentimiento);

	/** La vigente (la última) de un apoderado para esa finalidad y canal. */
	Optional<ConsentimientoAviso> findFirstByApoderadoIdAndFinalidadAndCanalOrderByIdDesc(Long apoderadoId,
			Finalidad finalidad, Canal canal);

	/** La historia de una familia, de la más reciente a la más antigua («Mis datos»). */
	List<ConsentimientoAviso> findByFamiliaIdOrderByIdDesc(Long familiaId);

	/** Las registradas por una persona desde la ficha (la alerta de más de 30 en un día). */
	long countByCreadoPorAndOrigenAndCreadoEnGreaterThanEqual(String creadoPor,
			pe.edu.virgenmaria.cuentasclaras.academico.model.OrigenConsentimiento origen, java.time.LocalDateTime desde);
}
