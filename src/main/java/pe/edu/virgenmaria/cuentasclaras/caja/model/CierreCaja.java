package pe.edu.virgenmaria.cuentasclaras.caja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Cierre de una caja: lo que dijo el libro (esperado = fondo + efectivo VIGENTE, calculado por el sistema), lo que contó
 * la cajera a ciegas (primer conteo y, si hubo, el reconteo) y la diferencia. El conteo no cambia (GRANT por columna);
 * solo se revisa: lo aprueba u observa otra persona (CHECK {@code revisado_por <> creado_por}) y ya no cambia.
 * {@code numero}: 1 el primero de la caja; 2 si se reabrió y se volvió a cerrar.
 */
@Entity
@Table(name = "cierre_caja")
public class CierreCaja extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "caja_diaria_id", nullable = false, updatable = false)
	private CajaDiaria caja;

	@Column(nullable = false, updatable = false)
	private int numero;

	@Column(name = "fondo_fijo", nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal fondoFijo;

	@Column(name = "efectivo_cobrado", nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal efectivoCobrado;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal esperado;

	@Column(name = "primer_conteo", nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal primerConteo;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal contado;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal diferencia;

	@Column(updatable = false, length = 300)
	private String denominaciones;

	@Column(updatable = false, length = 500)
	private String explicacion;

	@Column(name = "pagos_efectivo", nullable = false, updatable = false)
	private int pagosEfectivo;

	@Column(name = "pagos_digitales", nullable = false, updatable = false)
	private int pagosDigitales;

	@Column(name = "total_digital", nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal totalDigital;

	// --- Lo único que cambia: la revisión (GRANT UPDATE por columna; el trigger impide cambiarla una vez hecha) ---

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoCierre estado;

	@Column(name = "revisado_por", length = 60)
	private String revisadoPor;

	@Column(name = "revisado_en")
	private LocalDateTime revisadoEn;

	@Column(name = "comentario_revision", length = 500)
	private String comentarioRevision;

	protected CierreCaja() {
		// requerido por JPA
	}

	/**
	 * El cierre de la caja (abierta y con su conteo registrado). Con diferencia, la explicación es obligatoria.
	 *
	 * @param primerConteo lo que contó a ciegas la primera vez
	 * @param contado el conteo final (igual al primero si coincidió)
	 */
	public static CierreCaja registrar(CajaDiaria caja, ResumenCaja libro, BigDecimal primerConteo, BigDecimal contado,
			String denominaciones, String explicacion) {
		Objects.requireNonNull(caja, "caja");
		Objects.requireNonNull(libro, "libro");
		if (caja.getEstado() != EstadoCaja.ABIERTA || caja.getConteos() == 0) {
			throw new IllegalStateException("Se cierra una caja abierta con su conteo registrado");
		}
		CierreCaja cierre = new CierreCaja();
		cierre.caja = caja;
		cierre.numero = caja.getCierres() + 1;
		cierre.fondoFijo = caja.getFondoFijo();
		cierre.efectivoCobrado = Dinero.normalizar(libro.efectivo());
		cierre.esperado = cierre.fondoFijo.add(cierre.efectivoCobrado);
		cierre.primerConteo = Dinero.exigirDecimos(Dinero.normalizar(primerConteo),
				"El conteo debe ser múltiplo de S/ 0.10 (no hay monedas de 1 ni 5 céntimos).");
		cierre.contado = Dinero.exigirDecimos(Dinero.normalizar(contado),
				"El conteo debe ser múltiplo de S/ 0.10 (no hay monedas de 1 ni 5 céntimos).");
		cierre.diferencia = cierre.contado.subtract(cierre.esperado);
		cierre.denominaciones = denominaciones;
		cierre.explicacion = explicacion == null || explicacion.isBlank() ? null : Motivo.exigir(explicacion);
		if (cierre.diferencia.signum() != 0 && cierre.explicacion == null) {
			throw new ReglaNegocioException("Hay una diferencia: explica qué pasó (de 10 a 500 caracteres).");
		}
		cierre.pagosEfectivo = libro.pagosEfectivo();
		cierre.pagosDigitales = libro.pagosDigitales();
		cierre.totalDigital = Dinero.normalizar(libro.digital());
		cierre.estado = EstadoCierre.POR_REVISAR;
		return cierre;
	}

	public void aprobar(String por, LocalDateTime ahora, String comentario) {
		revisar(EstadoCierre.APROBADO, por, ahora, comentario);
	}

	public void observar(String por, LocalDateTime ahora, String comentario) {
		revisar(EstadoCierre.OBSERVADO, por, ahora, Motivo.exigir(comentario));
	}

	private void revisar(EstadoCierre nuevo, String por, LocalDateTime ahora, String comentario) {
		if (estado != EstadoCierre.POR_REVISAR) {
			throw new ReglaNegocioException("El cierre ya fue " + estado.etiqueta().toLowerCase() + ".");
		}
		if (por == null || por.equals(getCreadoPor()) || por.equals(caja.getCajero())) {
			throw new ReglaNegocioException("La cajera no revisa su propio cierre.");
		}
		estado = nuevo;
		revisadoPor = por;
		revisadoEn = Objects.requireNonNull(ahora, "ahora");
		comentarioRevision = comentario == null || comentario.isBlank() ? null : comentario.strip();
	}

	/** Faltante (negativo) o sobrante (positivo): ambos alertan (un sobrante puede ser un cobro sin registrar). */
	public boolean conDiferencia() {
		return diferencia.signum() != 0;
	}

	/** Contó dos veces: el primer conteo no coincidió con el esperado. */
	public boolean huboReconteo() {
		return primerConteo.compareTo(esperado) != 0;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los cierres de caja no se borran.");
	}

	public CajaDiaria getCaja() {
		return caja;
	}

	public int getNumero() {
		return numero;
	}

	public BigDecimal getFondoFijo() {
		return fondoFijo;
	}

	public BigDecimal getEfectivoCobrado() {
		return efectivoCobrado;
	}

	public BigDecimal getEsperado() {
		return esperado;
	}

	public BigDecimal getPrimerConteo() {
		return primerConteo;
	}

	public BigDecimal getContado() {
		return contado;
	}

	public BigDecimal getDiferencia() {
		return diferencia;
	}

	public String getDenominaciones() {
		return denominaciones;
	}

	public String getExplicacion() {
		return explicacion;
	}

	public int getPagosEfectivo() {
		return pagosEfectivo;
	}

	public int getPagosDigitales() {
		return pagosDigitales;
	}

	public BigDecimal getTotalDigital() {
		return totalDigital;
	}

	public EstadoCierre getEstado() {
		return estado;
	}

	public String getRevisadoPor() {
		return revisadoPor;
	}

	public LocalDateTime getRevisadoEn() {
		return revisadoEn;
	}

	public String getComentarioRevision() {
		return comentarioRevision;
	}
}
