package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;

/** Una tarjeta de la búsqueda de caja: el alumno, su grado, su familia y cuánto debe vencido. */
public record ResultadoBusqueda(Long familiaId, Long alumnoId, String alumno, String documento, String grado,
		String familia, BigDecimal vencido, BigDecimal porPagar) {

	public boolean debeVencido() {
		return vencido.signum() > 0;
	}
}
