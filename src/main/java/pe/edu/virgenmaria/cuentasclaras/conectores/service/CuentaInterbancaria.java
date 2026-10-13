package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;

import java.util.Map;

/**
 * Correcciones del sprint 8 (S8-M3): el Código de Cuenta Interbancario (CCI) de 20 dígitos: entidad (3), oficina (3),
 * cuenta (12) y dos dígitos de control. Se comprueba que la entidad sea la del banco elegido y los dos dígitos de control
 * (el primero sobre entidad y oficina, el segundo sobre la cuenta): factores 1 y 2 alternados desde la izquierda, cada
 * producto de dos cifras se reduce a la suma de sus cifras y el dígito es lo que falta para llegar a la decena siguiente
 * (0 si la suma ya es múltiplo de 10). Así un CCI con un dígito mal copiado (o de otro banco) no llega a aprobarse.
 * Residual: el algoritmo es el que publican los bancos para validar el CCI; si un banco usara otro, su cuenta se registra
 * con el proveedor OTRO después de confirmarlo con el banco (sección «Riesgos residuales» de las correcciones).
 */
public final class CuentaInterbancaria {

	/** El código de entidad del CCI de cada banco con proveedor propio. */
	public static final Map<ProveedorConector, String> ENTIDADES = Map.of(ProveedorConector.BCP, "002",
			ProveedorConector.INTERBANK, "003", ProveedorConector.SCOTIABANK, "009", ProveedorConector.BBVA, "011");

	private CuentaInterbancaria() {
	}

	/** Lanza {@link ReglaNegocioException} si el CCI no es de ese banco o sus dígitos de control no corresponden. */
	public static void validar(String cci, ProveedorConector banco) {
		String motivo = problema(cci, banco);
		if (motivo != null) {
			throw new ReglaNegocioException(motivo);
		}
	}

	/** El problema del CCI para ese banco, o {@code null} si es válido. */
	public static String problema(String cci, ProveedorConector banco) {
		if (cci == null || !cci.matches("[0-9]{20}")) {
			return "El CCI tiene 20 dígitos.";
		}
		String entidad = cci.substring(0, 3);
		String esperada = ENTIDADES.get(banco);
		if (esperada != null && !esperada.equals(entidad)) {
			return "Ese CCI no es de " + banco.etiqueta() + ": sus 3 primeros dígitos deben ser " + esperada + ".";
		}
		if (esperada == null && ENTIDADES.containsValue(entidad)) {
			return "Ese CCI es de un banco de la lista: elige ese banco en lugar de «Otro banco».";
		}
		int control1 = digitoControl(cci.substring(0, 6));
		int control2 = digitoControl(cci.substring(6, 18));
		if (cci.charAt(18) - '0' != control1 || cci.charAt(19) - '0' != control2) {
			return "Los dígitos de control del CCI no corresponden: revisa que esté bien copiado (cópialo de la constancia "
					+ "del banco).";
		}
		return null;
	}

	/** El dígito de control de un tramo del CCI. */
	static int digitoControl(String tramo) {
		int suma = 0;
		for (int i = 0; i < tramo.length(); i++) {
			int producto = (tramo.charAt(i) - '0') * (i % 2 == 0 ? 1 : 2);
			suma += producto >= 10 ? producto / 10 + producto % 10 : producto;
		}
		int resto = suma % 10;
		return resto == 0 ? 0 : 10 - resto;
	}

	/** Un CCI válido para esa entidad, oficina y cuenta (para las pruebas y los datos de ejemplo). */
	public static String con(String entidad, String oficina, String cuenta) {
		String base = entidad + oficina;
		return base + cuenta + digitoControl(base) + digitoControl(cuenta);
	}
}
