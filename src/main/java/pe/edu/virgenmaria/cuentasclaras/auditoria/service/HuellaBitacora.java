package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * "Huella" de la bitácora: un evento de la cadena (número y primeros 16 caracteres de su hash) que Promotoría
 * anota fuera del sistema. Si en una verificación posterior ese evento ya no está, o su código cambió, la
 * bitácora fue recortada o alterada, aunque el resto de la cadena calce (alguien con acceso a la base puede
 * borrar los últimos eventos y retroceder el eslabón).
 *
 * @param ocurridoEn cuándo ocurrió el evento ({@code null} si la huella la escribió una persona)
 */
public record HuellaBitacora(long secuencia, String codigo, LocalDateTime ocurridoEn) implements Serializable {

	public static final int LARGO_CODIGO = 16;

	public HuellaBitacora {
		if (secuencia < 1) {
			throw new ReglaNegocioException("El número de evento de la huella debe ser mayor que cero.");
		}
		codigo = codigo == null ? "" : codigo.strip().toLowerCase(Locale.ROOT);
		if (!codigo.matches("[0-9a-f]{" + LARGO_CODIGO + "}")) {
			throw new ReglaNegocioException("El código de la huella son " + LARGO_CODIGO
					+ " caracteres: números del 0 al 9 y letras de la a a la f.");
		}
	}

	/** Huella de un evento recién sellado. */
	static HuellaBitacora de(long secuencia, String hash, LocalDateTime ocurridoEn) {
		return new HuellaBitacora(secuencia, hash.substring(0, LARGO_CODIGO), ocurridoEn);
	}

	/** La que escribe Promotoría en el formulario: ambos campos o ninguno. */
	public static HuellaBitacora anotada(Long secuencia, String codigo) {
		boolean sinCodigo = codigo == null || codigo.isBlank();
		if (secuencia == null && sinCodigo) {
			return null;
		}
		if (secuencia == null || sinCodigo) {
			throw new ReglaNegocioException("Para comparar con tu huella escribe el número de evento y el código.");
		}
		return new HuellaBitacora(secuencia, codigo, null);
	}

	boolean coincideCon(String hash) {
		return hash != null && hash.toLowerCase(Locale.ROOT).startsWith(codigo);
	}
}
