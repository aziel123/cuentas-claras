package pe.edu.virgenmaria.cuentasclaras.conciliacion.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.MovimientoBancario;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Movimientos del extracto del colegio actual ({@code @TenantId}). Solo inserción. */
public interface MovimientoBancarioRepository extends Repository<MovimientoBancario, Long> {

	MovimientoBancario save(MovimientoBancario movimiento);

	Optional<MovimientoBancario> findById(Long id);

	List<MovimientoBancario> findByExtractoIdOrderByNumeroAsc(Long extractoId);

	List<MovimientoBancario> findByIdIn(Collection<Long> ids);

	/** Los movimientos de los extractos VIGENTES de la cuenta entre dos fechas (días repetidos de un archivo nuevo). */
	@Query("select m from MovimientoBancario m where m.cuentaId = :cuenta and m.extracto.secuenciaVigente is not null "
			+ "and m.fecha between :desde and :hasta order by m.extracto.secuencia, m.numero")
	List<MovimientoBancario> vigentesEntre(@Param("cuenta") Long cuentaId, @Param("desde") LocalDate desde,
			@Param("hasta") LocalDate hasta);

	/**
	 * Movimientos de extractos CONFIRMADOS que no tienen partida vigente (sin pareja), desde una fecha. Los de un extracto
	 * por confirmar no cuentan todavía (S4-A1: sus montos no se muestran ni se suman en las alertas de quien confirma).
	 */
	@Query("select m from MovimientoBancario m where m.extracto.estado = "
			+ "pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto.CONFIRMADO and m.fecha >= :desde "
			+ "and not exists (select p.id from PartidaConciliacion p where p.movimiento = m "
			+ "and p.movimientoVigente is not null) order by m.fecha, m.id")
	List<MovimientoBancario> sinParejaDesde(@Param("desde") LocalDate desde);

	/** Cuántos movimientos tienen los extractos confirmados desde una fecha, y cuántos de ellos ya tienen pareja. */
	@Query("select count(m) from MovimientoBancario m where m.extracto.estado = "
			+ "pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto.CONFIRMADO and m.fecha >= :desde")
	long confirmadosDesde(@Param("desde") LocalDate desde);

	@Query("select count(m) from MovimientoBancario m where m.extracto.estado = "
			+ "pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto.CONFIRMADO and m.fecha >= :desde "
			+ "and exists (select p.id from PartidaConciliacion p where p.movimiento = m and p.estado = "
			+ "pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida.CONFIRMADA)")
	long conciliadosDesde(@Param("desde") LocalDate desde);
}
