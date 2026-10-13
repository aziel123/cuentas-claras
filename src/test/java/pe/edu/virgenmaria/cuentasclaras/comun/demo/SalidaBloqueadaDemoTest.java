package pe.edu.virgenmaria.cuentasclaras.comun.demo;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.client.ResourceAccessException;

import java.net.Proxy;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 8, tanda 3 (sección 8.3, E20): la capa de la aplicación de la demo no sale a internet. El constructor de clientes
 * HTTP y el correo fallan siempre (sin abrir ninguna conexión) y el selector de proxy manda toda conexión que no es local a
 * un proxy que no existe. La capa de la red (Docker {@code internal: true}) la prueba el job {@code demo} del CI.
 */
class SalidaBloqueadaDemoTest {

	private final SalidaBloqueadaDemo salida = new SalidaBloqueadaDemo();

	@Test
	void elClienteHttpDeLaAplicacionNoSale() {
		assertThatThrownBy(() -> salida.restClientSinSalida().build().get().uri("https://graph.facebook.com/v21.0/me")
				.retrieve().toBodilessEntity()).isInstanceOf(ResourceAccessException.class)
				.hasMessageContaining("Salida bloqueada").hasMessageContaining("graph.facebook.com");
	}

	@Test
	void elCorreoNoSale() {
		SimpleMailMessage mensaje = new SimpleMailMessage();
		mensaje.setTo("padre@example.com");
		mensaje.setText("Aviso de pago");
		assertThatThrownBy(() -> salida.correoSinSalida().send(mensaje)).isInstanceOf(MailSendException.class)
				.hasMessageContaining("Salida bloqueada");
	}

	@Test
	void todaConexionQueNoEsLocalVaAUnProxyQueNoExiste() {
		SalidaBloqueadaDemo.SelectorSinSalida selector = new SalidaBloqueadaDemo.SelectorSinSalida();
		for (String externa : List.of("https://graph.facebook.com/", "https://api.nubefact.com/api/v1/x",
				"http://169.254.169.254/computeMetadata/v1/", "https://smtp.gmail.com")) {
			List<Proxy> proxies = selector.select(URI.create(externa));
			assertThat(proxies).as(externa).singleElement().satisfies(p -> {
				assertThat(p.type()).isEqualTo(Proxy.Type.HTTP);
				assertThat(p.address()).isEqualTo(SalidaBloqueadaDemo.PROXY_INEXISTENTE);
			});
		}
		for (String local : List.of("http://localhost:8080/", "http://127.0.0.1:8080/actuator/health")) {
			assertThat(selector.select(URI.create(local))).as(local).containsExactly(Proxy.NO_PROXY);
		}
	}
}
