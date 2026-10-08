package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

/** Qué pasará con una fila al confirmar. */
public enum Clasificacion {

	NUEVO("Nuevo"),
	ACTUALIZA("Con cambios"),
	SIN_CAMBIOS("Sin cambios"),
	ERROR("Con errores");

	private final String etiqueta;

	Clasificacion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
