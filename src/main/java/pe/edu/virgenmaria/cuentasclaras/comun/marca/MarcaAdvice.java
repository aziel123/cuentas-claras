package pe.edu.virgenmaria.cuentasclaras.comun.marca;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Sprint 8, tanda 1 (sección 3.7): {@code ${marca}} en todo modelo (título, cabecera, pie e ingreso). */
@ControllerAdvice
public class MarcaAdvice {

	private final Marca marca;

	public MarcaAdvice(Marca marca) {
		this.marca = marca;
	}

	@ModelAttribute("marca")
	public Marca marca() {
		return marca;
	}
}
