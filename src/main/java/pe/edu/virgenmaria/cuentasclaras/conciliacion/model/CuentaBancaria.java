package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.TextoSeguro;

import java.util.Objects;

/**
 * Cuenta del colegio cuyo extracto se concilia (sprint 4, tanda 3). La registra Promotoría; el banco, el número y la
 * moneda no cambian ({@code updatable = false} y GRANT por columna): si cambia la cuenta, se desactiva y se registra
 * otra. Solo soles (CHECK).
 */
@Entity
@Table(name = "cuenta_bancaria")
public class CuentaBancaria extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private BancoCuenta banco;

	@Column(nullable = false, updatable = false, length = 30)
	private String numero;

	@Column(nullable = false, updatable = false, length = 3)
	private String moneda;

	@Column(nullable = false, updatable = false, length = 60)
	private String alias;

	@Column(nullable = false)
	private boolean activa;

	protected CuentaBancaria() {
		// requerido por JPA
	}

	/** Cuenta nueva en soles: el número se guarda sin espacios (dígitos y guiones, de 6 a 30 caracteres). */
	public static CuentaBancaria registrar(BancoCuenta banco, String numero, String alias) {
		String limpio = Normalizador.sinEspacios(numero);
		if (limpio == null || !limpio.matches("[0-9][0-9-]{4,28}[0-9]")) {
			throw new ReglaNegocioException("Escribe el número de la cuenta como aparece en el banco: solo dígitos y "
					+ "guiones (por ejemplo 191-2345678-0-12).");
		}
		String nombre = Normalizador.limpiar(alias);
		if (nombre == null || nombre.length() < 3 || nombre.length() > 60) {
			throw new ReglaNegocioException("Ponle un nombre corto a la cuenta (de 3 a 60 caracteres), por ejemplo «BCP "
					+ "soles recaudación».");
		}
		CuentaBancaria cuenta = new CuentaBancaria();
		cuenta.banco = Objects.requireNonNull(banco, "banco");
		cuenta.numero = limpio;
		cuenta.moneda = "PEN";
		cuenta.alias = TextoSeguro.exigir(nombre, "el nombre de la cuenta");
		cuenta.activa = true;
		return cuenta;
	}

	public void desactivar() {
		if (!activa) {
			throw new ReglaNegocioException("La cuenta ya estaba desactivada.");
		}
		activa = false;
	}

	/** Solo los dígitos del número (para reconocer la cuenta en el archivo del banco, escrita con o sin guiones). */
	public String digitos() {
		return soloDigitos(numero);
	}

	public static String soloDigitos(String texto) {
		return texto == null ? "" : texto.replaceAll("\\D", "");
	}

	/** «BCP · 191-2345678-0-12 · BCP soles». */
	public String descripcion() {
		return banco.etiqueta() + " · " + numero + " · " + alias;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las cuentas bancarias no se borran: se desactivan.");
	}

	public BancoCuenta getBanco() {
		return banco;
	}

	public String getNumero() {
		return numero;
	}

	public String getMoneda() {
		return moneda;
	}

	public String getAlias() {
		return alias;
	}

	public boolean isActiva() {
		return activa;
	}
}
