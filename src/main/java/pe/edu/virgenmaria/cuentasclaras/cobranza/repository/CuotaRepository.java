package pe.edu.virgenmaria.cuentasclaras.cobranza.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Cuotas del colegio actual. Sin borrados ni {@code @Modifying}: una cuota solo se crea y cambia de estado por sus
 * métodos (y en MySQL el UPDATE está limitado por columna).
 */
public interface CuotaRepository extends Repository<Cuota, Long> {

	Cuota save(Cuota cuota);

	Cuota saveAndFlush(Cuota cuota);

	Optional<Cuota> findById(Long id);

	List<Cuota> findByAlumnoIdOrderByFechaVencimientoAscIdAsc(Long alumnoId);

	boolean existsByClave(String clave);

	List<Cuota> findByAlumnoIdAndAnioEscolarId(Long alumnoId, Long anioId);

	List<Cuota> findByAlumnoIdAndObligacionIn(Long alumnoId, Collection<String> obligaciones);

	boolean existsByMatriculaIdAndTipoIn(Long matriculaId, Collection<TipoCuota> tipos);

	boolean existsByMatriculaId(Long matriculaId);

	List<Cuota> findByMatriculaIdAndTipoOrderByFechaVencimientoAscIdAsc(Long matriculaId, TipoCuota tipo);

	long countByAnioEscolarId(Long anioId);

	/**
	 * Cuotas a cobrar, bloqueadas ({@code SELECT ... FOR UPDATE}) en orden de id: así dos cajeras no cobran la misma
	 * cuota y el orden de bloqueo es siempre el mismo. Sin {@code join fetch}: H2 no admite FOR UPDATE con uniones.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Cuota c where c.id in :ids order by c.id")
	List<Cuota> bloquear(@Param("ids") Collection<Long> ids);

	/** Cuotas por pagar (PENDIENTE o PARCIAL) de todos los hermanos de una familia, de la más antigua a la más nueva. */
	@Query("select c from Cuota c join fetch c.alumno a where a.familia.id = :familia "
			+ "and c.estado in (pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PENDIENTE, "
			+ "pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PARCIAL) order by c.fechaVencimiento, c.id")
	List<Cuota> porPagarDeFamilia(@Param("familia") Long familiaId);

	/** Cuotas por pagar (PENDIENTE o PARCIAL) de estos alumnos: para el resumen de deuda en la búsqueda de caja. */
	@Query("select c from Cuota c where c.alumno.id in :alumnos "
			+ "and c.estado in (pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PENDIENTE, "
			+ "pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PARCIAL)")
	List<Cuota> porPagarDeAlumnos(@Param("alumnos") Collection<Long> alumnoIds);

	/**
	 * Ids de las cuotas por pagar (PENDIENTE o PARCIAL) de UN alumno, de la más antigua a la más nueva. Solo ids: se
	 * cargan después con {@link #bloquear} (así la primera lectura es la bloqueada). Recaudación bancaria (sprint 4).
	 */
	@Query("select c.id from Cuota c where c.alumno.id = :alumno "
			+ "and c.estado in (pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PENDIENTE, "
			+ "pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PARCIAL) order by c.fechaVencimiento, c.id")
	List<Long> idsPorPagarDeAlumno(@Param("alumno") Long alumnoId);

	/** Todas las cuotas por pagar del colegio, por alumno y vencimiento (base de deudas para el banco, sprint 4). */
	@Query("select c from Cuota c join fetch c.alumno a where "
			+ "c.estado in (pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PENDIENTE, "
			+ "pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PARCIAL) "
			+ "order by a.apellidoPaterno, a.id, c.fechaVencimiento, c.id")
	List<Cuota> porPagar();

	/** Sprint 5, tanda 3 (recordatorios): las cuotas por pagar que vencen entre dos fechas, con su alumno. */
	@Query("select c from Cuota c join fetch c.alumno a where "
			+ "c.estado in (pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PENDIENTE, "
			+ "pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PARCIAL) "
			+ "and c.fechaVencimiento between :desde and :hasta order by c.fechaVencimiento, a.id, c.id")
	List<Cuota> porPagarQueVencenEntre(@Param("desde") java.time.LocalDate desde,
			@Param("hasta") java.time.LocalDate hasta);

	List<Cuota> findByLineaSaldoInicialIdIn(Collection<Long> lineas);

