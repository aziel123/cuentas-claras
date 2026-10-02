package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelladorAuditoriaTest {

	static final String CLAVE = "clave-de-prueba-unitaria-de-32-caracteres-o-mas";

	private static final String HASH_INICIAL = VerificadorIntegridadAuditoria.HASH_INICIAL;

	private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 2, 8, 30, 15, 123_456_000);

	private final SelladorAuditoria sellador = new SelladorAuditoria(CLAVE);

	@Test
	void exigeUnaClaveDeAlMenos32Caracteres() {
		assertThatThrownBy(() -> new SelladorAuditoria("corta")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AUDITORIA_CLAVE_HMAC");
		assertThatThrownBy(() -> new SelladorAuditoria(null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new SelladorAuditoria("x".repeat(31))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void elHashEsHexadecimalDe64CaracteresYDeterminista() {
		String hash = sellador.sellar(HASH_INICIAL, evento("detalle"));
		assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(sellador.sellar(HASH_INICIAL, evento("detalle"))).isEqualTo(hash);
	}

	@Test
	void elHashDependeDelHashAnteriorDeLaClaveYDeCadaCampo() {
		String base = sellador.sellar(HASH_INICIAL, evento("detalle"));
		assertThat(sellador.sellar("a".repeat(64), evento("detalle"))).isNotEqualTo(base);
		assertThat(new SelladorAuditoria(CLAVE + "-otra").sellar(HASH_INICIAL, evento("detalle"))).isNotEqualTo(base);
		assertThat(sellador.sellar(HASH_INICIAL, evento("detallE"))).isNotEqualTo(base);
	}

	@Test
	void nuloYVacioProducenHashesDistintos() {
		assertThat(sellador.sellar(HASH_INICIAL, evento(null)))
				.isNotEqualTo(sellador.sellar(HASH_INICIAL, evento("")));
	}

	@Test
	void laFormaCanonicaNoEsAmbiguaEntreCamposContiguos() {
		EventoAuditoria a = EventoAuditoria.crear(1, Actor.sistema(1L), AccionAuditoria.USUARIO_CREADO, "ab", "c",
				null, null, null, FECHA, e -> "x");
		EventoAuditoria b = EventoAuditoria.crear(1, Actor.sistema(1L), AccionAuditoria.USUARIO_CREADO, "a", "bc",
				null, null, null, FECHA, e -> "x");
		assertThat(SelladorAuditoria.formaCanonica(HASH_INICIAL, a))
				.isNotEqualTo(SelladorAuditoria.formaCanonica(HASH_INICIAL, b));
		assertThat(SelladorAuditoria.formaCanonica(HASH_INICIAL, a)).contains("26:2026-10-02T08:30:15.123456|");
	}

	@Test
	void esValidoDetectaUnHashAlterado() {
		EventoAuditoria valido = EventoAuditoria.crear(1, Actor.sistema(1L), AccionAuditoria.USUARIO_CREADO, "usuario",
				"7", null, "activo", null, FECHA, e -> sellador.sellar(HASH_INICIAL, e));
		EventoAuditoria alterado = EventoAuditoria.crear(1, Actor.sistema(1L), AccionAuditoria.USUARIO_CREADO,
				"usuario", "7", null, "activo", null, FECHA, e -> "0".repeat(64));
		assertThat(sellador.esValido(HASH_INICIAL, valido)).isTrue();
		assertThat(sellador.esValido(HASH_INICIAL, alterado)).isFalse();
	}

	private static EventoAuditoria evento(String detalle) {
		return EventoAuditoria.crear(5, new Actor(1L, 9L, "director", "DIRECTOR", "10.0.0.1"),
				AccionAuditoria.ROLES_CAMBIADOS, "usuario", "9", "CAJA", "DOCENTE", detalle, FECHA, e -> "x");
	}
}
