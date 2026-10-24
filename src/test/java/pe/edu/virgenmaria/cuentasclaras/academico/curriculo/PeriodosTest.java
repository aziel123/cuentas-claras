package pe.edu.virgenmaria.cuentasclaras.academico.curriculo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.PeriodosVista;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.proceso.AperturaPeriodos;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 10, tanda 1 (sección 3.3; E9, E10, E41 y M7): los periodos del año. Dirección los programa en orden, dentro del año
 * y sin solaparse; abren solos en su fecha ({@code sistema.academico}, en hora de Lima) o antes con la firma de Dirección;
 * se cierran solo desde {@code fin − 7} y con el anterior cerrado; un periodo cerrado no se reabre ni cambia sus fechas; en
 * ABIERTO el fin solo se extiende. Todo con firma y en la bitácora.
 */
class PeriodosTest extends PruebaCurriculo {

	@Autowired
	private AperturaPeriodos apertura;

	@Autowired
	private MockMvc mvc;

	@Test
	void programaLosCuatroBimestresEnOrdenConSuFirma() {
		configurar2026();
		Long[] ids = programar(4);
		assertThat(jdbc.queryForList("SELECT nombre FROM periodo_evaluacion WHERE anio_escolar_id = ? ORDER BY numero",
				String.class, escuela.anio2026())).containsExactly("I bimestre", "II bimestre", "III bimestre",
						"IV bimestre");
		assertThat(ids).allSatisfy(id -> assertThat(estado(id)).isEqualTo("PROGRAMADO"));
		assertThat(jdbc.queryForList("SELECT clave FROM firma_operacion WHERE clave LIKE 'periodo_evaluacion:%' ORDER BY id",
				String.class)).containsExactly("periodo_evaluacion:" + escuela.anio2026() + ":1:PROGRAMADO",
						"periodo_evaluacion:" + escuela.anio2026() + ":2:PROGRAMADO",
						"periodo_evaluacion:" + escuela.anio2026() + ":3:PROGRAMADO",
						"periodo_evaluacion:" + escuela.anio2026() + ":4:PROGRAMADO");
		Map<String, Object> evento = EscenarioEscolar.ultimoEvento(jdbc, "PERIODO_PROGRAMADO");
		assertThat(evento.get("DETALLE").toString()).contains("IV bimestre", "19/10/2026", "18/12/2026");
		// Un quinto periodo no cabe con bimestres.
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.programar(escuela.anio2026(),
				LocalDate.of(2026, 12, 19), LocalDate.of(2026, 12, 20)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya tiene sus 4 periodos");
		PeriodosVista vista = como(EscenarioCobranza.DIRECCION, () -> periodos.vista(escuela.anio2026()));
		assertThat(vista.siguienteNumero()).isNull();
		assertThat(vista.periodos()).hasSize(4);
	}

	@Test
	void sinConfiguracionNoSeProgramaYSoloDireccion() {
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.programar(escuela.anio2026(),
				BIMESTRES[0][0], BIMESTRES[0][1]))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Primero guarda la evaluación");
		configurar2026();
		for (var quien : new pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado[] {
				EscenarioCobranza.ADMINISTRACION, EscenarioCobranza.PROMOTORIA, UsuariosDePrueba.autenticado(Rol.DOCENTE) }) {
			assertThatThrownBy(() -> como(quien, () -> periodos.programar(escuela.anio2026(), BIMESTRES[0][0],
					BIMESTRES[0][1]))).isInstanceOf(AccessDeniedException.class);
		}
		assertThat(EscenarioEscolar.contar(jdbc, "periodo_evaluacion")).isZero();
	}

