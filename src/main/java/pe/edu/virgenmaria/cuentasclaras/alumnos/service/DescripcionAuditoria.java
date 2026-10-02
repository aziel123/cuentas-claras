package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosApoderado;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;

import java.util.ArrayList;
import java.util.List;

/**
 * Textos de la bitácora para alumnos y apoderados (Ley 29733). La bitácora es inmutable, así que:
 * documento, celular y correo van ENMASCARADOS y de la fecha de nacimiento solo el año. Los nombres sí van:
 * se necesitan para la rendición de cuentas.
 */
final class DescripcionAuditoria {

	private DescripcionAuditoria() {
	}

	static String alumno(Alumno alumno) {
		return alumno.nombreCompleto() + "; " + alumno.getDocumento().enmascarado() + "; nació en "
				+ alumno.getFechaNacimiento().getYear() + "; responsable de pago: "
				+ alumno.getResponsablePago().nombreCompleto() + "; familia " + alumno.getFamilia().getId();
	}

	static String apoderado(Apoderado apoderado) {
		return apoderado.nombreCompleto() + "; " + apoderado.getDocumento().enmascarado() + "; "
				+ apoderado.getParentesco().etiqueta() + contacto(apoderado.getTelefonoWhatsapp(), apoderado.getCorreo())
				+ "; familia " + apoderado.getFamilia().getId();
	}

	/** Solo los campos que cambiaron. */
	static String camposAlumno(DatosAlumno datos, List<String> campos) {
		List<String> partes = new ArrayList<>();
		if (campos.contains("documento")) {
			partes.add(datos.documento().enmascarado());
		}
		if (campos.contains("nombre")) {
			partes.add(datos.nombres() + " " + datos.apellidoPaterno()
					+ (datos.apellidoMaterno() == null ? "" : " " + datos.apellidoMaterno()));
		}
		if (campos.contains("fecha de nacimiento")) {
			partes.add("nació en " + datos.fechaNacimiento().getYear());
		}
		return String.join("; ", partes);
	}

	static String camposApoderado(DatosApoderado datos, List<String> campos) {
		return camposApoderado(datos.documento().enmascarado(), datos.nombres() + " " + datos.apellidoPaterno()
						+ (datos.apellidoMaterno() == null ? "" : " " + datos.apellidoMaterno()),
				datos.parentesco().etiqueta(), datos.telefonoWhatsapp(), datos.correo(), campos);
	}

	private static String camposApoderado(String documento, String nombre, String parentesco, String telefono,
			String correo, List<String> campos) {
		List<String> partes = new ArrayList<>();
		if (campos.contains("documento")) {
			partes.add(documento);
		}
		if (campos.contains("nombre")) {
			partes.add(nombre);
		}
		if (campos.contains("parentesco")) {
			partes.add(parentesco);
		}
		if (campos.contains("celular")) {
			partes.add("WhatsApp " + (telefono == null ? "(ninguno)" : Enmascarar.telefono(telefono)));
		}
		if (campos.contains("correo")) {
			partes.add("correo " + (correo == null ? "(ninguno)" : Enmascarar.correo(correo)));
		}
		return String.join("; ", partes);
	}

	private static String contacto(String telefono, String correo) {
		StringBuilder texto = new StringBuilder();
		if (telefono != null) {
			texto.append("; WhatsApp ").append(Enmascarar.telefono(telefono));
		}
		if (correo != null) {
			texto.append("; correo ").append(Enmascarar.correo(correo));
		}
		return texto.toString();
	}

	/** «WhatsApp +51 *** *** 321, correo j***@gmail.com» (enmascarado), para el resumen de una solicitud. */
	static String contactoVisible(String telefono, String correo) {
		return "WhatsApp " + (telefono == null ? "(ninguno)" : Enmascarar.telefono(telefono)) + ", correo "
				+ (correo == null ? "(ninguno)" : Enmascarar.correo(correo));
	}
}
