package pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.config.PropiedadesMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ProveedorMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ResultadoEnvio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Sprint 5: el correo de respaldo por SMTP (con un {@link JavaMailSender} falso). Apagado por defecto. */
class CorreoSmtpContratoTest {

	private static PropiedadesMensajeria.Correo cuenta(boolean permitir, String prueba) {
		return new PropiedadesMensajeria.Correo("SMTP", "avisos@virgenmaria.edu.pe", permitir, prueba);
	}

	@Test
	void enviaDesdeElRemitenteDelColegioYDevuelveUnId() {
		JavaMailSender servidor = mock(JavaMailSender.class);
		CorreoSmtp correo = new CorreoSmtp(servidor, cuenta(false, ""), true);

		ResultadoEnvio r = correo.enviar("rosa@gmail.com", "Registramos su pago", "Colegio Virgen María: registramos...");

		ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(servidor).send(enviado.capture());
		assertThat(enviado.getValue().getFrom()).isEqualTo("avisos@virgenmaria.edu.pe");
		assertThat(enviado.getValue().getTo()).containsExactly("rosa@gmail.com");
		assertThat(r.aceptado()).isTrue();
		assertThat(r.proveedor()).isEqualTo(ProveedorMensajeria.SMTP);
	}

	@Test
	void servidorCaidoEsReintentoYCorreoInvalidoEsDefinitivo() {
		JavaMailSender servidor = mock(JavaMailSender.class);
		doThrow(new MailSendException("sin conexión")).when(servidor).send(any(SimpleMailMessage.class));
		CorreoSmtp correo = new CorreoSmtp(servidor, cuenta(false, ""), true);

		assertThat(correo.enviar("rosa@gmail.com", "a", "b").tipo()).isEqualTo(ResultadoEnvio.Tipo.ERROR_REINTENTABLE);
		assertThat(correo.enviar("no-es-un-correo", "a", "b").tipo()).isEqualTo(ResultadoEnvio.Tipo.ERROR_DEFINITIVO);
	}

	@Test
	void fueraDeProdSoloACorreosDePrueba() {
		JavaMailSender servidor = mock(JavaMailSender.class);
		CorreoSmtp correo = new CorreoSmtp(servidor, cuenta(true, "prueba@colegio.pe"), false);

		assertThat(correo.enviar("rosa@gmail.com", "a", "b").tipo()).isEqualTo(ResultadoEnvio.Tipo.ERROR_DEFINITIVO);
		verify(servidor, never()).send(any(SimpleMailMessage.class));
		assertThat(correo.enviar("Prueba@Colegio.pe", "a", "b").aceptado()).isTrue();
		assertThatThrownBy(() -> new CorreoSmtp(servidor, cuenta(false, "prueba@colegio.pe"), false))
				.isInstanceOf(IllegalStateException.class);
	}
}
