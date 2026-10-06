package pe.edu.virgenmaria.cuentasclaras.conciliacion.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ReglaPartida;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Partidas de la conciliación del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}. */
public interface PartidaConciliacionRepository extends Repository<PartidaConciliacion, Long> {

	PartidaConciliacion save(PartidaConciliacion partida);

	PartidaConciliacion saveAndFlush(PartidaConciliacion partida);

	Optional<PartidaConciliacion> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PartidaConciliacion p where p.id = :id")
	Optional<PartidaConciliacion> bloquear(@Param("id") Long id);

	/** Las partidas vigentes (PROPUESTA o CONFIRMADA) de esos objetos («PAGO:125»...). */
	List<PartidaConciliacion> findByObjetoVigenteIn(Collection<String> claves);

	/** Las partidas vigentes de esos movimientos. */
	List<PartidaConciliacion> findByMovimientoVigenteIn(Collection<Long> movimientos);

	Optional<PartidaConciliacion> findByMovimientoVigente(Long movimientoId);

	/** Las partidas de esos movimientos en ese estado (por ejemplo, las DESCARTADAS: ese par no se vuelve a proponer). */
	List<PartidaConciliacion> findByEstadoAndMovimientoIdIn(EstadoPartida estado, Collection<Long> movimientos);

	/** Las propuestas de un extracto (al descartarlo o rechazarlo, se liberan sus objetos). */
	@Query("select p from PartidaConciliacion p where p.movimiento.extracto.id = :extracto "
			+ "and p.estado = pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida.PROPUESTA order by p.id")
	List<PartidaConciliacion> propuestasDelExtracto(@Param("extracto") Long extractoId);

	/** Las propuestas de una regla sobre extractos ya CONFIRMADOS (las EXACTAS las confirma el sistema). */
	@Query("select p from PartidaConciliacion p where p.estado = "
			+ "pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida.PROPUESTA and p.regla = :regla "
			+ "and p.movimiento.extracto.estado = "
			+ "pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto.CONFIRMADO order by p.id")
	List<PartidaConciliacion> propuestasConfirmables(@Param("regla") ReglaPartida regla);

	/** Las propuestas que no son exactas (para la pantalla de diferencias y su alerta). */
	List<PartidaConciliacion> findByEstadoAndReglaNotOrderByIdAsc(EstadoPartida estado, ReglaPartida regla);

	/** Las confirmadas desde una fecha (para dejar sus verificaciones automáticas; es idempotente). */
	List<PartidaConciliacion> findByEstadoAndResueltoEnAfterOrderByIdAsc(EstadoPartida estado, LocalDateTime desde);

	List<PartidaConciliacion> findByMovimientoIdInOrderByIdAsc(Collection<Long> movimientos);

	/** Las MANUALES y EXPLICADAS confirmadas recientes (resumen para Promotoría). */
	List<PartidaConciliacion> findByEstadoAndReglaInAndResueltoEnAfterOrderByIdDesc(EstadoPartida estado,
			Collection<ReglaPartida> reglas, LocalDateTime desde);

	/** S4-C1: las partidas vigentes con diferencia de monto de movimientos desde una fecha (alerta CRÍTICA). */
	@Query("select p from PartidaConciliacion p where p.estado <> "
			+ "pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida.DESCARTADA and p.diferencia <> 0 "
			+ "and p.movimiento.fecha >= :desde order by p.movimiento.fecha, p.id")
	List<PartidaConciliacion> vigentesConDiferenciaDesde(@Param("desde") java.time.LocalDate desde);
}
