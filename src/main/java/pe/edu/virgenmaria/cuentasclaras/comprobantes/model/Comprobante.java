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

	private static String recortar(String texto) {
		return texto.length() <= MAX_DESCRIPCION ? texto : texto.substring(0, MAX_DESCRIPCION - 1) + "…";
	}

	/** Respuesta del OSE: el envío suma un intento y guarda su resultado. Los datos tributarios no cambian. */
	public void registrarEnvio(ResultadoEnvio resultado, LocalDateTime ahora) {
		Objects.requireNonNull(resultado, "resultado");
		intentos++;
		estadoEnvio = resultado.estado();
		enviadoEn = Objects.requireNonNull(ahora, "ahora");
		respuesta = corto(resultado.respuesta(), 500);
		codigoHash = corto(resultado.codigoHash(), 100);
		enlacePdf = corto(resultado.enlacePdf(), 300);
	}

	/** El envío falló (sin respuesta del OSE): queda PENDIENTE para reintentarlo y suma un intento. */
	public void registrarFalloEnvio(String motivo, LocalDateTime ahora) {
		intentos++;
		enviadoEn = Objects.requireNonNull(ahora, "ahora");
		respuesta = corto(motivo, 500);
		if (estadoEnvio != EstadoEnvio.ACEPTADO) {
			estadoEnvio = EstadoEnvio.PENDIENTE;
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
}
