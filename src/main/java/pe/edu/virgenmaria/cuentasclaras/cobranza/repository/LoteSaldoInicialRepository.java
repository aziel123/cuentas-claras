package pe.edu.virgenmaria.cuentasclaras.cobranza.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.LoteSaldoInicial;

import java.util.List;
import java.util.Optional;

/** Lotes de saldo inicial del colegio actual. Sin borrados ni {@code @Modifying}. */
public interface LoteSaldoInicialRepository extends Repository<LoteSaldoInicial, Long> {

	LoteSaldoInicial save(LoteSaldoInicial lote);

	LoteSaldoInicial saveAndFlush(LoteSaldoInicial lote);

	Optional<LoteSaldoInicial> findById(Long id);

	List<LoteSaldoInicial> findAllByOrderByCreadoEnDescIdDesc();

	/** Lee y bloquea el lote: dos confirmaciones simultáneas se serializan. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from LoteSaldoInicial l where l.id = :id")
	Optional<LoteSaldoInicial> bloquear(@Param("id") Long id);
}
