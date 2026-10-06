package pe.edu.virgenmaria.cuentasclaras.conciliacion.inicial;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Extracto de EJEMPLO (solo perfil {@code dev}): lo arma {@link DatosDemoConciliacionDev} al arrancar con los pagos de la
 * demostración de AYER (el Yape real, el depósito y el abono de la recaudación, más intereses y una comisión), SIN el
 * Yape inventado por la cajera. Se descarga desde la pantalla de extractos para probar el flujo completo.
 */
@Component
@Profile("dev")
public class EjemploExtractoDev {

	private volatile String nombre;

	private volatile byte[] contenido;

	private volatile BigDecimal saldoFinal;

	void guardar(String nombre, byte[] contenido, BigDecimal saldoFinal) {
		this.nombre = nombre;
		this.contenido = contenido.clone();
		this.saldoFinal = saldoFinal;
	}

	public boolean disponible() {
		return contenido != null;
	}

	public String nombre() {
		return nombre;
	}

	public byte[] contenido() {
		return contenido == null ? new byte[0] : contenido.clone();
	}

	public BigDecimal saldoFinal() {
		return saldoFinal;
	}
}
