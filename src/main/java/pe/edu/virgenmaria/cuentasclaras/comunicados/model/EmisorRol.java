package pe.edu.virgenmaria.cuentasclaras.comunicados.model;

import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

/**
 * Sprint 9, tanda 3 (sección 3.8, E16): quién firma un comunicado. Lo decide el sistema desde el rol REAL de la cuenta en
 * sesión (y la base lo compara con {@code usuario_rol}); nadie lo escribe. B3: el otro órgano aprueba lo que requiere
 * revisión (Promotoría lo de Dirección y al revés; lo de un docente, cualquiera de los dos).
 */
public enum EmisorRol {

	PROMOTORIA(Rol.PROMOTOR, "Promotoría"),
	DIRECCION(Rol.DIRECTOR, "Dirección"),
	DOCENTE(Rol.DOCENTE, "Docente");

	private final Rol rol;

	private final String etiqueta;

	EmisorRol(Rol rol, String etiqueta) {
		this.rol = rol;
		this.etiqueta = etiqueta;
	}

	public Rol rol() {
		return rol;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Si una persona con ese rol puede aprobar lo que este emisor dejó por aprobar (B3: el OTRO órgano). */
	public boolean loApruebaQuienTiene(Rol rolDeQuienAprueba) {
		return switch (this) {
			case DIRECCION -> rolDeQuienAprueba == Rol.PROMOTOR;
			case PROMOTORIA -> rolDeQuienAprueba == Rol.DIRECTOR;
			case DOCENTE -> rolDeQuienAprueba == Rol.PROMOTOR || rolDeQuienAprueba == Rol.DIRECTOR;
		};
	}
}
