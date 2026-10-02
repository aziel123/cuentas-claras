package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.config.PropiedadesComprobantes;
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

	private final PropiedadesComprobantes propiedades;

	public AperturaSerie(SerieComprobanteRepository series, PropiedadesComprobantes propiedades) {
		this.series = series;
		this.propiedades = propiedades;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void crearSiFalta(TipoComprobante tipo) {
		String serie = propiedades.serieDe(tipo);
		if (!series.existsBySerie(serie)) {
			series.save(SerieComprobante.nueva(tipo, serie, propiedades.proveedor()));
		}
	}
}
