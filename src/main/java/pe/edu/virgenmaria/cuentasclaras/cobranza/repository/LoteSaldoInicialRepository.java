package pe.edu.virgenmaria.cuentasclaras.cobranza.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.LoteSaldoInicial;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Lotes de saldo inicial del colegio actual. Sin borrados ni {@code @Modifying}. */
public interface LoteSaldoInicialRepository extends Repository<LoteSaldoInicial, Long> {

	LoteSaldoInicial save(LoteSaldoInicial lote);

	LoteSaldoInicial saveAndFlush(LoteSaldoInicial lote);

	Optional<LoteSaldoInicial> findById(Long id);

	List<LoteSaldoInicial> findAllByOrderByCreadoEnDescIdDesc();

	@Query("select l.anioEscolar.id from LoteSaldoInicial l where l.id = :id")
	Optional<Long> anioDe(@Param("id") Long id);

	/** La fecha de corte más reciente de los lotes confirmados del año ({@code null} si no hay). */
	@Query("select max(l.fechaCorte) from LoteSaldoInicial l where l.anioEscolar.id = :anio "
			+ "and l.estado = pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoLote.CONFIRMADO")
	LocalDate ultimoCorteConfirmado(@Param("anio") Long anioId);

	/** Lee y bloquea el lote: dos confirmaciones simultáneas se serializan. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from LoteSaldoInicial l where l.id = :id")
	Optional<LoteSaldoInicial> bloquear(@Param("id") Long id);
}
