package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

/**
 * Resultado de corregir un apoderado, para el aviso de la pantalla.
 *
 * @param contactoSolicitado el celular o el correo quedaron como solicitud que aprueba otra persona
 */
public record ApoderadoCorregido(Long familiaId, boolean datosCorregidos, boolean contactoSolicitado) {

	public String aviso() {
		if (contactoSolicitado) {
			return (datosCorregidos ? "Listo: se corrigieron los datos del apoderado. " : "")
					+ "El cambio de celular o correo quedó pedido: lo aprueba otra persona de Promotoría o Dirección "
					+ "en Aprobaciones. Mientras tanto, los avisos siguen yendo al contacto actual.";
		}
		return "Listo: se corrigieron los datos del apoderado. El cambio quedó en la bitácora.";
	}
}
