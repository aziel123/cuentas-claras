package pe.edu.virgenmaria.cuentasclaras.seguridad.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.FirmaOperacion;

/** Firmas de las aprobaciones (sprint 7, tanda 2): solo inserción, sin consultas ni borrado. */
public interface FirmaOperacionRepository extends Repository<FirmaOperacion, Long> {

	FirmaOperacion saveAndFlush(FirmaOperacion firma);

	boolean existsByClave(String clave);
}
