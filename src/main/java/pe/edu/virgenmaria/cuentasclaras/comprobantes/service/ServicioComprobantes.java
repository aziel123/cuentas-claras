package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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

	private final OseDelColegio ose;

	private final ApplicationEventPublisher eventos;

	private final pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService colegios;

	private final pe.edu.virgenmaria.cuentasclaras.colegio.service.LogosColegio logos;

	public ServicioComprobantes(SerieComprobanteRepository series, ComprobanteRepository comprobantes,
			AperturaSerie apertura, OseDelColegio ose, ApplicationEventPublisher eventos,
			pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService colegios,
			pe.edu.virgenmaria.cuentasclaras.colegio.service.LogosColegio logos) {
		this.colegios = colegios;
		this.logos = logos;
		this.series = series;
		this.comprobantes = comprobantes;
		this.apertura = apertura;
		this.ose = ose;
		this.eventos = eventos;
	}

	/** Boleta o factura por la suma de las líneas. */
	public Comprobante emitir(TipoComprobante tipo, Receptor receptor, LocalDate fecha, List<LineaDocumento> lineas) {
		Objects.requireNonNull(tipo, "tipo");
		SerieComprobante serie = bloquear(tipo);
		int numero = serie.siguiente();
		// Hibernate deja el UPDATE para el flush: debe salir ANTES del INSERT del comprobante (trigger en MySQL).
		series.saveAndFlush(serie);
		Comprobante comprobante = comprobantes.save(conFoto(Comprobante.emitir(serie, numero, fecha, receptor,
				ose.actual().afectacion(), lineas)));
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
		SerieComprobante serie = bloquear(TipoComprobante.NOTA_CREDITO, serieNota, original.getProveedor());
		int numero = serie.siguiente();
		series.saveAndFlush(serie);
		Comprobante nota = comprobantes.save(conFoto(Comprobante.notaDeCredito(serie, numero, fecha, original, motivo)));
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
		Comprobante nuevo = comprobantes.save(conFoto(Comprobante.reemitir(serie, numero, fecha, rechazado, receptor)));
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
		// Sprint 8, tanda 2: con una serie del MISMO proveedor que el comprobante (el colegio pudo cambiar de OSE).
		return ose.serieNotaPara(original.getProveedor(), original.getTipo());
	}

	/** El proveedor configurado (para la marca «simulado» del impreso). */
	public boolean simulado() {
		return ose.actual().simulado();
	}

	private SerieComprobante bloquear(TipoComprobante tipo) {
		return bloquear(tipo, ose.actual().serieDe(tipo));
	}

	private SerieComprobante bloquear(TipoComprobante tipo, String serie) {
		return bloquear(tipo, serie, null);
	}

	/** {@code proveedor}: el de la serie si hay que abrirla ({@code null}: el del OSE de hoy). */
	private SerieComprobante bloquear(TipoComprobante tipo, String serie,
			pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes proveedor) {
		return series.bloquear(serie).orElseGet(() -> {
			try {
				apertura.crearSiFalta(tipo, serie, proveedor);
			}
			catch (DataIntegrityViolationException otraLaCreo) {
				// Otro cobro la creó a la vez: se usa esa.
			}
			return series.bloquear(serie).orElseThrow(() -> new IllegalStateException("No se pudo abrir la serie " + serie));
		});
	}

	/**
	 * Correcciones del sprint 8 (S8-B4 y QA-S8-5): la foto del emisor al emitir (nombre, razón social, RUC, dirección y el
	 * logo vigente del colegio en contexto). La reimpresión la usa siempre.
	 */
	private Comprobante conFoto(Comprobante comprobante) {
		Long colegioId = pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.actual();
		colegios.marcaDe(colegioId).ifPresent(m -> comprobante.fotografiarEmisor(m.nombre(), m.razonSocial(), m.ruc(),
				m.direccion(), logos.vigente().map(pe.edu.virgenmaria.cuentasclaras.colegio.model.LogoColegio::getId)
						.orElse(null)));
		return comprobante;
	}
}
