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
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Administración compara un pago digital (Yape, Plin, transferencia, tarjeta) o un depósito con el estado de cuenta
 * del banco. SOLO INSERCIÓN; una por pago o por depósito. Nunca la hace quien cobró o depositó (trigger en MySQL).
 * Detecta el fraude «cobré en efectivo y lo registré como Yape con un número inventado», que el cierre solo no ve.
 */
@Entity
@Immutable
@Table(name = "verificacion_bancaria")
public class VerificacionBancaria extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pago_id", updatable = false)
	private Pago pago;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "deposito_id", updatable = false)
	private DepositoCaja deposito;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ResultadoVerificacion resultado;

	@Column(updatable = false, length = 500)
	private String nota;

	// --- Lo que Administración vio en el banco, escrito a ciegas (V12; obligatorio en ENCONTRADO) ---

	@Column(name = "banco_operacion", updatable = false, length = 30)
	private String bancoOperacion;

	@Column(name = "banco_fecha", updatable = false)
	private LocalDate bancoFecha;

	@Column(name = "banco_monto", updatable = false, precision = 10, scale = 2)
	private BigDecimal bancoMonto;

	protected VerificacionBancaria() {
		// requerido por JPA
	}

	/**
	 * Lo que Administración vio en el banco, ya comparado con lo registrado: si es ENCONTRADO, {@code banco} trae la
	 * operación (canónica), la fecha y el monto que escribió a ciegas (la base exige que estén y, en MySQL, que coincidan).
	 */
	public record DatosBanco(String operacion, LocalDate fecha, BigDecimal monto) {
	}

	public static VerificacionBancaria dePago(Pago pago, ResultadoVerificacion resultado, String nota, DatosBanco banco,
			String por) {
		Objects.requireNonNull(pago, "pago");
		if (!pago.getMedio().digital()) {
			throw new ReglaNegocioException("Solo se verifican contra el banco los pagos digitales; el efectivo se "
					+ "verifica con el depósito.");
		}
		if (!pago.vigente()) {
			throw new ReglaNegocioException("Ese pago está anulado: no se verifica.");
		}
		if (por == null || por.equals(pago.getCajero()) || por.equals(pago.getCreadoPor())) {
			throw new ReglaNegocioException("Quien cobró el pago no lo verifica.");
		}
		VerificacionBancaria v = nueva(resultado, nota, banco);
		v.pago = pago;
		return v;
	}

	public static VerificacionBancaria deDeposito(DepositoCaja deposito, ResultadoVerificacion resultado, String nota,
			DatosBanco banco, String por) {
		Objects.requireNonNull(deposito, "deposito");
		if (por == null || por.equals(deposito.getCreadoPor()) || por.equals(deposito.getCaja().getCajero())) {
			throw new ReglaNegocioException("Quien depositó no verifica su depósito.");
		}
		VerificacionBancaria v = nueva(resultado, nota, banco);
		v.deposito = deposito;
		return v;
	}

	private static VerificacionBancaria nueva(ResultadoVerificacion resultado, String nota, DatosBanco banco) {
		VerificacionBancaria v = new VerificacionBancaria();
		v.resultado = Objects.requireNonNull(resultado, "resultado");
		boolean conNota = nota != null && !nota.isBlank();
		if (resultado == ResultadoVerificacion.NO_ENCONTRADO && !conNota) {
			throw new ReglaNegocioException("Si no aparece en el banco, anota qué revisaste (de 10 a 500 caracteres).");
		}
		if (resultado == ResultadoVerificacion.ENCONTRADO) {
			if (banco == null || banco.operacion() == null || banco.fecha() == null || banco.monto() == null) {
				throw new ReglaNegocioException("Escribe la operación, la fecha y el monto que ves en el banco.");
			}
			v.bancoOperacion = banco.operacion();
			v.bancoFecha = banco.fecha();
			v.bancoMonto = banco.monto();
		}
		v.nota = conNota ? Motivo.exigir(nota) : null;
		return v;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las verificaciones bancarias no se borran.");
	}

	public Pago getPago() {
		return pago;
	}

	public DepositoCaja getDeposito() {
		return deposito;
	}

	public ResultadoVerificacion getResultado() {
		return resultado;
	}

	public String getNota() {
		return nota;
	}

	public String getBancoOperacion() {
		return bancoOperacion;
	}

	public LocalDate getBancoFecha() {
		return bancoFecha;
	}

	public BigDecimal getBancoMonto() {
		return bancoMonto;
	}
}
