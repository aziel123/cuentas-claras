package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.config.PropiedadesComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.AfectacionIgv;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.LineaDocumento;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.config.VerificadorConfiguracion;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conector real con Nubefact (OSE/PSE), apagado por defecto: solo existe con
 * {@code cuentasclaras.comprobantes.proveedor: NUBEFACT}, y {@code VerificadorConfiguracion} exige antes la RUTA
 * {@code https} de un dominio de la lista cerrada, el TOKEN y, fuera de prod, la marca explícita. El simulado sigue siendo
 * el valor por defecto.
 * <ul>
 *   <li>{@code POST} a la ruta con {@code Authorization: Token token="..."} y la operación {@code generar_comprobante}
 *       (boleta 2, factura 1, nota de crédito 3; ítems inafectos 9 o exonerados 8, sin IGV).</li>
 *   <li>{@code aceptada_por_sunat = true} → ACEPTADO (u OBSERVADO si trae nota); {@code false} sin código de rechazo →
 *       ENVIADO (se consultará); código 2000–3999 → RECHAZADO.</li>
 *   <li>Error de red, 429 o 5xx → excepción: el outbox lo deja PENDIENTE y reintenta con espera creciente.</li>
 *   <li>«El documento ya existe» (código 23) → se consulta ({@code consultar_comprobante}) en vez de fallar: el envío es
 *       idempotente por serie y número.</li>
 * </ul>
 * Los nombres de los campos siguen la documentación pública de la API de Nubefact; antes de activarlo se comprueban con la
 * cuenta DEMO del colegio (sus páginas están bloqueadas en el entorno donde se diseñó). El log no lleva el token ni datos
 * personales.
 */
@Component
@ConditionalOnProperty(name = "cuentasclaras.comprobantes.proveedor", havingValue = "NUBEFACT")
public class EmisorNubefact implements EmisorElectronico {

	private static final Logger LOG = LoggerFactory.getLogger(EmisorNubefact.class);

	static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	/** Código de Nubefact: «Este documento ya existe». */
	static final int YA_EXISTE = 23;

	private final RestClient cliente;

	private final PropiedadesComprobantes.Nubefact cuenta;

	@Autowired
	public EmisorNubefact(PropiedadesComprobantes propiedades) {
		this(propiedades, RestClient.builder().requestFactory(fabrica(propiedades.nubefact())));
	}

