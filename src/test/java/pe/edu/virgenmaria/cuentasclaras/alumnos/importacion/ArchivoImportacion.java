package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import pe.edu.virgenmaria.cuentasclaras.comun.excel.PlantillaXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.XlsxDePrueba;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Archivos de importación hechos sobre la PLANTILLA OFICIAL (como los llenaría el colegio). Datos ficticios.
 * Con el reloj de pruebas hoy es 02/10/2026 y el año escolar es 2026.
 */
public final class ArchivoImportacion {

	public static final byte[] PLANTILLA = new PlantillaImportacionAlumnos(new PlantillaXlsx()).generar();

	private ArchivoImportacion() {
	}

	/** Una fila de 17 columnas (A a Q). Tipo de documento: DNI para alumno y apoderado. */
	public static List<Object> fila(String dniAlumno, String paterno, String materno, String nombres, Object nacimiento,
			String nivel, Object grado, String seccion, String dniApoderado, String paternoApoderado,
			String maternoApoderado, String nombresApoderado, String parentesco, Object celular, String correo) {
		return new ArrayList<>(Arrays.asList("DNI", dniAlumno, paterno, materno, nombres, nacimiento, nivel, grado, seccion,
				"DNI", dniApoderado, paternoApoderado, maternoApoderado, nombresApoderado, parentesco, celular, correo));
	}

	public static List<Object> mateo() {
		return fila("78451236", "Quispe", "Huamán", "Mateo", "14/06/2015", "Primaria", "5", "A", "45678912", "Huamán",
				"Ccori", "Rosa", "Madre", "987654321", "rosa.huaman@gmail.com");
	}

	public static List<Object> valeria() {
		return fila("80127745", "Quispe", "Huamán", "Valeria", "03/09/2018", "Primaria", "2", "B", "45678912", "Huamán",
				"Ccori", "Rosa", "Madre", "987654321", "rosa.huaman@gmail.com");
	}

	public static List<Object> sebastian() {
		return fila("75330981", "Flores", "Rojas", "Sebastián", "21/08/2013", "Secundaria", "1", "A", "41235678",
				"Flores", "Díaz", "Pedro", "Padre", "912345678", null);
	}

	/** Copia de la fila con la columna (0 = A) cambiada. */
	public static List<Object> con(List<Object> fila, int columna, Object valor) {
		List<Object> copia = new ArrayList<>(fila);
		copia.set(columna, valor);
		return copia;
	}

	@SafeVarargs
	public static byte[] archivo(List<Object>... filas) {
		return XlsxDePrueba.llenar(PLANTILLA, PlantillaImportacionAlumnos.HOJA, 1, List.of(filas));
	}
}
