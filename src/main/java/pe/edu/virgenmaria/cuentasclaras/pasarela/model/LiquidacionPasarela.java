package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Liquidación de la pasarela (sprint 4, tanda 3): lo que abona al banco del colegio, NETO de su comisión y del IGV de la
 * comisión (CHECK: neto = bruto − comisión − IGV). SOLO INSERCIÓN. Una por referencia del proveedor (UNIQUE): se importa
 * por la API (tarea diaria) o, si el proveedor no tiene API, por archivo. Se empareja con UN abono del extracto; si
 * cuadra, los pagos de sus líneas quedan verificados.
 */
@Entity
@Immutable
@Table(name = "liquidacion_pasarela")
public class LiquidacionPasarela extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ProveedorPasarela proveedor;

	@Column(nullable = false, updatable = false, length = 80)
	private String referencia;

	@Column(name = "fecha_liquidacion", nullable = false, updatable = false)
	private LocalDate fechaLiquidacion;

	@Column(name = "fecha_abono", nullable = false, updatable = false)
	private LocalDate fechaAbono;

	@Column(name = "total_bruto", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal totalBruto;

	@Column(name = "total_comision", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal totalComision;

	@Column(name = "total_igv", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal totalIgv;

	@Column(name = "total_neto", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal totalNeto;

	@Column(nullable = false, updatable = false)
	private int lineas;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private OrigenLiquidacion origen;

	@Column(name = "archivo_id", updatable = false)
	private Long archivoId;

	protected LiquidacionPasarela() {
		// requerido por JPA
	}

	public static LiquidacionPasarela registrar(ProveedorPasarela proveedor, String referencia, LocalDate fechaLiquidacion,
			LocalDate fechaAbono, BigDecimal totalBruto, BigDecimal totalComision, BigDecimal totalIgv, int lineas,
			OrigenLiquidacion origen, Long archivoId) {
		LiquidacionPasarela l = new LiquidacionPasarela();
		l.proveedor = Objects.requireNonNull(proveedor, "proveedor");
		l.referencia = Objects.requireNonNull(referencia, "referencia");
		l.fechaLiquidacion = Objects.requireNonNull(fechaLiquidacion, "fechaLiquidacion");
		l.fechaAbono = Objects.requireNonNull(fechaAbono, "fechaAbono");
		l.totalBruto = Dinero.normalizar(totalBruto);
		l.totalComision = Dinero.normalizar(totalComision);
		l.totalIgv = Dinero.normalizar(totalIgv);
		if (l.totalComision.signum() < 0 || l.totalIgv.signum() < 0 || lineas <= 0) {
			throw new IllegalArgumentException("La comisión y el IGV no son negativos y la liquidación tiene líneas");
		}
		l.totalNeto = l.totalBruto.subtract(l.totalComision).subtract(l.totalIgv);
		l.lineas = lineas;
		l.origen = Objects.requireNonNull(origen, "origen");
		if ((origen == OrigenLiquidacion.ARCHIVO) != (archivoId != null)) {
			throw new IllegalArgumentException("Solo la liquidación por archivo lleva su archivo");
		}
		l.archivoId = archivoId;
		return l;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las liquidaciones de la pasarela no se borran.");
	}

	public ProveedorPasarela getProveedor() {
		return proveedor;
	}

	public String getReferencia() {
		return referencia;
	}

	public LocalDate getFechaLiquidacion() {
		return fechaLiquidacion;
	}

	public LocalDate getFechaAbono() {
		return fechaAbono;
	}

	public BigDecimal getTotalBruto() {
		return totalBruto;
	}

	public BigDecimal getTotalComision() {
		return totalComision;
	}

	public BigDecimal getTotalIgv() {
		return totalIgv;
	}

	public BigDecimal getTotalNeto() {
		return totalNeto;
	}

	public int getLineas() {
		return lineas;
	}

	public OrigenLiquidacion getOrigen() {
		return origen;
	}

	public Long getArchivoId() {
		return archivoId;
	}
}
