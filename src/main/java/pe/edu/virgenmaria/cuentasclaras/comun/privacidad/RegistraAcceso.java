package pe.edu.virgenmaria.cuentasclaras.comun.privacidad;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un método de un controlador que muestra datos personales (sprint 7, tanda 3; Ley 29733, sección 8.2). Si la
 * respuesta es una página (200, no una redirección) y quien la pide es del personal, se registra en
 * {@code acceso_dato_personal} quién la vio, ANTES de mostrarla: si el registro falla, la página no se muestra.
 * <p>
 * Vive en {@code comun} para que ningún módulo dependa del paquete {@code privacidad}. El controlador indica qué mostró
 * con {@link AccesoMostrado} (la familia, el alumno o cuántas filas).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RegistraAcceso {

	TipoAcceso value();
}
