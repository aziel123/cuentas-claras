package pe.edu.virgenmaria.cuentasclaras.comun.privacidad;

import java.time.LocalDateTime;

/** Una consulta a los datos personales de una familia, para el bloque «Quién consultó estos datos» de su ficha. */
public record AccesoReciente(String persona, String nombreCompleto, String pantalla, LocalDateTime fecha) {
}
