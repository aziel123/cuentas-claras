package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificadorConfiguracionTest {

	private static final String CLAVE_DEV = "clave-hmac-de-desarrollo-no-usar-en-produccion-01";

	private static final String CLAVE_REAL = "k3y-real-del-gestor-de-secretos-0123456789abcdef";

	@Test
	void sinPerfilNoArranca() {
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[0], CLAVE_REAL))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("SPRING_PROFILES_ACTIVE");
	}

	@Test
	void enProduccionNoAceptaLaClaveDeDesarrolloNiUnaClaveVacia() {
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, CLAVE_DEV))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("AUDITORIA_CLAVE_HMAC");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, (String) null))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "otro" }, CLAVE_DEV))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void laAplicacionSinPerfilSeNiegaAntesDeTocarLaBase() {
		org.springframework.boot.SpringApplication aplicacion = new org.springframework.boot.SpringApplication(
				pe.edu.virgenmaria.cuentasclaras.CuentasClarasApplication.class);
		aplicacion.addListeners(new VerificadorConfiguracion.AlPrepararEntorno());
		aplicacion.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);

		assertThatThrownBy(() -> aplicacion.run()).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No hay perfil activo");
	}

	@Test
	void conPerfilYClaveCorrectosArranca() {
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, CLAVE_REAL))
				.doesNotThrowAnyException();
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "dev" }, CLAVE_DEV))
				.doesNotThrowAnyException();
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "test", "mysql" }, CLAVE_DEV))
				.doesNotThrowAnyException();
	}

	// Sprint 4, tanda 1: perfiles de despliegue, pasarela y OSE real.

	private static java.util.Map<String, String> config(String... pares) {
		java.util.Map<String, String> mapa = new java.util.HashMap<>();
		mapa.put(VerificadorConfiguracion.CLAVE_HMAC, CLAVE_REAL);
		for (int i = 0; i < pares.length; i += 2) {
			mapa.put(pares[i], pares[i + 1]);
		}
		return mapa;
	}

	@Test
	void prodYPilotoNoSeCombinanConDevNiTestNiEntreSi() {
		for (String[] perfiles : new String[][] { { "prod", "dev" }, { "prod", "test" }, { "piloto", "dev" },
				{ "prod", "piloto" } }) {
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(perfiles, config()))
					.as(String.join(",", perfiles)).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("no se combinan");
		}
	}

	@Test
	void laPasarelaSimuladaNuncaArrancaEnProduccion() {
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" },
				config(VerificadorConfiguracion.PASARELA, "SIMULADA")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("nunca en producción");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "otro" },
				config(VerificadorConfiguracion.PASARELA, "simulada")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("SIMULADA");
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "dev" },
				config(VerificadorConfiguracion.PASARELA, "SIMULADA"))).doesNotThrowAnyException();
	}

	/** S4-M1: en el piloto, la simulada exige además la marca del entorno (franja «PILOTO» en todas las páginas). */
	@Test
	void enElPilotoLaSimuladaExigeLaMarcaPilotoYUnSecretoPropio() {
		String secreto = "un-secreto-propio-del-piloto-0123456789";
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" },
				config(VerificadorConfiguracion.PASARELA, "SIMULADA", VerificadorConfiguracion.SECRETO_SIMULADA, secreto)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("cuentasclaras.entorno.nombre: PILOTO");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" },
				config(VerificadorConfiguracion.PASARELA, "SIMULADA", VerificadorConfiguracion.ENTORNO, "PRUEBAS",
						VerificadorConfiguracion.SECRETO_SIMULADA, secreto)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("PILOTO");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" },
				config(VerificadorConfiguracion.PASARELA, "SIMULADA", VerificadorConfiguracion.ENTORNO, "PILOTO")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("PASARELA_SIMULADA_SECRETO");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" },
				config(VerificadorConfiguracion.PASARELA, "SIMULADA", VerificadorConfiguracion.ENTORNO, "PILOTO",
						VerificadorConfiguracion.SECRETO_SIMULADA, "secreto-no-usar-en-produccion")))
				.isInstanceOf(IllegalStateException.class);
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" },
				config(VerificadorConfiguracion.PASARELA, "SIMULADA", VerificadorConfiguracion.ENTORNO, "PILOTO",
						VerificadorConfiguracion.SECRETO_SIMULADA, secreto))).doesNotThrowAnyException();
	}

	@Test
	void unaPasarelaRealExigeSusLlavesYLaLlaveDelEntornoCorrecto() {
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" },
				config(VerificadorConfiguracion.PASARELA, "IZIPAY")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("todavía no está integrada");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" },
				config(VerificadorConfiguracion.PASARELA, "CULQI")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("PASARELA_LLAVE_SECRETA");
		String[] credenciales = { VerificadorConfiguracion.PASARELA, "CULQI", VerificadorConfiguracion.CULQI_WEBHOOK_USUARIO,
				"u", VerificadorConfiguracion.CULQI_WEBHOOK_CLAVE, "c", VerificadorConfiguracion.CULQI_LLAVE_SECRETA, null };
		credenciales[7] = "sk_test_abc";
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, config(credenciales)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("sk_live_");
		credenciales[7] = "sk_live_abc";
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" }, config(credenciales)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("sk_test_");
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, config(credenciales)))
				.doesNotThrowAnyException();
	}

	@Test
	void nubefactSoloConRutaHttpsDeLaListaYTokenYFueraDeProdSoloSiSePermite() {
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" },
				config(VerificadorConfiguracion.COMPROBANTES, "NUBEFACT")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("NUBEFACT_TOKEN");
		for (String ruta : new String[] { "http://api.nubefact.com/api/v1/x", "https://api.nubefact.com.evil.pe/api",
				"https://evil.pe/api.nubefact.com", "https://usuario@evil.pe/api" }) {
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" },
					config(VerificadorConfiguracion.COMPROBANTES, "NUBEFACT", VerificadorConfiguracion.NUBEFACT_RUTA, ruta,
							VerificadorConfiguracion.NUBEFACT_TOKEN, "t"))).as(ruta)
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("lista cerrada");
		}
		String[] buena = { VerificadorConfiguracion.COMPROBANTES, "NUBEFACT", VerificadorConfiguracion.NUBEFACT_RUTA,
				"https://api.nubefact.com/api/v1/abc", VerificadorConfiguracion.NUBEFACT_TOKEN, "t" };
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, config(buena)))
				.doesNotThrowAnyException();
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" }, config(buena)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("permitir-real-fuera-de-prod");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" },
				config(VerificadorConfiguracion.COMPROBANTES, "OTRO"))).isInstanceOf(IllegalStateException.class);
	}
}
