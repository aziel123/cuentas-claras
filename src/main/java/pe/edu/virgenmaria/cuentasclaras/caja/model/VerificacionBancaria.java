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

	protected VerificacionBancaria() {
		// requerido por JPA
	}

	public static VerificacionBancaria dePago(Pago pago, ResultadoVerificacion resultado, String nota, String por) {
		Objects.requireNonNull(pago, "pago");
		if (!pago.getMedio().digital()) {
			throw new ReglaNegocioException("Solo se verifican contra el banco los pagos digitales; el efectivo se "
					+ "verifica con el depósito.");
		}
		if (por == null || por.equals(pago.getCajero()) || por.equals(pago.getCreadoPor())) {
			throw new ReglaNegocioException("Quien cobró el pago no lo verifica.");
		}
		VerificacionBancaria v = nueva(resultado, nota);
		v.pago = pago;
		return v;
	}

	public static VerificacionBancaria deDeposito(DepositoCaja deposito, ResultadoVerificacion resultado, String nota,
			String por) {
		Objects.requireNonNull(deposito, "deposito");
		if (por == null || por.equals(deposito.getCreadoPor()) || por.equals(deposito.getCaja().getCajero())) {
			throw new ReglaNegocioException("Quien depositó no verifica su depósito.");
		}
		VerificacionBancaria v = nueva(resultado, nota);
		v.deposito = deposito;
		return v;
	}

	private static VerificacionBancaria nueva(ResultadoVerificacion resultado, String nota) {
		VerificacionBancaria v = new VerificacionBancaria();
		v.resultado = Objects.requireNonNull(resultado, "resultado");
		boolean conNota = nota != null && !nota.isBlank();
		if (resultado == ResultadoVerificacion.NO_ENCONTRADO && !conNota) {
			throw new ReglaNegocioException("Si no aparece en el banco, anota qué revisaste (de 10 a 500 caracteres).");
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
}
