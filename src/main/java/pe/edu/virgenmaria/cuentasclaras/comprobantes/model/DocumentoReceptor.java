package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

import java.util.regex.Pattern;

/** Documento de quien recibe el comprobante, con su código del catálogo 06 de SUNAT. */
public enum DocumentoReceptor {

	DNI("1", "DNI", "^\\d{8}$"),
	CE("4", "CE", "^[A-Z0-9]{8,12}$"),
	PASAPORTE("7", "Pasaporte", "^[A-Z0-9]{6,12}$"),
	RUC("6", "RUC", "^\\d{11}$");

	private final String codigoSunat;

	private final String abreviatura;

	private final Pattern formato;

	DocumentoReceptor(String codigoSunat, String abreviatura, String formato) {
		this.codigoSunat = codigoSunat;
		this.abreviatura = abreviatura;
		this.formato = Pattern.compile(formato);
	}

	public String codigoSunat() {
		return codigoSunat;
	}

	public String abreviatura() {
		return abreviatura;
	}

	boolean formatoValido(String numero) {
		return numero != null && formato.matcher(numero).matches();
	}
}
