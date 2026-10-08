package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.security.test.context.support.WithSecurityContext;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ejecuta la prueba con un {@code UsuarioAutenticado} en sesión. Usar en lugar de {@code @WithMockUser}:
 * con este el colegio quedaría en NINGUNO y la prueba no vería datos.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithSecurityContext(factory = FabricaContextoComoUsuario.class)
public @interface ComoUsuario {

	Rol[] roles() default Rol.PROMOTOR;

	long colegioId() default 1L;

	long usuarioId() default 1L;

	String nombreUsuario() default "usuario.prueba";

	String nombreCompleto() default "Usuario de Prueba";

	/** {@code true}: ingresó con clave temporal y solo tiene la autoridad CLAVE_PENDIENTE. */
	boolean clavePendiente() default false;
}
