package pe.edu.virgenmaria.cuentasclaras.academico.curriculo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.ConfiguracionEvaluacionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 10, tanda 1 (sección 3.3; S1, S3, S9, S11; E11 y M7): la evaluación del año la guarda SOLO Dirección, con su firma
 * ({@code configuracion_evaluacion:{anio}:GUARDADA:{version}}), y queda resaltada en la bitácora con el antes y el después.
 * Administración, Promotoría y el docente no la guardan (403); por defecto son bimestres. Con un periodo abierto, la versión,
 * el tipo de periodo y los días de cierre anticipado ya no cambian.
 */
class ConfiguracionEvaluacionTest extends PruebaCurriculo {

	@Autowired
	private MockMvc mvc;

	@Test
	void porDefectoBimestresYLaGuardaDireccionConSuFirma() {
		ConfiguracionEvaluacionVista vista = como(EscenarioCobranza.ADMINISTRACION, () -> configuraciones.vista(
				escuela.anio2026()));
		assertThat(vista.guardada()).isFalse();
		assertThat(vista.tipoPeriodo()).isEqualTo(TipoPeriodo.BIMESTRE);
		assertThat(vista.conclusionMax()).isEqualTo(500);
		assertThat(vista.diasCierreAnticipado()).isEqualTo(7);
		assertThat(vista.lenguaMaterna()).isEqualTo("Castellano");
		assertThat(vista.versionCodigo()).isEqualTo("CNEB-2016.1");
		assertThat(vista.puedeGuardar()).as("Administración la lee, no la guarda").isFalse();

		assertThat(como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(TipoPeriodo.BIMESTRE, 7)))).isTrue();
		assertThat(jdbc.queryForObject("SELECT numero_periodos FROM configuracion_evaluacion WHERE anio_escolar_id = ?",
				Integer.class, escuela.anio2026())).isEqualTo(4);
		Map<String, Object> evento = EscenarioEscolar.ultimoEvento(jdbc, "CONFIGURACION_EVALUACION_GUARDADA");
		assertThat(evento.get("VALOR_ANTERIOR")).isNull();
		assertThat(evento.get("VALOR_NUEVO").toString()).contains("CNEB-2016.1", "4 bimestres", "7 días");
		assertThat(AccionAuditoria.CONFIGURACION_EVALUACION_GUARDADA.requiereAtencion()).isTrue();

		// Otro cambio: la versión sube de a una, con otra firma; sin cambios no se firma ni se guarda.
		assertThat(como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(TipoPeriodo.TRIMESTRE, 5)))).isTrue();
		assertThat(como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(TipoPeriodo.TRIMESTRE, 5)))).isFalse();
		assertThat(jdbc.queryForList("SELECT clave FROM firma_operacion WHERE clave LIKE 'configuracion_evaluacion:%' "
				+ "ORDER BY id", String.class)).containsExactly("configuracion_evaluacion:" + escuela.anio2026()
						+ ":GUARDADA:0", "configuracion_evaluacion:" + escuela.anio2026() + ":GUARDADA:1");
		Map<String, Object> cambio = EscenarioEscolar.ultimoEvento(jdbc, "CONFIGURACION_EVALUACION_GUARDADA");
		assertThat(cambio.get("VALOR_ANTERIOR").toString()).contains("4 bimestres");
		assertThat(cambio.get("VALOR_NUEVO").toString()).contains("3 trimestres", "5 días");
	}

	@Test
	void soloDireccion() {
		for (var quien : new pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado[] {
				EscenarioCobranza.ADMINISTRACION, EscenarioCobranza.PROMOTORIA, UsuariosDePrueba.autenticado(Rol.DOCENTE) }) {
			assertThatThrownBy(() -> como(quien, () -> configuraciones.guardar(escuela.anio2026(),
					valores(TipoPeriodo.BIMESTRE, 7)))).as(quien.getUsername()).isInstanceOf(AccessDeniedException.class);
		}
		assertThat(EscenarioEscolar.contar(jdbc, "configuracion_evaluacion")).isZero();
	}

	@Test
	void porLaWebAdministracionYPromotoriaRecibenUn403AlGuardar() throws Exception {
		for (Rol rol : new Rol[] { Rol.ADMINISTRACION, Rol.PROMOTOR }) {
			mvc.perform(get("/academico/evaluacion/configuracion").param("anio", escuela.anio2026().toString())
					.with(UsuariosDePrueba.como(rol))).andExpect(status().isOk())
					.andExpect(content().string(org.hamcrest.Matchers.containsString("Solo Dirección guarda")));
			mvc.perform(guardar().with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
		mvc.perform(guardar().with(UsuariosDePrueba.como(Rol.DOCENTE))).andExpect(status().isForbidden());
		assertThat(EscenarioEscolar.contar(jdbc, "configuracion_evaluacion")).isZero();
		mvc.perform(guardar().with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION)))
				.andExpect(redirectedUrl("/academico/evaluacion/configuracion?anio=" + escuela.anio2026()));
		assertThat(EscenarioEscolar.contar(jdbc, "configuracion_evaluacion")).isOne();
	}

	/** E11 y M7: con un periodo abierto, la versión, el tipo de periodo y los días de cierre no cambian. */
	@Test
	void conUnPeriodoAbiertoNoCambiaElTipoNiLosDiasDeCierre() {
		configurar2026();
		Long[] ids = programar(1);
		como(EscenarioCobranza.DIRECCION, () -> periodos.abrir(ids[0]));
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(TipoPeriodo.TRIMESTRE, 7)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya tiene un periodo abierto");
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(TipoPeriodo.BIMESTRE, 3)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("días de cierre anticipado");
		// Lo demás (la DRE, la UGEL, el largo de la conclusión) sí cambia.
		assertThat(como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				new pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion.Valores(version(),
						TipoPeriodo.BIMESTRE, 800, true, 7, "Castellano", "DRE Callao", "UGEL Ventanilla")))).isTrue();
		assertThat(como(EscenarioCobranza.DIRECCION, () -> configuraciones.vista(escuela.anio2026())).fijos()).isTrue();
	}

	/** Un tipo con menos periodos que los ya programados no se guarda. */
	@Test
	void unTipoConMenosPeriodosQueLosProgramadosNoSeGuarda() {
		configurar2026();
		programar(3);
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(TipoPeriodo.SEMESTRE, 7)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("3 periodos programados");
	}

	@Test
	void losRangosSeValidan() {
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(TipoPeriodo.BIMESTRE, 16)))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("0 a 15");
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				new pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion.Valores(version(),
						TipoPeriodo.BIMESTRE, 50, false, 7, "Castellano", null, null))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("100 a 1000");
		assertThat(EscenarioEscolar.contar(jdbc, "configuracion_evaluacion")).isZero();
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder guardar() {
		return post("/academico/evaluacion/configuracion").with(csrf()).param("anioId", escuela.anio2026().toString())
				.param("curriculoVersionId", version().toString()).param("tipoPeriodo", "BIMESTRE")
				.param("conclusionMax", "500").param("diasCierreAnticipado", "7").param("lenguaMaterna", "Castellano");
	}
}