	/**
	 * Matrículas del año, de esos grados, sin cronograma: las activas sin cuotas de matrícula ni de pensión y (sprint 5)
	 * las reservadas sin su cuota de matrícula.
	 */
	@Query("select m from Matricula m join fetch m.alumno join fetch m.seccion s "
			+ "where m.anioEscolar.id = :anio and s.grado in :grados and ("
			+ "(m.estado = pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula.ACTIVA "
			+ "and not exists (select c.id from Cuota c where c.matriculaId = m.id "
			+ "and c.tipo in (pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota.MATRICULA, "
			+ "pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota.PENSION))) "
			+ "or (m.estado = pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula.RESERVADA "
			+ "and not exists (select c.id from Cuota c where c.matriculaId = m.id "
			+ "and c.tipo = pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota.MATRICULA))) order by m.id")
	List<Matricula> matriculasSinCronograma(@Param("anio") Long anioId, @Param("grados") Collection<Grado> grados);

	/** Matrículas RETIRADAS del año, de esos grados, que no tienen ninguna cuota (un retiro que evitó todo el cobro). */
	@Query("select m from Matricula m join fetch m.alumno join fetch m.seccion s "
			+ "where m.anioEscolar.id = :anio and m.estado = pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula.RETIRADA "
			+ "and s.grado in :grados and not exists (select c.id from Cuota c where c.matriculaId = m.id) order by m.id")
	List<Matricula> retiradasSinCuotas(@Param("anio") Long anioId, @Param("grados") Collection<Grado> grados);

	// --- Sprint 6, tanda 1: cifras del panel y reportes (JPQL agregado, sin SQL nativo) ---

	/**
	 * Saldo vencido al día {@code al} por alumno y año de la cuota: familia, nombre de la familia, alumno, año, vencimiento
	 * más antiguo, saldo ({@code monto − pagado − descuento}) y cuántas cuotas. Vencida = PENDIENTE o PARCIAL con
	 * vencimiento ANTERIOR al día (la misma definición que {@code Cuota.vencidaAl}). QA-S6-3: de un alumno retirado no
	 * cuentan las cuotas que vencen DESPUÉS de su retiro (en MySQL, trg_resumen_diario_registro usa la misma regla).
	 */
	@Query("select f.id, f.nombre, a.id, c.anioEscolar.id, min(c.fechaVencimiento), "
			+ "sum(c.monto - c.montoPagado - c.montoDescuento), count(c) from Cuota c join c.alumno a join a.familia f "
			+ "where c.estado in (pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PENDIENTE, pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PARCIAL) and c.fechaVencimiento < :al "
			+ "and (a.retiradoEn is null or c.fechaVencimiento <= a.retiradoEn) "
			+ "group by f.id, f.nombre, a.id, c.anioEscolar.id")
	List<Object[]> vencidasPorAlumno(@Param("al") java.time.LocalDate al);

	/** Grado y estado de la matrícula de cada alumno en un año (una por alumno y año: uk_matricula_alumno_anio). */
	@Query("select m.alumno.id, s.grado, m.estado from Matricula m join m.seccion s where m.anioEscolar.id = :anio")
	List<Object[]> gradosDelAnio(@Param("anio") Long anioId);

	/**
	 * Cuotas ANULADAS (aprobadas) en un rango de momentos [desde, hasta), por quien aprobó: aprobador, cuántas y lo que
	 * se dejó de cobrar ({@code monto − descuento}; una cuota con pagos no se anula).
	 */
	@Query("select c.anulacionAprobadaPor, count(c), sum(c.monto - c.montoDescuento) from Cuota c "
			+ "where c.estado = pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.ANULADA and c.anuladaEn >= :desde and c.anuladaEn < :hasta "
			+ "group by c.anulacionAprobadaPor")
	List<Object[]> anuladasEntre(@Param("desde") java.time.LocalDateTime desde,
			@Param("hasta") java.time.LocalDateTime hasta);

	/** Lo que vence en un rango (cuotas no anuladas, descontado el descuento) y lo que ya se pagó de eso. */
	@Query("select sum(c.monto - c.montoDescuento), sum(c.montoPagado) from Cuota c "
			+ "where c.estado <> pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.ANULADA and c.fechaVencimiento between :desde and :hasta")
	List<Object[]> avanceEntre(@Param("desde") java.time.LocalDate desde, @Param("hasta") java.time.LocalDate hasta);
}
