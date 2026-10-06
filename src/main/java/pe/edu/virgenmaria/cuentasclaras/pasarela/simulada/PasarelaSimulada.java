package pe.edu.virgenmaria.cuentasclaras.pasarela.simulada;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.pasarela.config.PropiedadesPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.CobroConfirmado;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoCobro;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.AvisoNoAutenticoException;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.AvisoPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.OrdenCreada;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.PasarelaPagos;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ReembolsoPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.SolicitudOrden;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pasarela SIMULADA: NO MUEVE DINERO REAL. Solo existe en dev, test y piloto (además de la configuración, la base de
 * producción rechaza sus órdenes con un trigger y el arranque en prod se niega si se configura). Guarda en memoria sus
 * «cobros» y firma sus avisos con HMAC-SHA256, como haría una pasarela real; el apoderado la usa desde
 * {@code /familia/pasarela-simulada/{referencia}} (página con la franja «SIMULADOR · NO ES DINERO REAL»).
 */
@Component
@Profile({ "dev", "test", "piloto" })
@ConditionalOnProperty(name = "cuentasclaras.pasarela.proveedor", havingValue = "SIMULADA")
public class PasarelaSimulada implements PasarelaPagos {

	/** Cabecera con la firma HMAC-SHA256 (hexadecimal) del cuerpo del aviso. */
	public static final String CABECERA_FIRMA = "x-simulada-firma";

	public static final String TIPO_AVISO = "order.status.changed";

	/** Lo que el simulador puede hacer con una orden. */
	public enum Accion {
		YAPE, TARJETA, RECHAZAR, MONTO_MENOR, CONTRACARGO
	}

	/** Un aviso listo para entregarse al webhook: el cuerpo y sus cabeceras (con la firma). */
	public record AvisoFirmado(byte[] cuerpo, Map<String, String> cabeceras) {
	}

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private final Map<String, OrdenSimulada> ordenes = new ConcurrentHashMap<>();

	private final byte[] secreto;

	private final Clock reloj;

	public PasarelaSimulada(PropiedadesPasarela propiedades, Clock reloj) {
		String clave = propiedades.simulada().secretoAviso();
		if (clave == null || clave.length() < 16) {
			throw new IllegalStateException("La pasarela simulada necesita su secreto de avisos "
					+ "(cuentasclaras.pasarela.simulada.secreto-aviso, 16 caracteres o más).");
		}
		this.secreto = clave.getBytes(StandardCharsets.UTF_8);
		this.reloj = reloj;
	}

	@Override
	public ProveedorPasarela proveedor() {
		return ProveedorPasarela.SIMULADA;
	}

	@Override
	public OrdenCreada crearOrden(SolicitudOrden solicitud) {
		Objects.requireNonNull(solicitud, "solicitud");
		String id = "SIMORD" + aleatorio(10);
		ordenes.put(id, new OrdenSimulada(id, solicitud.referencia(), Dinero.normalizar(solicitud.monto()),
				solicitud.venceEn(), solicitud.colegioId()));
		return new OrdenCreada(id, "/familia/pasarela-simulada/" + solicitud.referencia());
	}

