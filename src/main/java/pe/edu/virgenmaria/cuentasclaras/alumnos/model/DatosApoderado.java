package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

/** Datos del apoderado ya validados y normalizados por {@link ReglasDatosPersonales}. Tiene WhatsApp o correo. */
public record DatosApoderado(DocumentoIdentidad documento, String apellidoPaterno, String apellidoMaterno,
		String nombres, Parentesco parentesco, String telefonoWhatsapp, String correo) implements java.io.Serializable {

	/** Los mismos datos con otro contacto (por ejemplo, el actual: el cambio de contacto va por solicitud). */
	public DatosApoderado conContacto(String otroTelefono, String otroCorreo) {
		return new DatosApoderado(documento, apellidoPaterno, apellidoMaterno, nombres, parentesco, otroTelefono,
				otroCorreo);
	}

	/** {@code true} si el celular o el correo son distintos de los dados. */
	public boolean cambiaContacto(String telefonoActual, String correoActual) {
		return !java.util.Objects.equals(telefonoWhatsapp, telefonoActual) || !java.util.Objects.equals(correo, correoActual);
	}
}
