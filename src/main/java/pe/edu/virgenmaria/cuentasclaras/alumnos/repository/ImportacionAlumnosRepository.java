package pe.edu.virgenmaria.cuentasclaras.alumnos.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.ImportacionAlumnos;

import java.util.List;
import java.util.Optional;

/** Importaciones del colegio actual ({@code @TenantId}). Solo guardar y leer: no extiende CrudRepository. */
public interface ImportacionAlumnosRepository extends Repository<ImportacionAlumnos, Long> {

	ImportacionAlumnos save(ImportacionAlumnos importacion);

	Optional<ImportacionAlumnos> findFirstByArchivoSha256OrderByCreadoEnDesc(String archivoSha256);

	List<ImportacionAlumnos> findAllByOrderByCreadoEnDesc(Pageable pagina);

	long count();
}
