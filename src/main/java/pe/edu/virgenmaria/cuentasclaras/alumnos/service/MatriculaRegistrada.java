package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

/**
 * Se publica al registrar una matrícula, dentro de su transacción. En la tanda 3, {@code cobranza} lo escucha con un
 * {@code @EventListener} síncrono para generar el cronograma: si la generación falla, la matrícula tampoco queda.
 */
public record MatriculaRegistrada(Long matriculaId) {
}
