package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

/** Sprint 10, tanda 1: un valor de la escala de un nivel (AD, A, B o C), con su descripción del CNEB. */
public record ValorEscala(String valor, String nombre, String descripcion, int orden, boolean exigeConclusion) {
}
