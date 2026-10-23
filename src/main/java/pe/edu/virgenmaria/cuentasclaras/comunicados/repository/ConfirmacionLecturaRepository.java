package pe.edu.virgenmaria.cuentasclaras.comunicados.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ConfirmacionLectura;

import java.util.List;
import java.util.Optional;

/** Sprint 9, tanda 3: «Confirmo que lo leí». SOLO INSERCIÓN: sin {@code delete*} ni cambios. */
public interface ConfirmacionLecturaRepository extends Repository<ConfirmacionLectura, Long> {

	ConfirmacionLectura saveAndFlush(ConfirmacionLectura confirmacion);

	boolean existsByComunicadoIdAndFamiliaId(Long comunicadoId, Long familiaId);

	Optional<ConfirmacionLectura> findByComunicadoIdAndFamiliaId(Long comunicadoId, Long familiaId);

	List<ConfirmacionLectura> findByComunicadoId(Long comunicadoId);

	long countByComunicadoId(Long comunicadoId);
}
