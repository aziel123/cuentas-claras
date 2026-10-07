package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.EnlaceActivacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PropositoEnlace;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.EnlaceActivacionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Enlace de activación de UN solo uso (S4-M2; sprint 5, A2): un token de 32 bytes al azar (SecureRandom) que solo existe
 * en el mensaje al titular; en la base queda su SHA-256 con el id de ese mensaje.
 * <ul>
 *   <li>{@link #generarParaMensaje}: lo llama SOLO el proceso de envío ({@code sistema.mensajeria}, regla ArchUnit) en
 *       la misma transacción en que envía el mensaje. Si el proveedor falla, todo se revierte y el siguiente intento
 *       genera otro token. Quien dio el acceso nunca lo ve.</li>
 *   <li>{@link #anularVigentes}: al restablecer un acceso, los enlaces anteriores dejan de servir.</li>
 * </ul>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class EnlacesActivacion {

	/** La ruta pública del enlace: {@code /activar/{colegio}/{token}}. */
	public static final String RUTA = "/activar";

	/** El trigger {@code trg_enlace_activacion_nace} no admite más de 72 h (con margen por la hora de creado_en). */
	public static final Duration VIGENCIA_MAXIMA = Duration.ofHours(71);

	private static final SecureRandom AZAR = new SecureRandom();

	private final EnlaceActivacionRepository enlaces;

	private final Clock reloj;

	public EnlacesActivacion(EnlaceActivacionRepository enlaces, Clock reloj) {
		this.enlaces = enlaces;
		this.reloj = reloj;
	}

	/**
	 * Genera el enlace del mensaje de activación {@code mensajeId} (PENDIENTE, para ESE titular: lo exige el trigger) y
	 * devuelve su ruta {@code /activar/{colegio}/{token}} SOLO al proceso de envío. Anula los enlaces anteriores del
	 * usuario que sigan vigentes.
	 */
	@PreAuthorize("hasRole('SISTEMA_MENSAJERIA')")
	public String generarParaMensaje(Long colegioId, Long mensajeId, Long usuarioId, PropositoEnlace proposito,
			Duration vigencia) {
		Objects.requireNonNull(colegioId, "colegioId");
		Duration duracion = vigencia == null || vigencia.compareTo(VIGENCIA_MAXIMA) > 0 ? VIGENCIA_MAXIMA : vigencia;
		LocalDateTime ahora = ahora();
		anular(usuarioId, ahora);
		byte[] bytes = new byte[32];
		AZAR.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		enlaces.save(EnlaceActivacion.paraMensaje(usuarioId, hash(token), ahora.plus(duracion), mensajeId, proposito));
		return RUTA + "/" + colegioId + "/" + token;
	}

	/** Restablecer un acceso: los enlaces que el usuario aún no usó dejan de servir. */
	public void anularVigentes(Long usuarioId) {
		anular(usuarioId, ahora());
	}

	private void anular(Long usuarioId, LocalDateTime ahora) {
		enlaces.findByUsuarioIdOrderByIdDesc(usuarioId).stream()
				.filter(e -> e.getUsadoEn() == null && e.getAnuladoEn() == null).forEach(e -> {
					e.anular(ahora);
					enlaces.save(e);
				});
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

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
