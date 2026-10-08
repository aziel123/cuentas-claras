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

	/**
	 * Nota de crédito que anula {@code original} (boleta → BC01, factura → FC01), con su propio correlativo sin huecos.
	 * El número del original no se reutiliza nunca.
	 */
	public Comprobante emitirNotaCredito(Comprobante original, String motivo, LocalDate fecha) {
		Objects.requireNonNull(original, "original");
		String serieNota = serieNotaDe(original);
		SerieComprobante serie = bloquear(TipoComprobante.NOTA_CREDITO, serieNota);
		int numero = serie.siguiente();
		series.saveAndFlush(serie);
		Comprobante nota = comprobantes.save(Comprobante.notaDeCredito(serie, numero, fecha, original, motivo));
		eventos.publishEvent(new ComprobanteEmitido(nota.getId(), nota.getColegioId()));
		return nota;
	}

	/**
	 * Reemite un comprobante RECHAZADO por el OSE con un número NUEVO de la serie vigente de su tipo y el receptor ya
	 * corregido. El rechazado se queda con su número: la serie sigue sin huecos.
	 */
	public Comprobante reemitir(Comprobante rechazado, Receptor receptor, LocalDate fecha) {
		Objects.requireNonNull(rechazado, "rechazado");
		if (comprobantes.existsByReemplazaId(rechazado.getId())) {
			throw new pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException("Este comprobante ya se reemitió.");
		}
		SerieComprobante serie = bloquear(rechazado.getTipo());
		int numero = serie.siguiente();
		series.saveAndFlush(serie);
		Comprobante nuevo = comprobantes.save(Comprobante.reemitir(serie, numero, fecha, rechazado, receptor));
		eventos.publishEvent(new ComprobanteEmitido(nuevo.getId(), nuevo.getColegioId()));
		return nuevo;
	}

	/** El comprobante que hoy vale por {@code original}: sigue la cadena de reemisiones (el último de ella). */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public Comprobante vigenteDe(Comprobante original) {
		Comprobante actual = Objects.requireNonNull(original, "original");
		for (int i = 0; i < 20; i++) {
			java.util.Optional<Comprobante> siguiente = comprobantes.findByReemplazaId(actual.getId());
			if (siguiente.isEmpty()) {
				return actual;
			}
			actual = siguiente.get();
		}
		return actual;
	}

	/** BC01 para una boleta, FC01 para una factura (la letra del comprobante que se anula). */
	public String serieNotaDe(Comprobante original) {
		return switch (original.getTipo()) {
			case BOLETA -> propiedades.serieNotaBoleta();
			case FACTURA -> propiedades.serieNotaFactura();
			case NOTA_CREDITO -> throw new IllegalArgumentException("Una nota de crédito no se anula con otra");
		};
	}

	/** El proveedor configurado (para la marca «simulado» del impreso). */
	public boolean simulado() {
		return propiedades.proveedor() == pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes.SIMULADO;
	}

	private SerieComprobante bloquear(TipoComprobante tipo) {
		return bloquear(tipo, propiedades.serieDe(tipo));
	}

	private SerieComprobante bloquear(TipoComprobante tipo, String serie) {
		return series.bloquear(serie).orElseGet(() -> {
			try {
				apertura.crearSiFalta(tipo, serie);
			}
			catch (DataIntegrityViolationException otraLaCreo) {
				// Otro cobro la creó a la vez: se usa esa.
			}
			return series.bloquear(serie).orElseThrow(() -> new IllegalStateException("No se pudo abrir la serie " + serie));
		});
	}
}
