package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

import java.util.List;

/** Secciones de un año agrupadas por nivel y grado, en orden escolar. Solo grados con secciones. */
public record NivelConGrados(Nivel nivel, String etiqueta, List<GradoConSecciones> grados) {
}
