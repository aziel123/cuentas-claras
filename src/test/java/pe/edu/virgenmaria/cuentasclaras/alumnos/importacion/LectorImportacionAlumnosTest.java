package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.LectorXlsxSeguro;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.PropiedadesExcel;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.XlsxDePrueba;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.archivo;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.con;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.mateo;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.sebastian;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.valeria;

/** Lectura de la plantilla llena: mismas reglas que los formularios, más las reglas entre filas. */
class LectorImportacionAlumnosTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 2);

	private final PropiedadesExcel propiedades = PropiedadesExcel.porDefecto();

	private final LectorImportacionAlumnos lector = new LectorImportacionAlumnos(new LectorXlsxSeguro(propiedades),
			propiedades);

	@Test
	void filasValidasDeLaPlantillaSeLeenNormalizadas() {
		LecturaImportacion lectura = lector.leer(archivo(mateo(), valeria(), sebastian()), 2026, HOY);

		assertThat(lectura.filas()).allMatch(FilaImportacion::valida);
		FilaImportacion primera = lectura.filas().getFirst();
		assertThat(primera.fila()).isEqualTo(2);
		assertThat(primera.alumno().documento().numero()).isEqualTo("78451236");
		assertThat(primera.alumno().fechaNacimiento()).isEqualTo(LocalDate.of(2015, 6, 14));
		assertThat(primera.grado()).isEqualTo(Grado.PRIMARIA_5);
		assertThat(primera.apoderado().telefonoWhatsapp()).isEqualTo("+51987654321");
		assertThat(primera.apoderado().parentesco()).isEqualTo(Parentesco.MADRE);
		assertThat(lectura.filas().get(2).grado()).isEqualTo(Grado.SECUNDARIA_1);
		assertThat(lectura.avisos()).isEmpty();
	}

	@Test
	void encabezadosDistintosALaPlantillaSonRechazados() {
		byte[] otro = XlsxDePrueba.libro("Alumnos", List.of(List.of("DNI", "Nombre"), List.of("78451236", "Mateo")));
		assertThatThrownBy(() -> lector.leer(otro, 2026, HOY))
				.isInstanceOf(ArchivoNoValidoException.class)
				.hasMessageContaining("en la columna A esperábamos «Tipo de documento del alumno» y dice «DNI»");
		byte[] sinHoja = XlsxDePrueba.libro("Hoja1", List.of(List.of("x")));
		assertThatThrownBy(() -> lector.leer(sinHoja, 2026, HOY)).hasMessageContaining("no tiene la hoja «Alumnos»");
		assertThatThrownBy(() -> lector.leer(ArchivoImportacion.PLANTILLA, 2026, HOY))
				.hasMessageContaining("no tiene alumnos");
	}

	@Test
	void filaConFormulaSeReportaConFilaYColumna() {
		LecturaImportacion lectura = lector.leer(archivo(mateo(),
				con(valeria(), 2, new XlsxDePrueba.Formula("\"Quis\"&\"pe\""))), 2026, HOY);

		assertThat(lectura.filas().get(0).valida()).isTrue();
		assertThat(lectura.filas().get(1).errores()).singleElement()
				.satisfies(e -> assertThat(e.texto()).isEqualTo("Fila 3, columna C (Apellido paterno del alumno): tiene "
						+ "una fórmula: escribe el valor (o usa Pegado especial → Valores)."));
	}

	/** QA (mutación): no solo «=»; también «+», «-» y «@» al inicio de un texto se reportan (inyección en Excel). */
	@Test
	void celdaQueEmpiezaConMasMenosOArrobaSeReporta() {
		LecturaImportacion lectura = lector.leer(archivo(con(mateo(), 2, "+Quispe"), con(valeria(), 2, "-Quispe"),
				con(sebastian(), 2, "@Flores")), 2026, HOY);

		assertThat(lectura.filas()).allSatisfy(f -> assertThat(f.errores()).singleElement()
				.satisfies(e -> assertThat(e.texto()).contains("columna C (Apellido paterno del alumno)")));
	}

	@Test
	void dniGuardadoComoNumeroConCeroPerdidoSeExplica() {
		LecturaImportacion lectura = lector.leer(archivo(con(mateo(), 1, 1234567), con(valeria(), 1, 80127745)), 2026,
				HOY);

		assertThat(lectura.filas().get(0).errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("perdió el cero inicial («1234567»)",
						"formatea la columna como Texto"));
		// Un DNI de 8 dígitos guardado como número sí se acepta.
		assertThat(lectura.filas().get(1).valida()).isTrue();
		assertThat(lectura.filas().get(1).alumno().documento().numero()).isEqualTo("80127745");
	}

	@Test
	void fecha29DeFebreroDe2015SeReporta() {
		LecturaImportacion lectura = lector.leer(archivo(con(mateo(), 5, "29/02/2015"), con(valeria(), 5, 43346)), 2026,
				HOY);

		assertThat(lectura.filas().get(0).errores()).singleElement()
				.satisfies(e -> assertThat(e.texto()).startsWith("Fila 2, columna F (Fecha de nacimiento (dd/mm/aaaa)): "
						+ "la fecha «29/02/2015» no es válida"));
		// Una fecha de Excel (serial) también vale: 43346 = 03/09/2018.
		assertThat(lectura.filas().get(1).alumno().fechaNacimiento()).isEqualTo(LocalDate.of(2018, 9, 3));
	}

	@Test
	void gradoInexistenteParaElNivelSeReporta() {
		LecturaImportacion lectura = lector.leer(archivo(con(mateo(), 7, "7"), con(valeria(), 6, "Universidad")), 2026,
				HOY);

		assertThat(lectura.filas().get(0).errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).isEqualTo("Primaria no tiene el grado «7». Escribe un número de 1 a 6."));
		assertThat(lectura.filas().get(1).errores()).extracting(ErrorFila::columna).containsExactly("G");
	}

	@Test
	void alumnoRepetidoEnElArchivoSeReportaEnAmbasFilas() {
		LecturaImportacion lectura = lector.leer(archivo(mateo(), valeria(), con(mateo(), 8, "B")), 2026, HOY);

		assertThat(lectura.filas().get(0).errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("DNI 78451236", "repetido en las filas 2 y 4"));
		assertThat(lectura.filas().get(1).valida()).isTrue();
		assertThat(lectura.filas().get(2).errores()).hasSize(1);
	}

	@Test
	void apoderadoConDatosDistintosEnDosFilasSeReporta() {
		LecturaImportacion lectura = lector.leer(archivo(mateo(), con(valeria(), 15, "999888777")), 2026, HOY);

		assertThat(lectura.filas()).allSatisfy(f -> assertThat(f.errores()).singleElement()
				.satisfies(e -> assertThat(e.texto()).contains("columna K", "DNI 45678912",
						"datos distintos en las filas 2 y 3")));
	}

	@Test
	void edadQueNoCorrespondeAlGradoEsAdvertencia() {
		LecturaImportacion lectura = lector.leer(archivo(con(mateo(), 6, "Secundaria")), 2026, HOY);
		FilaImportacion fila = lectura.filas().getFirst();

		assertThat(fila.valida()).isTrue();
		assertThat(fila.advertencias()).singleElement().asString().contains("Tendrá 10 años", "5.° Secundaria");
	}

	@Test
	void columnasExtraSeIgnoranYSeAvisa() {
		List<Object> conDireccion = new java.util.ArrayList<>(mateo());
		conDireccion.add("Av. Siempre Viva 123");
		byte[] libro = XlsxDePrueba.llenar(archivo(conDireccion), "Alumnos", 0,
				List.of(java.util.Arrays.asList((Object[]) encabezadosMas("Dirección"))));

		LecturaImportacion lectura = lector.leer(libro, 2026, HOY);

		assertThat(lectura.avisos()).containsExactly("Se ignoró la columna R: no es parte de la plantilla y el sistema "
				+ "no guarda esos datos.");
		assertThat(lectura.filas().getFirst().valida()).isTrue();
	}

	@Test
	void erroresEnEspanolConFilaColumnaYValor() {
		LecturaImportacion lectura = lector.leer(archivo(con(mateo(), 1, "1234567"), con(sebastian(), 15, "014567890"),
				con(con(valeria(), 15, null), 16, null), con(sebastian(), 1, "=1+1")), 2026, HOY);

		assertThat(lectura.filas().get(0).errores()).singleElement().extracting(ErrorFila::texto)
				.isEqualTo("Fila 2, columna B (N.° de documento del alumno): el DNI debe tener 8 dígitos; escribiste "
						+ "«1234567». Si el DNI empieza con 0, revisa que no se haya perdido el cero inicial (en Excel, "
						+ "formatea la columna como Texto).");
		assertThat(lectura.filas().get(1).errores()).singleElement().extracting(ErrorFila::texto).asString()
				.startsWith("Fila 3, columna P (Celular para WhatsApp): escribe un celular de 9 dígitos");
		assertThat(lectura.filas().get(2).errores()).singleElement().extracting(ErrorFila::mensaje).asString()
				.contains("celular para WhatsApp o el correo");
		assertThat(lectura.filas().get(3).errores()).extracting(ErrorFila::mensaje)
				.contains("No puede empezar con =, +, - ni @.");
	}

	private static String[] encabezadosMas(String extra) {
		String[] encabezados = new String[ColumnaImportacion.values().length + 1];
		for (ColumnaImportacion c : ColumnaImportacion.values()) {
			encabezados[c.ordinal()] = c.encabezado();
		}
		encabezados[encabezados.length - 1] = extra;
		return encabezados;
	}
}
