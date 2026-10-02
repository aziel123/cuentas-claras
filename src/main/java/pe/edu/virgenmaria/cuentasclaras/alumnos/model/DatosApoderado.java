package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

/** Datos del apoderado ya validados y normalizados por {@link ReglasDatosPersonales}. Tiene WhatsApp o correo. */
public record DatosApoderado(DocumentoIdentidad documento, String apellidoPaterno, String apellidoMaterno,
		String nombres, Parentesco parentesco, String telefonoWhatsapp, String correo) implements java.io.Serializable {
}