	@Override
	public AvisoPasarela verificarAviso(byte[] cuerpo, Map<String, String> cabeceras) {
		String firma = cabeceras == null ? null : cabeceras.entrySet().stream()
				.filter(c -> CABECERA_FIRMA.equalsIgnoreCase(c.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
		if (firma == null || !MessageDigest.isEqual(firma.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
				firmar(cuerpo).getBytes(StandardCharsets.UTF_8))) {
			throw new AvisoNoAutenticoException("firma del aviso no válida");
		}
		Map<String, String> campos = leer(cuerpo);
		if (campos.get("evento") == null || campos.get("tipo") == null) {
			throw new AvisoNoAutenticoException("aviso incompleto");
		}
		return new AvisoPasarela(campos.get("evento"), campos.get("tipo"), campos.get("referencia"), campos.get("orden"));
	}

	@Override
	public EstadoCobro consultar(String proveedorOrdenId) {
		OrdenSimulada orden = ordenes.get(proveedorOrdenId);
		if (orden == null) {
			return EstadoCobro.sin(EstadoCobro.Estado.PENDIENTE);
		}
		synchronized (orden) {
			if (orden.contracargo) {
				return EstadoCobro.sin(EstadoCobro.Estado.CONTRACARGO);
			}
			if (orden.cobro != null) {
				return EstadoCobro.pagado(orden.cobro);
			}
			if (orden.rechazada) {
				return EstadoCobro.sin(EstadoCobro.Estado.RECHAZADO);
			}
			if (orden.venceEn != null && !LocalDateTime.now(reloj).isBefore(orden.venceEn)) {
				return EstadoCobro.sin(EstadoCobro.Estado.EXPIRADO);
			}
			return EstadoCobro.sin(EstadoCobro.Estado.PENDIENTE);
		}
	}

	@Override
	public ReembolsoPasarela reembolsar(String cargoId, BigDecimal monto, String motivo) {
		OrdenSimulada orden = ordenes.values().stream().filter(o -> o.cobro != null && o.cobro.cargoId().equals(cargoId))
				.findFirst().orElseThrow(() -> new IllegalStateException("La pasarela simulada no tiene ese cargo"));
		synchronized (orden) {
			if (orden.reembolso != null) {
				return orden.reembolso;
			}
			if (Dinero.normalizar(monto).compareTo(orden.cobro.monto()) > 0) {
				throw new IllegalStateException("No se reembolsa más de lo cobrado");
			}
			orden.reembolso = new ReembolsoPasarela("SIMREF" + aleatorio(10), cargoId, Dinero.normalizar(monto));
			return orden.reembolso;
		}
	}

	/**
	 * Lo que hace el apoderado en la página del simulador. Devuelve el aviso firmado que la «pasarela» envía al webhook.
	 */
	public AvisoFirmado simular(String proveedorOrdenId, Accion accion) {
		OrdenSimulada orden = ordenes.get(proveedorOrdenId);
		if (orden == null) {
			throw new IllegalStateException("La pasarela simulada no tiene esa orden (¿se reinició la aplicación?)");
		}
		synchronized (orden) {
			switch (accion) {
				case YAPE, TARJETA, MONTO_MENOR -> {
					if (orden.cobro == null && !orden.rechazada) {
						BigDecimal monto = accion == Accion.MONTO_MENOR
								? orden.monto.subtract(BigDecimal.ONE).max(new BigDecimal("0.01")) : orden.monto;
						orden.cobro = new CobroConfirmado("SIMCHG" + aleatorio(10), "SIM" + numerico(12), monto, "PEN",
								accion == Accion.TARJETA ? MedioPago.TARJETA : MedioPago.YAPE,
								LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
					}
				}
				case RECHAZAR -> {
					if (orden.cobro == null) {
						orden.rechazada = true;
					}
				}
				case CONTRACARGO -> {
					if (orden.cobro != null) {
						orden.contracargo = true;
					}
				}
			}
		}
		return aviso(orden, "SIMEVT" + aleatorio(12));
	}

	/** El mismo aviso que ya se envió (para probar que un aviso repetido no duplica nada). */
	public AvisoFirmado aviso(String proveedorOrdenId, String eventoId) {
		return aviso(ordenes.get(proveedorOrdenId), eventoId);
	}

	public String proveedorOrdenIdDe(String referencia) {
		return ordenes.values().stream().filter(o -> o.referencia.equals(referencia)).map(o -> o.id).findFirst()
				.orElse(null);
	}

	private AvisoFirmado aviso(OrdenSimulada orden, String eventoId) {
		Objects.requireNonNull(orden, "orden");
		String texto = "evento=" + codificar(eventoId) + "&tipo=" + codificar(TIPO_AVISO) + "&referencia="
				+ codificar(orden.referencia) + "&orden=" + codificar(orden.id);
		byte[] cuerpo = texto.getBytes(StandardCharsets.UTF_8);
		return new AvisoFirmado(cuerpo, Map.of(CABECERA_FIRMA, firmar(cuerpo)));
	}

	/** HMAC-SHA256 del cuerpo con el secreto, en hexadecimal. */
	public String firmar(byte[] cuerpo) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(cuerpo == null ? new byte[0] : cuerpo));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("HMAC-SHA256 no disponible", e);
		}
	}

	private static Map<String, String> leer(byte[] cuerpo) {
		Map<String, String> campos = new HashMap<>();
		for (String par : new String(cuerpo, StandardCharsets.UTF_8).split("&")) {
			int igual = par.indexOf('=');
			if (igual > 0) {
				campos.put(URLDecoder.decode(par.substring(0, igual), StandardCharsets.UTF_8),
						URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8));
			}
		}
		return campos;
	}

	private static String codificar(String texto) {
		return URLEncoder.encode(texto, StandardCharsets.UTF_8);
	}

	private static String aleatorio(int largo) {
		String alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
		StringBuilder texto = new StringBuilder(largo);
		for (int i = 0; i < largo; i++) {
			texto.append(alfabeto.charAt(ALEATORIO.nextInt(alfabeto.length())));
		}
		return texto.toString();
	}

	private static String numerico(int largo) {
		StringBuilder texto = new StringBuilder(largo);
		texto.append(1 + ALEATORIO.nextInt(9));
		for (int i = 1; i < largo; i++) {
			texto.append(ALEATORIO.nextInt(10));
		}
		return texto.toString();
	}

	/** Una orden en la «pasarela» (en memoria). */
	private static final class OrdenSimulada {

		private final String id;

		private final String referencia;

		private final BigDecimal monto;

		private final LocalDateTime venceEn;

		private final Long colegioId;

		private CobroConfirmado cobro;

		private boolean rechazada;

		private boolean contracargo;

		private ReembolsoPasarela reembolso;

		private OrdenSimulada(String id, String referencia, BigDecimal monto, LocalDateTime venceEn, Long colegioId) {
			this.id = id;
			this.referencia = referencia;
			this.monto = monto;
			this.venceEn = venceEn;
			this.colegioId = colegioId;
		}
	}
}
