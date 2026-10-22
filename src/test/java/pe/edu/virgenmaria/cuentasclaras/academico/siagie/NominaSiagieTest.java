package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicados.PruebaComunicados;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 9, tanda 3 (sección 3.11, E34, B14): la nómina provisional de una sección para el SIAGIE. Solo Dirección; cada
 * descarga queda en exportacion_nomina, en la bitácora y en «Quién vio datos personales»; el DNI conserva sus ceros, no
 * hay fórmulas y la primera fila dice «PROVISIONAL» con el usuario y la fecha.
 */
class NominaSiagieTest extends PruebaComunicados {

	@Autowired
	MockMvc mvc;

	private MockHttpServletResponse descargar(pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado quien)
			throws Exception {
		return mvc.perform(get("/academico/siagie/nomina").param("seccion", seccionA.toString())
				.with(UsuariosDePrueba.como(quien))).andReturn().getResponse();
	}

	@Test
	void soloDireccionExporta() throws Exception {
		for (var quien : List.of(EscenarioCobranza.ADMINISTRACION, EscenarioCobranza.PROMOTORIA, EscenarioCobranza.CAJA)) {
			assertThat(descargar(quien).getStatus()).as(quien.getUsername()).isEqualTo(403);
		}
		assertThat(mvc.perform(get("/academico/siagie/nomina").param("seccion", seccionA.toString())
				.with(UsuariosDePrueba.como(tutora))).andReturn().getResponse().getStatus()).isEqualTo(403);
		assertThat(contar("SELECT COUNT(*) FROM exportacion_nomina")).isZero();
		MockHttpServletResponse ok = descargar(EscenarioCobranza.DIRECCION);
		assertThat(ok.getStatus()).isEqualTo(200);
		assertThat(ok.getHeader("Content-Disposition")).contains("PROVISIONAL").contains(".xlsx");
	}

	@Test
	void cadaDescargaQuedaRegistrada() throws Exception {
		descargar(EscenarioCobranza.DIRECCION);
		descargar(EscenarioCobranza.DIRECCION);
		assertThat(contar("SELECT COUNT(*) FROM exportacion_nomina WHERE seccion_id = ? AND filas = 3 AND sesion_id IS NOT "
				+ "NULL", seccionA)).isEqualTo(2);
		assertThat(contar("SELECT COUNT(DISTINCT codigo) FROM exportacion_nomina")).isEqualTo(2);
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'NOMINA_SIAGIE_EXPORTADA'")).isEqualTo(2);
		assertThat(contar("SELECT COUNT(*) FROM acceso_dato_personal WHERE tipo = 'NOMINA_SIAGIE'")).isGreaterThanOrEqualTo(2);
		String sha = jdbc.queryForObject("SELECT sha256 FROM exportacion_nomina ORDER BY id DESC LIMIT 1", String.class);
		assertThat(jdbc.queryForObject("SELECT valor_nuevo FROM evento_auditoria WHERE accion = 'NOMINA_SIAGIE_EXPORTADA' "
				+ "ORDER BY secuencia DESC LIMIT 1", String.class)).isEqualTo(sha);
	}

	@Test
	void elDniConservaSusCerosYNoHayFormulas() throws Exception {
		jdbc.update("UPDATE alumno SET numero_documento = '01234567' WHERE nombres = 'Lucas'");
		jdbc.update("UPDATE alumno SET apellido_materno = '=HYPERLINK(\"http://x\")' WHERE nombres = 'Mateo'");
		List<List<String>> filas = filas(descargar(EscenarioCobranza.DIRECCION).getContentAsByteArray());
		assertThat(filas.stream().flatMap(List::stream)).contains("01234567");
		assertThat(filas.stream().flatMap(List::stream)).noneMatch(c -> c.startsWith("="));
	}

	@Test
	void imprimeUsuarioYFecha() throws Exception {
		List<List<String>> filas = filas(descargar(EscenarioCobranza.DIRECCION).getContentAsByteArray());
		String primera = filas.getFirst().getFirst();
		assertThat(primera).contains("PROVISIONAL").contains(EscenarioCobranza.DIRECCION.getUsername())
				.contains("06/10/2026").contains("código NOM-");
		assertThat(filas.get(1)).contains("Número de documento", "Sexo", "Situación");
		assertThat(filas).hasSize(2 + 3);
	}

	@Test
	void losDatosQueFaltanSeListan() throws Exception {
		String pagina = mvc.perform(get("/academico/siagie").with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION)))
				.andReturn().getResponse().getContentAsString();
		assertThat(pagina).contains("Sin sexo").contains("Falta el código modular");
	}

	private static List<List<String>> filas(byte[] xlsx) throws Exception {
		List<List<String>> filas = new ArrayList<>();
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			Sheet hoja = libro.getSheetAt(0);
			for (Row fila : hoja) {
				List<String> celdas = new ArrayList<>();
				for (Cell celda : fila) {
					assertThat(celda.getCellType()).as("todo como texto, sin fórmulas").isNotEqualTo(CellType.FORMULA);
					celdas.add(celda.getCellType() == CellType.STRING ? celda.getStringCellValue() : celda.toString());
				}
				filas.add(celdas);
			}
		}
		return filas;
	}
}
