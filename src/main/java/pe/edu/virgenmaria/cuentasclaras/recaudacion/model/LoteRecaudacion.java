package pe.edu.virgenmaria.cuentasclaras.recaudacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Lote de recaudación: un archivo del banco con los pagos que las familias hicieron con el código del alumno. Nada se
 * borra.
 * <ul>
 *   <li>Lo registra Administración (CARGADO) con el archivo original y su SHA-256; el archivo, el banco, las fechas, la
 *       cantidad de líneas y el total no cambian ({@code updatable = false} y GRANT por columna).</li>
 *   <li>Lo confirma OTRA persona (Promotoría o Dirección) escribiendo a ciegas el total que ve en el portal del banco
 *       (CHECK: {@code confirmado_por <> creado_por} y {@code total_ciego = total}). Con {@code intentos-confirmacion}
 *       totales distintos queda RECHAZADO.</li>
 *   <li>Recién confirmado, {@code sistema.recaudacion} registra los pagos y lo deja APLICADO.</li>
 * </ul>
 * {@code sha_vigente} impide cargar dos veces el mismo archivo mientras el lote esté vigente (UNIQUE).
 */
@Entity
@Table(name = "lote_recaudacion")
public class LoteRecaudacion extends BaseEntity {

	/** Tope de los totales del lote ({@code DECIMAL(12,2)}, hallazgo 12 del diseño). */
	public static final BigDecimal MAXIMO_TOTAL = new BigDecimal("9999999999.99");

	@Column(name = "archivo_id", nullable = false, updatable = false)
	private Long archivoId;

	@Column(name = "archivo_sha256", nullable = false, updatable = false, length = 64)
	private String archivoSha256;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private BancoRecaudacion banco;

	@Column(nullable = false, updatable = false, length = 30)
	private String formato;

	@Column(name = "fecha_proceso", nullable = false, updatable = false)
	private LocalDate fechaProceso;

	@Column(nullable = false, updatable = false)
	private LocalDate desde;

	@Column(nullable = false, updatable = false)
	private LocalDate hasta;

	@Column(nullable = false, updatable = false)
	private int lineas;

	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal total;

	@Column(name = "total_banco", updatable = false, precision = 12, scale = 2)
	private BigDecimal totalBanco;

	// --- Lo que puede cambiar (GRANT UPDATE por columna; trg_lote_recaudacion_estado vigila las transiciones) ---

	@Column(name = "sha_vigente", length = 64)
	private String shaVigente;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoLote estado;

	@Column(name = "intentos_confirmacion", nullable = false)
	private int intentosConfirmacion;

	@Column(name = "total_ciego", precision = 12, scale = 2)
	private BigDecimal totalCiego;

	@Column(name = "confirmado_por", length = 60)
	private String confirmadoPor;

	@Column(name = "confirmado_en")
	private LocalDateTime confirmadoEn;

	@Column(name = "aplicado_en")
	private LocalDateTime aplicadoEn;

	@Column(name = "lineas_aplicadas", nullable = false)
	private int lineasAplicadas;

	@Column(name = "lineas_excepcion", nullable = false)
	private int lineasExcepcion;

	@Column(name = "monto_aplicado", nullable = false, precision = 12, scale = 2)
	private BigDecimal montoAplicado;

	@Column(name = "monto_excepcion", nullable = false, precision = 12, scale = 2)
	private BigDecimal montoExcepcion;

	@Column(name = "rechazado_por", length = 60)
	private String rechazadoPor;

	@Column(name = "rechazado_en")
	private LocalDateTime rechazadoEn;

	@Column(name = "motivo_rechazo", length = 500)
	private String motivoRechazo;

	protected LoteRecaudacion() {
		// requerido por JPA
	}

	/** Lote nuevo (CARGADO, sin confirmar ni aplicar). Todos los NOT NULL con DEFAULT se llenan aquí. */
	public static LoteRecaudacion registrar(Long archivoId, String archivoSha256, BancoRecaudacion banco, String formato,
			LocalDate fechaProceso, LocalDate desde, LocalDate hasta, int lineas, BigDecimal total, BigDecimal totalBanco) {
		Objects.requireNonNull(desde, "desde");
		Objects.requireNonNull(hasta, "hasta");
		if (lineas <= 0 || desde.isAfter(hasta)) {
			throw new IllegalArgumentException("Un lote tiene al menos una línea y sus fechas en orden");
		}
		LoteRecaudacion lote = new LoteRecaudacion();
		lote.archivoId = Objects.requireNonNull(archivoId, "archivoId");
		lote.archivoSha256 = Objects.requireNonNull(archivoSha256, "archivoSha256");
		lote.shaVigente = archivoSha256;
		lote.banco = Objects.requireNonNull(banco, "banco");
		lote.formato = Objects.requireNonNull(formato, "formato");
		lote.fechaProceso = Objects.requireNonNull(fechaProceso, "fechaProceso");
		lote.desde = desde;
		lote.hasta = hasta;
		lote.lineas = lineas;
		lote.total = Dinero.enRango(total, new BigDecimal("0.01"), MAXIMO_TOTAL, "el total del archivo");
		if (totalBanco != null && !Dinero.iguales(totalBanco, lote.total)) {
			throw new IllegalArgumentException("El total que declara el banco debe ser la suma de las líneas");
		}
		lote.totalBanco = totalBanco == null ? null : Dinero.normalizar(totalBanco);
		lote.estado = EstadoLote.CARGADO;
		lote.intentosConfirmacion = 0;
		lote.lineasAplicadas = 0;
		lote.lineasExcepcion = 0;
		lote.montoAplicado = Dinero.CERO;
		lote.montoExcepcion = Dinero.CERO;
		return lote;
	}

