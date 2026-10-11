package pe.edu.virgenmaria.cuentasclaras.comunicacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor.BuzonSimulado;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.CreadorMensajes;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 8, tanda 1 (escenario E18, decisión 121): un mensaje de un colegio nunca sale con el nombre de otro. Con dos
 * colegios y un enlace de activación en cada uno, cada mensaje guardado empieza con el nombre de SU colegio (lo
 * antepone {@code CreadorMensajes} desde el contexto) y el texto que recibe el proveedor también.
 */
@PruebaIntegracion
class PlantillasConColegioTest {

	@Autowired
	private CreadorMensajes creador;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private BuzonSimulado buzon;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private ColegioService nombres;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	private long colegioB;

	@BeforeEach
	void preparar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
		buzon.vaciar();
		colegioB = colegios.save(new Colegio("Colegio Los Álamos de Prueba")).getId();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
		buzon.vaciar();
	}

	@Test
	void cadaMensajeLlevaElNombreDeSuColegio() {
		String nombreA = jdbc.queryForObject("SELECT nombre FROM colegio WHERE id = 1", String.class);
		String nombreB = "Colegio Los Álamos de Prueba";
		assertThat(nombreA).isNotEqualTo(nombreB);
		Usuario cajeraA = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja.a", UsuariosDePrueba.CLAVE, true,
				Rol.CAJA);
		Usuario cajeraB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "caja.b", UsuariosDePrueba.CLAVE, true,
				Rol.CAJA);

		// Un enlace de activación en cada colegio (lo crea el creador en la transacción del alta, con el colegio en contexto).
		TransactionTemplate transaccion = new TransactionTemplate(transacciones);
		ContextoColegio.en(1L, () -> transaccion.executeWithoutResult(t -> creador.paraUsuario(cajeraA,
				activacion(cajeraA), null)));
		ContextoColegio.en(colegioB, () -> transaccion.executeWithoutResult(t -> creador.paraUsuario(cajeraB,
				activacion(cajeraB), null)));

		List<Map<String, Object>> mensajes = jdbc.queryForList("SELECT colegio_id, parametros FROM mensaje "
				+ "WHERE tipo = 'ACTIVACION_CUENTA' ORDER BY colegio_id");
		assertThat(mensajes).hasSize(2);
		assertThat(mensajes.get(0)).containsEntry("colegio_id", 1L).containsEntry("parametros", nombreA);
		assertThat(mensajes.get(1)).containsEntry("colegio_id", colegioB).containsEntry("parametros", nombreB);
		assertThat(ContextoColegio.en(colegioB, () -> nombres.nombreParaMensajes(colegioB))).isEqualTo(nombreB);

		// Al enviarlos, el texto que recibe el proveedor empieza con el nombre de su colegio (plantilla v2, {{1}}).
		assertThat(despacho.despacharColegio(1L)).isEqualTo(1);
		assertThat(despacho.despacharColegio(colegioB)).isEqualTo(1);
		assertThat(buzon.ultimaPara(cajeraA.getTelefonoWhatsapp())).hasValueSatisfying(e -> assertThat(e.texto())
				.startsWith(nombreA + ": para activar su acceso").doesNotContain(nombreB));
		assertThat(buzon.ultimaPara(cajeraB.getTelefonoWhatsapp())).hasValueSatisfying(e -> assertThat(e.texto())
				.startsWith(nombreB + ": para activar su acceso").doesNotContain(nombreA));
		assertThat(buzon.entregas()).allSatisfy(e -> assertThat(e.texto()).doesNotContain("Cuentas Claras"));
	}

	private static CreadorMensajes.Contenido activacion(Usuario usuario) {
		return new CreadorMensajes.Contenido(TipoMensaje.ACTIVACION_CUENTA, PlantillaMensaje.ACTIVACION, List.of(),
				"usuario", usuario.getId());
	}
}
