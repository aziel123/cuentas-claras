package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Claves temporales para usuarios nuevos o restablecidos: 12 caracteres aleatorios
 * ({@link SecureRandom}) sin caracteres que se confunden al dictarlos (0/O, 1/l/I).
 * Se muestran una sola vez y obligan a cambiarlas en el primer ingreso.
 */
@Component
public class GeneradorClaveTemporal {

	static final int LONGITUD = 12;

	static final String ALFABETO = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private final SecureRandom aleatorio = new SecureRandom();

	public String generar() {
		StringBuilder clave = new StringBuilder(LONGITUD);
		for (int i = 0; i < LONGITUD; i++) {
			clave.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
		}
		return clave.toString();
	}
}
