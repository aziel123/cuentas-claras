package pe.edu.virgenmaria.cuentasclaras.demo.dto;

import java.util.List;

/**
 * Sprint 8, tanda 3: lo que ve el visitante al crear su demo (una sola vez): el nombre de su colegio ficticio, la clave común
 * de sus cuentas y una cuenta por rol.
 */
public record DemoCreada(String colegio, String clave, List<Cuenta> cuentas) {

	/** Una cuenta de la demo: el rol (en lenguaje claro), el nombre de usuario y el nombre de la persona (ficticia). */
	public record Cuenta(String rol, String usuario, String nombre) {
	}
}
