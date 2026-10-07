package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.FeriadoRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.FeriadosVista;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/**
 * Sprint 5, tanda 3 (G20): los días no laborables del colegio. Solo Promotoría o Dirección, solo a futuro, se anulan
 * antes de su fecha, quedan resaltados en la bitácora y el calendario hábil los usa en el acto.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioFeriadosTest {

	/** Viernes 9 de octubre de 2026 (el reloj de prueba está el viernes 2). */
	private static final LocalDate VIERNES_9 = LocalDate.of(2026, 10, 9);

	@Autowired
	private ServicioFeriados feriados;

	@Autowired
	private CalendarioHabil calendario;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		calendario.invalidarTodo();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		calendario.invalidarTodo();
	}

	@Test
	void promotoriaRegistraUnDiaYElCalendarioLoSaltaEnElActo() {
		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		assertThat(calendario.siguienteDiaHabil(LocalDate.of(2026, 10, 7))).isEqualTo(VIERNES_9);

		Long id = feriados.registrar(new FeriadoRequest(VIERNES_9, "Aniversario del colegio"));

		assertThat(calendario.siguienteDiaHabil(LocalDate.of(2026, 10, 7))).isEqualTo(LocalDate.of(2026, 10, 12));
		Map<String, Object> evento = ultimoEvento(jdbc, "FERIADO_REGISTRADO");
		assertThat(evento).containsEntry("nombre_usuario", "promotor").containsEntry("entidad_id", id.toString());
		assertThat(evento.get("valor_nuevo")).asString().contains("09/10/2026", "Aniversario del colegio");
		FeriadosVista vista = feriados.vista(2026);
		assertThat(vista.nacionales()).hasSize(16);
		assertThat(vista.extras()).singleElement().satisfies(f -> {
			assertThat(f.vigente()).isTrue();
			assertThat(f.anulable()).isTrue();
		});
		assertThat(vista.puedeEditar()).isTrue();
	}

	@Test
	void administracionNoRegistraFeriados() {
		for (var quien : new pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado[] { ADMINISTRACION,
				CAJA }) {
			UsuariosDePrueba.iniciarSesion(quien);
			assertThatThrownBy(() -> feriados.registrar(new FeriadoRequest(VIERNES_9, "Día libre de la caja")))
					.isInstanceOf(AccessDeniedException.class);
			// Pero los ve (pantalla de solo lectura).
			assertThat(feriados.vista(2026).puedeEditar()).isFalse();
		}
		assertThat(contar(jdbc, "feriado")).isZero();
	}

	@Test
	void soloFechasFuturas() {
		UsuariosDePrueba.iniciarSesion(DIRECCION);
		for (LocalDate fecha : new LocalDate[] { LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2) }) {
			assertThatThrownBy(() -> feriados.registrar(new FeriadoRequest(fecha, "Para que no salte la alerta")))
					.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("desde mañana");
		}
		assertThatThrownBy(() -> feriados.registrar(new FeriadoRequest(LocalDate.of(2026, 10, 8), "Angamos otra vez")))
				.hasMessageContaining("ya es feriado nacional");
		assertThatThrownBy(() -> feriados.registrar(new FeriadoRequest(LocalDate.of(2026, 10, 4), "Un domingo")))
				.hasMessageContaining("domingo");
		feriados.registrar(new FeriadoRequest(VIERNES_9, "Aniversario del colegio"));
		assertThatThrownBy(() -> feriados.registrar(new FeriadoRequest(VIERNES_9, "Aniversario repetido")))
				.hasMessageContaining("ya está registrado");
		assertThat(contar(jdbc, "feriado")).isEqualTo(1);
	}

	@Test
	void anularSoloAntesDeSuFecha() {
		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		Long id = feriados.registrar(new FeriadoRequest(VIERNES_9, "Aniversario del colegio"));
		assertThatThrownBy(() -> feriados.anular(id, "corto")).hasMessageContaining("motivo");

		reloj.fijar(Instant.parse("2026-10-09T15:00:00Z")); // el mismo día 9
		assertThatThrownBy(() -> feriados.anular(id, "Se decidió trabajar ese día"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("antes de su fecha");

		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		feriados.anular(id, "Se decidió trabajar ese día");
		assertThat(jdbc.queryForMap("SELECT vigente, anulado_por FROM feriado WHERE id = ?", id))
				.containsEntry("vigente", null).containsEntry("anulado_por", "promotor");
		assertThat(ultimoEvento(jdbc, "FERIADO_ANULADO").get("detalle")).asString()
				.contains("Se decidió trabajar ese día");
		assertThat(calendario.esHabil(VIERNES_9)).isTrue();
		assertThatThrownBy(() -> feriados.anular(id, "Otra vez lo mismo")).hasMessageContaining("ya está anulado");
		// Anulado, la fecha se puede volver a registrar.
		feriados.registrar(new FeriadoRequest(VIERNES_9, "Aniversario del colegio"));
		assertThat(contar(jdbc, "feriado")).isEqualTo(2);
	}

	@Test
	void otroColegioNoVeNiAnulaElDia() {
		UsuariosDePrueba.iniciarSesion(PROMOTORIA);
		Long id = feriados.registrar(new FeriadoRequest(VIERNES_9, "Aniversario del colegio"));
		jdbc.update("INSERT INTO colegio (nombre) VALUES ('Colegio de Prueba B')");
		long colegioB = jdbc.queryForObject("SELECT MAX(id) FROM colegio", Long.class);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(colegioB, 90L, "promotor.b", "Promotor B", false,
				EnumSet.of(Rol.PROMOTOR)));

		assertThat(feriados.vista(2026).extras()).isEmpty();
		assertThat(calendario.esHabil(VIERNES_9)).isTrue();
		assertThatThrownBy(() -> feriados.anular(id, "Intento desde otro colegio"))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}
}
