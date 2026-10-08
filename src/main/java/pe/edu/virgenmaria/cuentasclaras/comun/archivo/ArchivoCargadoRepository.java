package pe.edu.virgenmaria.cuentasclaras.comun.archivo;

import org.springframework.data.repository.Repository;

import java.util.Optional;

/** Archivos originales del colegio actual ({@code @TenantId}). Solo inserción: sin borrados ni {@code @Modifying}. */
public interface ArchivoCargadoRepository extends Repository<ArchivoCargado, Long> {

	ArchivoCargado save(ArchivoCargado archivo);

	Optional<ArchivoCargado> findById(Long id);

	Optional<ArchivoCargado> findByTipoAndSha256(TipoArchivo tipo, String sha256);
}
