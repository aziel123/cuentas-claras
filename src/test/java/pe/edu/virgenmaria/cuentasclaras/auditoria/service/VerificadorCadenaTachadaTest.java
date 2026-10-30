package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.Sha256;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaJpa;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 2 (3.11 y AP2-8): la verificación de la cadena acepta los eventos TACHADOS de un colegio suprimido solo
 * con su acta ejecutada: toma su {@code hash} guardado como eslabón y compara la huella encadenada de sus eventos
 * ({@code h = SHA-256(h|id|hash)} desde 64 ceros, por secuencia) con la del acta. Sin acta, con otra huella o con un
 * evento tachado de otro colegio, la cadena sale ALTERADA. Los eventos de los demás colegios se siguen recalculando.
 */
@PruebaJpa
@Import({ AuditoriaService.class, SelladorAuditoria.class, VerificadorIntegridadAuditoria.class,
		VerificadorCadenaTachadaTest.Actas.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VerificadorCadenaTachadaTest {

	/** Las actas ejecutadas que «tiene» la base en cada prueba. */
	static final Map<Long, SupresionesDeLaBitacora.ActaEjecutada> ACTAS = new HashMap<>();

	@TestConfiguration
	static class Actas {

		@Bean
		SupresionesDeLaBitacora supresionesDePrueba() {
			return () -> Map.copyOf(ACTAS);
		}
	}

	@Autowired
	private VerificadorIntegridadAuditoria verificador;

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private JdbcTemplate jdbc;

	private long suprimido;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		ACTAS.clear();
		suprimido = colegios.save(new Colegio("Colegio Suprimido")).getId();
		for (int i = 1; i <= 3; i++) {
			auditoria.registrar(Actor.sistema(1L), AccionAuditoria.USUARIO_CREADO, "usuario", "a" + i, null, "activo", null);
			auditoria.registrar(Actor.sistema(suprimido), AccionAuditoria.USUARIO_CREADO, "usuario", "b" + i, null, "Rosa",
					"datos de la familia");
		}
	}

	@AfterEach
	void limpiar() {
		ACTAS.clear();
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void conSuActaLosTachadosVerificanYSinElla() {
		tachar(suprimido);
		assertThat(verificador.verificar().integra()).as("tachados sin acta").isFalse();

		ACTAS.put(suprimido, acta(huella(suprimido), ultima(suprimido)));
		ResultadoVerificacion resultado = verificador.verificar();
		assertThat(resultado.integra()).as(resultado.detalle()).isTrue();
		// Lo tachado ya no dice nada de la familia; la cadena del otro colegio sigue íntegra.
		assertThat(jdbc.queryForList("SELECT detalle FROM evento_auditoria WHERE colegio_id = ? AND detalle IS NOT NULL",
				String.class, suprimido)).isEmpty();
	}

	@Test
	void conOtraHuellaFalla() {
		tachar(suprimido);
		ACTAS.put(suprimido, acta("a".repeat(64), ultima(suprimido)));
		ResultadoVerificacion resultado = verificador.verificar();
		assertThat(resultado.integra()).isFalse();
		assertThat(resultado.detalle()).contains("huella de su acta");
	}

	@Test
	void unTachadoDeOtroColegioFallaAunqueHayaActa() {
		tachar(suprimido);
		ACTAS.put(suprimido, acta(huella(suprimido), ultima(suprimido)));
		jdbc.update("UPDATE evento_auditoria SET nombre_usuario = 'suprimido', detalle = NULL WHERE colegio_id = 1 AND "
				+ "secuencia = (SELECT MIN(secuencia) FROM evento_auditoria WHERE colegio_id = 1)");
		assertThat(verificador.verificar().integra()).isFalse();
	}

	@Test
	void unHashCambiadoDeUnTachadoFalla() {
		tachar(suprimido);
		String antes = huella(suprimido);
		ACTAS.put(suprimido, acta(antes, ultima(suprimido)));
		jdbc.update("UPDATE evento_auditoria SET hash = ? WHERE colegio_id = ? AND secuencia = (SELECT MAX(secuencia) FROM "
				+ "evento_auditoria WHERE colegio_id = ?)", "b".repeat(64), suprimido, suprimido);
		assertThat(verificador.verificar().integra()).isFalse();
	}

	/** Lo que hace suprimir.sql con los eventos del colegio. */
	private void tachar(long colegio) {
		jdbc.update("UPDATE evento_auditoria SET nombre_usuario = 'suprimido', usuario_id = NULL, roles = NULL, "
				+ "valor_anterior = NULL, valor_nuevo = NULL, detalle = NULL, ip = NULL WHERE colegio_id = ?", colegio);
	}

	/** La huella que calcula trg_acta_supresion_estado al pasar a EJECUTANDO. */
	private String huella(long colegio) {
		String h = VerificadorIntegridadAuditoria.HASH_INICIAL;
		for (Map<String, Object> e : jdbc.queryForList("SELECT id, hash FROM evento_auditoria WHERE colegio_id = ? ORDER BY "
				+ "secuencia", colegio)) {
			h = Sha256.hex(h + "|" + e.get("id") + "|" + e.get("hash"));
		}
		return h;
	}

	private long ultima(long colegio) {
		return jdbc.queryForObject("SELECT MAX(secuencia) FROM evento_auditoria WHERE colegio_id = ?", Long.class, colegio);
	}

	private SupresionesDeLaBitacora.ActaEjecutada acta(String huella, long ultima) {
		return new SupresionesDeLaBitacora.ActaEjecutada(suprimido, 77L, huella, ultima);
	}
}
