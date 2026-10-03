package pe.edu.virgenmaria.cuentasclaras.caja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Caja de un cajero en un día (hora de Lima). Se abre con el primer cobro del día y la abre su propio cajero (CHECK
 * {@code cajero = creado_por}). Cajero, fecha y fondo fijo no cambian (GRANT por columna).
 * <p>
 * Cierre ciego: el primer conteo se guarda ({@code primerConteo}) y no se reescribe; si no coincide hay UN reconteo,
 * que siempre cierra. Así no se puede «tantear» el esperado contando varias veces (en MySQL lo vigila el trigger
 * trg_caja_diaria_estado). Se cierra solo con un {@link CierreCaja} registrado y se reabre solo con una reapertura
 * aprobada del mismo día y sin depósito.
 */
@Entity
@Table(name = "caja_diaria")
public class CajaDiaria extends BaseEntity {

	@Column(nullable = false, updatable = false, length = 60)
	private String cajero;

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	@Column(name = "fondo_fijo", nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal fondoFijo;

	// --- Lo único que puede cambiar (GRANT UPDATE por columna en MySQL; los triggers vigilan el cierre) ---

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoCaja estado;

	@Column(nullable = false)
	private int cierres;

	@Column(nullable = false)
	private int conteos;

	@Column(name = "primer_conteo", precision = 10, scale = 2)
	private BigDecimal primerConteo;

	/** M1: la solicitud de reapertura APROBADA que reabrió la caja por última vez (en MySQL la exige un trigger). */
	@Column(name = "reapertura_solicitud_id")
	private Long reaperturaSolicitudId;

	protected CajaDiaria() {
		// requerido por JPA
	}

	public static CajaDiaria abrir(String cajero, LocalDate fecha, BigDecimal fondoFijo) {
		CajaDiaria caja = new CajaDiaria();
		caja.cajero = Objects.requireNonNull(cajero, "cajero");
		caja.fecha = Objects.requireNonNull(fecha, "fecha");
		caja.fondoFijo = Dinero.normalizar(fondoFijo);
		caja.estado = EstadoCaja.ABIERTA;
		caja.cierres = 0;
		caja.conteos = 0;
		return caja;
	}

	/** Una caja cerrada no recibe efectivo (los pagos digitales sí). */
	public boolean aceptaEfectivo() {
		return estado == EstadoCaja.ABIERTA;
	}

	/**
	 * Registra un conteo a ciegas comparándolo con el esperado (que la cajera no ve). El primero se guarda y no cambia.
	 *
	 * @return COINCIDE (cierra), RECONTAR (vuelve a contar, sin montos) o FINAL (el reconteo: cierra)
	 */
	public ResultadoConteo registrarConteo(BigDecimal contado, BigDecimal esperado) {
		exigirAbierta();
		if (conteos == 0) {
			primerConteo = Dinero.normalizar(contado);
			conteos = 1;
			return Dinero.iguales(primerConteo, esperado) ? ResultadoConteo.COINCIDE : ResultadoConteo.RECONTAR;
		}
		if (conteos == 1) {
			conteos = 2;
			return ResultadoConteo.FINAL;
		}
		throw new IllegalStateException("La caja ya tiene sus dos conteos");
	}

	/** Cierra con el cierre ya registrado (número = cierres + 1). */
	public void cerrar(CierreCaja cierre) {
		exigirAbierta();
		if (conteos == 0 || cierre == null || cierre.getNumero() != cierres + 1) {
			throw new IllegalStateException("La caja se cierra con su cierre registrado");
		}
		estado = EstadoCaja.CERRADA;
		cierres++;
	}

	/**
	 * Reapertura aprobada con esa solicitud: vuelve a ABIERTA y reinicia el conteo (el cierre anterior queda como
	 * estaba). El siguiente cierre ya no es ciego: queda marcado «tras reapertura».
	 */
	public void reabrir(Long solicitudId) {
		if (estado != EstadoCaja.CERRADA) {
			throw new ReglaNegocioException("La caja no está cerrada.");
		}
		reaperturaSolicitudId = java.util.Objects.requireNonNull(solicitudId, "solicitudId");
		estado = EstadoCaja.ABIERTA;
		conteos = 0;
		primerConteo = null;
	}

	/** Hay un primer conteo que no coincidió y falta el reconteo. */
	public boolean esperaReconteo() {
		return estado == EstadoCaja.ABIERTA && conteos == 1;
	}

	private void exigirAbierta() {
		if (estado != EstadoCaja.ABIERTA) {
			throw new ReglaNegocioException("Esta caja ya está cerrada.");
		}
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las cajas no se borran.");
	}

	public String getCajero() {
		return cajero;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public BigDecimal getFondoFijo() {
		return fondoFijo;
	}

	public EstadoCaja getEstado() {
		return estado;
	}

	public int getCierres() {
		return cierres;
	}

	public int getConteos() {
		return conteos;
	}

	public BigDecimal getPrimerConteo() {
		return primerConteo;
	}

	public Long getReaperturaSolicitudId() {
		return reaperturaSolicitudId;
	}
}
