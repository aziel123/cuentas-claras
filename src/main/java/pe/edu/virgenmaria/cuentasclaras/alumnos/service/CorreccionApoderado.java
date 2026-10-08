package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

/**
 * Resultado de corregir un apoderado.
 *
 * @param datosCorregidos    cambiaron nombres, documento o parentesco (se aplicó al momento)
 * @param contactoSolicitado cambió el celular o el correo: quedó una solicitud que aprueba otra persona
 */
public record CorreccionApoderado(boolean datosCorregidos, boolean contactoSolicitado) {

	public boolean sinCambios() {
		return !datosCorregidos && !contactoSolicitado;
	}
}
