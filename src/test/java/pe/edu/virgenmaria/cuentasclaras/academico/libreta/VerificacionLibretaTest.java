package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.VerificacionVista;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 10, tanda 3 (A4, E16): la verificación de una libreta impresa. El código lleva 16 caracteres de la huella; la
 * pantalla dice si existe, si es la versión vigente y muestra los NIVELES de esa versión (sin conclusiones); cada consulta
 * queda en la bitácora y en «Quién vio datos personales».
 */
class VerificacionLibretaTest extends PruebaLibretas {

	@Test
	void codigoDe16() {
		cerrarYEmitir();
		Long id = libreta(mateo);
		String huella = jdbc.queryForObject("SELECT huella_contenido FROM libreta_emitida WHERE id = ?", String.class, id);
		VerificacionVista v = como(EscenarioCobranza.DIRECCION, () -> verificacion.verificar("LIB-" + id + "-"
				+ huella.substring(0, 16)));
		assertThat(v.codigo()).matches("LIB-\\d+-[0-9a-f]{16}").isEqualTo("LIB-" + id + "-" + huella.substring(0, 16));
		assertThat(v.existe()).isTrue();
		// Con 8 caracteres (o uno cambiado) ya no corresponde.
		assertThat(como(EscenarioCobranza.DIRECCION, () -> verificacion.verificar("LIB-" + id + "-"
				+ huella.substring(0, 8))).existe()).isFalse();
		String cambiado = huella.substring(0, 15) + (huella.charAt(15) == 'a' ? 'b' : 'a');
		assertThat(como(EscenarioCobranza.DIRECCION, () -> verificacion.verificar("LIB-" + id + "-" + cambiado)).existe())
				.isFalse();
		assertThat(como(EscenarioCobranza.DIRECCION, () -> verificacion.verificar("cualquier cosa")).existe()).isFalse();
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'LIBRETA_VERIFICADA'")).isEqualTo(4);
	}

	/** A4: muestra los niveles de esa versión para compararlos con el papel (sin conclusiones). */
	@Test
	void muestraLosNiveles() throws Exception {
		cerrarYEmitir();
		Long id = libreta(mateo);
		String codigo = codigoDe(id);
		VerificacionVista v = como(EscenarioCobranza.DIRECCION, () -> verificacion.verificar(codigo));
		assertThat(v.vigente()).isTrue();
		assertThat(v.coincide()).isTrue();
		List<VerificacionVista.Nivel> deMatematica = v.niveles().stream().filter(n -> n.area().equals("Matemática"))
				.toList();
		assertThat(deMatematica).isNotEmpty().allSatisfy(n -> assertThat(n.nivel()).isEqualTo("A"));
		String pagina = mvc.perform(get("/academico/libretas/verificar").param("codigo", codigo).with(UsuariosDePrueba.como(
				EscenarioCobranza.DIRECCION))).andReturn().getResponse().getContentAsString();
		assertThat(pagina).contains("Es la versión vigente").contains("Resuelve problemas de cantidad");
		assertThat(contar("SELECT COUNT(*) FROM acceso_dato_personal WHERE tipo = 'LIBRETA' AND alumno_id = (SELECT "
				+ "alumno_id FROM matricula WHERE id = ?)", mateo)).isEqualTo(1);
	}

	/** E16: un papel de una versión reemplazada se reconoce (no es la vigente). */
	@Test
	void versionNoVigente() {
		cerrarYEmitir();
		Long primera = libreta(mateo);
		corregir(mateo, competencias("MAT").get(0), "AD", null);
		emitir();
		VerificacionVista v = como(EscenarioCobranza.DIRECCION, () -> verificacion.verificar(codigoDe(primera)));
		assertThat(v.existe()).isTrue();
		assertThat(v.vigente()).isFalse();
		assertThat(v.version()).isEqualTo(1);
		assertThat(v.versionVigente()).isEqualTo(2);
	}

	/** Solo Dirección verifica (el tutor y Promotoría: 403). */
	@Test
	void soloDireccionVerifica() throws Exception {
		cerrarYEmitir();
		assertThat(mvc.perform(get("/academico/libretas/verificar").param("codigo", codigoDe(libreta(mateo)))
				.with(UsuariosDePrueba.como(UsuariosDePrueba.autenticado(tito)))).andReturn().getResponse().getStatus())
				.isEqualTo(403);
		assertThat(mvc.perform(get("/academico/libretas/verificar").with(UsuariosDePrueba.como(
				EscenarioCobranza.PROMOTORIA))).andReturn().getResponse().getStatus()).isEqualTo(403);
	}

	private String codigoDe(Long id) {
		return "LIB-" + id + "-" + jdbc.queryForObject("SELECT huella_contenido FROM libreta_emitida WHERE id = ?",
				String.class, id).substring(0, 16);
	}
}
