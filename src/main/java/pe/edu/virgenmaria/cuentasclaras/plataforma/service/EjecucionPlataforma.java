package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;

import java.util.function.Supplier;

/**
 * Sprint 8, tanda 1 (sección 10): lo que el operador escribe con la conexión de {@code cc_sistema} conservando su propio
 * {@code SecurityContext} (la bitácora y {@code creado_por} dicen quién fue): el colegio (INSERT y su ficha en
 * preparación), el año escolar inicial y el correo externo de control. Es la ruta de identidad
 * ({@link EjecucionIdentidad}): abre su propia transacción y falla si ya hay una abierta. Solo la usa
 * {@code plataforma.service} (regla ArchUnit).
 */
@Component
public class EjecucionPlataforma {

	private final EjecucionIdentidad identidad;

	public EjecucionPlataforma(EjecucionIdentidad identidad) {
		this.identidad = identidad;
	}

	public <T> T como(Supplier<T> operacion) {
		return identidad.como(operacion);
	}

	public void ejecutar(Runnable operacion) {
		identidad.ejecutar(operacion);
	}
}
