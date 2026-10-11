package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

/**
 * Sprint 8, tanda 1 (sección 3.7): la marca del colegio tal como la ve Promotoría en su pantalla: la ficha (el RUC solo
 * se muestra) y el logo vigente, si existe, con sus medidas y su peso.
 */
public record MarcaColegioVista(String nombre, String razonSocial, String ruc, String direccion,
		String correoInstitucional, boolean conLogo, Integer logoAncho, Integer logoAlto, Integer logoBytes) {

	/** El logo en KB para mostrarlo (sin decimales; una medida, no dinero). */
	public Integer logoKb() {
		return logoBytes == null ? null : (int) Math.round(logoBytes / 1024.0);
	}
}