	/** E41: fechas fuera de las clases del año y periodos que se solapan se rechazan. */
	@Test
	void fueraDelAnioOSolapadoSeRechaza() {
		configurar2026();
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.programar(escuela.anio2026(),
				LocalDate.of(2026, 2, 20), LocalDate.of(2026, 5, 8)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("dentro de las clases");
		programar(1);
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.programar(escuela.anio2026(),
				LocalDate.of(2026, 5, 8), LocalDate.of(2026, 7, 24)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no se solapan");
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.programar(escuela.anio2026(),
				LocalDate.of(2026, 7, 24), LocalDate.of(2026, 5, 18)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("antes de su fin");
		assertThat(EscenarioEscolar.contar(jdbc, "periodo_evaluacion")).isOne();
	}

	@Test
	void direccionAbreAntesDeTiempoConSuFirmaYEnOrden() {
		configurar2026();
		Long[] ids = programar(2);
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.abrir(ids[1])))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Primero abre el I bimestre");
		hoyEs(LocalDate.of(2026, 2, 27));
		como(EscenarioCobranza.DIRECCION, () -> periodos.abrir(ids[0]));
		assertThat(estado(ids[0])).isEqualTo("ABIERTO");
		assertThat(jdbc.queryForObject("SELECT abierto_por FROM periodo_evaluacion WHERE id = ?", String.class, ids[0]))
				.isEqualTo("director");
		assertThat(jdbc.queryForList("SELECT clave FROM firma_operacion WHERE clave = ?", String.class,
				"periodo_evaluacion:" + ids[0] + ":ABIERTO")).hasSize(1);
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "PERIODO_ABIERTO").get("DETALLE").toString())
				.contains("antes de su fecha");
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.abrir(ids[0])))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está abierto");
	}

	/** AperturaPeriodos abre el periodo de hoy en hora de Lima (y no el que aún no empieza). */
	@Test
	void elSistemaAbreElDeHoyEnHoraDeLima() {
		configurar2026();
		Long[] ids = programar(2);
		hoyEs(LocalDate.of(2026, 3, 1));
		assertThat(apertura.abrir(1L)).isZero();
		assertThat(estado(ids[0])).isEqualTo("PROGRAMADO");
		hoyEs(LocalDate.of(2026, 3, 2));
		assertThat(apertura.abrir(1L)).isOne();
		assertThat(estado(ids[0])).isEqualTo("ABIERTO");
		assertThat(estado(ids[1])).isEqualTo("PROGRAMADO");
		assertThat(jdbc.queryForObject("SELECT abierto_por FROM periodo_evaluacion WHERE id = ?", String.class, ids[0]))
				.isEqualTo("sistema.academico");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "PERIODO_ABIERTO").get("NOMBRE_USUARIO"))
				.isEqualTo("sistema.academico");
		// Una segunda pasada el mismo día no hace nada.
		assertThat(apertura.abrir(1L)).isZero();
	}

	/** E9 y E10: se cierra desde fin − 7 y con el anterior cerrado; cerrado no se reabre ni cambia. */
	@Test
	void seCierraDesdeSieteDiasAntesYNoSeReabre() {
		configurar2026();
		Long[] ids = programar(2);
		apertura.abrir(1L);
		hoyEs(LocalDate.of(2026, 4, 30));
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.cerrar(ids[0])))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("desde el 01/05/2026");
		hoyEs(LocalDate.of(2026, 5, 18));
		apertura.abrir(1L);
		assertThat(estado(ids[1])).as("pueden estar abiertos dos seguidos").isEqualTo("ABIERTO");
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.cerrar(ids[1])))
				.as("el II no cierra con el I abierto").isInstanceOf(ReglaNegocioException.class);
		hoyEs(LocalDate.of(2026, 5, 1));
		assertThatThrownBy(() -> como(EscenarioCobranza.PROMOTORIA, () -> periodos.cerrar(ids[0])))
				.isInstanceOf(AccessDeniedException.class);
		como(EscenarioCobranza.DIRECCION, () -> periodos.cerrar(ids[0]));
		assertThat(estado(ids[0])).isEqualTo("CERRADO");
		assertThat(jdbc.queryForObject("SELECT cerrado_por FROM periodo_evaluacion WHERE id = ?", String.class, ids[0]))
				.isEqualTo("director");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "PERIODO_CERRADO").get("VALOR_NUEVO")).isEqualTo("CERRADO");
		// E10: cerrado no se reabre ni cambia sus fechas.
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.abrir(ids[0])))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.cerrar(ids[0])))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.moverFechas(ids[0], BIMESTRES[0][0],
				LocalDate.of(2026, 5, 15)))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cerrado");
		assertThat(estado(ids[0])).isEqualTo("CERRADO");
	}

	/** M7: en ABIERTO el fin solo se extiende y el inicio no cambia; en PROGRAMADO cambian las dos, con firma. */
	@Test
	void enAbiertoElFinSoloSeExtiende() {
		configurar2026();
		Long[] ids = programar(2);
		como(EscenarioCobranza.DIRECCION, () -> periodos.moverFechas(ids[1], LocalDate.of(2026, 5, 19),
				LocalDate.of(2026, 7, 23)));
		assertThat(jdbc.queryForObject("SELECT inicio FROM periodo_evaluacion WHERE id = ?", LocalDate.class, ids[1]))
				.isEqualTo(LocalDate.of(2026, 5, 19));
		apertura.abrir(1L);
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.moverFechas(ids[0], BIMESTRES[0][0],
				LocalDate.of(2026, 5, 1)))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("solo se extiende");
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.moverFechas(ids[0],
				LocalDate.of(2026, 3, 3), BIMESTRES[0][1]))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("inicio no cambia");
		// Extender hasta chocar con el siguiente tampoco.
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> periodos.moverFechas(ids[0], BIMESTRES[0][0],
				LocalDate.of(2026, 5, 19)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("mueve primero ese periodo");
		como(EscenarioCobranza.DIRECCION, () -> periodos.moverFechas(ids[0], BIMESTRES[0][0], LocalDate.of(2026, 5, 15)));
		assertThat(jdbc.queryForObject("SELECT fin FROM periodo_evaluacion WHERE id = ?", LocalDate.class, ids[0]))
				.isEqualTo(LocalDate.of(2026, 5, 15));
		Map<String, Object> evento = EscenarioEscolar.ultimoEvento(jdbc, "PERIODO_FECHAS_CAMBIADAS");
		assertThat(evento.get("VALOR_ANTERIOR").toString()).contains("08/05/2026");
		assertThat(evento.get("VALOR_NUEVO").toString()).contains("15/05/2026");
		assertThat(jdbc.queryForList("SELECT clave FROM firma_operacion WHERE clave LIKE ?", String.class,
				"periodo_evaluacion:" + ids[0] + ":FECHAS:%")).hasSize(1);
	}

	@Test
	void laPantallaEsSoloDeDireccion() throws Exception {
		configurar2026();
		Long[] ids = programar(1);
		mvc.perform(get("/academico/periodos").param("anio", escuela.anio2026().toString())
				.with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION))).andExpect(status().isOk());
		for (Rol rol : new Rol[] { Rol.ADMINISTRACION, Rol.PROMOTOR, Rol.DOCENTE, Rol.CAJA }) {
			mvc.perform(get("/academico/periodos").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
			mvc.perform(post("/academico/periodos/{id}/abrir", ids[0]).with(csrf()).with(UsuariosDePrueba.como(rol)))
					.andExpect(status().isForbidden());
		}
		assertThat(estado(ids[0])).isEqualTo("PROGRAMADO");
	}
}
