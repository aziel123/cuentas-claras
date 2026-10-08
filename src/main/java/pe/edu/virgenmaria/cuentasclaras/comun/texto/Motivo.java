package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/** Motivo de una acción sensible: de 10 a 500 caracteres. Se valida también en el servicio (el formulario se puede saltar). */
public final class Motivo {

	public static final int MINIMO = 10;

	public static final int MAXIMO = 500;

	private Motivo() {
	}

	public static String exigir(String motivo) {
		String texto = Normalizador.limpiar(motivo);
		if (texto == null || texto.length() < MINIMO || texto.length() > MAXIMO) {
			throw new ReglaNegocioException("El motivo debe tener entre " + MINIMO + " y " + MAXIMO + " caracteres.");
		}
		return TextoSeguro.exigir(texto, "el motivo");
	}
}
