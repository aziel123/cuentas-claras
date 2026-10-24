package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.CalificacionesSiagie;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 10, tanda 3 (secciones 3.10 y 3.13; A6, E17): la exportación de calificaciones para el SIAGIE sale de la libreta
 * VIGENTE de cada matrícula (lo mismo que recibió la familia), se rechaza mientras una corrección aprobada no llegó a su
 * libreta y trae la hoja «Control». Cada descarga queda registrada (sin datos de alumnos) y en «Quién vio datos personales».
 */
class CalificacionesSiagieTest extends PruebaLibretas {

	@Autowired
	private CalificacionesSiagie calificaciones;

	@Test
	void saleDeLaLibretaVigente() throws IOException {
		cerrarYEmitir();
		corregir(lucas, competencias("MAT").get(0), "AD", null);
		emitir();
		CalificacionesSiagie.Archivo archivo = como(EscenarioCobranza.DIRECCION, () -> calificaciones.exportar(seccion,
				periodo));
		assertThat(archivo.nombreArchivo()).startsWith("PROVISIONAL-calificaciones-primaria_5-A-p1-CAL-")
				.endsWith(".xlsx");
		assertThat(archivo.filas()).isEqualTo(3);
		try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo.contenido()))) {
			Sheet matematica = libro.getSheet("Matemática");
			assertThat(matematica).isNotNull();
			assertThat(celda(matematica, 0, 0)).startsWith("PROVISIONAL").contains(archivo.codigo()).contains("director");
			List<String> lucasFila = fila(matematica, "Flores Rojas, Lucas");
			// La primera competencia de Matemática: AD (la corregida, de su libreta versión 2); la de Mateo, A.
			assertThat(lucasFila.get(4)).isEqualTo("AD");
			assertThat(fila(matematica, "Quispe Huamán, Mateo").get(4)).isEqualTo("A");
			assertThat(libro.getSheet("Transversales")).isNotNull();
		}
		assertThat(jdbc.queryForMap("SELECT filas, perfil, creado_por FROM exportacion_calificaciones"))
				.containsEntry("FILAS", 3).containsEntry("PERFIL", "SIAGIE_CALIFICACIONES_PROVISIONAL")
				.containsEntry("CREADO_POR", "director");
		// La huella de las libretas usadas es la de las vigentes (la v2 de Lucas).
		String huella = jdbc.queryForObject("SELECT huella_libretas FROM exportacion_calificaciones", String.class);
		assertThat(huella).matches("[0-9a-f]{64}");
		assertThat(eventos("CALIFICACIONES_SIAGIE_EXPORTADAS")).singleElement().asString().contains("3 alumno(s)")
				.doesNotContain("Lucas");
	}

	/** A6: una corrección aprobada que todavía no llegó a su libreta: la exportación espera (al SIAGIE va lo mismo). */
	@Test
	void correccionSinLibretaRechaza() {
		cerrarYEmitir();
		corregir(lucas, competencias("MAT").get(0), "AD", null);
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> calificaciones.exportar(seccion, periodo)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Espere a que se emitan las libretas corregidas");
		assertThat(contar("SELECT COUNT(*) FROM exportacion_calificaciones")).isZero();
		emitir();
		assertThat(como(EscenarioCobranza.DIRECCION, () -> calificaciones.exportar(seccion, periodo)).filas()).isEqualTo(3);
	}

	/** A6: la hoja «Control» cuenta por competencia los AD, A, B, C, sin nota y EXO. */
	@Test
	void hojaDeControl() throws IOException {
		cerrarYEmitir();
		CalificacionesSiagie.Archivo archivo = como(EscenarioCobranza.DIRECCION, () -> calificaciones.exportar(seccion,
				periodo));
		try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo.contenido()))) {
			Sheet control = libro.getSheet("Control");
			assertThat(control).isNotNull();
			assertThat(fila(control, 1)).containsExactly("Área", "Competencia", "AD", "A", "B", "C", "Sin nota", "EXO");
			List<String> cantidad = filaQueEmpiezaCon(control, "Matemática");
			assertThat(cantidad.subList(2, 8)).containsExactly("0", "3", "0", "0", "0", "0");
			List<String> comunicacion = filaQueEmpiezaCon(control, "Comunicación");
			assertThat(comunicacion.get(6)).as("sin nota").isEqualTo("3");
		}
	}

	/** El periodo abierto no se exporta; la descarga por la pantalla registra el acceso con la cantidad. */
	@Test
	void periodoAbiertoNoYLaDescargaQuedaRegistrada() throws Exception {
		anaPoneA();
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> calificaciones.exportar(seccion, periodo)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no está cerrado");
		cerrarPeriodo();
		emitir();
		MockHttpServletResponse r = mvc.perform(get("/academico/siagie/calificaciones").param("seccion", seccion.toString())
				.param("periodo", periodo.toString()).with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION))).andReturn()
				.getResponse();
		assertThat(r.getStatus()).isEqualTo(200);
		assertThat(r.getHeader("Content-Disposition")).contains("PROVISIONAL-calificaciones");
		assertThat(contar("SELECT COUNT(*) FROM acceso_dato_personal WHERE tipo = 'CALIFICACIONES_SIAGIE'")).isEqualTo(1);
		// El docente no exporta (403).
		assertThat(mvc.perform(get("/academico/siagie/calificaciones").param("seccion", seccion.toString())
				.param("periodo", periodo.toString()).with(UsuariosDePrueba.como(UsuariosDePrueba.autenticado(tito))))
				.andReturn().getResponse().getStatus()).isEqualTo(403);
	}

	private static String celda(Sheet hoja, int fila, int columna) {
		Row r = hoja.getRow(fila);
		return r == null || r.getCell(columna) == null ? "" : r.getCell(columna).toString();
	}

	private static List<String> fila(Sheet hoja, int numero) {
		List<String> valores = new ArrayList<>();
		Row r = hoja.getRow(numero);
		for (int c = 0; r != null && c < r.getLastCellNum(); c++) {
			valores.add(r.getCell(c) == null ? "" : r.getCell(c).toString().replaceAll("\\.0$", ""));
		}
		return valores;
	}

	private static List<String> fila(Sheet hoja, String alumno) {
		for (int i = 0; i <= hoja.getLastRowNum(); i++) {
			if (fila(hoja, i).contains(alumno)) {
				return fila(hoja, i);
			}
		}
		throw new AssertionError("No está " + alumno);
	}

	private static List<String> filaQueEmpiezaCon(Sheet hoja, String inicio) {
		for (int i = 0; i <= hoja.getLastRowNum(); i++) {
			List<String> f = fila(hoja, i);
			if (!f.isEmpty() && f.get(0).equals(inicio)) {
				return f;
			}
		}
		throw new AssertionError("No hay fila de " + inicio);
	}
}
