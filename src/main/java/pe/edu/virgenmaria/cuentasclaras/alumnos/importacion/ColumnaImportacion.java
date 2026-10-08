package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import pe.edu.virgenmaria.cuentasclaras.comun.excel.ColumnaPlantilla;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.Columnas;

import java.util.List;

/**
 * Las 17 columnas de la hoja «Alumnos», en orden (A a Q). Solo datos mínimos (Ley 29733): ni dirección, ni sexo,
 * ni salud. El apoderado es el responsable de pago; el segundo apoderado se agrega en la ficha de la familia.
 */
public enum ColumnaImportacion {

	ALUMNO_TIPO_DOCUMENTO("Tipo de documento del alumno", true, List.of("DNI", "CE", "Pasaporte"),
			"DNI, CE (carné de extranjería) o Pasaporte.", 14),
	ALUMNO_NUMERO_DOCUMENTO("N.° de documento del alumno", true, List.of(),
			"El DNI tiene 8 dígitos. La columna tiene formato Texto para no perder el cero inicial.", 16),
	ALUMNO_APELLIDO_PATERNO("Apellido paterno del alumno", false, List.of(), "Obligatorio.", 20),
	ALUMNO_APELLIDO_MATERNO("Apellido materno del alumno", false, List.of(), "Opcional.", 20),
	ALUMNO_NOMBRES("Nombres del alumno", false, List.of(), "Obligatorio.", 22),
	ALUMNO_FECHA_NACIMIENTO("Fecha de nacimiento (dd/mm/aaaa)", true, List.of(),
			"Día/mes/año con el año de 4 dígitos, por ejemplo 05/03/2015.", 16),
	NIVEL("Nivel", false, List.of("Inicial", "Primaria", "Secundaria"), "Inicial, Primaria o Secundaria.", 12),
	GRADO("Grado (número)", false, List.of("1", "2", "3", "4", "5", "6"),
			"En Inicial, la edad (3, 4 o 5). En Primaria, de 1 a 6. En Secundaria, de 1 a 5.", 10),
	SECCION("Sección", true, List.of(), "Como está creada en Colegio, por ejemplo A.", 10),
	APODERADO_TIPO_DOCUMENTO("Tipo de documento del apoderado", true, List.of("DNI", "CE", "Pasaporte"),
			"DNI, CE (carné de extranjería) o Pasaporte.", 14),
	APODERADO_NUMERO_DOCUMENTO("N.° de documento del apoderado", true, List.of(),
			"Del responsable de pago. Los hermanos llevan el mismo apoderado: así quedan en la misma familia.", 16),
	APODERADO_APELLIDO_PATERNO("Apellido paterno del apoderado", false, List.of(), "Obligatorio.", 20),
	APODERADO_APELLIDO_MATERNO("Apellido materno del apoderado", false, List.of(), "Opcional.", 20),
	APODERADO_NOMBRES("Nombres del apoderado", false, List.of(), "Obligatorio.", 22),
	PARENTESCO("Parentesco", false, List.of("Madre", "Padre", "Abuelo o abuela", "Tío o tía", "Hermano o hermana",
			"Tutor legal", "Otro"), "Del apoderado con el alumno.", 16),
	CELULAR("Celular para WhatsApp", true, List.of(),
			"9 dígitos, por ejemplo 987654321. Del extranjero: con + y el código del país. Celular o correo: al menos uno.",
			16),
	CORREO("Correo del apoderado", false, List.of(), "Opcional si hay celular.", 26);

	private final String encabezado;

	private final boolean texto;

	private final List<String> lista;

	private final String ayuda;

	private final int ancho;

	ColumnaImportacion(String encabezado, boolean texto, List<String> lista, String ayuda, int ancho) {
		this.encabezado = encabezado;
		this.texto = texto;
		this.lista = lista;
		this.ayuda = ayuda;
		this.ancho = ancho;
	}

	public String encabezado() {
		return encabezado;
	}

	/** Letra de la columna en la hoja: A, B… */
	public String letra() {
		return Columnas.letra(ordinal());
	}

	public ColumnaPlantilla paraPlantilla() {
		return new ColumnaPlantilla(encabezado, texto, lista, ayuda, ancho);
	}
}
