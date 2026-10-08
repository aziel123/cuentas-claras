package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.config.PropiedadesComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.ComprobanteRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Outbox del OSE (sprint 4). Cada comprobante se procesa en su PROPIA transacción, después del commit del pago que lo
 * emitió: si el OSE falla, el pago no se revierte.
 * <ul>
 *   <li>PENDIENTE: se envía. Si falla la red, hay un 429 o un 5xx, sigue PENDIENTE con un intento más y el próximo
 *       intento con espera creciente (1, 2, 4… hasta 60 minutos).</li>
 *   <li>ENVIADO (el OSE lo recibió y falta su respuesta): se CONSULTA, no se reenvía. Si el OSE no lo encuentra, vuelve a
 *       PENDIENTE (el envío es idempotente por serie y número).</li>
 *   <li>Una nota de crédito no se envía hasta que su comprobante esté ACEPTADO u OBSERVADO.</li>
 *   <li>ACEPTADO, OBSERVADO y RECHAZADO son definitivos: no se vuelven a tocar (en MySQL lo exige un trigger).</li>
 * </ul>
 * Quién llama fija el colegio y el actor (el envío después del commit y los reintentos corren como {@code sistema.ose}).
 * Nunca deja escapar una excepción. El log no lleva datos personales: solo la serie y el número.
 */
@Component
public class EnvioComprobantes {

	private static final Logger LOG = LoggerFactory.getLogger(EnvioComprobantes.class);

	static final String SIN_RESPUESTA = "Sin respuesta del OSE: se reintentará.";

	/** Lote máximo por colegio en cada pasada del outbox. */
	public static final int LOTE = 20;

	private final ComprobanteRepository comprobantes;

	private final EmisorElectronico emisor;

	private final TransactionTemplate transaccion;

	private final TransactionTemplate lectura;

	private final Clock reloj;

	private final PropiedadesComprobantes propiedades;

	private final ApplicationEventPublisher eventos;

	public EnvioComprobantes(ComprobanteRepository comprobantes, EmisorElectronico emisor,
			PlatformTransactionManager transacciones, Clock reloj, PropiedadesComprobantes propiedades,
			ApplicationEventPublisher eventos) {
		this.comprobantes = comprobantes;
		this.emisor = emisor;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.lectura.setReadOnly(true);
		this.reloj = reloj;
		this.propiedades = propiedades;
		this.eventos = eventos;
	}

	/**
	 * Envía o consulta el comprobante en una transacción nueva.
	 *
	 * @return el estado del envío después de procesarlo ({@code null} si no se pudo registrar)
	 */
	public EstadoEnvio procesar(Long id) {
		try {
			return transaccion.execute(estado -> procesarEnTransaccion(id));
		}
		catch (RuntimeException e) {
			LOG.error("No se pudo registrar el envío del comprobante {}: {}", id, e.getClass().getSimpleName());
			return null;
		}
	}

	/** Los comprobantes del colegio actual cuyo envío o consulta ya toca (hasta {@link #LOTE}), el más antiguo primero. */
	public List<Long> porEnviar() {
		LocalDateTime ahora = ahora();
		List<Long> ids = lectura.execute(estado -> comprobantes.porEnviar(ahora, ahora.minusMinutes(1),
				PageRequest.of(0, LOTE)));
		return ids == null ? List.of() : ids;
	}