	/** Con el constructor del cliente que se quiera (las pruebas de contrato usan {@code MockRestServiceServer}). */
	EmisorNubefact(PropiedadesComprobantes propiedades, RestClient.Builder constructor) {
		this.cuenta = propiedades.nubefact();
		if (!VerificadorConfiguracion.rutaPermitida(cuenta.ruta(), cuenta.dominiosPermitidos())
				|| cuenta.token() == null || cuenta.token().isBlank()) {
			throw new IllegalStateException("Nubefact necesita una ruta https de un dominio permitido y su token.");
		}
		this.cliente = constructor.baseUrl(cuenta.ruta().strip())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Token token=\"" + cuenta.token().strip() + "\"")
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	private static SimpleClientHttpRequestFactory fabrica(PropiedadesComprobantes.Nubefact cuenta) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(cuenta.tiempoConexion());
		fabrica.setReadTimeout(cuenta.tiempoLectura());
		return fabrica;
	}

	@Override
	public ProveedorComprobantes proveedor() {
		return ProveedorComprobantes.NUBEFACT;
	}

	@Override
	public ResultadoEnvio enviar(DocumentoElectronico documento) {
		Map<String, Object> respuesta = llamar(cuerpo(documento));
		Integer error = codigoDeError(respuesta);
		if (error != null) {
			if (error == YA_EXISTE) {
				LOG.info("Nubefact ya tiene {}-{}: se consulta.", documento.serie(), documento.numero());
				return consultar(documento.tipo(), documento.serie(), documento.numero());
			}
			throw new IllegalStateException("Nubefact rechazó el pedido (código " + error + "): "
					+ texto(respuesta.get("errors")));
		}
		return interpretar(respuesta);
	}

	@Override
	public ResultadoEnvio consultar(TipoComprobante tipo, String serie, int numero) {
		Map<String, Object> pedido = new LinkedHashMap<>();
		pedido.put("operacion", "consultar_comprobante");
		pedido.put("tipo_de_comprobante", tipoNubefact(tipo));
		pedido.put("serie", serie);
		pedido.put("numero", numero);
		Map<String, Object> respuesta = llamar(pedido);
		Integer error = codigoDeError(respuesta);
		if (error != null) {
			// No lo tiene (o no se pudo consultar): el outbox lo vuelve a PENDIENTE y lo reenvía.
			return new ResultadoEnvio(EstadoEnvio.PENDIENTE, texto(respuesta.get("errors")), null, null,
					String.valueOf(error), null);
		}
		return interpretar(respuesta);
	}

	/** El JSON de {@code generar_comprobante}. */
	static Map<String, Object> cuerpo(DocumentoElectronico d) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("operacion", "generar_comprobante");
		json.put("tipo_de_comprobante", tipoNubefact(d.tipo()));
		json.put("serie", d.serie());
		json.put("numero", d.numero());
		json.put("sunat_transaction", 1);
		json.put("cliente_tipo_de_documento", d.receptor().tipo().codigoSunat());
		json.put("cliente_numero_de_documento", d.receptor().numero());
		json.put("cliente_denominacion", d.receptor().nombre());
		json.put("cliente_direccion", "");
		json.put("fecha_de_emision", FECHA.format(d.fecha()));
		json.put("moneda", 1);
		json.put("porcentaje_de_igv", new BigDecimal("18.00"));
		boolean inafecto = d.afectacion() == AfectacionIgv.INAFECTO;
		json.put(inafecto ? "total_inafecta" : "total_exonerada", d.total());
		json.put("total_igv", BigDecimal.ZERO.setScale(2));
		json.put("total", d.total());
		json.put("enviar_automaticamente_a_la_sunat", true);
		json.put("enviar_automaticamente_al_cliente", false);
		if (d.modifica() != null) {
			json.put("documento_que_se_modifica_tipo", tipoNubefact(d.modifica().tipo()));
			json.put("documento_que_se_modifica_serie", d.modifica().serie());
			json.put("documento_que_se_modifica_numero", d.modifica().numero());
			json.put("tipo_de_nota_de_credito", 1); // catálogo 09: anulación de la operación
		}
		List<Map<String, Object>> items = new ArrayList<>();
		for (LineaDocumento linea : d.lineas()) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("unidad_de_medida", "ZZ");
			item.put("codigo", "");
			item.put("descripcion", linea.descripcion());
			item.put("cantidad", 1);
			item.put("valor_unitario", linea.monto());
			item.put("precio_unitario", linea.monto());
			item.put("subtotal", linea.monto());
			item.put("tipo_de_igv", inafecto ? 9 : 8);
			item.put("igv", BigDecimal.ZERO.setScale(2));
			item.put("total", linea.monto());
			item.put("anticipo_regularizacion", false);
			items.add(item);
		}
		json.put("items", items);
		return json;
	}

	/** Factura 1, boleta 2, nota de crédito 3. */
	static int tipoNubefact(TipoComprobante tipo) {
		return switch (tipo) {
			case FACTURA -> 1;
			case BOLETA -> 2;
			case NOTA_CREDITO -> 3;
		};
	}

	static ResultadoEnvio interpretar(Map<String, Object> r) {
		boolean aceptada = Boolean.TRUE.equals(r.get("aceptada_por_sunat"));
		String codigo = texto(r.get("sunat_responsecode"));
		String descripcion = texto(r.get("sunat_description"));
		String nota = texto(r.get("sunat_note"));
		String hash = texto(r.get("codigo_hash"));
		String pdf = texto(r.get("enlace_del_pdf"));
		Integer numerico = entero(codigo);
		if (aceptada) {
			boolean observado = (nota != null && !nota.isBlank()) || (numerico != null && numerico >= 4000);
			return new ResultadoEnvio(observado ? EstadoEnvio.OBSERVADO : EstadoEnvio.ACEPTADO,
					descripcion == null ? "Aceptado por SUNAT" : descripcion, hash, pdf, codigo, nota);
		}
		if (numerico != null && numerico >= 2000 && numerico <= 3999) {
			return new ResultadoEnvio(EstadoEnvio.RECHAZADO, descripcion == null ? "Rechazado por SUNAT" : descripcion,
					hash, pdf, codigo, nota);
		}
		return new ResultadoEnvio(EstadoEnvio.ENVIADO, descripcion == null ? "Recibido por el OSE" : descripcion, hash,
				pdf, codigo, nota);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> llamar(Map<String, Object> pedido) {
		try {
			Map<String, Object> respuesta = cliente.post().contentType(MediaType.APPLICATION_JSON).body(pedido).retrieve()
					.body(Map.class);
			return respuesta == null ? Map.of() : respuesta;
		}
		catch (RestClientResponseException e) {
			int estado = e.getStatusCode().value();
			if (estado == 429 || estado >= 500) {
				throw new IllegalStateException("Nubefact respondió " + estado + ": se reintentará", e);
			}
			Map<String, Object> cuerpo = e.getResponseBodyAs(Map.class);
			if (cuerpo != null && codigoDeError(cuerpo) != null) {
				return cuerpo;
			}
			throw new IllegalStateException("Nubefact respondió " + estado, e);
		}
	}

	private static Integer codigoDeError(Map<String, Object> respuesta) {
		if (respuesta.get("errors") == null) {
			return null;
		}
		Integer codigo = entero(texto(respuesta.get("codigo")));
		return codigo == null ? -1 : codigo;
	}

	private static Integer entero(String texto) {
		if (texto == null) {
			return null;
		}
		try {
			return Integer.valueOf(texto.strip());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private static String texto(Object valor) {
		return valor == null ? null : String.valueOf(valor);
	}
}
