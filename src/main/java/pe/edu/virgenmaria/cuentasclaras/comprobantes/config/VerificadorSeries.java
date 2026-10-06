package pe.edu.virgenmaria.cuentasclaras.comprobantes.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.SerieComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;

import java.util.List;
import java.util.Set;

/**
 * En prod y en el piloto, con el OSE real activado, se niega a arrancar si alguna serie configurada ya existe en la base
 * con el proveedor SIMULADO: un número simulado nunca se confunde con uno real (decisión 20: con Nubefact se usan series
 * nuevas, por ejemplo B002). La base lo refuerza con la FK {@code (serie, proveedor)} de V13.
 */
@Component
@Profile({ "prod", "piloto" })
public class VerificadorSeries implements InitializingBean {

	private static final Logger LOG = LoggerFactory.getLogger(VerificadorSeries.class);

	private final PropiedadesComprobantes propiedades;

	private final SerieComprobanteRepository series;

	private final RecorridoColegios colegios;

	public VerificadorSeries(PropiedadesComprobantes propiedades, SerieComprobanteRepository series,
			RecorridoColegios colegios) {
		this.propiedades = propiedades;
		this.series = series;
		this.colegios = colegios;
	}

	@Override
	public void afterPropertiesSet() {
		verificar();
	}

	public void verificar() {
		if (propiedades.proveedor() == ProveedorComprobantes.SIMULADO) {
			return;
		}
		Set<String> configuradas = Set.of(propiedades.serieBoleta(), propiedades.serieFactura(),
				propiedades.serieNotaBoleta(), propiedades.serieNotaFactura());
		for (Long colegio : colegios.activos()) {
			List<String> simuladas = ContextoColegio.en(colegio, () -> series.findAll()).stream()
					.filter(s -> s.getProveedor() != propiedades.proveedor() && configuradas.contains(s.getSerie()))
					.map(SerieComprobante::getSerie).toList();
			if (!simuladas.isEmpty()) {
				throw new IllegalStateException("Las series " + simuladas + " ya se usaron con el emisor SIMULADO en el colegio "
						+ colegio + ": con " + propiedades.proveedor() + " configura series nuevas (por ejemplo B002, F002, "
						+ "BC02 y FC02) para que un número simulado nunca pase por uno real.");
			}
		}
		LOG.info("Series del OSE verificadas: ninguna serie configurada se usó con el emisor simulado.");
	}
}
