package pe.edu.virgenmaria.cuentasclaras.panel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Foto del resumen diario de un colegio (sprint 6, tanda 2): las cifras que salieron a Promotoría a las 19:30. La escribe
 * solo {@code sistema.panel} (CHECK y trigger), una por día ({@code uk_resumen_diario}), y es de SOLO INSERCIÓN (sin GRANT
 * de UPDATE ni DELETE: 1142). En MySQL, {@code trg_resumen_diario_registro} exige que lo cobrado, el efectivo, lo cobrado
 * en el mes, la deuda vencida y las familias morosas sean EXACTAMENTE la suma de los libros en ese momento (P3). El panel
 * nunca la muestra como cifra del día: la usa para comparar (P4).
 */
@Entity
@Immutable
@Table(name = "resumen_diario")
public class ResumenDiario extends BaseEntity {

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	@Column(name = "cortado_en", nullable = false, updatable = false)
	private LocalDateTime cortadoEn;

	@Column(name = "cobrado_total", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal cobradoTotal;

	@Column(name = "pagos_cantidad", nullable = false, updatable = false)
	private int pagosCantidad;

	@Column(name = "cobrado_efectivo", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal cobradoEfectivo;

	@Column(name = "pagos_efectivo", nullable = false, updatable = false)
	private int pagosEfectivo;

	@Column(name = "cobrado_mes", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal cobradoMes;

	@Column(name = "deuda_vencida", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal deudaVencida;

	@Column(name = "familias_morosas", nullable = false, updatable = false)
	private int familiasMorosas;

	@Column(name = "cajas_sin_cerrar", nullable = false, updatable = false)
	private int cajasSinCerrar;

	@Column(name = "cierres_con_diferencia", nullable = false, updatable = false)
	private int cierresConDiferencia;

	@Column(name = "solicitudes_pendientes", nullable = false, updatable = false)
	private int solicitudesPendientes;

	@Column(name = "alertas_criticas", nullable = false, updatable = false)
	private int alertasCriticas;

	@Column(name = "avisos_familias", nullable = false, updatable = false)
	private int avisosFamilias;

	@Column(name = "avisos_entregados", nullable = false, updatable = false)
	private int avisosEntregados;

	@Column(name = "huella_secuencia", updatable = false)
	private Long huellaSecuencia;

	@Column(name = "huella_codigo", updatable = false, length = 16)
	private String huellaCodigo;

	protected ResumenDiario() {
		// requerido por JPA
	}

	/** Las cifras de los libros (las comprueba el trigger) y los conteos del día. */
	public record Cifras(BigDecimal cobradoTotal, long pagosCantidad, BigDecimal cobradoEfectivo, long pagosEfectivo,
			BigDecimal cobradoMes, BigDecimal deudaVencida, long familiasMorosas) {

		public Cifras {
			cobradoTotal = Dinero.normalizar(cobradoTotal);
			cobradoEfectivo = Dinero.normalizar(cobradoEfectivo);
			cobradoMes = Dinero.normalizar(cobradoMes);
			deudaVencida = Dinero.normalizar(deudaVencida);
		}
	}

	/** Lo demás que informa el resumen (no sale de pago ni cuota; el trigger no lo recalcula). */
	public record Conteos(long cajasSinCerrar, long cierresConDiferencia, long solicitudesPendientes, long alertasCriticas,
			long avisosFamilias, long avisosEntregados) {
	}

	public static ResumenDiario de(LocalDate fecha, LocalDateTime cortadoEn, Cifras cifras, Conteos conteos,
			Long huellaSecuencia, String huellaCodigo) {
		Objects.requireNonNull(cifras, "cifras");
		Objects.requireNonNull(conteos, "conteos");
		if ((huellaSecuencia == null) != (huellaCodigo == null)) {
			throw new IllegalArgumentException("La huella va con su secuencia y su código");
		}
		ResumenDiario r = new ResumenDiario();
		r.fecha = Objects.requireNonNull(fecha, "fecha");
		r.cortadoEn = Objects.requireNonNull(cortadoEn, "cortadoEn");
		r.cobradoTotal = cifras.cobradoTotal();
		r.pagosCantidad = Math.toIntExact(cifras.pagosCantidad());
		r.cobradoEfectivo = cifras.cobradoEfectivo();
		r.pagosEfectivo = Math.toIntExact(cifras.pagosEfectivo());
		r.cobradoMes = cifras.cobradoMes();
		r.deudaVencida = cifras.deudaVencida();
		r.familiasMorosas = Math.toIntExact(cifras.familiasMorosas());
		r.cajasSinCerrar = Math.toIntExact(conteos.cajasSinCerrar());
		r.cierresConDiferencia = Math.toIntExact(conteos.cierresConDiferencia());
		r.solicitudesPendientes = Math.toIntExact(conteos.solicitudesPendientes());
		r.alertasCriticas = Math.toIntExact(conteos.alertasCriticas());
		r.avisosFamilias = Math.toIntExact(conteos.avisosFamilias());
		r.avisosEntregados = Math.toIntExact(conteos.avisosEntregados());
		r.huellaSecuencia = huellaSecuencia;
		r.huellaCodigo = huellaCodigo;
		return r;
	}

	@PreUpdate
	void impedirEdicion() {
		throw new IllegalStateException("La foto del resumen diario es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La foto del resumen diario no se borra.");
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public LocalDateTime getCortadoEn() {
		return cortadoEn;
	}

	public BigDecimal getCobradoTotal() {
		return cobradoTotal;
	}

	public int getPagosCantidad() {
		return pagosCantidad;
	}

	public BigDecimal getCobradoEfectivo() {
		return cobradoEfectivo;
	}

	public int getPagosEfectivo() {
		return pagosEfectivo;
	}

	public BigDecimal getCobradoMes() {
		return cobradoMes;
	}

	public BigDecimal getDeudaVencida() {
		return deudaVencida;
	}

	public int getFamiliasMorosas() {
		return familiasMorosas;
	}

	public int getCajasSinCerrar() {
		return cajasSinCerrar;
	}

	public int getCierresConDiferencia() {
		return cierresConDiferencia;
	}

	public int getSolicitudesPendientes() {
		return solicitudesPendientes;
	}

	public int getAlertasCriticas() {
		return alertasCriticas;
	}

	public int getAvisosFamilias() {
		return avisosFamilias;
	}

	public int getAvisosEntregados() {
		return avisosEntregados;
	}

	public Long getHuellaSecuencia() {
		return huellaSecuencia;
	}

	public String getHuellaCodigo() {
		return huellaCodigo;
	}
}
