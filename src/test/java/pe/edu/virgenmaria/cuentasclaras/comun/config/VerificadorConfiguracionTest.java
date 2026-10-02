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
		assertThatThrownBy(() -> VerificadorConfiguracion.verificar(new String[] { "prod" }, null))
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
}
