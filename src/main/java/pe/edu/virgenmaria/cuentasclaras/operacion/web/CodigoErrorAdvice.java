package pe.edu.virgenmaria.cuentasclaras.operacion.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pe.edu.virgenmaria.cuentasclaras.operacion.log.FiltroIdPeticion;

/**
 * Sprint 7 (A05): la página de error muestra un «código de error» (el id de la petición que va en los logs), nunca el
 * mensaje de la excepción. Con ese código el operador encuentra lo que pasó.
 */
@ControllerAdvice
public class CodigoErrorAdvice {

	@ModelAttribute("codigoError")
	public String codigoError(HttpServletRequest peticion) {
		Object id = peticion.getAttribute(FiltroIdPeticion.ATRIBUTO);
		return id instanceof String texto ? texto : null;
	}
}
