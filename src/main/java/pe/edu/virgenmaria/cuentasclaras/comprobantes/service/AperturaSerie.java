package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.SerieComprobanteRepository;

/**
 * Crea la serie de un tipo de comprobante la primera vez que se usa, en su PROPIA transacción (ya confirmada cuando
 * el cobro la bloquea). Si dos cobros la crean a la vez, el segundo falla por el UNIQUE con
 * {@code DataIntegrityViolationException}: quien llama la ignora y usa la serie que creó el otro.
 * <p>
 * Conviene llamarla ANTES de abrir la transacción del cobro: dentro de ella pide una segunda conexión del pool.
 */
@Component
public class AperturaSerie {

	private final SerieComprobanteRepository series;

	private final OseDelColegio ose;

	public AperturaSerie(SerieComprobanteRepository series, OseDelColegio ose) {
		this.series = series;
		this.ose = ose;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void crearSiFalta(TipoComprobante tipo) {
		crearSiFalta(tipo, ose.actual().serieDe(tipo));
	}

	/** Para las series de notas de crédito (BC01, FC01), que dependen de la letra del comprobante que anulan. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void crearSiFalta(TipoComprobante tipo, String serie) {
		crearSiFalta(tipo, serie, null);
	}

	/** Sprint 8, tanda 2: con el proveedor indicado (la nota de un comprobante de otro OSE); {@code null}, el de hoy. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void crearSiFalta(TipoComprobante tipo, String serie,
			pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes proveedor) {
		if (!series.existsBySerie(serie)) {
			// Sprint 8, tanda 2: con el proveedor del OSE del colegio (su conector vigente o, sin conector, el del entorno).
			series.save(SerieComprobante.nueva(tipo, serie, proveedor == null ? ose.actual().proveedor() : proveedor));
		}
	}
}
