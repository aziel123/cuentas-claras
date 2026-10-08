package pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.config.PropiedadesMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ProveedorMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ProveedorCorreo;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ResultadoEnvio;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * Correo real por SMTP ({@code spring-boot-starter-mail}, decisión 39) con la cuenta del colegio. APAGADO por defecto:
 * solo existe con {@code cuentasclaras.mensajeria.correo.proveedor: SMTP} y el {@code spring.mail.host}. Fuera de
 * {@code prod} SOLO escribe a los {@code correos-de-prueba}. Un correo pasa a ENVIADO cuando el servidor SMTP lo acepta
 * (no hay avisos de entrega).
 */
@Component
@ConditionalOnProperty(name = "cuentasclaras.mensajeria.correo.proveedor", havingValue = "SMTP")
public class CorreoSmtp implements ProveedorCorreo {

	private static final Logger LOG = LoggerFactory.getLogger(CorreoSmtp.class);

	private final JavaMailSender servidor;

	private final PropiedadesMensajeria.Correo cuenta;

	private final boolean produccion;

	@Autowired
	public CorreoSmtp(JavaMailSender servidor, PropiedadesMensajeria propiedades, Environment entorno) {
		this(servidor, propiedades.correo(), Arrays.asList(entorno.getActiveProfiles()).contains("prod"));
	}

	CorreoSmtp(JavaMailSender servidor, PropiedadesMensajeria.Correo cuenta, boolean produccion) {
		this.servidor = servidor;
		this.cuenta = cuenta;
		this.produccion = produccion;
		if (cuenta.remitente() == null || !cuenta.remitente().contains("@")) {
			throw new IllegalStateException("El correo de respaldo necesita el remitente del colegio (CORREO_REMITENTE).");
		}
		if (!produccion && (!cuenta.permitirRealFueraDeProd() || cuenta.listaDePrueba().isEmpty())) {
			throw new IllegalStateException("Fuera de producción, el correo real exige permitir-real-fuera-de-prod y una "
					+ "lista de correos de prueba.");
		}
	}

	@Override
	public ResultadoEnvio enviar(String destino, String asunto, String cuerpo) {
		if (destino == null || !destino.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
			return ResultadoEnvio.definitivo("El correo no es válido.");
		}
		if (!produccion && !cuenta.listaDePrueba().contains(destino.toLowerCase(Locale.ROOT))) {
			return ResultadoEnvio.definitivo("Fuera de producción solo se escribe a los correos de prueba.");
		}
		SimpleMailMessage correo = new SimpleMailMessage();
		correo.setFrom(cuenta.remitente());
		correo.setTo(destino);
		correo.setSubject(asunto);
		correo.setText(cuerpo);
		try {
			servidor.send(correo);
			return ResultadoEnvio.aceptado(ProveedorMensajeria.SMTP, "smtp-" + UUID.randomUUID());
		}
		catch (MailParseException | MailPreparationException e) {
			return ResultadoEnvio.definitivo("El servidor de correo rechazó el mensaje.");
		}
		catch (MailAuthenticationException e) {
			LOG.warn("El servidor de correo rechazó las credenciales: se reintentará.");
			return ResultadoEnvio.reintentable("El servidor de correo rechazó las credenciales.");
		}
		catch (MailException e) {
			LOG.warn("No se pudo enviar un correo: {}", e.getClass().getSimpleName());
			return ResultadoEnvio.reintentable("Sin respuesta del servidor de correo (" + e.getClass().getSimpleName() + ").");
		}
	}
}
