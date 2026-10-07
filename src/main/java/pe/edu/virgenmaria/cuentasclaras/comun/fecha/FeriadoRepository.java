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

	boolean existsByFechaAndVigenteTrue(LocalDate fecha);

	/** Para la pantalla: los de un rango de fechas, vigentes y anulados. */
	List<Feriado> findByFechaBetweenOrderByFechaAscIdAsc(LocalDate desde, LocalDate hasta);

	/** Para el aviso informativo del inicio: los registrados recientemente. */
	List<Feriado> findByVigenteTrueAndCreadoEnGreaterThanEqualOrderByFechaAsc(java.time.LocalDateTime desde);
}
