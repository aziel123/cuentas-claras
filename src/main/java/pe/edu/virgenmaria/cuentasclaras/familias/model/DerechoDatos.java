package pe.edu.virgenmaria.cuentasclaras.familias.model;

import pe.edu.virgenmaria.cuentasclaras.comun.fecha.DiasHabiles;

import java.time.LocalDate;

/**
 * Lo que pide una familia sobre sus datos personales (sprint 7, tanda 3; Ley 29733, sección 8.3). Los nombres coinciden
 * con el CHECK {@code ck_aviso_familia_derecho} de V26. Los plazos (días hábiles desde que se recibe) son configurables y
 * los confirma el asesor legal (decisión 97): 20 para el acceso y 10 para los demás.
 */
public enum DerechoDatos {

	ACCESO("Quiero saber qué datos míos y de mis hijos tiene el colegio", "Acceso", true),
	RECTIFICACION("Quiero corregir un dato que está mal", "Rectificación", false),
	CANCELACION("Quiero que dejen de guardar mis datos", "Cancelación", false),
	OPOSICION("No quiero que usen mis datos para algo", "Oposición", false);

	private final String etiqueta;

	private final String nombre;

	private final boolean acceso;

	DerechoDatos(String etiqueta, String nombre, boolean acceso) {
		this.etiqueta = etiqueta;
		this.nombre = nombre;
		this.acceso = acceso;
	}

	/** Cómo lo elige la familia en el portal. */
	public String etiqueta() {
		return etiqueta;
	}

	/** El nombre del derecho, para el personal. */
	public String nombre() {
		return nombre;
	}

	public int plazoDias(int plazoAcceso, int plazoOtros) {
		return acceso ? plazoAcceso : plazoOtros;
	}

	/** El último día hábil para responder un pedido recibido ese día. */
	public LocalDate vence(LocalDate recibido, DiasHabiles calendario, int plazoAcceso, int plazoOtros) {
		return calendario.sumarHabiles(recibido, plazoDias(plazoAcceso, plazoOtros));
	}
}
