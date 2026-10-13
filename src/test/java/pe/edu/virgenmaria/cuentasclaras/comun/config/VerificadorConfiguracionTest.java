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
	// Sprint 8, tanda 1: toda configuración de despliegue de las pruebas lleva también la marca (en prod es obligatoria).
	private static final java.util.Map<String, String> DOS_USUARIOS = java.util.Map.of(VerificadorConfiguracion.DB_USUARIO,
			"cc_app", VerificadorConfiguracion.DB_SISTEMA_USUARIO, "cc_sistema", VerificadorConfiguracion.MARCA_NOMBRE,
			"Colegio al Día");

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

	// Sprint 8, tanda 1 (decisión 120): la marca es obligatoria en prod; las variables del promotor inicial solo avisan.

	@Test
	void enProduccionLaMarcaEsObligatoria() {
		for (String sinMarca : new String[] { null, "", "   " }) {
			java.util.Map<String, String> conf = config();
			conf.put(VerificadorConfiguracion.MARCA_NOMBRE, sinMarca);
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, conf)).as("'" + sinMarca + "'")
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("CC_MARCA_NOMBRE");
		}
		// Fuera de prod vale el nombre por defecto de Marca.
		java.util.Map<String, String> piloto = sinCorreo(VerificadorConfiguracion.WHATSAPP, "SIMULADO",
				VerificadorConfiguracion.CORREO, "SIMULADO");
		piloto.remove(VerificadorConfiguracion.MARCA_NOMBRE);
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "piloto" }, piloto))
				.doesNotThrowAnyException();
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "dev" }, CLAVE_DEV)).doesNotThrowAnyException();
	}

	// Sprint 8, tanda 2 (secciones 3.1 y 3.6, decisión 118): la clave de los conectores y la instalación compartida.

	/** 32 bytes que no son de desarrollo, en base64. */
	private static final String CLAVE_CONECTORES = java.util.Base64.getEncoder().encodeToString(
			"k3y-de-conectores-real-32-bytes!".getBytes(java.nio.charset.StandardCharsets.UTF_8));

	private static java.util.Map<String, String> compartida(String... pares) {
		java.util.Map<String, String> conf = config(VerificadorConfiguracion.INSTALACION, "COMPARTIDA",
				VerificadorConfiguracion.CLAVE_CONECTORES, CLAVE_CONECTORES, VerificadorConfiguracion.OPERADORES,
				"operador:operador@plataforma.pe");
		for (int i = 0; i < pares.length; i += 2) {
			conf.put(pares[i], pares[i + 1]);
		}
		return conf;
	}

	@Test
	void unaCompartidaConNubefactTokenEnElEntornoNoArranca() {
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, compartida()))
				.doesNotThrowAnyException();
		for (String secreto : new String[] { VerificadorConfiguracion.NUBEFACT_TOKEN,
				VerificadorConfiguracion.CULQI_LLAVE_SECRETA, VerificadorConfiguracion.CULQI_WEBHOOK_USUARIO,
				VerificadorConfiguracion.CULQI_WEBHOOK_CLAVE }) {
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, compartida(secreto,
					"un-secreto-del-entorno"))).as(secreto).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("no lleva secretos de conectores");
		}
		// Ni un OSE o una pasarela reales en el entorno (son de cada colegio).
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, compartida(
				VerificadorConfiguracion.COMPROBANTES, "NUBEFACT", VerificadorConfiguracion.NUBEFACT_RUTA,
				"https://api.nubefact.com/x"))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "test" }, compartida(
				VerificadorConfiguracion.PASARELA, "CULQI"))).isInstanceOf(IllegalStateException.class);
		// En una DEDICADA, la cuenta de Nubefact del entorno sigue valiendo (el colegio aún no registra la suya).
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, config(
				VerificadorConfiguracion.COMPROBANTES, "NUBEFACT", VerificadorConfiguracion.NUBEFACT_RUTA,
				"https://api.nubefact.com/x", VerificadorConfiguracion.NUBEFACT_TOKEN, "un-token-real")))
				.doesNotThrowAnyException();
	}

	/**
	 * Correcciones del sprint 8 (S8-B5): en una COMPARTIDA desplegada, la cuenta de depósito y la recaudación son de cada
	 * colegio (sus conectores): el entorno no las trae. Con los valores por defecto (sin configurar) arranca; en una
	 * DEDICADA, el entorno sigue valiendo.
	 */
	@Test
	void unaCompartidaConLaCuentaDeDepositoOLaRecaudacionEnElEntornoNoArranca() {
		String[][] casos = { { "cuentasclaras.caja.cuentas-deposito", "BCP Soles 191-1234567-0-12" },
				{ "cuentasclaras.recaudacion.banco", "BCP" }, { "cuentasclaras.recaudacion.modalidad", "SIN_BASE" },
				{ "cuentasclaras.conciliacion.patron-abono-recaudacion", "RECAUD" },
				{ "cuentasclaras.conciliacion.abono-recaudacion", "POR_PAGO" } };
		for (String[] caso : casos) {
			assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, compartida(caso[0],
					caso[1]))).as(caso[0]).isInstanceOf(IllegalStateException.class).hasMessageContaining(caso[0]);
			assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, config(caso[0], caso[1])))
					.as("dedicada " + caso[0]).doesNotThrowAnyException();
		}
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, compartida(
				"cuentasclaras.caja.cuentas-deposito", "", "cuentasclaras.recaudacion.banco", "GENERICO",
				"cuentasclaras.recaudacion.modalidad", "CON_BASE_DE_DEUDAS", "cuentasclaras.conciliacion.abono-recaudacion",
				"POR_LOTE"))).doesNotThrowAnyException();
	}

	@Test
	void unaCompartidaExigeLaClaveDeLosConectoresYOperadores() {
		java.util.Map<String, String> sinClave = compartida();
		sinClave.remove(VerificadorConfiguracion.CLAVE_CONECTORES);
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, sinClave))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("CC_CLAVE_CONECTORES");
		java.util.Map<String, String> sinOperadores = compartida();
		sinOperadores.remove(VerificadorConfiguracion.OPERADORES);
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, sinOperadores))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("CC_OPERADORES");
		// En una DEDICADA la clave es opcional (sin ella no se guarda un token) y el entorno sigue sirviendo.
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, config(
				VerificadorConfiguracion.INSTALACION, "DEDICADA"))).doesNotThrowAnyException();
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, config(
				VerificadorConfiguracion.INSTALACION, "MIXTA"))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void laClaveDeLosConectoresSonTreintaYDosBytesYNuncaLaDeDesarrolloEnProduccion() {
		String deDesarrollo = "Y2xhdmUtY29uZWN0b3Jlcy1kZXNhcnJvbGxvLTAwMDE=";
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, compartida(
				VerificadorConfiguracion.CLAVE_CONECTORES, deDesarrollo))).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("desarrollo");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, compartida(
				VerificadorConfiguracion.CLAVE_CONECTORES, java.util.Base64.getEncoder().encodeToString(new byte[16]))))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, compartida(
				VerificadorConfiguracion.CLAVE_CONECTORES_ANTERIOR, "no-es-base64!!"))).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CC_CLAVE_CONECTORES_ANTERIOR");
		java.util.Map<String, String> dev = new java.util.HashMap<>(java.util.Map.of(VerificadorConfiguracion.CLAVE_HMAC,
				CLAVE_DEV, VerificadorConfiguracion.INSTALACION, "COMPARTIDA", VerificadorConfiguracion.CLAVE_CONECTORES,
				deDesarrollo));
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "dev" }, dev)).doesNotThrowAnyException();
	}

	@Test
	void lasVariablesDelPromotorInicialYaNoSeUsanYSoloAvisan() {
		java.util.Map<String, String> conf = config("CC_PROMOTOR_USUARIO", "promotor", "CC_PROMOTOR_NOMBRE", "Ana",
				"CC_PROMOTOR_CLAVE", "una-clave", "CC_COLEGIO_ID", "1");
		assertThatCode(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, conf)).doesNotThrowAnyException();
		org.assertj.core.api.Assertions.assertThat(VerificadorConfiguracion.variablesRetiradas(conf::get))
				.containsExactly("CC_PROMOTOR_USUARIO", "CC_PROMOTOR_NOMBRE", "CC_PROMOTOR_CLAVE", "CC_COLEGIO_ID");
		org.assertj.core.api.Assertions.assertThat(VerificadorConfiguracion.variablesRetiradas(config()::get)).isEmpty();
	}
}
