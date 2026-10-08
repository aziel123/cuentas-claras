package pe.edu.virgenmaria.cuentasclaras.pasarela.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.pasarela.config.PropiedadesPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EventoPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.EventoPasarelaRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.AvisoNoAutenticoException;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.AvisoPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ContadorAvisosNoAutenticos;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.PasarelaPagos;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.Pasarelas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Bandeja de entrada de los avisos (webhooks) de la pasarela, en {@code POST /webhooks/pasarela/{proveedor}/{colegio}}.
 * <ol>
 *   <li>Cuerpo de hasta 16 KB; el proveedor debe ser el configurado y el colegio, uno activo.</li>
 *   <li>Firma o autenticación del aviso: si no es auténtico se responde 401, se cuenta en memoria y NO se escribe en la
 *       bitácora (nadie llena la cadena HMAC con avisos falsos).</li>
 *   <li>Como {@code sistema.pasarela} y en el colegio de la URL: guarda el evento (uno por proveedor y evento: el repetido
 *       responde «ya recibido»). La orden se busca filtrada por ese colegio.</li>
 *   <li>Lo procesa: CONSULTA la pasarela con la llave secreta. El aviso por sí solo nunca registra dinero.</li>
 * </ol>
 */
@Component
public class RecepcionAvisos {

	private static final Logger LOG = LoggerFactory.getLogger(RecepcionAvisos.class);

	public enum Resultado {
		ACEPTADO, YA_RECIBIDO, NO_AUTENTICO, NO_ENCONTRADO, MUY_GRANDE
	}

	private final Pasarelas pasarelas;

	private final ColegioRepository colegios;

	private final OrdenPagoRepository ordenes;

	private final EventoPasarelaRepository eventos;

	private final ProcesadorPagosEnLinea procesador;

	private final ContadorAvisosNoAutenticos noAutenticos;

	private final PropiedadesPasarela propiedades;

	private final TransactionTemplate transaccion;

	public RecepcionAvisos(Pasarelas pasarelas, ColegioRepository colegios, OrdenPagoRepository ordenes,
			EventoPasarelaRepository eventos, ProcesadorPagosEnLinea procesador, ContadorAvisosNoAutenticos noAutenticos,
			PropiedadesPasarela propiedades, PlatformTransactionManager transacciones) {
		this.pasarelas = pasarelas;
		this.colegios = colegios;
		this.ordenes = ordenes;
		this.eventos = eventos;
		this.procesador = procesador;
		this.noAutenticos = noAutenticos;
		this.propiedades = propiedades;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** Tamaño máximo del cuerpo de un aviso (16 KB por defecto). */
	public int maximoBytes() {
		return propiedades.maximoBytesAviso();
	}

	public Resultado recibir(String proveedorTexto, Long colegioId, byte[] cuerpo, Map<String, String> cabeceras) {
		if (cuerpo == null || cuerpo.length > propiedades.maximoBytesAviso()) {
			return Resultado.MUY_GRANDE;
		}
		ProveedorPasarela proveedor;
		PasarelaPagos pasarela;
		try {
			proveedor = ProveedorPasarela.valueOf(String.valueOf(proveedorTexto).toUpperCase(Locale.ROOT));
			pasarela = pasarelas.de(proveedor);
		}
		catch (RuntimeException e) {
			return Resultado.NO_ENCONTRADO;
		}
		if (colegioId == null || colegioId <= 0
				|| colegios.findById(colegioId).filter(Colegio::isActivo).isEmpty()) {
			// S4-B4: igual que un aviso no auténtico (el controlador responde 401 en ambos casos) y cuenta para la alerta.
			noAutenticos.registrar();
			return Resultado.NO_ENCONTRADO;
		}
		AvisoPasarela aviso;
		try {
			aviso = pasarela.verificarAviso(cuerpo, cabeceras);
		}
		catch (AvisoNoAutenticoException e) {
			noAutenticos.registrar();
			LOG.warn("Aviso de {} no auténtico para el colegio {}: {}", proveedor, colegioId, e.getMessage());
			return Resultado.NO_AUTENTICO;
		}
		String huella = sha256(cuerpo);
		Long eventoId = EjecucionComoSistema.como(ActorSistema.PASARELA, colegioId, () -> guardar(proveedor, aviso, huella));
		if (eventoId == null) {
			return Resultado.YA_RECIBIDO;
		}
		procesador.procesarEvento(colegioId, eventoId);
		return Resultado.ACEPTADO;
	}

	/** @return el id del evento nuevo, o {@code null} si ya se había recibido */
	private Long guardar(ProveedorPasarela proveedor, AvisoPasarela aviso, String huella) {
		try {
			return transaccion.execute(e -> {
				if (eventos.findByProveedorAndEventoId(proveedor, aviso.eventoId()).isPresent()) {
					return null;
				}
				OrdenPago orden = (aviso.referenciaOrden() == null ? java.util.Optional.<OrdenPago>empty()
						: ordenes.findByReferencia(aviso.referenciaOrden()))
						.or(() -> aviso.proveedorOrdenId() == null ? java.util.Optional.empty()
								: ordenes.findByProveedorOrdenId(aviso.proveedorOrdenId()))
						.filter(o -> o.getProveedor() == proveedor).orElse(null);
				return eventos.saveAndFlush(EventoPasarela.recibido(proveedor, aviso.eventoId(), aviso.tipo(),
						orden == null ? null : orden.getId(), huella)).getId();
			});
		}
		catch (DataIntegrityViolationException repetido) {
			return null; // el mismo evento llegó dos veces a la vez: lo procesa el primero
		}
	}

	static String sha256(byte[] cuerpo) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cuerpo));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}

	static String texto(byte[] cuerpo) {
		return new String(cuerpo, StandardCharsets.UTF_8);
	}
}
