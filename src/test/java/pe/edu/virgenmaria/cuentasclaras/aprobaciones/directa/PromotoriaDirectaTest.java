package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.FeriadoRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioFeriados;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.Equipo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.CORREO_CONTROL;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (sección 3.16 y decisiones 213 a 215): la Promotoría hace y aplica lo suyo en un paso (plan, feriado
 * y lote de saldo inicial), con su firma; cada directa deja su evento resaltado y avisa en la misma transacción a TODAS
 * las Promotorías (también a quien actuó) y al correo de control. Si no se puede avisar, no se guarda. Y A5 ya no la
 * bloquea: aprueba lo de las cuentas que ella creó.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class PromotoriaDirectaTest {

	private static final LocalDate VIERNES_9 = LocalDate.of(2026, 10, 9);

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioSaldoInicial saldo;

	@Autowired
	private ServicioFeriados feriados;

	@Autowired
	private CalendarioHabil calendario;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	private Equipo e;

	private Estructura escuela;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		e = EscenarioDirecta.preparar(usuarios, codificador, jdbc, reloj);
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
		alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		calendario.invalidarTodo();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void elTitularApruebaSinDireccionElPlanDeLaCuentaQueCreo() {
		// A5 ya no la bloquea: la cuenta de Administración la creó la Promotoría hace un rato.
		jdbc.update("UPDATE usuario SET creado_por = ?, creado_en = ? WHERE id = ?", e.promotora().getNombreUsuario(),
				LocalDateTime.now(reloj).minusHours(2), e.administracion().getId());
		Long plan = como(e.administracion(), () -> {
			Long id = planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, EscenarioCobranza.plan(2027, "450", "350",
					null));
			planes.enviar(id);
			return id;
		});
		como(e.promotora(), () -> planes.aprobar(plan, planes.obtener(plan).version()));

		assertThat(jdbc.queryForMap("SELECT estado, creado_por, aprobado_por FROM plan_pension WHERE id = ?", plan))
				.containsEntry("estado", "APROBADO").containsEntry("creado_por", "admin.dir")
				.containsEntry("aprobado_por", "promotora.dir");
		// No es una directa (la pidió otra persona): no hay evento resaltado.
		assertThat(EscenarioDirecta.eventos(jdbc, "ACCION_DIRECTA_PROMOTORIA")).isZero();
	}

	@Test
	void elTitularHaceYApruebaSuPlanEnUnPasoYAvisaATodos() {
		Long plan = como(e.promotora(), () -> {
			Long id = planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, EscenarioCobranza.plan(2027, "450", "350",
					null));
			planes.enviar(id);
			planes.aprobar(id, planes.obtener(id).version());
			return id;
		});

		assertThat(jdbc.queryForMap("SELECT estado, creado_por, aprobado_por FROM plan_pension WHERE id = ?", plan))
				.containsEntry("estado", "APROBADO").containsEntry("creado_por", "promotora.dir")
				.containsEntry("aprobado_por", "promotora.dir");
		assertThat(jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = 'ACCION_DIRECTA_PROMOTORIA' "
				+ "AND entidad = 'plan_pension'", String.class)).contains("sin segunda aprobación", "con su firma");
		// El aviso: a TODAS las Promotorías (también a quien lo hizo) y al correo de control.
		assertThat(EscenarioDirecta.avisados(jdbc, "ACCION_DIRECTA"))
				.contains(e.promotora().getId(), e.socia().getId()).doesNotContain(e.directora().getId());
		assertThat(EscenarioDirecta.destinos(jdbc, "ACCION_DIRECTA")).contains(CORREO_CONTROL);
	}

	@Test
	void elFeriadoYElLoteDeSaldoInicialTambienEnUnPaso() {
		como(e.promotora(), () -> feriados.registrar(new FeriadoRequest(VIERNES_9, "Aniversario del colegio")));
		assertThat(habil(VIERNES_9)).isFalse();
		assertThat(jdbc.queryForMap("SELECT pendiente, creado_por, aprobado_por FROM feriado WHERE fecha = ?", VIERNES_9))
				.containsEntry("pendiente", false).containsEntry("aprobado_por", "promotora.dir");

		Long lote = como(e.promotora(), () -> {
			Long id = saldo.crearLote(new LoteRequest(escuela.anio2026(), LocalDate.of(2026, 9, 30), "Informe CPC Ramos",
					new BigDecimal("450.00")));
			saldo.agregarLinea(id, new LineaSaldoRequest(EscenarioEscolar.DNI_MATEO, ConceptoSaldo.PENSION, 2026, 9, null,
					new BigDecimal("450.00"), null));
			saldo.enviar(id);
			saldo.confirmar(id, saldo.obtener(id).version(), new BigDecimal("450.00"));
			return id;
		});
		assertThat(jdbc.queryForObject("SELECT estado FROM lote_saldo_inicial WHERE id = ?", String.class, lote))
				.isEqualTo("CONFIRMADO");
		assertThat(jdbc.queryForList("SELECT entidad FROM evento_auditoria WHERE accion = 'ACCION_DIRECTA_PROMOTORIA'",
				String.class)).contains("feriado", "lote_saldo_inicial");
		assertThat(EscenarioDirecta.destinos(jdbc, "ACCION_DIRECTA")).contains(CORREO_CONTROL);
	}

	@Test
	void siNoSePuedeAvisarNoSeGuarda() {
		// Nadie a quién avisar: sin correo de control, sin correo institucional y las Promotorías sin contacto.
		jdbc.update("DELETE FROM configuracion_colegio WHERE clave = 'huella_correo_externo'");
		jdbc.update("UPDATE colegio SET correo_institucional = NULL WHERE id = 1");
		jdbc.update("UPDATE usuario SET telefono_whatsapp = NULL, correo = NULL WHERE id IN (?, ?)", e.promotora().getId(),
				e.socia().getId());

		assertThatThrownBy(() -> como(e.promotora(), () -> feriados.registrar(new FeriadoRequest(VIERNES_9,
				"Aniversario del colegio")))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("No hay a quién avisar");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feriado", Long.class)).isZero();
		assertThat(habil(VIERNES_9)).isTrue();
	}

	private boolean habil(LocalDate dia) {
		return pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.en(1L, () -> calendario.esHabil(dia));
	}
}
