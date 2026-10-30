package pe.edu.virgenmaria.cuentasclaras.exportacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.DescargaExportacion;

/** Sprint 11, tanda 2: las descargas de las exportaciones del colegio en contexto. */
public interface DescargaExportacionRepository extends JpaRepository<DescargaExportacion, Long> {

	long countByExportacionId(Long exportacionId);
}
