package pe.edu.virgenmaria.cuentasclaras.panel.dto;

import java.util.List;

/**
 * La llamada de control de esta semana (sprint 6, tanda 3; sección 12.1, pantalla 5). Las fechas y montos ya vienen con
 * su formato. Los pagos registrados de cada familia van aparte, para mostrarlos solo DESPUÉS de que la familia dijo
 * cuánto y cuándo pagó.
 *
 * @param semana   el lunes («19/04/2027»)
 * @param desde    desde cuándo se miran los pagos («15/03/2027»)
 */
public record LlamadasSemana(String semana, String desde, int esperadas, int hechas, List<Familia> familias) {

	public LlamadasSemana {
		familias = List.copyOf(familias);
	}

	public int faltan() {
		return Math.max(0, esperadas - hechas);
	}

	/** Una familia de la muestra; {@code registrada} es {@code null} mientras no se registre la llamada. */
	public record Familia(Long familiaId, String nombre, List<Contacto> contactos, boolean sinPortal,
			boolean unSoloApoderado, List<Pago> pagos, Registrada registrada) {

		public Familia {
			contactos = List.copyOf(contactos);
			pagos = List.copyOf(pagos);
		}

		public boolean pendiente() {
			return registrada == null;
		}
	}

	public record Contacto(String nombre, String parentesco, String celular) {
	}

	/** Un pago registrado: «15/04/2027», «Efectivo», «S/ 350.00», «Vigente» o «Anulado», «B001-00000012», quién. */
	public record Pago(String fecha, String medio, String monto, String estado, boolean anulado, String comprobante,
			String registradoPor) {
	}

	public record Registrada(String resultado, String variante, String nota, String por, String en) {
	}
}
