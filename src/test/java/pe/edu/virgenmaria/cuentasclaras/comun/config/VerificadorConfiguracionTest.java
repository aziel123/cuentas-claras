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
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, config()))
				.doesNotThrowAnyException();
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "dev" }, CLAVE_DEV))
				.doesNotThrowAnyException();
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "test", "mysql" }, CLAVE_DEV))
				.doesNotThrowAnyException();
	}

	// Sprint 4, tanda 1: perfiles de despliegue, pasarela y OSE real.

	private static final java.util.Map<String, String> CORREO_REAL = java.util.Map.of(VerificadorConfiguracion.CORREO,
			"SMTP", VerificadorConfiguracion.SMTP_HOST, "smtp.colegio.pe", VerificadorConfiguracion.CORREO_REMITENTE,
			"avisos@colegio.pe", VerificadorConfiguracion.CORREO_REAL_FUERA_DE_PROD, "true",
			VerificadorConfiguracion.CORREO_PRUEBA, "prueba@colegio.pe");

	private static java.util.Map<String, String> sinCorreo(String... pares) {
		java.util.Map<String, String> mapa = new java.util.HashMap<>();
		mapa.put(VerificadorConfiguracion.CLAVE_HMAC, CLAVE_REAL);
		mapa.putAll(DOS_USUARIOS);
		for (int i = 0; i < pares.length; i += 2) {
			mapa.put(pares[i], pares[i + 1]);
		}
		return mapa;
	}

	/** Sprint 7, tanda 1: en prod el respaldo simulado no cuenta y la falta de respaldo siempre es alerta. */
	@Test
	void enProduccionElRespaldoSimuladoNoCuentaYElRespaldoSeExige() {
		java.util.Map<String, String> prod = new java.util.HashMap<>(sinCorreo());
		prod.putAll(CORREO_REAL);
		prod.remove(VerificadorConfiguracion.CORREO_REAL_FUERA_DE_PROD);
		prod.remove(VerificadorConfiguracion.CORREO_PRUEBA);
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, prod)).doesNotThrowAnyException();

		java.util.Map<String, String> simulado = new java.util.HashMap<>(prod);
		simulado.put(VerificadorConfiguracion.RESPALDO_SIMULADO, "true");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, simulado))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("aceptar-respaldo-simulado");

		java.util.Map<String, String> sinExigir = new java.util.HashMap<>(prod);
		sinExigir.put(VerificadorConfiguracion.RESPALDO_EXIGIDO, "false");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, sinExigir))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("respaldo-exigido");

		java.util.Map<String, String> piloto = sinCorreo(VerificadorConfiguracion.WHATSAPP, "SIMULADO",
				VerificadorConfiguracion.CORREO, "SIMULADO", VerificadorConfiguracion.RESPALDO_SIMULADO, "true");
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" }, piloto))
				.doesNotThrowAnyException();
	}

	// Sprint 5, sección 8.3: la mensajería simulada nunca en prod; prod sin canal real no arranca.


	@Test
	void mensajeriaSimuladaNuncaArrancaEnProduccion() {
		for (String canal : new String[] { VerificadorConfiguracion.WHATSAPP, VerificadorConfiguracion.CORREO }) {
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" },
					sinCorreo(canal, "SIMULADO", VerificadorConfiguracion.WHATSAPP_REAL_FUERA_DE_PROD, "false")))
					.as(canal).isInstanceOf(IllegalStateException.class).hasMessageContaining("SIMULADA");
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "otro" }, sinCorreo(canal, "SIMULADO")))
					.as(canal).isInstanceOf(IllegalStateException.class).hasMessageContaining("SIMULADA");
		}
		for (String perfil : new String[] { "dev", "test", "piloto" }) {
			java.util.Map<String, String> conf = sinCorreo(VerificadorConfiguracion.WHATSAPP, "SIMULADO",
					VerificadorConfiguracion.CORREO, "SIMULADO");
			if (perfil.equals("piloto")) {
				conf.put(VerificadorConfiguracion.CLAVE_HMAC, CLAVE_REAL);
			}
			else {
				conf.put(VerificadorConfiguracion.CLAVE_HMAC, CLAVE_DEV);
			}
			assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { perfil }, conf)).as(perfil)
					.doesNotThrowAnyException();
		}
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" },
				sinCorreo(VerificadorConfiguracion.WHATSAPP, "TWILIO")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("no existe");
	}

	@Test
	void prodSinCanalRealNoArranca() {
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, sinCorreo()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("al menos un canal real");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" },
				sinCorreo(VerificadorConfiguracion.CORREO, "NINGUNO", VerificadorConfiguracion.WHATSAPP, "NINGUNO")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("al menos un canal real");
		// SMTP sin servidor o sin remitente, o WhatsApp sin sus credenciales, tampoco.
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" },
				sinCorreo(VerificadorConfiguracion.CORREO, "SMTP", VerificadorConfiguracion.CORREO_REMITENTE, "a@b.pe")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("spring.mail.host");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" },
				sinCorreo(VerificadorConfiguracion.WHATSAPP, "WHATSAPP_CLOUD", VerificadorConfiguracion.WHATSAPP_API,
						"https://graph.facebook.com", VerificadorConfiguracion.WHATSAPP_TOKEN, "t")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("WHATSAPP_SECRETO_APP");
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, whatsappReal()))
				.doesNotThrowAnyException();
		// Nadie apunta «WhatsApp» a un servidor propio.
		java.util.Map<String, String> propio = whatsappReal();
		propio.put(VerificadorConfiguracion.WHATSAPP_API, "https://evil.pe");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, propio))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("dominio permitido");
	}

	@Test
	void realFueraDeProdSoloANumerosDePrueba() {
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" }, whatsappReal()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("números de prueba");
		java.util.Map<String, String> conMarcaSinLista = whatsappReal();
		conMarcaSinLista.put(VerificadorConfiguracion.WHATSAPP_REAL_FUERA_DE_PROD, "true");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" }, conMarcaSinLista))
				.isInstanceOf(IllegalStateException.class);
		java.util.Map<String, String> conLista = whatsappReal();
		conLista.put(VerificadorConfiguracion.WHATSAPP_REAL_FUERA_DE_PROD, "true");
		conLista.put(VerificadorConfiguracion.WHATSAPP_PRUEBA, "51966777321");
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" }, conLista))
				.doesNotThrowAnyException();
		java.util.Map<String, String> correo = sinCorreo(VerificadorConfiguracion.CORREO, "SMTP",
				VerificadorConfiguracion.SMTP_HOST, "smtp.colegio.pe", VerificadorConfiguracion.CORREO_REMITENTE,
				"avisos@colegio.pe");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" }, correo))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("correos de prueba");
	}

	private static java.util.Map<String, String> whatsappReal() {
		return sinCorreo(VerificadorConfiguracion.WHATSAPP, "WHATSAPP_CLOUD", VerificadorConfiguracion.WHATSAPP_API,
				"https://graph.facebook.com", VerificadorConfiguracion.WHATSAPP_NUMERO_ID, "1234567890",
				VerificadorConfiguracion.WHATSAPP_TOKEN, "token-real", VerificadorConfiguracion.WHATSAPP_SECRETO, "secreto",
				VerificadorConfiguracion.WHATSAPP_VERIFICACION, "verifica");
	}

	/** Sprint 7, tanda 2: prod y piloto usan dos usuarios de base (cc_app y cc_sistema). */
	private static final java.util.Map<String, String> DOS_USUARIOS = java.util.Map.of(VerificadorConfiguracion.DB_USUARIO,
			"cc_app", VerificadorConfiguracion.DB_SISTEMA_USUARIO, "cc_sistema");

	private static java.util.Map<String, String> config(String... pares) {
		java.util.Map<String, String> mapa = new java.util.HashMap<>();
		mapa.put(VerificadorConfiguracion.CLAVE_HMAC, CLAVE_REAL);
		mapa.putAll(DOS_USUARIOS);
		// Sprint 5: prod no arranca sin un canal real para avisar a las familias (decisión 40).
		mapa.putAll(CORREO_REAL);
		for (int i = 0; i < pares.length; i += 2) {
			mapa.put(pares[i], pares[i + 1]);
		}
		return mapa;
	}

	/**
	 * Sprint 7, tanda 2 (sección 3.2): en prod y piloto, sin el usuario de los procesos y la identidad (cc_sistema) o con el
	 * mismo usuario para las personas y para el sistema, no arranca. En dev y test (H2) no hace falta.
	 */
	@Test
	void enProdYPilotoExigeDosUsuariosDeBaseDistintos() {
		for (String perfil : new String[] { "prod", "piloto" }) {
			java.util.Map<String, String> sinSistema = new java.util.HashMap<>(config(VerificadorConfiguracion.ENTORNO, "PILOTO"));
			sinSistema.remove(VerificadorConfiguracion.DB_SISTEMA_USUARIO);
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { perfil }, sinSistema)).as(perfil)
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("DB_SISTEMA_USUARIO");
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { perfil },
					config(VerificadorConfiguracion.ENTORNO, "PILOTO", VerificadorConfiguracion.DB_SISTEMA_USUARIO, "CC_APP")))
					.as(perfil).isInstanceOf(IllegalStateException.class).hasMessageContaining("no pueden ser el mismo usuario");
		}
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "test", "mysql" }, CLAVE_DEV))
				.doesNotThrowAnyException();
	}

	/**
	 * Sprint 7, tanda 3 (decisión 103): la cookie __Host-CCSESION exige Secure (sin ella el navegador la rechaza) y, en prod
	 * y piloto con Secure, la cookie debe llevar el prefijo. La instalación local por http usa CCSESION sin Secure.
	 */
	@Test
	void laCookieDeSesionHostExigeSecureYEnProdSiempreLlevaElPrefijo() {
		for (String perfil : new String[] { "prod", "piloto" }) {
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { perfil }, config(
					VerificadorConfiguracion.ENTORNO, "PILOTO", VerificadorConfiguracion.COOKIE_NOMBRE, "CCSESION",
					VerificadorConfiguracion.COOKIE_SEGURA, "true"))).as(perfil)
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("__Host-CCSESION");
			assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { perfil }, config(
					VerificadorConfiguracion.ENTORNO, "PILOTO", VerificadorConfiguracion.COOKIE_NOMBRE, "__Host-CCSESION",
					VerificadorConfiguracion.COOKIE_SEGURA, "true"))).as(perfil).doesNotThrowAnyException();
			// La instalación local en Docker (http): sin Secure y sin el prefijo.
			assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { perfil }, config(
					VerificadorConfiguracion.ENTORNO, "PILOTO", VerificadorConfiguracion.COOKIE_NOMBRE, "CCSESION",
					VerificadorConfiguracion.COOKIE_SEGURA, "false"))).as(perfil).doesNotThrowAnyException();
		}
		for (String perfil : new String[] { "prod", "dev" }) {
			java.util.Map<String, String> mal = new java.util.HashMap<>(config(VerificadorConfiguracion.ENTORNO, "PILOTO",
					VerificadorConfiguracion.COOKIE_NOMBRE, "__Host-CCSESION", VerificadorConfiguracion.COOKIE_SEGURA,
					"false"));
			if (perfil.equals("dev")) {
				mal = new java.util.HashMap<>(java.util.Map.of(VerificadorConfiguracion.CLAVE_HMAC, CLAVE_DEV,
						VerificadorConfiguracion.COOKIE_NOMBRE, "__Host-CCSESION", VerificadorConfiguracion.COOKIE_SEGURA,
						"false"));
			}
			java.util.Map<String, String> configuracion = mal;
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { perfil }, configuracion)).as(perfil)
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("exige Secure");
		}
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
