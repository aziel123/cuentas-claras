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

	public record Apoderado(String nombre, String parentesco, String documento, String celular, boolean celularVerificado,
			String correo, boolean correoVerificado, String ruc, String razonSocial, boolean recordatorios,
			boolean responsableDePago) {
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
