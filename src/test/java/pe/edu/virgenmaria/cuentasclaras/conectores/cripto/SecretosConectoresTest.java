package pe.edu.virgenmaria.cuentasclaras.conectores.cripto;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.conectores.config.PropiedadesConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.CifradoAesGcm.Aad;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.CifradoAesGcm.SecretoIlegibleException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 8, tanda 2 (sección 3.6, E13): el secreto de un conector se cifra con AES-256-GCM y un nonce aleatorio; el AAD
 * ata el cifrado a su colegio, su tipo y su clave pública: copiado a la fila de otro colegio (o de otro conector) no se
 * descifra. Con otra clave tampoco. La pista son solo los 4 últimos caracteres.
 */
class SecretosConectoresTest {

	static final String CLAVE_A = Base64.getEncoder().encodeToString("clave-a-de-32-bytes-para-pruebas".getBytes(
			StandardCharsets.UTF_8));

	static final String CLAVE_B = Base64.getEncoder().encodeToString("clave-b-de-32-bytes-para-pruebas".getBytes(
			StandardCharsets.UTF_8));

	private static final String CLAVE_PUBLICA = "0f0e0d0c-0b0a-4908-8706-050403020100";

	private static final String TOKEN = "tok_1234567890abcdefXYZ9";

	private static SecretosConectores con(String clave, int version, String anterior) {
		return new SecretosConectores(new PropiedadesConectores(clave, version, anterior, 7));
	}

	@Test
	void cifraYDescifraConSuColegioTipoYClavePublica() {
		byte[] clave = CifradoAesGcm.leerClave(CLAVE_A);
		Aad aad = new Aad(3, "OSE", CLAVE_PUBLICA);
		byte[] cifrado = CifradoAesGcm.cifrar(clave, TOKEN.getBytes(StandardCharsets.UTF_8), aad);

		assertThat(new String(CifradoAesGcm.descifrar(clave, cifrado, aad), StandardCharsets.UTF_8)).isEqualTo(TOKEN);
		// nonce (12) + texto + etiqueta (16); el texto en claro no aparece en el cifrado.
		assertThat(cifrado).hasSize(12 + TOKEN.length() + 16);
		assertThat(new String(cifrado, StandardCharsets.ISO_8859_1)).doesNotContain(TOKEN).doesNotContain("1234");
		// Un nonce nuevo cada vez: el mismo secreto no da el mismo cifrado.
		assertThat(CifradoAesGcm.cifrar(clave, TOKEN.getBytes(StandardCharsets.UTF_8), aad)).isNotEqualTo(cifrado);
	}

	@Test
	void unSecretoCopiadoAOtroColegioNoSeDescifra() {
		byte[] clave = CifradoAesGcm.leerClave(CLAVE_A);
		byte[] cifrado = CifradoAesGcm.cifrar(clave, TOKEN.getBytes(StandardCharsets.UTF_8), new Aad(3, "OSE", CLAVE_PUBLICA));

		assertThatThrownBy(() -> CifradoAesGcm.descifrar(clave, cifrado, new Aad(4, "OSE", CLAVE_PUBLICA)))
				.isInstanceOf(SecretoIlegibleException.class).hasMessageNotContaining(TOKEN);
		assertThatThrownBy(() -> CifradoAesGcm.descifrar(clave, cifrado, new Aad(3, "PASARELA", CLAVE_PUBLICA)))
				.isInstanceOf(SecretoIlegibleException.class);
		assertThatThrownBy(() -> CifradoAesGcm.descifrar(clave, cifrado,
				new Aad(3, "OSE", "11111111-2222-4333-8444-555555555555"))).isInstanceOf(SecretoIlegibleException.class);
		// Con otra clave maestra, tampoco; y un byte cambiado se detecta (etiqueta GCM).
		assertThatThrownBy(() -> CifradoAesGcm.descifrar(CifradoAesGcm.leerClave(CLAVE_B), cifrado,
				new Aad(3, "OSE", CLAVE_PUBLICA))).isInstanceOf(SecretoIlegibleException.class);
		byte[] alterado = cifrado.clone();
		alterado[15] ^= 1;
		assertThatThrownBy(() -> CifradoAesGcm.descifrar(clave, alterado, new Aad(3, "OSE", CLAVE_PUBLICA)))
				.isInstanceOf(SecretoIlegibleException.class);
	}

	@Test
	void conVariosSecretosConNombreYLaVersionDeLaClave() {
		SecretosConectores secretos = con(CLAVE_A, 1, "");
		byte[] cifrado = secretos.cifrar(Map.of("llaveSecreta", "sk_test_abcdefghijklmnop", "secretoAviso",
				"whsec_0123456789abcdef"), 5, "PASARELA", CLAVE_PUBLICA);

		assertThat(secretos.version()).isEqualTo(1);
		assertThat(secretos.descifraVersion(1)).isTrue();
		assertThat(secretos.descifraVersion(2)).isFalse();
		assertThat(new String(CifradoAesGcm.descifrar(CifradoAesGcm.leerClave(CLAVE_A), cifrado,
				new Aad(5, "PASARELA", CLAVE_PUBLICA)), StandardCharsets.UTF_8)).contains("sk_test_abcdefghijklmnop",
						"whsec_0123456789abcdef");
	}

	@Test
	void durantelaRotacionDescifraLaVersionAnterior() {
		SecretosConectores rotando = con(CLAVE_B, 2, CLAVE_A);

		assertThat(rotando.descifraVersion(2)).isTrue();
		assertThat(rotando.descifraVersion(1)).isTrue();
		assertThat(rotando.descifraVersion(3)).isFalse();
		assertThatThrownBy(() -> new PropiedadesConectores(CLAVE_B, 1, CLAVE_A, 7))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2 o más");
	}

	@Test
	void sinClaveNoSeGuardaNingunSecreto() {
		SecretosConectores sinClave = con("", 1, "");

		assertThat(sinClave.disponible()).isFalse();
		assertThatThrownBy(() -> sinClave.cifrar(Map.of("token", TOKEN), 1, "OSE", CLAVE_PUBLICA))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("CC_CLAVE_CONECTORES");
	}

	@Test
	void unaClaveQueNoSonTreintaYDosBytesNoArranca() {
		assertThatThrownBy(() -> con(Base64.getEncoder().encodeToString(new byte[16]), 1, ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
		assertThatThrownBy(() -> con("esto no es base64 %%", 1, "")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("base64");
		assertThat(CifradoAesGcm.esDeDesarrollo("Y2xhdmUtY29uZWN0b3Jlcy1kZS1wcnVlYmFzLTAwMDE=")).isTrue();
		assertThat(CifradoAesGcm.esDeDesarrollo(CLAVE_A)).isFalse();
	}

	@Test
	void laPistaSonLosCuatroUltimosCaracteres() {
		assertThat(SecretosConectores.pista(TOKEN)).isEqualTo("XYZ9");
		assertThat(SecretosConectores.pista("  sk_live_1234567890abcd  ")).isEqualTo("abcd");
		assertThat(SecretosConectores.pista(null)).isNull();
		assertThat(new PropiedadesConectores(CLAVE_A, 1, "", 7).toString()).doesNotContain(CLAVE_A);
	}
}
