package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.LectorXlsxSeguro;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.PropiedadesExcel;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ValidadorArchivoXlsx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.con;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.fila;

/**
 * Archivos de ejemplo para la demostración al colegio, en {@code docs/ux/}: la plantilla oficial llena con 10 alumnos
 * FICTICIOS y 2 errores a propósito ({@code ejemplo-importacion.xlsx}), y la misma corregida
 * ({@code ejemplo-importacion-corregido.xlsx}). Sirven con los datos de demostración de dev (año 2026, secciones A).
 * <p>
 * Para regenerarlos: {@code ./mvnw test -Dtest=EjemploImportacionTest -Dcuentasclaras.generarEjemplo=true}. Sin esa
 * propiedad, la prueba solo comprueba que los archivos guardados sigan teniendo exactamente esos 2 errores.
 */
class EjemploImportacionTest {

	static final Path EJEMPLO = Path.of("docs", "ux", "ejemplo-importacion.xlsx");

	static final Path CORREGIDO = Path.of("docs", "ux", "ejemplo-importacion-corregido.xlsx");

	private final PropiedadesExcel propiedades = PropiedadesExcel.porDefecto();

	private final LectorImportacionAlumnos lector = new LectorImportacionAlumnos(new LectorXlsxSeguro(propiedades),
			propiedades);

	/** Diez alumnos ficticios de 2026, con dos familias de hermanos. Celulares y correos inventados. */
	static List<List<Object>> alumnos() {
		return List.of(
				fila("73000001", "Ramos", "Torres", "Lucía", "05/03/2015", "Primaria", "5", "A", "43000001", "Torres",
						"Vega", "Carmen", "Madre", "900000001", "carmen.torres@example.com"),
				fila("73000002", "Ramos", "Torres", "Andrés", "12/07/2019", "Primaria", "1", "A", "43000001", "Torres",
						"Vega", "Carmen", "Madre", "900000001", "carmen.torres@example.com"),
				fila("73000003", "Salas", "Ruiz", "Diego", "20/01/2012", "Secundaria", "2", "A", "43000002", "Salas",
						"Peña", "Óscar", "Padre", "900000002", null),
				fila("73000004", "Núñez", "Ccahua", "Valentina", "14/11/2020", "Inicial", "5", "A", "43000003", "Ccahua",
						"Mamani", "Rosa", "Madre", "900000003", null),
				fila("73000005", "Paz", "Ríos", "Matías", "02/05/2016", "Primaria", "4", "A", "43000004", "Ríos", "León",
						"Elena", "Abuelo o abuela", null, "elena.rios@example.com"),
				fila("73000006", "Paz", "Ríos", "Camila", "30/09/2014", "Primaria", "6", "A", "43000004", "Ríos", "León",
						"Elena", "Abuelo o abuela", null, "elena.rios@example.com"),
				fila("73000007", "Vega", "Soto", "Thiago", "08/08/2009", "Secundaria", "5", "A", "43000005", "Vega",
						"Mora", "Jorge", "Padre", "900000005", null),
				fila("73000008", "Huamán", "Quispe", "Ariana", "17/04/2017", "Primaria", "3", "A", "43000006", "Quispe",
						"Apaza", "Nélida", "Madre", "900000006", null),
				fila("73000009", "Chávez", "Lino", "Bruno", "25/06/2021", "Inicial", "4", "A", "43000007", "Lino",
						"Rojas", "Patricia", "Tutor legal", "900000007", "patricia.lino@example.com"),
				fila("73000010", "Mendoza", "Arce", "Sofía", "11/12/2010", "Secundaria", "3", "A", "43000008", "Arce",
						"Vidal", "Luis", "Tío o tía", "+34 612 345 678", null));
	}

	/** Las mismas filas con 2 errores típicos: un DNI al que le falta un dígito y una fecha que no existe. */
	static List<List<Object>> conDosErrores() {
		List<List<Object>> filas = new ArrayList<>(alumnos());
		filas.set(2, con(filas.get(2), 1, "7300003"));
		filas.set(7, con(filas.get(7), 5, "31/02/2017"));
		return filas;
	}

	static byte[] archivo(List<List<Object>> filas) {
		return pe.edu.virgenmaria.cuentasclaras.comun.excel.XlsxDePrueba.llenar(ArchivoImportacion.PLANTILLA,
				PlantillaImportacionAlumnos.HOJA, 1, filas);
	}

	@Test
	void elEjemploTieneExactamenteDosErroresYElCorregidoNinguno() throws IOException {
		if (Boolean.getBoolean("cuentasclaras.generarEjemplo")) {
			Files.write(EJEMPLO, archivo(conDosErrores()));
			Files.write(CORREGIDO, archivo(alumnos()));
		}
		byte[] ejemplo = Files.exists(EJEMPLO) ? Files.readAllBytes(EJEMPLO) : archivo(conDosErrores());
		byte[] corregido = Files.exists(CORREGIDO) ? Files.readAllBytes(CORREGIDO) : archivo(alumnos());
		ValidadorArchivoXlsx validador = new ValidadorArchivoXlsx(propiedades);
		assertThatCode(() -> validador.validar("ejemplo-importacion.xlsx", ejemplo)).doesNotThrowAnyException();

		LecturaImportacion conErrores = lector.leer(ejemplo, 2026, LocalDate.of(2026, 10, 2));
		assertThat(conErrores.filas()).hasSize(10);
		assertThat(conErrores.filas().stream().flatMap(f -> f.errores().stream()).map(ErrorFila::texto))
				.containsExactly(
						"Fila 4, columna B (N.° de documento del alumno): el DNI debe tener 8 dígitos; escribiste "
								+ "«7300003». Si el DNI empieza con 0, revisa que no se haya perdido el cero inicial (en "
								+ "Excel, formatea la columna como Texto).",
						"Fila 9, columna F (Fecha de nacimiento (dd/mm/aaaa)): la fecha «31/02/2017» no es válida: "
								+ "escríbela como dd/mm/aaaa (por ejemplo 05/03/2015) y revisa que exista.");
		assertThat(lector.leer(corregido, 2026, LocalDate.of(2026, 10, 2)).filas()).hasSize(10)
				.allMatch(FilaImportacion::valida);
	}
}
