package pe.edu.virgenmaria.cuentasclaras.recaudacion.inicial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.inicial.DatosDemoColegioDevDePrueba;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.inicial.DatosDemoPensionesDevDePrueba;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LoteRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.estadoLote;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.linea;

/**
 * Recaudación de demostración (perfil dev): con los datos del colegio y las pensiones de demostración, «administracion»
 * deja un archivo del banco POR CONFIRMAR y, al confirmarlo otra persona con el total, se aplica con sus excepciones.
 */
@PruebaIntegracion
@Import({ ConfiguracionRelojAjustable.class, DatosDemoColegioDevDePrueba.class, DatosDemoPensionesDevDePrueba.class })
class DatosDemoRecaudacionDevTest {

	@Autowired
	private DatosDemoColegioDevDePrueba colegioDemo;

	@Autowired
	private DatosDemoPensionesDevDePrueba pensionesDemo;

	@Autowired
	private ServicioRecaudacion servicio;

	@Autowired
	private AlumnoRepository alumnos;

	@Autowired
	private CuotaRepository cuotas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private LoteRecaudacionRepository lotes;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private Clock reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		colegioDemo.crear();
		pensionesDemo.crear();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void dejaUnArchivoPorConfirmarQueSeAplicaAlConfirmarlo() {
		crearAdministracion();

		assertThat(demo(true).crearSiCorresponde()).isTrue();

		Map<String, Object> lote = jdbc.queryForMap("SELECT * FROM lote_recaudacion");
		assertThat(lote).containsEntry("estado", "CARGADO").containsEntry("creado_por", "administracion")
				.containsEntry("lineas", 5);
		Long id = ((Number) lote.get("id")).longValue();
		String total = ((BigDecimal) lote.get("total")).toPlainString();

		confirmar(servicio, jdbc, PROMOTORIA, id, total);

		assertThat(estadoLote(jdbc, id)).isEqualTo("APLICADO");
		assertThat(linea(jdbc, id, 1)).isEqualTo("APLICADA");
		assertThat(linea(jdbc, id, 2)).isEqualTo("APLICADA");
		assertThat(linea(jdbc, id, 3)).isEqualTo("APLICADA");
		assertThat(linea(jdbc, id, 4)).isEqualTo("EXCEPCION:EXCESO");
		assertThat(linea(jdbc, id, 5)).isEqualTo("EXCEPCION:CODIGO_INVALIDO");
		// Tres pagos del banco, cada uno con su boleta; el de Luciana es a cuenta.
		assertThat(jdbc.queryForList("SELECT CONCAT(p.medio, ' ', p.a_cuenta) FROM pago p "
				+ "WHERE p.linea_recaudacion_id IS NOT NULL ORDER BY p.id", String.class))
				.containsExactly("RECAUDACION_BANCARIA FALSE", "RECAUDACION_BANCARIA FALSE", "RECAUDACION_BANCARIA TRUE");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante c JOIN pago p ON p.comprobante_id = c.id "
				+ "WHERE p.linea_recaudacion_id IS NOT NULL AND c.tipo = 'BOLETA'", Long.class)).isEqualTo(3);
	}

	@Test
	void noRepiteNiCargaSinAdministracionNiFueraDeH2EnMemoria() {
		assertThat(demo(true).crearSiCorresponde()).isFalse();
		crearAdministracion();
		assertThat(demo(false).crearSiCorresponde()).isFalse();
		assertThat(new DatosDemoRecaudacionDev(servicio, alumnos, cuotas, usuarios, lotes, transacciones, reloj,
				"jdbc:mysql://localhost/cuentasclaras", true).crearSiCorresponde()).isFalse();
		assertThat(demo(true).crearSiCorresponde()).isTrue();
		assertThat(demo(true).crearSiCorresponde()).isFalse();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lote_recaudacion", Long.class)).isEqualTo(1);
	}

	private void crearAdministracion() {
		ContextoColegio.en(DatosDemoDev.COLEGIO_PRINCIPAL, () -> new TransactionTemplate(transacciones).execute(e -> {
			Usuario usuario = Usuario.nuevo(DatosDemoRecaudacionDev.USUARIO, "Administración de prueba", null,
					"{noop}no-se-usa", EnumSet.of(Rol.ADMINISTRACION));
			usuario.cambiarClave("{noop}no-se-usa", LocalDateTime.now(reloj), false);
			return usuarios.save(usuario);
		}));
	}

	private DatosDemoRecaudacionDev demo(boolean habilitado) {
		return new DatosDemoRecaudacionDev(servicio, alumnos, cuotas, usuarios, lotes, transacciones, reloj,
				"jdbc:h2:mem:demo", habilitado);
	}
}
