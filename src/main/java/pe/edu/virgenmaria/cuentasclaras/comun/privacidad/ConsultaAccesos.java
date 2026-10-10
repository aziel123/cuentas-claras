package pe.edu.virgenmaria.cuentasclaras.comun.privacidad;

import java.util.List;

/**
 * Puerto: quién consultó los datos personales de una familia en los últimos días (sprint 7, tanda 3; sección 8.2). Lo
 * implementa {@code privacidad} y lo usa la ficha de la familia sin depender de ese paquete. Solo Promotoría.
 */
public interface ConsultaAccesos {

	/** Días que muestra la ficha. */
	int DIAS_FICHA = 90;

	List<AccesoReciente> deFamilia(Long familiaId);
}
