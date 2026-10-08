package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;

import java.time.LocalDate;

/** Año escolar en listas y cabeceras. */
public record AnioEscolarVista(Long id, int anio, EstadoAnioEscolar estado, String estadoEtiqueta,
		LocalDate inicioClases, LocalDate finClases, long secciones, long matriculados) {

	public boolean enCurso() {
		return estado == EstadoAnioEscolar.EN_CURSO;
	}

	public boolean cerrado() {
		return estado == EstadoAnioEscolar.CERRADO;
	}
}
