package pe.edu.virgenmaria.cuentasclaras.conectores.config;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.CifradoAesGcm;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 8, tanda 2 (decisión 118): prod no arranca si la clave de los conectores no descifra todos los secretos guardados
 * (otra clave, una versión que no tiene o sin clave); con la clave correcta, arranca. El mensaje no lleva secretos.
 */
class VerificadorClaveConectoresTest {

	private static final String CLAVE = Base64.getEncoder().encodeToString("clave-de-arranque-32-bytes-prueb".getBytes(
			StandardCharsets.UTF_8));

	private static final String OTRA = Base64.getEncoder().encodeToString("otra-clave-de-32-bytes-distintaa".getBytes(
			StandardCharsets.UTF_8));

	private static final String CLAVE_PUBLICA = "0f0e0d0c-0b0a-4908-8706-050403020100";

	private final ConectorColegioRepository conectores = mock(ConectorColegioRepository.class);

	private final RecorridoColegios colegios = mock(RecorridoColegios.class);

	/** Un conector del colegio 7 con su secreto cifrado con la clave (simulado: la fila ya guardada). */
	private ConectorColegio conSecreto(String clave, int version) {
		byte[] cifrado = CifradoAesGcm.cifrar(CifradoAesGcm.leerClave(clave), "{\"token\":\"t0k3n-0123456789abcd\"}"
				.getBytes(StandardCharsets.UTF_8), new CifradoAesGcm.Aad(7, "OSE", CLAVE_PUBLICA));
		ConectorColegio c = mock(ConectorColegio.class);
		when(c.getId()).thenReturn(11L);
		when(c.getColegioId()).thenReturn(7L);
		when(c.getTipo()).thenReturn(TipoConector.OSE);
		when(c.getClavePublica()).thenReturn(CLAVE_PUBLICA);
		when(c.getClaveVersion()).thenReturn(version);
		when(c.getSecretoCifrado()).thenReturn(cifrado);
		when(c.conSecreto()).thenReturn(true);
		return c;
	}

	private VerificadorClaveConectores verificador(String clave, int version) {
		return new VerificadorClaveConectores(conectores, new SecretosConectores(new PropiedadesConectores(clave, version, "",
				7)), colegios);
	}

	@Test
	void conLaClaveCorrectaArrancaYConOtraNo() {
		when(colegios.activos()).thenReturn(List.of(7L));
		ConectorColegio guardado = conSecreto(CLAVE, 1);
		when(conectores.findBySecretoPistaIsNotNullOrClaveVersionIsNotNull()).thenReturn(List.of(guardado));

		assertThatCode(() -> verificador(CLAVE, 1).verificar()).doesNotThrowAnyException();
		assertThatThrownBy(() -> verificador(OTRA, 1).verificar()).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("no descifra 1 secreto").hasMessageNotContaining("t0k3n");
		assertThatThrownBy(() -> verificador("", 1).verificar()).isInstanceOf(IllegalStateException.class);
		// La versión 1 con una instalación en la 2 (rotación a medias, sin la clave anterior): no arranca.
		assertThatThrownBy(() -> verificador(CLAVE, 2).verificar()).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("recifrar-conectores");
		assertThat(guardado.conSecreto()).isTrue();
	}

	@Test
	void sinSecretosGuardadosArrancaAunqueNoHayaClave() {
		when(colegios.activos()).thenReturn(List.of(7L));
		when(conectores.findBySecretoPistaIsNotNullOrClaveVersionIsNotNull()).thenReturn(List.of());

		assertThatCode(() -> verificador("", 1).verificar()).doesNotThrowAnyException();
	}
}
