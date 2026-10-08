package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.TextoSeguro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Comprobante emitido (boleta, factura o nota de crédito).
 * <ul>
 *   <li>Sus datos tributarios (serie, número, receptor, total, líneas) no cambian: {@code updatable = false}, sin
 *       setters y, en MySQL, GRANT de UPDATE solo sobre las columnas del envío al OSE.</li>
 *   <li>El número es el que la serie acaba de asignar (en MySQL lo exige un trigger) y el total es la suma de las
 *       líneas.</li>
 *   <li>Nunca se borra: una anulación emite una nota de crédito con su propio correlativo (tanda 2).</li>
 * </ul>
 */
@Entity
@Table(name = "comprobante")
public class Comprobante extends BaseEntity {

	public static final String MONEDA = "PEN";

	public static final int MAX_DESCRIPCION = 250;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "serie_id", nullable = false, updatable = false)
	private SerieComprobante serieComprobante;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private TipoComprobante tipo;

	@Column(nullable = false, updatable = false, length = 4)
	private String serie;

	@Column(nullable = false, updatable = false)
	private int numero;

	@Column(name = "fecha_emision", nullable = false, updatable = false)
	private LocalDate fechaEmision;

	@Enumerated(EnumType.STRING)
	@Column(name = "receptor_tipo_documento", nullable = false, updatable = false, length = 20)
	private DocumentoReceptor receptorTipoDocumento;

	@Column(name = "receptor_numero_documento", nullable = false, updatable = false, length = 12)
	private String receptorNumeroDocumento;

	@Column(name = "receptor_nombre", nullable = false, updatable = false, length = 150)
	private String receptorNombre;

	@Column(nullable = false, updatable = false, length = 3)
	private String moneda;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal total;

	@Enumerated(EnumType.STRING)
	@Column(name = "afectacion_igv", nullable = false, updatable = false, length = 20)
	private AfectacionIgv afectacionIgv;

	@Column(name = "modifica_id", updatable = false)
	private Long modificaId;

	@Column(name = "motivo_nota", updatable = false, length = 250)
	private String motivoNota;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ProveedorComprobantes proveedor;

	@OneToMany(mappedBy = "comprobante", cascade = CascadeType.PERSIST)
	@OrderBy("orden")
	private List<ComprobanteLinea> lineas = new ArrayList<>();

	// --- Lo único que puede cambiar: el envío al OSE (GRANT UPDATE por columna en MySQL) ---

	@Enumerated(EnumType.STRING)
	@Column(name = "estado_envio", nullable = false, length = 20)
	private EstadoEnvio estadoEnvio;

	@Column(nullable = false)
	private int intentos;

	@Column(name = "enviado_en")
	private LocalDateTime enviadoEn;

	@Column(length = 500)
	private String respuesta;

	@Column(name = "codigo_hash", length = 100)
	private String codigoHash;

	@Column(name = "enlace_pdf", length = 300)
	private String enlacePdf;

	// --- Sprint 4: outbox del OSE (también por columna) ---

	/** PENDIENTE: cuándo se reintenta; ENVIADO: cuándo se vuelve a consultar. */
	@Column(name = "proximo_intento_en")
	private LocalDateTime proximoIntentoEn;

	@Column(name = "ultimo_error", length = 250)
	private String ultimoError;

	@Column(name = "codigo_respuesta", length = 10)
	private String codigoRespuesta;

	/** Cuándo quedó ACEPTADO u OBSERVADO (lo exige la base). */
	@Column(name = "aceptado_en")
	private LocalDateTime aceptadoEn;

	/** El comprobante RECHAZADO que este reemite con un número nuevo (uno por rechazado: UNIQUE). No cambia. */
	@Column(name = "reemplaza_id", updatable = false)
	private Long reemplazaId;

	protected Comprobante() {
		// requerido por JPA
	}

	/**
	 * Boleta o factura con el número que la serie acaba de asignar ({@link SerieComprobante#siguiente()}). El total es la
	 * suma de las líneas.
	 */
	public static Comprobante emitir(SerieComprobante serie, int numero, LocalDate fecha, Receptor receptor,
			AfectacionIgv afectacion, List<LineaDocumento> lineas) {
		Objects.requireNonNull(serie, "serie");
		Objects.requireNonNull(receptor, "receptor");
		Objects.requireNonNull(afectacion, "afectacion");
		if (serie.getTipo() == TipoComprobante.NOTA_CREDITO) {
			throw new IllegalArgumentException("Una nota de crédito se emite con notaDeCredito");
		}
		if (numero != serie.getUltimoNumero()) {
			throw new IllegalStateException("El número " + numero + " no es el que asignó la serie " + serie.getSerie());
		}
		if (serie.getTipo() == TipoComprobante.FACTURA && receptor.tipo() != DocumentoReceptor.RUC) {
			throw new ReglaNegocioException("Una factura necesita el RUC de quien la recibe.");
		}
		if (serie.getTipo() == TipoComprobante.BOLETA && receptor.tipo() == DocumentoReceptor.RUC) {
			throw new ReglaNegocioException("Con RUC se emite factura, no boleta.");
		}
		if (lineas == null || lineas.isEmpty()) {
			throw new ReglaNegocioException("El comprobante necesita al menos una línea.");
		}
		Comprobante comprobante = new Comprobante();
		comprobante.serieComprobante = serie;
		comprobante.tipo = serie.getTipo();
		comprobante.serie = serie.getSerie();
		comprobante.numero = numero;
		comprobante.fechaEmision = Objects.requireNonNull(fecha, "fecha");
		comprobante.receptorTipoDocumento = receptor.tipo();
		comprobante.receptorNumeroDocumento = receptor.numero();
		comprobante.receptorNombre = receptor.nombre();
		comprobante.moneda = MONEDA;
		comprobante.afectacionIgv = afectacion;
		comprobante.proveedor = serie.getProveedor();
		comprobante.estadoEnvio = EstadoEnvio.PENDIENTE;
		comprobante.intentos = 0;
		BigDecimal total = Dinero.CERO;
		int orden = 1;
		for (LineaDocumento linea : lineas) {
			BigDecimal monto = Dinero.positivo(linea.monto(), "el monto de la línea");
			String descripcion = Normalizador.limpiar(linea.descripcion());
			if (descripcion == null) {
				throw new ReglaNegocioException("Cada línea del comprobante necesita una descripción.");
			}
			descripcion = TextoSeguro.exigir(recortar(descripcion), "la descripción");
			comprobante.lineas.add(ComprobanteLinea.de(comprobante, orden++, descripcion, monto));
			total = total.add(monto);
		}
		comprobante.total = Dinero.positivo(total, "el total del comprobante");
		return comprobante;
	}

	/**
	 * Nota de crédito que anula por completo {@code modificado} (una sola por comprobante: UNIQUE en la base). Usa la
	 * serie de notas de su letra (BC01 para boletas, FC01 para facturas), el mismo receptor y las mismas líneas.
	 */
	public static Comprobante notaDeCredito(SerieComprobante serie, int numero, LocalDate fecha, Comprobante modificado,
			String motivo) {
		Objects.requireNonNull(serie, "serie");
		Objects.requireNonNull(modificado, "modificado");
		if (serie.getTipo() != TipoComprobante.NOTA_CREDITO || modificado.getTipo() == TipoComprobante.NOTA_CREDITO
				|| serie.getSerie().charAt(0) != modificado.getSerie().charAt(0)) {
			throw new IllegalArgumentException("La nota de crédito usa una serie de notas con la letra del comprobante");
		}
		if (numero != serie.getUltimoNumero()) {
			throw new IllegalStateException("El número " + numero + " no es el que asignó la serie " + serie.getSerie());
		}
		String texto = Normalizador.limpiar(motivo);
		if (texto == null) {
			throw new ReglaNegocioException("La nota de crédito necesita un motivo.");
		}
		Comprobante nota = new Comprobante();
		nota.serieComprobante = serie;
		nota.tipo = TipoComprobante.NOTA_CREDITO;
		nota.serie = serie.getSerie();
		nota.numero = numero;
		nota.fechaEmision = Objects.requireNonNull(fecha, "fecha");
		// Con getters: «modificado» suele llegar como proxy perezoso de Hibernate (sus campos estarían vacíos).
		Receptor receptor = modificado.getReceptor();
		nota.receptorTipoDocumento = receptor.tipo();
		nota.receptorNumeroDocumento = receptor.numero();
		nota.receptorNombre = receptor.nombre();
		nota.moneda = modificado.getMoneda();
		nota.afectacionIgv = modificado.getAfectacionIgv();
		nota.modificaId = Objects.requireNonNull(modificado.getId(), "el comprobante anulado debe estar guardado");
		nota.motivoNota = recortar(TextoSeguro.exigir(texto, "el motivo"));
		nota.proveedor = serie.getProveedor();
		nota.estadoEnvio = EstadoEnvio.PENDIENTE;
		nota.intentos = 0;
		int orden = 1;
		for (ComprobanteLinea linea : modificado.getLineas()) {
			nota.lineas.add(ComprobanteLinea.de(nota, orden++, linea.getDescripcion(), linea.getMonto()));
		}
		nota.total = modificado.getTotal();
		return nota;
	}

	/**
	 * Reemisión de un comprobante RECHAZADO por el OSE: el mismo tipo, total y líneas, con un número NUEVO de la serie
	 * vigente y el receptor corregido. El rechazado se queda en la tabla con su número (la numeración sigue sin huecos).
	 * En MySQL, trg_comprobante_correlativo exige que el reemplazado esté RECHAZADO y sea del mismo tipo y total.
	 */
	public static Comprobante reemitir(SerieComprobante serie, int numero, LocalDate fecha, Comprobante rechazado,
			Receptor receptor) {
		Objects.requireNonNull(rechazado, "rechazado");
		if (rechazado.getEstadoEnvio() != EstadoEnvio.RECHAZADO) {
			throw new ReglaNegocioException("Solo se reemite un comprobante rechazado por el OSE.");
		}
		if (rechazado.getTipo() == TipoComprobante.NOTA_CREDITO || serie.getTipo() != rechazado.getTipo()) {
			throw new IllegalArgumentException("La reemisión usa una serie del mismo tipo (boleta o factura)");
		}
		List<LineaDocumento> lineas = rechazado.getLineas().stream()
				.map(l -> new LineaDocumento(l.getDescripcion(), l.getMonto())).toList();
		Comprobante nuevo = emitir(serie, numero, fecha, receptor, rechazado.getAfectacionIgv(), lineas);
		if (!Dinero.iguales(nuevo.total, rechazado.getTotal())) {
			throw new IllegalStateException("La reemisión debe tener el mismo total");
		}
		nuevo.reemplazaId = Objects.requireNonNull(rechazado.getId(), "rechazado.id");
		return nuevo;
	}

	private static String recortar(String texto) {
		return texto.length() <= MAX_DESCRIPCION ? texto : texto.substring(0, MAX_DESCRIPCION - 1) + "…";
	}

	/**
	 * Respuesta del OSE a un envío (desde PENDIENTE) o a una consulta (desde ENVIADO): suma un intento y guarda su
	 * resultado. Los datos tributarios no cambian. ENVIADO: lo recibió y falta la respuesta (se consultará en
	 * {@code proximaConsulta}); ACEPTADO u OBSERVADO: exige el hash y queda la fecha de aceptación; RECHAZADO: exige la
	 * respuesta. Un resultado definitivo ya no cambia (en MySQL lo exige trg_comprobante_envio).
	 */
	public void registrarEnvio(ResultadoEnvio resultado, LocalDateTime ahora, LocalDateTime proximaConsulta) {
		Objects.requireNonNull(resultado, "resultado");
		Objects.requireNonNull(ahora, "ahora");
		exigirSinResolver();
		if (resultado.estado() == EstadoEnvio.PENDIENTE) {
			throw new IllegalArgumentException("Sin respuesta del OSE: usa registrarFalloEnvio o volverAPendiente");
		}
		String hash = resultado.codigoHash() != null ? resultado.codigoHash() : codigoHash;
		String texto = resultado.respuesta() != null ? resultado.respuesta() : respuesta;
		if (resultado.estado().valido() && (hash == null || texto == null)) {
			throw new IllegalStateException("El OSE no devolvió el hash o la respuesta de un comprobante aceptado");
		}
		intentos++;
		if (estadoEnvio == EstadoEnvio.PENDIENTE) {
			enviadoEn = ahora; // un envío (desde ENVIADO es una consulta: la fecha de envío no cambia)
		}
		estadoEnvio = resultado.estado();
		respuesta = corto(texto == null ? "Rechazado por el OSE" : texto, 500);
		codigoHash = corto(hash, 100);
		if (resultado.enlacePdf() != null) {
			enlacePdf = corto(resultado.enlacePdf(), 300);
		}
		codigoRespuesta = corto(resultado.codigoRespuesta(), 10);
		ultimoError = null;
		if (resultado.estado() == EstadoEnvio.ENVIADO) {
			proximoIntentoEn = Objects.requireNonNull(proximaConsulta, "proximaConsulta");
		}
		else {
			proximoIntentoEn = null;
		}
		if (resultado.estado().valido()) {
			aceptadoEn = ahora;
		}
	}

	/** Igual, para un resultado definitivo (sin próxima consulta). */
	public void registrarEnvio(ResultadoEnvio resultado, LocalDateTime ahora) {
		registrarEnvio(resultado, ahora, resultado.estado() == EstadoEnvio.ENVIADO ? ahora : null);
	}

	/**
	 * El envío o la consulta fallaron (red, 429, 5xx): sigue como estaba (PENDIENTE o ENVIADO), suma un intento y se
	 * reintenta en {@code proximo}.
	 */
	public void registrarFalloEnvio(String motivo, String detalle, LocalDateTime ahora, LocalDateTime proximo) {
		Objects.requireNonNull(ahora, "ahora");
		exigirSinResolver();
		intentos++;
		if (estadoEnvio == EstadoEnvio.PENDIENTE) {
			enviadoEn = ahora;
			respuesta = corto(motivo, 500);
		}
		ultimoError = corto(detalle == null ? motivo : detalle, 250);
		proximoIntentoEn = Objects.requireNonNull(proximo, "proximo");
	}

	/** Sprint 3: el envío falló y se reintenta en el próximo ciclo. */
	public void registrarFalloEnvio(String motivo, LocalDateTime ahora) {
		registrarFalloEnvio(motivo, null, ahora, ahora);
	}

	/** Consultado, el OSE no lo tiene: vuelve a PENDIENTE para reenviarlo (el envío es idempotente por serie y número). */
	public void volverAPendiente(String motivo, LocalDateTime ahora) {
		if (estadoEnvio != EstadoEnvio.ENVIADO) {
			throw new IllegalStateException("Solo un comprobante ENVIADO vuelve a pendiente");
		}
		intentos++;
		estadoEnvio = EstadoEnvio.PENDIENTE;
		ultimoError = corto(motivo, 250);
		proximoIntentoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** Administración adelanta el próximo intento (o consulta) al momento: lo toma el outbox en su siguiente pasada. */
	public void adelantarReintento(LocalDateTime ahora) {
		exigirSinResolver();
		proximoIntentoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** Una nota de crédito espera a que su comprobante sea aceptado: no se envía ni suma intentos. */
	public void esperarComprobanteModificado(String motivo, LocalDateTime proximo) {
		exigirSinResolver();
		ultimoError = corto(motivo, 250);
		proximoIntentoEn = Objects.requireNonNull(proximo, "proximo");
	}

	/**
	 * A un día del plazo legal de envío (o pasado) y todavía sin respuesta válida del OSE. El plazo se cuenta en días
	 * calendario desde el día siguiente a la emisión (RS 000003-2023/SUNAT; se usa el más estricto, configurable).
	 */
	public boolean vencePlazo(LocalDate hoy, int plazoDias) {
		return !estadoEnvio.definitivo() && !hoy.isBefore(fechaEmision.plusDays(plazoDias - 1L));
	}

	/** El último día del plazo legal para que el OSE lo reciba. */
	public LocalDate fechaLimiteEnvio(int plazoDias) {
		return fechaEmision.plusDays(plazoDias);
	}

	private void exigirSinResolver() {
		if (estadoEnvio.definitivo()) {
			throw new IllegalStateException("El envío del comprobante ya se resolvió (" + estadoEnvio + "): no cambia.");
		}
	}

	private static String corto(String texto, int maximo) {
		return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}

	/** «B001-00000125». */
	public String numeroCompleto() {
		return formatearNumero(serie, numero);
	}

	public static String formatearNumero(String serie, int numero) {
		return serie + "-" + String.format("%08d", numero);
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los comprobantes no se borran: se anulan con una nota de crédito.");
	}

	public SerieComprobante getSerieComprobante() {
		return serieComprobante;
	}

	public TipoComprobante getTipo() {
		return tipo;
	}

	public String getSerie() {
		return serie;
	}

	public int getNumero() {
		return numero;
	}

	public LocalDate getFechaEmision() {
		return fechaEmision;
	}

	public Receptor getReceptor() {
		return new Receptor(receptorTipoDocumento, receptorNumeroDocumento, receptorNombre);
	}

	public String getMoneda() {
		return moneda;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public AfectacionIgv getAfectacionIgv() {
		return afectacionIgv;
	}

	public Long getModificaId() {
		return modificaId;
	}

	public String getMotivoNota() {
		return motivoNota;
	}

	public ProveedorComprobantes getProveedor() {
		return proveedor;
	}

	public List<ComprobanteLinea> getLineas() {
		return Collections.unmodifiableList(lineas);
	}

	public EstadoEnvio getEstadoEnvio() {
		return estadoEnvio;
	}

	public int getIntentos() {
		return intentos;
	}

	public LocalDateTime getEnviadoEn() {
		return enviadoEn;
	}

	public String getRespuesta() {
		return respuesta;
	}

	public String getCodigoHash() {
		return codigoHash;
	}

	public String getEnlacePdf() {
		return enlacePdf;
	}

	public LocalDateTime getProximoIntentoEn() {
		return proximoIntentoEn;
	}

	public String getUltimoError() {
		return ultimoError;
	}

	public String getCodigoRespuesta() {
		return codigoRespuesta;
	}

	public LocalDateTime getAceptadoEn() {
		return aceptadoEn;
	}

	public Long getReemplazaId() {
		return reemplazaId;
	}
}
