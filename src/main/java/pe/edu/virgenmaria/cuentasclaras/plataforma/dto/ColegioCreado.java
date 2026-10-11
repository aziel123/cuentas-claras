package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

/**
 * Sprint 8, tanda 1: el resultado del alta. {@code titularCanal} y {@code titularDestino} dicen a dónde se envió el enlace
 * (enmascarado), nunca el enlace. {@code pendiente}: lo que no se completó (el operador reintenta con «Completar alta»).
 */
public record ColegioCreado(Long colegioId, String nombre, String titularUsuario, String titularCanal,
		String titularDestino, String pendiente) {

	public boolean completo() {
		return pendiente == null;
	}
}
