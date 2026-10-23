package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Auditoría del PR #19 (hallazgo 1): el método de servicio que alimenta un {@link ContadorMenu}. Una prueba de
 * arquitectura exige que ese método tenga {@code @PreAuthorize} con los mismos roles que el módulo del contador en
 * {@code ModuloApp}: el número del menú nunca revela algo que el rol no puede ver.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FuenteContador {

	/** La clase del servicio que cuenta. */
	Class<?> servicio();

	/** El método (sin parámetros) que cuenta. */
	String metodo();
}
