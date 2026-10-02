package pe.edu.virgenmaria.cuentasclaras.caja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Caja de un cajero en un día (hora de Lima). Se abre con el primer cobro del día y la abre su propio cajero (CHECK
 * {@code cajero = creado_por}). Cajero, fecha y fondo fijo no cambian (GRANT por columna). El cierre ciego, el depósito
 * y la reapertura llegan en la tanda 3: hasta entonces, en MySQL, un trigger impide cambiar su estado.
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
}
