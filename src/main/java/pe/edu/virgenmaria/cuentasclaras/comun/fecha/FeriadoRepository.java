package pe.edu.virgenmaria.cuentasclaras.comun.fecha;

import org.springframework.data.repository.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Días no laborables extra del colegio actual ({@code @TenantId}). Sin borrados: se anulan. */
public interface FeriadoRepository extends Repository<Feriado, Long> {

	Feriado saveAndFlush(Feriado feriado);

	Optional<Feriado> findById(Long id);

	/** Los vigentes (para el calendario hábil). */
	List<Feriado> findByVigenteTrue();

	/** S5-M3: los vigentes y APROBADOS (los propuestos no cuentan en el calendario). */
	List<Feriado> findByVigenteTrueAndPendienteFalse();

	/** S5-M3: los vigentes (propuestos o aprobados) de un rango, para el tope por mes y los días seguidos. */
	List<Feriado> findByVigenteTrueAndFechaBetween(LocalDate desde, LocalDate hasta);

	boolean existsByFechaAndVigenteTrue(LocalDate fecha);

	/** Para la pantalla: los de un rango de fechas, vigentes y anulados. */
	List<Feriado> findByFechaBetweenOrderByFechaAscIdAsc(LocalDate desde, LocalDate hasta);

	/** Para el aviso informativo del inicio: los registrados recientemente. */
	List<Feriado> findByVigenteTrueAndCreadoEnGreaterThanEqualOrderByFechaAsc(java.time.LocalDateTime desde);
}