	/**
	 * Reconsulta nocturna: pregunta al OSE por cada ACEPTADO u OBSERVADO de ese día. Si el OSE no lo tiene o dice otra
	 * cosa, solo avisa ({@link ComprobanteNoCoincideOse}): el estado ya no puede cambiar.
	 *
	 * @return cuántos no coinciden
	 */
	public int reconsultar(LocalDate dia) {
		Integer distintos = transaccion.execute(estado -> {
			int cuenta = 0;
			for (Comprobante c : comprobantes.findByEstadoEnvioInAndAceptadoEnGreaterThanEqualAndAceptadoEnLessThanOrderByIdAsc(
					EnumSet.of(EstadoEnvio.ACEPTADO, EstadoEnvio.OBSERVADO), dia.atStartOfDay(),
					dia.plusDays(1).atStartOfDay())) {
				String enOse;
				try {
					ResultadoEnvio r = emisor.consultar(c.getTipo(), c.getSerie(), c.getNumero());
					enOse = r == null ? "SIN RESPUESTA" : r.estado().name();
				}
				catch (RuntimeException e) {
					LOG.warn("La reconsulta de {} falló ({}): se repite mañana.", c.numeroCompleto(),
							e.getClass().getSimpleName());
					continue;
				}
				if (!enOse.equals(c.getEstadoEnvio().name())) {
					cuenta++;
					eventos.publishEvent(new ComprobanteNoCoincideOse(c.getId(), c.numeroCompleto(),
							c.getEstadoEnvio().name(), enOse));
				}
			}
			return cuenta;
		});
		return distintos == null ? 0 : distintos;
	}

	private EstadoEnvio procesarEnTransaccion(Long id) {
		Comprobante comprobante = comprobantes.findById(id).orElse(null);
		if (comprobante == null) {
			LOG.error("El comprobante {} no existe en su colegio: no se envió.", id);
			return null;
		}
		if (comprobante.getEstadoEnvio().definitivo()) {
			return comprobante.getEstadoEnvio();
		}
		LocalDateTime ahora = ahora();
		Comprobante modificado = comprobante.getModificaId() == null ? null
				: comprobantes.findById(comprobante.getModificaId()).orElse(null);
		boolean consulta = comprobante.getEstadoEnvio() == EstadoEnvio.ENVIADO;
		if (!consulta && modificado != null && !modificado.getEstadoEnvio().valido()) {
			comprobante.esperarComprobanteModificado("Espera que el OSE acepte " + modificado.numeroCompleto()
					+ " antes de enviar la nota de crédito.", ahora.plus(propiedades.reintentoInicial()));
			return comprobante.getEstadoEnvio();
		}
		ResultadoEnvio resultado;
		try {
			resultado = consulta ? emisor.consultar(comprobante.getTipo(), comprobante.getSerie(), comprobante.getNumero())
					: emisor.enviar(DocumentoElectronico.de(comprobante, modificado));
			Objects.requireNonNull(resultado, "respuesta del OSE");
		}
		catch (RuntimeException e) {
			LOG.warn("El {} de {} al OSE falló ({}): se reintentará.", consulta ? "consulta" : "envío",
					comprobante.numeroCompleto(), e.getClass().getSimpleName());
			fallo(comprobante, (consulta ? "Falló la consulta al OSE (" : "Falló el envío al OSE (")
					+ e.getClass().getSimpleName() + ").", ahora);
			return comprobante.getEstadoEnvio();
		}
		if (resultado.estado() == EstadoEnvio.PENDIENTE) {
			if (consulta) {
				comprobante.volverAPendiente("El OSE no lo encontró al consultar: se volverá a enviar.", ahora);
			}
			else {
				fallo(comprobante, "El OSE respondió sin resultado.", ahora);
			}
			return comprobante.getEstadoEnvio();
		}
		try {
			comprobante.registrarEnvio(resultado, ahora, ahora.plus(propiedades.consultaCada()));
		}
		catch (IllegalStateException sinHash) {
			fallo(comprobante, "El OSE no devolvió el hash del comprobante aceptado: se consultará de nuevo.", ahora);
			return comprobante.getEstadoEnvio();
		}
		if (resultado.estado().definitivo()) {
			eventos.publishEvent(new EnvioComprobanteResuelto(comprobante.getId(), comprobante.numeroCompleto(),
					comprobante.getTipo().etiqueta(), comprobante.getEstadoEnvio(), comprobante.getRespuesta(),
					comprobante.getCodigoRespuesta(), comprobante.getIntentos()));
		}
		return comprobante.getEstadoEnvio();
	}

	private void fallo(Comprobante comprobante, String detalle, LocalDateTime ahora) {
		int intento = comprobante.getIntentos() + 1;
		comprobante.registrarFalloEnvio(SIN_RESPUESTA, detalle, ahora, ahora.plus(propiedades.esperaTrasIntentos(intento)));
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
