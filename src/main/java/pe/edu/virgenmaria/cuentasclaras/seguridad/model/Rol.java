package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

/**
 * Roles del sistema. El nombre se guarda tal cual en {@code usuario_rol.rol}
 * (la migración V2 restringe los valores con un CHECK; V28 agrega el operador).
 */
public enum Rol {

	PROMOTOR("Promotoría"),
	DIRECTOR("Dirección"),
	ADMINISTRACION("Administración"),
	CAJA("Caja"),
	DOCENTE("Docente"),
	APODERADO("Apoderado"),
	/**
	 * Sprint 8, tanda 1 (sección 3.2): quien da de alta colegios desde {@code /plataforma}. Solo existe en el colegio de
	 * plataforma, solo y sin otro rol, y no tiene ninguna ruta de un colegio: nunca ve datos de las familias.
	 */
	OPERADOR_PLATAFORMA("Operador de la plataforma");

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

	/** Los roles de las personas de un colegio (sin el apoderado ni el operador). */
	public boolean esDelPersonal() {
		return this != APODERADO && this != OPERADOR_PLATAFORMA;
	}
}
