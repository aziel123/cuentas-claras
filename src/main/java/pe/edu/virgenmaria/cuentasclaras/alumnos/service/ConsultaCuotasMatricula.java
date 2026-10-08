package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

/**
 * Puerto: ¿esta matrícula ya tiene cuotas? Lo define {@code alumnos} y lo implementará {@code cobranza} (tanda 3),
 * así {@code alumnos} no depende de {@code cobranza}. Mientras no haya implementación, ninguna matrícula tiene cuotas.
 */
public interface ConsultaCuotasMatricula {

	boolean tieneCuotas(Long matriculaId);
}
