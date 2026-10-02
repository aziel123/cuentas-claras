package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import java.io.Serial;
import java.io.Serializable;

/**
 * Un cambio que hará la importación sobre algo ya registrado. Documento, celular y correo van enmascarados.
 *
 * @param requiereSolicitud la importación NO lo aplica (celular, correo o responsable de pago de lo ya registrado):
 *                          se pide desde la ficha y lo aprueba otra persona (auditoría A4)
 */
public record CambioFila(String campo, String antes, String despues, boolean requiereSolicitud) implements Serializable {

	@Serial
	private static final long serialVersionUID = 2L;

	public CambioFila(String campo, String antes, String despues) {
		this(campo, antes, despues, false);
	}
}
