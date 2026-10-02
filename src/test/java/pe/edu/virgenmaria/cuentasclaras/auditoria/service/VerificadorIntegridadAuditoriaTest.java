package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EslabonCadenaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaJpa;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La cadena HMAC detecta lo que alguien con acceso directo a la base podría hacer para tapar
 * un fraude: editar, borrar o insertar eventos. Las alteraciones se simulan con SQL directo.
 */
@PruebaJpa
@Import({ AuditoriaService.class, SelladorAuditoria.class, VerificadorIntegridadAuditoria.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VerificadorIntegridadAuditoriaTest {

	private static final int EVENTOS = 5;

	@Autowired
	private VerificadorIntegridadAuditoria verificador;

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private EventoAuditoriaRepository eventos;

	@Autowired
	private EslabonCadenaRepository cadena;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void registrarEventos() {
		LimpiezaBaseDatos.limpiar(jdbc);
		for (int i = 1; i <= EVENTOS; i++) {
			auditoria.registrar(Actor.sistema(1L), AccionAuditoria.USUARIO_CREADO, "usuario", String.valueOf(i), null,
					"activo", null);
		}
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void cadenaIntactaEsIntegra() {
		ResultadoVerificacion resultado = verificador.verificar();

		assertThat(resultado.integra()).isTrue();
		assertThat(resultado.eventosRevisados()).isEqualTo(EVENTOS);
		assertThat(resultado.secuenciaConProblema()).isNull();
		assertThat(jdbc.queryForObject(
				"SELECT accion FROM evento_auditoria WHERE secuencia = ?", String.class, EVENTOS + 1))
				.isEqualTo(AccionAuditoria.INTEGRIDAD_VERIFICADA.name());
		assertThat(verificador.verificar().integra()).as("el propio registro de la verificación sigue la cadena")
				.isTrue();
	}

	@Test
	void editarUnEventoDirectoEnLaBaseSeDetecta() {
		jdbc.update("UPDATE evento_auditoria SET valor_nuevo = 'inactivo' WHERE secuencia = 3");

		ResultadoVerificacion resultado = verificador.verificar();

		assertThat(resultado.integra()).isFalse();
		assertThat(resultado.secuenciaConProblema()).isEqualTo(3L);
		assertThat(resultado.detalle()).contains("modificado");
		assertThat(jdbc.queryForObject("SELECT valor_nuevo FROM evento_auditoria WHERE secuencia = ?", String.class,
				EVENTOS + 1)).isEqualTo("ALTERADA");
	}

	@Test
	void borrarUnEventoIntermedioSeDetecta() {
		jdbc.update("DELETE FROM evento_auditoria WHERE secuencia = 3");

		ResultadoVerificacion resultado = verificador.verificar();

		assertThat(resultado.integra()).isFalse();
		assertThat(resultado.secuenciaConProblema()).isEqualTo(3L);
		assertThat(resultado.detalle()).contains("se borró");
	}

	@Test
	void borrarElUltimoEventoSeDetecta() {
		jdbc.update("DELETE FROM evento_auditoria WHERE secuencia = ?", EVENTOS);

		ResultadoVerificacion resultado = verificador.verificar();

		assertThat(resultado.integra()).isFalse();
		assertThat(resultado.secuenciaConProblema()).isEqualTo((long) EVENTOS);
	}

	@Test
	void insertarUnEventoFalsoSeDetecta() {
		String hashReal = cadena.leer().getUltimoHash();
		insertarEventoFalso(EVENTOS + 1, hashReal);

		ResultadoVerificacion resultado = verificador.verificar();

		assertThat(resultado.integra()).isFalse();
		assertThat(resultado.secuenciaConProblema()).isEqualTo(EVENTOS + 1L);
		assertThat(resultado.detalle()).contains("no fue registrado por la aplicación");
	}

	@Test
	void insertarUnEventoFalsoYAvanzarElEslabonSeDetecta() {
		insertarEventoFalso(EVENTOS + 1, "f".repeat(64));
		jdbc.update("UPDATE auditoria_cadena SET ultima_secuencia = ?, ultimo_hash = ? WHERE id = 1", EVENTOS + 1,
				"f".repeat(64));

		ResultadoVerificacion resultado = verificador.verificar();

		assertThat(resultado.integra()).isFalse();
		assertThat(resultado.secuenciaConProblema()).isEqualTo(EVENTOS + 1L);
		assertThat(resultado.detalle()).contains("modificado o insertado");
	}

	@Test
	void hashConOtraClaveNoEsValido() {
		VerificadorIntegridadAuditoria conOtraClave = new VerificadorIntegridadAuditoria(eventos, cadena,
				new SelladorAuditoria("otra-clave-que-no-es-la-de-la-aplicacion-0001"), auditoria, transacciones);

		ResultadoVerificacion resultado = conOtraClave.verificar();

		assertThat(resultado.integra()).isFalse();
		assertThat(resultado.secuenciaConProblema()).isEqualTo(1L);
	}

	private void insertarEventoFalso(long secuencia, String hash) {
		jdbc.update("""
				INSERT INTO evento_auditoria (secuencia, colegio_id, ocurrido_en, nombre_usuario, accion, hash)
				VALUES (?, 1, CURRENT_TIMESTAMP, 'caja.a', 'USUARIO_CREADO', ?)""", secuencia, hash);
	}
}
