package pe.edu.virgenmaria.cuentasclaras.colegio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;

import java.util.List;
import java.util.Optional;

/**
 * Colegios. No lleva {@code @TenantId} (es la tabla de los colegios): quien lo usa filtra por tipo y estado. Nunca se
 * borra un colegio (nadie llama a {@code delete*}).
 */
public interface ColegioRepository extends JpaRepository<Colegio, Long> {

	List<Colegio> findByActivoTrueOrderByIdAsc();

	Optional<Colegio> findFirstByNombreOrderByIdAsc(String nombre);

	/** Sprint 8, tanda 1: los colegios clientes (sin la plataforma), activos. */
	List<Colegio> findByTipoAndActivoTrueOrderByIdAsc(TipoColegio tipo);

	List<Colegio> findByTipoAndEstadoAndActivoTrueOrderByIdAsc(TipoColegio tipo, EstadoColegio estado);

	List<Colegio> findByTipoOrderByIdAsc(TipoColegio tipo);

	/** Correcciones (A2): los retiros de la conservación que ya cumplieron sus 24 horas. */
	List<Colegio> findByConservacionRetiroAplicaDesdeLessThanEqualOrderByIdAsc(java.time.LocalDateTime hasta);

	/** La única fila PLATAFORMA (V28). */
	Optional<Colegio> findFirstByTipo(TipoColegio tipo);

	Optional<Colegio> findFirstByRucOrderByIdAsc(String ruc);

	/** Correcciones del sprint 8 (QA-S8-6): las OTRAS sedes con el mismo RUC (comparten la numeración ante SUNAT). */
	List<Colegio> findByRucAndIdNot(String ruc, Long id);
}
