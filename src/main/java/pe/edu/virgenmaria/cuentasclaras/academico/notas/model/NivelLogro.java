package pe.edu.virgenmaria.cuentasclaras.academico.notas.model;

/**
 * Sprint 10, tanda 2: los valores de la escala del CNEB. Solo sus códigos y el orden (de menor a mayor logro, para saber si
 * una corrección SUBE el nivel: A5); qué exige conclusión lo dice el catálogo ({@code escala_logro}), nunca esta clase.
 */
public enum NivelLogro {

	C, B, A, AD;

	/** Si {@code nuevo} es mayor que {@code anterior}; desde «sin nota» ({@code null}) a cualquier nivel también sube. */
	public static boolean sube(NivelLogro anterior, NivelLogro nuevo) {
		return nuevo != null && (anterior == null || nuevo.ordinal() > anterior.ordinal());
	}

	public static NivelLogro de(String valor) {
		return valor == null || valor.isBlank() ? null : NivelLogro.valueOf(valor.trim());
	}
}
