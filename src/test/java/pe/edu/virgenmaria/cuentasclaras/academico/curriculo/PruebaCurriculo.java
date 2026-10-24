package pe.edu.virgenmaria.cuentasclaras.academico.curriculo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Catalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.ConfiguracionesEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Periodos;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;

/**
 * Sprint 10, tanda 1: la base de las pruebas de la evaluación del año y los periodos (H2, aplicación completa, reloj
 * ajustable). Cada prueba parte de la estructura de {@link EscenarioEscolar} (2026 con clases del 2 de marzo al 18 de
 * diciembre) con la base limpia y el reloj el lunes 2 de marzo de 2026 a las 08:00 de Lima.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
abstract class PruebaCurriculo {

	/** Los cuatro bimestres de 2026 de las pruebas, dentro de las clases y sin solaparse. */
	static final LocalDate[][] BIMESTRES = {
			{ LocalDate.of(2026, 3, 2), LocalDate.of(2026, 5, 8) },
			{ LocalDate.of(2026, 5, 18), LocalDate.of(2026, 7, 24) },
			{ LocalDate.of(2026, 8, 10), LocalDate.of(2026, 10, 9) },
			{ LocalDate.of(2026, 10, 19), LocalDate.of(2026, 12, 18) } };

	@Autowired
	protected ConfiguracionesEvaluacion configuraciones;

	@Autowired
	protected Periodos periodos;

	@Autowired
	protected Catalogo catalogo;

	@Autowired
	protected ServicioEstructura estructura;

	@Autowired
	protected TokenDeSesionDePrueba tokens;

	@Autowired
	protected RelojAjustable reloj;

	@Autowired
	protected JdbcTemplate jdbc;

	protected EscenarioEscolar.Estructura escuela;

	@BeforeEach
	void prepararCurriculo() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		hoyEs(LocalDate.of(2026, 3, 2));
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiarCurriculo() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
	}

	/** El reloj de la aplicación, ese día a las 08:00 de Lima. */
	protected void hoyEs(LocalDate dia) {
		reloj.fijar(LocalDateTime.of(dia, java.time.LocalTime.of(8, 0)).atZone(ZoneId.of("America/Lima")).toInstant());
	}

	protected <T> T como(UsuarioAutenticado quien, Supplier<T> operacion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return operacion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	protected void como(UsuarioAutenticado quien, Runnable operacion) {
		como(quien, () -> {
			operacion.run();
			return null;
		});
	}

	protected Long version() {
		return jdbc.queryForObject("SELECT id FROM curriculo_version WHERE codigo = 'CNEB-2016.1'", Long.class);
	}

	protected ConfiguracionEvaluacion.Valores valores(TipoPeriodo tipo, int dias) {
		return new ConfiguracionEvaluacion.Valores(version(), tipo, 500, false, dias, "Castellano", "DRE Lima Metropolitana",
				"UGEL 03");
	}

	/** Dirección guarda la evaluación de 2026 con bimestres y 7 días de cierre anticipado. */
	protected void configurar2026() {
		como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(), valores(TipoPeriodo.BIMESTRE, 7)));
	}

	/** Dirección programa los periodos 1 a {@code cuantos} de 2026. @return sus ids */
	protected Long[] programar(int cuantos) {
		Long[] ids = new Long[cuantos];
		for (int i = 0; i < cuantos; i++) {
			int n = i;
			ids[i] = como(EscenarioCobranza.DIRECCION, () -> periodos.programar(escuela.anio2026(), BIMESTRES[n][0],
					BIMESTRES[n][1]));
		}
		return ids;
	}

	protected String estado(Long periodo) {
		return jdbc.queryForObject("SELECT estado FROM periodo_evaluacion WHERE id = ?", String.class, periodo);
	}
}
