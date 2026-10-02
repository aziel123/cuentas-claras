package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import java.io.Serial;
import java.io.Serializable;

/** Un cambio que hará la importación sobre algo ya registrado. Documento, celular y correo van enmascarados. */
public record CambioFila(String campo, String antes, String despues) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
}
