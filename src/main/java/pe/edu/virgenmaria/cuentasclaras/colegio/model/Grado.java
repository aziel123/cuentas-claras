package pe.edu.virgenmaria.cuentasclaras.colegio.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo nacional de grados de la EBR (no es una tabla: es igual para todos los colegios).
 * Por colegio y año solo se crean secciones. El orden de declaración es el orden escolar.
 */
public enum Grado {

	INICIAL_3(Nivel.INICIAL, 3, 3),
	INICIAL_4(Nivel.INICIAL, 4, 4),
	INICIAL_5(Nivel.INICIAL, 5, 5),
	PRIMARIA_1(Nivel.PRIMARIA, 1, 6),
	PRIMARIA_2(Nivel.PRIMARIA, 2, 7),
	PRIMARIA_3(Nivel.PRIMARIA, 3, 8),
	PRIMARIA_4(Nivel.PRIMARIA, 4, 9),
	PRIMARIA_5(Nivel.PRIMARIA, 5, 10),
	PRIMARIA_6(Nivel.PRIMARIA, 6, 11),
	SECUNDARIA_1(Nivel.SECUNDARIA, 1, 12),
	SECUNDARIA_2(Nivel.SECUNDARIA, 2, 13),
	SECUNDARIA_3(Nivel.SECUNDARIA, 3, 14),
	SECUNDARIA_4(Nivel.SECUNDARIA, 4, 15),
	SECUNDARIA_5(Nivel.SECUNDARIA, 5, 16);

	private final Nivel nivel;

	private final int numero;

	private final int edadNormativa;

	Grado(Nivel nivel, int numero, int edadNormativa) {
		this.nivel = nivel;
		this.numero = numero;
		this.edadNormativa = edadNormativa;
	}

	public Nivel nivel() {
		return nivel;
	}

	/** En inicial, la edad (3, 4 o 5); en primaria y secundaria, el grado (1.°, 2.°…). */
	public int numero() {
		return numero;
	}

	/** Edad cumplida al 31 de marzo que corresponde al grado. */
	public int edadNormativa() {
		return edadNormativa;
	}

	/** «Inicial 3 años», «5.° Primaria», «1.° Secundaria». */
	public String etiqueta() {
		return nivel == Nivel.INICIAL ? "Inicial " + numero + " años" : numero + ".° " + nivel.etiqueta();
	}

	/** El grado del año siguiente (renovación de matrícula). Secundaria 5 no tiene siguiente. */
	public Optional<Grado> siguiente() {
		Grado[] grados = values();
		return ordinal() + 1 < grados.length ? Optional.of(grados[ordinal() + 1]) : Optional.empty();
	}

	public static Optional<Grado> de(Nivel nivel, int numero) {
		return Arrays.stream(values()).filter(g -> g.nivel == nivel && g.numero == numero).findFirst();
	}
}
