package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto;

import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.model.PasoPuestaEnMarcha;

import java.util.List;

/**
 * Sprint 11, tanda 1b (sección 3.14, decisión 206): lo que la guía de la puesta en marcha muestra a una persona del
 * personal. Sin datos de familias ni montos: títulos, roles, estados y, si {@code conConteos}, los conteos que ya muestra
 * la lista («Alumnos matriculados: 312»). El docente la recibe sin conteos (decisión 141).
 *
 * @param pendientes           obligatorios pendientes (N de «faltan N de 15 pasos»)
 * @param total                los 15 pasos
 * @param obligatoriosHechos   el valor de la barra (sobre 13)
 * @param opcionalesPendientes «(y 2 opcionales)»
 * @param siguiente            el primer obligatorio pendiente, desbloqueado, que hace el colegio (o nulo)
 * @param tuyo                 si el siguiente no le toca a este rol, el primer paso desbloqueado que sí (o nulo)
 * @param proximos             hasta 3 pasos pendientes después del siguiente (variante ampliada del inicio)
 * @param esperandoPlataforma  los pasos 10, 11 y 13 pendientes (los hace la plataforma o su operador)
 * @param conConteos           si se muestran los conteos
 * @param verLista             si el rol puede abrir la Puesta en marcha (el enlace «Ver los 15 pasos»)
 */
public record GuiaVista(long pendientes, int total, long obligatoriosHechos, long opcionalesPendientes, PasoGuia siguiente,
		PasoGuia tuyo, List<PasoGuia> proximos, List<PasoGuia> esperandoPlataforma, boolean conConteos, boolean verLista) {

	/** Un paso tal como lo muestra la guía. {@code enlace} es nulo si el rol no puede entrar a esa pantalla. */
	public record PasoGuia(PasoPuestaEnMarcha paso, String accion, String enlace, String conteo, boolean leToca) {

		public String titulo() {
			return paso.titulo();
		}

		public String responsable() {
			return paso.responsable();
		}

		public String ayuda() {
			return paso.ayuda();
		}

		public int numero() {
			return paso.numero();
		}

		/** Hay un botón: el rol hace este paso y puede entrar a la pantalla. */
		public boolean conBoton() {
			return leToca && enlace != null;
		}
	}

	/** «Tu colegio está en preparación: faltan 6 de 15 pasos (y 2 opcionales).» */
	public String textoAvance() {
		String texto = "Tu colegio está en preparación: faltan " + pendientes + " de " + total + " pasos";
		if (opcionalesPendientes > 0) {
			texto += " (y " + opcionalesPendientes + (opcionalesPendientes == 1 ? " opcional)" : " opcionales)");
		}
		return texto + ".";
	}

	/** La etiqueta visible de la barra: «9 de 13 pasos obligatorios hechos». */
	public String etiquetaBarra() {
		return obligatoriosHechos + " de " + PasoPuestaEnMarcha.OBLIGATORIOS + " pasos obligatorios hechos";
	}

	public int maximoBarra() {
		return PasoPuestaEnMarcha.OBLIGATORIOS;
	}

	/** El siguiente le toca a esta persona (con botón). */
	public boolean siguienteEsTuyo() {
		return siguiente != null && siguiente.conBoton();
	}

	/** No queda nada para el colegio: solo esperar a la plataforma. */
	public boolean soloEsperando() {
		return siguiente == null && !esperandoPlataforma.isEmpty();
	}
}
