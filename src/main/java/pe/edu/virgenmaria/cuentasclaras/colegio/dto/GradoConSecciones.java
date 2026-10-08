package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

import java.util.List;

public record GradoConSecciones(Grado grado, String etiqueta, List<SeccionVista> secciones) {
}
