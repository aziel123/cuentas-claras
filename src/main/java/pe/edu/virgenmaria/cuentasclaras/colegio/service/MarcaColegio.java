package pe.edu.virgenmaria.cuentasclaras.colegio.service;

/**
 * Sprint 8, tanda 1 (sección 3.7, decisión 123): la marca de un colegio para su portal, su cabecera y sus boletas: nombre
 * comercial, razón social, RUC y dirección del emisor, y si tiene un logo vigente (se sirve en {@code /marca/logo}). Sin
 * colores propios (protege el contraste AA del sistema de diseño).
 */
public record MarcaColegio(Long colegioId, String nombre, String razonSocial, String ruc, String direccion,
		boolean conLogo) {

	/** Lo que la boleta muestra como emisor: la razón social si existe; si no, el nombre comercial. */
	public String emisor() {
		return razonSocial == null || razonSocial.isBlank() ? nombre : razonSocial;
	}
}
