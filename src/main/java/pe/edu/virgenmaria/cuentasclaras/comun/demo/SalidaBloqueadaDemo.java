package pe.edu.virgenmaria.cuentasclaras.comun.demo;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Sprint 8, tanda 3 (sección 8.3, E20): en el perfil {@code demo} la aplicación no sale a internet aunque alguien la
 * despliegue mal. Es la capa de la aplicación; la de la red es la red interna de Docker ({@code internal: true}) del
 * despliegue de la demo, probada por el job {@code demo} del CI. Son independientes:
 * <ul>
 *   <li>{@code VerificadorConfiguracion} no deja arrancar la demo con un proveedor real (OSE, pasarela, WhatsApp o
 *       correo) ni con un secreto real en el entorno: los adaptadores reales ni se crean;</li>
 *   <li>aquí, el {@code RestClient.Builder} y el {@code JavaMailSender} de la aplicación fallan siempre, y el selector de
 *       proxy por defecto de la JVM manda toda conexión que no sea local a un proxy que no existe ({@code 127.0.0.1:9}).
 *       Solo se admite lo local (la propia aplicación).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class SalidaBloqueadaDemo {

	private static final Logger LOG = LoggerFactory.getLogger(SalidaBloqueadaDemo.class);

	/** Adonde va toda conexión saliente en la demo: un puerto local donde no escucha nadie (discard). */
	static final InetSocketAddress PROXY_INEXISTENTE = new InetSocketAddress("127.0.0.1", 9);

	static final String MENSAJE = "Salida bloqueada: la demo no envía mensajes ni se conecta a ningún servicio externo.";

	@PostConstruct
	void bloquearSalidaDeLaJvm() {
		ProxySelector.setDefault(new SelectorSinSalida());
		for (String protocolo : List.of("http", "https")) {
			System.setProperty(protocolo + ".proxyHost", PROXY_INEXISTENTE.getHostString());
			System.setProperty(protocolo + ".proxyPort", Integer.toString(PROXY_INEXISTENTE.getPort()));
		}
		System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]");
		LOG.info("Demo: salida a internet bloqueada en la aplicación (proxy inexistente, RestClient y correo bloqueados).");
	}

	/** El constructor de clientes HTTP que la aplicación pudiera inyectar: cada petición falla sin salir. */
	@Bean
	@Primary
	RestClient.Builder restClientSinSalida() {
		return RestClient.builder().requestInterceptor((peticion, cuerpo, ejecucion) -> {
			throw new IOException(MENSAJE + " (" + peticion.getURI().getHost() + ")");
		});
	}

	/** El correo de salida: nunca envía. */
	@Bean
	@Primary
	JavaMailSender correoSinSalida() {
		return new CorreoSinSalida();
	}

	/** Toda conexión que no es local va al proxy inexistente (la conexión falla); lo local, directo. */
	static final class SelectorSinSalida extends ProxySelector {

		@Override
		public List<Proxy> select(URI uri) {
			String host = uri == null || uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			if (local(host)) {
				return List.of(Proxy.NO_PROXY);
			}
			return List.of(new Proxy(Proxy.Type.HTTP, PROXY_INEXISTENTE));
		}

		@Override
		public void connectFailed(URI uri, SocketAddress direccion, IOException error) {
			// Esperado: no hay salida.
		}

		static boolean local(String host) {
			return host.equals("localhost") || host.startsWith("127.") || host.equals("[::1]") || host.equals("::1");
		}
	}

	/** Un {@code JavaMailSender} que falla en cada envío. */
	static final class CorreoSinSalida implements JavaMailSender {

		@Override
		public MimeMessage createMimeMessage() {
			return new MimeMessage((jakarta.mail.Session) null);
		}

		@Override
		public MimeMessage createMimeMessage(InputStream contenido) {
			throw new MailSendException(MENSAJE);
		}

		@Override
		public void send(MimeMessage... mensajes) {
			throw new MailSendException(MENSAJE);
		}

		@Override
		public void send(SimpleMailMessage... mensajes) {
			throw new MailSendException(MENSAJE);
		}
	}
}