	/**
	 * Otra persona escribió a ciegas el total CORRECTO: CONFIRMADO. Quien llama ya comprobó que no es quien lo subió y
	 * que el total coincide (aquí se vuelve a exigir: la base lo exige con un CHECK).
	 */
	public void confirmar(String por, BigDecimal totalVisto, LocalDateTime ahora) {
		exigirCargado();
		if (Objects.equals(por, getCreadoPor())) {
			throw new IllegalStateException("Quien subió el archivo no lo confirma");
		}
		if (totalVisto == null || !Dinero.iguales(totalVisto, total)) {
			throw new IllegalArgumentException("El total escrito no coincide con el del archivo");
		}
		estado = EstadoLote.CONFIRMADO;
		totalCiego = total;
		confirmadoPor = Objects.requireNonNull(por, "por");
		confirmadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/**
	 * Un total a ciegas distinto: suma un intento. Al llegar a {@code maximo}, el lote queda RECHAZADO (no se aplica) y
	 * sale de vigencia.
	 *
	 * @return si quedó RECHAZADO
	 */
	public boolean intentoFallido(int maximo, String por, LocalDateTime ahora) {
		exigirCargado();
		intentosConfirmacion++;
		if (intentosConfirmacion >= maximo) {
			estado = EstadoLote.RECHAZADO;
			shaVigente = null;
			rechazadoPor = Objects.requireNonNull(por, "por");
			rechazadoEn = Objects.requireNonNull(ahora, "ahora");
			motivoRechazo = intentosConfirmacion + " totales escritos a ciegas no coinciden con el total del archivo: no se "
					+ "aplica ningún pago.";
			return true;
		}
		return false;
	}

	/** Quien lo subió lo descarta antes de que se confirme (motivo de 10 a 500 caracteres). */
	public void descartar(String por, String motivo, LocalDateTime ahora) {
		exigirCargado();
		estado = EstadoLote.DESCARTADO;
		shaVigente = null;
		rechazadoPor = Objects.requireNonNull(por, "por");
		rechazadoEn = Objects.requireNonNull(ahora, "ahora");
		motivoRechazo = Motivo.exigir(motivo);
	}

	/** El sistema terminó de aplicar todas sus líneas (ninguna quedó PENDIENTE). */
	public void marcarAplicado(int aplicadas, int excepcion, BigDecimal aplicado, BigDecimal enExcepcion,
			LocalDateTime ahora) {
		if (estado != EstadoLote.CONFIRMADO) {
			throw new IllegalStateException("Solo se aplica un lote confirmado");
		}
		if (aplicadas + excepcion != lineas || !Dinero.iguales(aplicado.add(enExcepcion), total)) {
			throw new IllegalStateException("El lote se aplica completo: todas sus líneas aplicadas o en excepción");
		}
		estado = EstadoLote.APLICADO;
		lineasAplicadas = aplicadas;
		lineasExcepcion = excepcion;
		montoAplicado = Dinero.normalizar(aplicado);
		montoExcepcion = Dinero.normalizar(enExcepcion);
		aplicadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	public int intentosRestantes(int maximo) {
		return Math.max(0, maximo - intentosConfirmacion);
	}

	private void exigirCargado() {
		if (estado != EstadoLote.CARGADO) {
			throw new ReglaNegocioException("El lote ya no está por confirmar (está "
					+ estado.etiqueta().toLowerCase(java.util.Locale.ROOT) + ").");
		}
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los lotes de recaudación no se borran: se descartan o se rechazan.");
	}

	public Long getArchivoId() {
		return archivoId;
	}

	public String getArchivoSha256() {
		return archivoSha256;
	}

	public BancoRecaudacion getBanco() {
		return banco;
	}

	public String getFormato() {
		return formato;
	}

	public LocalDate getFechaProceso() {
		return fechaProceso;
	}

	public LocalDate getDesde() {
		return desde;
	}

	public LocalDate getHasta() {
		return hasta;
	}

	public int getLineas() {
		return lineas;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public BigDecimal getTotalBanco() {
		return totalBanco;
	}

	public String getShaVigente() {
		return shaVigente;
	}

	public EstadoLote getEstado() {
		return estado;
	}

	public int getIntentosConfirmacion() {
		return intentosConfirmacion;
	}

	public BigDecimal getTotalCiego() {
		return totalCiego;
	}

	public String getConfirmadoPor() {
		return confirmadoPor;
	}

	public LocalDateTime getConfirmadoEn() {
		return confirmadoEn;
	}

	public LocalDateTime getAplicadoEn() {
		return aplicadoEn;
	}

	public int getLineasAplicadas() {
		return lineasAplicadas;
	}

	public int getLineasExcepcion() {
		return lineasExcepcion;
	}

	public BigDecimal getMontoAplicado() {
		return montoAplicado;
	}

	public BigDecimal getMontoExcepcion() {
		return montoExcepcion;
	}

	public String getRechazadoPor() {
		return rechazadoPor;
	}

	public LocalDateTime getRechazadoEn() {
		return rechazadoEn;
	}

	public String getMotivoRechazo() {
		return motivoRechazo;
	}
}
