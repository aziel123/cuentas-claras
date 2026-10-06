package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.EnlaceActivacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.EnlaceActivacionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Genera el enlace de activación de UN solo uso (S4-M2): un token de 32 bytes al azar (SecureRandom) que solo existe en
 * el enlace; en la base queda su SHA-256. Anula los enlaces anteriores del mismo usuario. Lo usan los servicios
 * protegidos que dan o restablecen el acceso, dentro de su transacción.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class EnlacesActivacion {

	/** La ruta pública del enlace: {@code /activar/{colegio}/{token}}. */
	public static final String RUTA = "/activar";

	private static final SecureRandom AZAR = new SecureRandom();

	private final EnlaceActivacionRepository enlaces;

	private final Clock reloj;

	public EnlacesActivacion(EnlaceActivacionRepository enlaces, Clock reloj) {
		this.enlaces = enlaces;
		this.reloj = reloj;
	}

	/** El enlace generado (la ruta con el token, para entregarla) y hasta cuándo sirve. */
	public record Generado(String ruta, LocalDateTime venceEn) {
	}

	public Generado generar(Long colegioId, Long usuarioId, java.time.Duration vigencia) {
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		enlaces.findByUsuarioIdOrderByIdDesc(usuarioId).forEach(e -> {
			e.anular(ahora);
			enlaces.save(e);
		});
		byte[] bytes = new byte[32];
		AZAR.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		LocalDateTime vence = ahora.plus(vigencia);
		enlaces.save(EnlaceActivacion.nuevo(usuarioId, hash(token), vence, ipActual()));
		return new Generado(RUTA + "/" + colegioId + "/" + token, vence);
	}

	/** SHA-256 en hexadecimal (lo único que se guarda del token). */
	public static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(String.valueOf(token).getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}

	static String ipActual() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
			return atributos.getRequest().getRemoteAddr();
		}
		return null;
	}
}
