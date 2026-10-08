package pe.edu.virgenmaria.cuentasclaras.pasarela.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.LiquidacionPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Liquidaciones de la pasarela del colegio actual ({@code @TenantId}). Solo inserción. */
public interface LiquidacionPasarelaRepository extends Repository<LiquidacionPasarela, Long> {

	LiquidacionPasarela save(LiquidacionPasarela liquidacion);

	Optional<LiquidacionPasarela> findById(Long id);

	boolean existsByProveedorAndReferencia(ProveedorPasarela proveedor, String referencia);

	List<LiquidacionPasarela> findByFechaAbonoBetweenOrderByFechaAbonoAscIdAsc(LocalDate desde, LocalDate hasta);

	List<LiquidacionPasarela> findByIdIn(Collection<Long> ids);

	List<LiquidacionPasarela> findTop30ByOrderByIdDesc();
}
