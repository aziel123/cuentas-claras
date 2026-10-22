package pe.edu.virgenmaria.cuentasclaras.comunicados.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoAdjunto;

import java.util.Optional;

/** Sprint 9, tanda 3: el PDF saneado de un comunicado. SOLO INSERCIÓN: sin {@code save} de cambios ni {@code delete*}. */
public interface ComunicadoAdjuntoRepository extends Repository<ComunicadoAdjunto, Long> {

	ComunicadoAdjunto saveAndFlush(ComunicadoAdjunto adjunto);

	Optional<ComunicadoAdjunto> findById(Long id);
}
