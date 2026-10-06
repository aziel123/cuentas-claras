package pe.edu.virgenmaria.cuentasclaras.conciliacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.Extracto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CategoriaExplicacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.AlertasConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ResumenConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioPartidas;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.registrar;

/**
 * F24: el colegio B no ve ni toca las cuentas, los extractos, los movimientos ni las diferencias del A (recibe 404), y
 * no puede cargar un extracto de la cuenta del A.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoConciliacionTest {

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private ServicioPartidas partidas;

	@Autowired
	private ResumenConciliacion resumen;

	@Autowired
	private AlertasConciliacion alertas;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Long cuentaId;

	private Long extractoId;

	private Extracto banco;

	private UsuarioAutenticado promotorB;

	private UsuarioAutenticado administracionB;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(CAJA);
		cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), MedioPago.YAPE, "YP999888",
				"450.00"));
		cuentaId = EscenarioConciliacion.cuenta(cuentas);
		reloj.fijar(Instant.parse("2026-10-05T14:00:00Z"));
		banco = extracto("100.00").abono("2026-10-02", "INTERESES GANADOS", "", "1.23");
		extractoId = registrar(extractos, ADMINISTRACION, banco);
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		promotorB = UsuariosDePrueba.autenticado(colegioB, 92L, "promotor.b", "Promotor B", false, EnumSet.of(Rol.PROMOTOR));
		administracionB = UsuariosDePrueba.autenticado(colegioB, 93L, "administracion.b", "Administración B", false,
				EnumSet.of(Rol.ADMINISTRACION));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void colegioBNoVeNiTocaLaConciliacionDelA() {
		UsuariosDePrueba.iniciarSesion(promotorB);
		assertThat(cuentas.lista()).isEmpty();
		assertThat(resumen.extractos().extractos()).isEmpty();
		assertThatThrownBy(() -> resumen.extracto(extractoId)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> extractos.paraConfirmar(cuentaId)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> extractos.confirmar(cuentaId, extractoId, 0L, banco.saldoFinal()))
				.isInstanceOf(RecursoNoEncontradoException.class);

		// El A confirma; el B sigue sin ver sus diferencias ni sus alertas.
		confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		UsuariosDePrueba.iniciarSesion(promotorB);
		assertThat(resumen.diferencias().faltantes()).isEmpty();
		assertThat(resumen.diferencias().sinPareja()).isEmpty();
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().contains("YP999888") || a.texto().contains("abono"));

		Long movimiento = jdbc.queryForObject("SELECT id FROM movimiento_bancario", Long.class);
		UsuariosDePrueba.iniciarSesion(administracionB);
		assertThatThrownBy(() -> partidas.explicar(movimiento, CategoriaExplicacion.INTERESES,
				"Intento desde otro colegio con nota")).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> partidas.posibles(movimiento)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> extractos.descartar(extractoId, "Intento desde otro colegio con motivo"))
				.isInstanceOf(RecursoNoEncontradoException.class);
		// Un extracto de la cuenta del A no es de ninguna cuenta del B.
		byte[] contenido = extracto(banco.saldoFinal().toPlainString()).abono("2026-10-03", "ABONO", "", "5.00").csv();
		assertThatThrownBy(() -> extractos.previsualizar("extracto-b.csv", contenido, contenido.length))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no está registrada");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM extracto_bancario", Long.class)).isEqualTo(1);
	}
}
