package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

/**
 * Puerto (sprint 6, tanda 2): si un celular o un correo es de un apoderado del colegio. Lo implementa {@code alumnos},
 * así {@code seguridad} no depende de las familias. El contacto del personal no puede ser el de una familia: el resumen,
 * la huella y las alertas llegarían a alguien de afuera.
 */
public interface ContactosDeFamilias {

	/** Compara la forma NORMALIZADA ({@code ContactoNormal}): un alias o un formato distinto es el mismo contacto. */
	boolean esDeUnApoderado(String contacto);
}
