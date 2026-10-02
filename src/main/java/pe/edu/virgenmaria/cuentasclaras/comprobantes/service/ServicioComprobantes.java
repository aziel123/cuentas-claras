package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.config.PropiedadesComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.LineaDocumento;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Receptor;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.ComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.SerieComprobanteRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Emite comprobantes con numeración SIN HUECOS: bloquea la serie, toma el número siguiente, guarda la serie con flush
 * (en MySQL el trigger del comprobante compara con ella) y guarda el comprobante, todo en la transacción del pago. Si
 * el pago falla, el rollback devuelve el número. El envío al OSE ocurre después del commit ({@link EnvioComprobantes}).
 * <p>
 * Sin rol propio: exige la transacción del servicio que lo llama (que ya exigió el suyo). ArchUnit permite usarlo solo
 * desde {@code caja.service}.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ServicioComprobantes {

	private final SerieComprobanteRepository series;

	private final ComprobanteRepository comprobantes;

	private final AperturaSerie apertura;

	private final PropiedadesComprobantes propiedades;

	private final ApplicationEventPublisher eventos;

	public ServicioComprobantes(SerieComprobanteRepository series, ComprobanteRepository comprobantes,
			AperturaSerie apertura, PropiedadesComprobantes propiedades, ApplicationEventPublisher eventos) {
		this.series = series;
		this.comprobantes = comprobantes;
		this.apertura = apertura;
		this.propiedades = propiedades;
		this.eventos = eventos;
	}

	/** Boleta o factura por la suma de las líneas. */
	public Comprobante emitir(TipoComprobante tipo, Receptor receptor, LocalDate fecha, List<LineaDocumento> lineas) {
		Objects.requireNonNull(tipo, "tipo");
		SerieComprobante serie = bloquear(tipo);
		int numero = serie.siguiente();
		// Hibernate deja el UPDATE para el flush: debe salir ANTES del INSERT del comprobante (trigger en MySQL).
		series.saveAndFlush(serie);
		Comprobante comprobante = comprobantes.save(Comprobante.emitir(serie, numero, fecha, receptor,
				propiedades.afectacionIgv(), lineas));
		eventos.publishEvent(new ComprobanteEmitido(comprobante.getId(), comprobante.getColegioId()));
		return comprobante;
	}

	/** El proveedor configurado (para la marca «simulado» del impreso). */
	public boolean simulado() {
		return propiedades.proveedor() == pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes.SIMULADO;
	}

	private SerieComprobante bloquear(TipoComprobante tipo) {
		String serie = propiedades.serieDe(tipo);
		return series.bloquear(serie).orElseGet(() -> {
			try {
				apertura.crearSiFalta(tipo);
			}
			catch (DataIntegrityViolationException otraLaCreo) {
				// Otro cobro la creó a la vez: se usa esa.
			}
			return series.bloquear(serie).orElseThrow(() -> new IllegalStateException("No se pudo abrir la serie " + serie));
		});
	}
}
