package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pe.edu.virgenmaria.cuentasclaras.comun.config.PropiedadesEntorno;

/** S4-M1: la franja del entorno de prueba («PILOTO» o, desde el sprint 8, «DEMO») en todas las páginas, con o sin sesión. */
@ControllerAdvice
public class FranjaEntornoAdvice {

	private final PropiedadesEntorno entorno;

	public FranjaEntornoAdvice(PropiedadesEntorno entorno) {
		this.entorno = entorno;
	}

	@ModelAttribute("franjaEntorno")
	public String franjaEntorno() {
		return entorno.franja();
	}

	/** Sprint 8, tanda 3: en la demo, el título lleva «[DEMO]», la franja enlaza al recorrido y la boleta dice «SIN VALOR». */
	@ModelAttribute("entornoDemo")
	public boolean entornoDemo() {
		return entorno.demo();
	}
}
