package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import java.util.regex.Pattern;

/** Documento de identidad aceptado. Otros (CPP, PTP, código SIAGIE) se agregarían con una migración. */
public enum TipoDocumento {

	DNI("DNI", "^\\d{8}$", "el DNI debe tener 8 dígitos"),
	CE("Carné de extranjería", "^[A-Z0-9]{8,12}$", "el carné de extranjería debe tener de 8 a 12 letras o números"),
	PASAPORTE("Pasaporte", "^[A-Z0-9]{6,12}$", "el pasaporte debe tener de 6 a 12 letras o números");

	private final String etiqueta;

	private final Pattern patron;

	private final String regla;

	TipoDocumento(String etiqueta, String patron, String regla) {
		this.etiqueta = etiqueta;
		this.patron = Pattern.compile(patron);
		this.regla = regla;
	}

	public String etiqueta() {
		return etiqueta;
	}

	public Pattern patron() {
		return patron;
	}

	/** «el DNI debe tener 8 dígitos». */
	public String regla() {
		return regla;
	}

	/** Abreviatura para mostrar junto al número: DNI, CE o Pasaporte. */
	public String abreviatura() {
		return this == PASAPORTE ? "Pasaporte" : name();
	}
}
