package pe.edu.virgenmaria.cuentasclaras.caja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.TextoSeguro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Depósito en el banco del efectivo de una caja CERRADA (uno por caja). SOLO INSERCIÓN. Lo esperado es lo contado en
 * el último cierre menos el fondo fijo (el sencillo se queda en caja). Si el monto es otro, la explicación es
 * obligatoria y Promotoría recibe la alerta. Después lo verifica Administración contra el estado de cuenta.
 */
@Entity
@Immutable
@Table(name = "deposito_caja")
public class DepositoCaja extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "caja_diaria_id", nullable = false, updatable = false)
	private CajaDiaria caja;

	@Column(nullable = false, updatable = false, length = 60)
	private String cuenta;

	@Column(name = "numero_operacion", nullable = false, updatable = false, length = 30)
	private String numeroOperacion;

	@Column(name = "fecha_deposito", nullable = false, updatable = false)
	private LocalDate fechaDeposito;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal esperado;

	@Column(updatable = false, length = 500)
	private String explicacion;

	protected DepositoCaja() {
		// requerido por JPA
	}

	public static DepositoCaja registrar(CajaDiaria caja, String cuenta, String numeroOperacion, LocalDate fecha,
			BigDecimal monto, BigDecimal esperado, String explicacion) {
		Objects.requireNonNull(caja, "caja");
		if (caja.getEstado() != EstadoCaja.CERRADA) {
			throw new ReglaNegocioException("Primero cierra la caja: se deposita lo contado en el cierre.");
		}
		DepositoCaja deposito = new DepositoCaja();
		deposito.caja = caja;
		deposito.cuenta = TextoSeguro.exigir(Objects.requireNonNull(cuenta, "cuenta").strip(), "la cuenta");
		deposito.numeroOperacion = NumeroOperacion.normalizar(numeroOperacion);
		deposito.fechaDeposito = Objects.requireNonNull(fecha, "fecha");
		deposito.monto = Dinero.exigirDecimos(Dinero.positivo(monto, "el monto depositado"),
				"El monto depositado debe ser múltiplo de S/ 0.10.");
		deposito.esperado = Dinero.normalizar(esperado);
		deposito.explicacion = explicacion == null || explicacion.isBlank() ? null : Motivo.exigir(explicacion);
		if (!Dinero.iguales(deposito.monto, deposito.esperado) && deposito.explicacion == null) {
			throw new ReglaNegocioException("El depósito no es igual a lo contado en el cierre ("
					+ Dinero.formatear(deposito.esperado) + "): explica por qué (de 10 a 500 caracteres).");
		}
		return deposito;
	}

	/** Depositó otro monto que el contado (menos es posible faltante; más, un error a revisar). */
	public boolean distinto() {
		return !Dinero.iguales(monto, esperado);
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los depósitos no se borran.");
	}

	public CajaDiaria getCaja() {
		return caja;
	}

	public String getCuenta() {
		return cuenta;
	}

	public String getNumeroOperacion() {
		return numeroOperacion;
	}

	public LocalDate getFechaDeposito() {
		return fechaDeposito;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public BigDecimal getEsperado() {
		return esperado;
	}

	public String getExplicacion() {
		return explicacion;
	}
}
