package pe.edu.virgenmaria.cuentasclaras.comun.excel;

/** Letras de columna de Excel: 0 → A, 25 → Z, 26 → AA. */
public final class Columnas {

	private Columnas() {
	}

	public static String letra(int indice) {
		StringBuilder letras = new StringBuilder();
		for (int n = indice + 1; n > 0; n = (n - 1) / 26) {
			letras.insert(0, (char) ('A' + (n - 1) % 26));
		}
		return letras.toString();
	}

	/** «AB12» → 27. -1 si la referencia no es válida. */
	public static int indice(String referencia) {
		if (referencia == null) {
			return -1;
		}
		int indice = 0;
		int i = 0;
		while (i < referencia.length() && Character.isLetter(referencia.charAt(i))) {
			char c = Character.toUpperCase(referencia.charAt(i));
			if (c < 'A' || c > 'Z' || i >= 3) {
				return -1;
			}
			indice = indice * 26 + (c - 'A' + 1);
			i++;
		}
		return i == 0 ? -1 : indice - 1;
	}
}
