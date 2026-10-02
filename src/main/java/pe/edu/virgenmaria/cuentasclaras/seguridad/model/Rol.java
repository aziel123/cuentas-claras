package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

/**
 * Roles del sistema. El nombre se guarda tal cual en {@code usuario_rol.rol}
 * (la migración V2 restringe los valores con un CHECK).
 */
public enum Rol {

	PROMOTOR("Promotoría"),
	DIRECTOR("Dirección"),
	ADMINISTRACION("Administración"),
	CAJA("Caja"),
	DOCENTE("Docente"),
	APODERADO("Apoderado");

	private final String etiqueta;

	Rol(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	/** Autoridad de Spring Security ({@code ROLE_PROMOTOR}, ...). */
	public String autoridad() {
		return "ROLE_" + name();
	}

	/** Nombre para mostrar en pantalla. */
	public String etiqueta() {
		return etiqueta;
	}
}
