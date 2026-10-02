package pe.edu.virgenmaria.cuentasclaras.cobranza.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.LineaSaldoInicial;

import java.util.Collection;
import java.util.List;

/** Las líneas se crean desde su lote ({@code LoteSaldoInicial.agregarLinea}) y se quitan con un flag: sin borrados. */
public interface LineaSaldoInicialRepository extends Repository<LineaSaldoInicial, Long> {

	LineaSaldoInicial saveAndFlush(LineaSaldoInicial linea);

	@Query("select l from LineaSaldoInicial l join fetch l.lote where l.id in :ids")
	List<LineaSaldoInicial> conLote(@Param("ids") Collection<Long> ids);
}
