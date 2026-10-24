package pe.edu.virgenmaria.cuentasclaras.academico.libreta.model;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;

/**
 * Sprint 10, tanda 3 (M9): qué identifica al alumno en la libreta. El código de estudiante del SIAGIE; el documento solo si
 * no hay código.
 */
public enum TipoCodigo {

	CODIGO_ESTUDIANTE("Código de estudiante"),
	DNI("DNI"),
	CE("Carné de extranjería"),
	PASAPORTE("Pasaporte");

	private final String etiqueta;

	TipoCodigo(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}

	public static TipoCodigo de(TipoDocumento documento) {
		return switch (documento) {
			case DNI -> DNI;
			case CE -> CE;
			case PASAPORTE -> PASAPORTE;
		};
	}
}
