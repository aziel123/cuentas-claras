package pe.edu.virgenmaria.cuentasclaras.privacidad.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * «Mis datos» del portal (sprint 7, tanda 3; Ley 29733, derecho de acceso, sección 8.3): los datos personales de la familia
 * en sesión y de sus hijos que guarda el colegio, para qué se usa cada dato, a quién se envía y cuánto tiempo se guarda. Sin
 * deudas ni nada académico (la deuda está en el estado de cuenta).
 */
public record VistaMisDatos(String familia, List<Apoderado> apoderados, List<Hijo> hijos, List<Uso> usos,
		List<Destinatario> destinatarios, List<Plazo> plazos, String versionAviso) {

	/**
	 * Un apoderado de la familia. {@code propio}: es quien consulta. De los OTROS apoderados solo van el nombre, el
	 * parentesco y si es responsable de pago: su documento, sus contactos y su RUC son datos personales de otra persona
	 * (correcciones del sprint 7, QA-S7-8).
	 */
	public record Apoderado(String nombre, String parentesco, String documento, String celular, boolean celularVerificado,
			String correo, boolean correoVerificado, String ruc, String razonSocial, boolean recordatorios,
			boolean responsableDePago, boolean propio) {

		public static Apoderado otro(String nombre, String parentesco, boolean responsableDePago) {
			return new Apoderado(nombre, parentesco, null, null, false, null, false, null, null, false, responsableDePago,
					false);
		}
	}

	public record Hijo(String nombre, String documento, LocalDate fechaNacimiento, String estado,
			List<Matricula> matriculas) {
	}

	public record Matricula(int anio, String grado, String seccion, String estado) {
	}

	/** Un dato y para qué lo usa el colegio. */
	public record Uso(String dato, String paraQue) {
	}

	/** A quién se envía un dato, para qué y si sale del Perú. */
	public record Destinatario(String quien, String que, String paraQue) {
	}

	/** Cuánto tiempo se guarda un tipo de dato. */
	public record Plazo(String datos, String cuanto) {
	}
}
