package pe.edu.virgenmaria.cuentasclaras.caja.dto;

/**
 * Sprint 8, tanda 1 (H8, sección 3.7): el emisor de un comprobante tal como sale impreso: el nombre comercial del
 * colegio, su razón social, su RUC y su dirección (los tres últimos solo si el colegio los registró: sin ellos la boleta
 * sigue saliendo) y si tiene un logo vigente (se sirve en {@code /marca/logo}).
 */
public record EmisorVista(String nombre, String razonSocial, String ruc, String direccion, boolean conLogo) {

	/** Lo que encabeza la boleta: la razón social si existe; si no, el nombre comercial. */
	public String titulo() {
		return razonSocial == null || razonSocial.isBlank() ? nombre : razonSocial;
	}

	/** El nombre comercial debajo de la razón social, solo cuando son distintos. */
	public boolean muestraNombreAparte() {
		return razonSocial != null && !razonSocial.isBlank() && !razonSocial.equals(nombre);
	}
}
