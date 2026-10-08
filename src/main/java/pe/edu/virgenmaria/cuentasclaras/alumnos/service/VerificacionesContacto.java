package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.VerificacionContacto;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.VerificacionContactoRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnlacesActivacion;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;

/**
 * Correcciones del sprint 5 (S5-A1): genera el enlace de verificación de un contacto. Como el de activación, lo llama SOLO
 * el proceso de envío ({@code sistema.mensajeria}, regla ArchUnit) en la misma transacción en que envía el mensaje: si el
 * proveedor falla, todo se revierte y el siguiente intento genera otro token. Nadie del colegio lo ve.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class VerificacionesContacto {

	/** La ruta pública del enlace: {@code /verificar/{colegio}/{token}}. */
	public static final String RUTA = "/verificar";

	private static final SecureRandom AZAR = new SecureRandom();

	private final VerificacionContactoRepository verificaciones;

	private final Clock reloj;

	public VerificacionesContacto(VerificacionContactoRepository verificaciones, Clock reloj) {
		this.verificaciones = verificaciones;
		this.reloj = reloj;
	}

	/**
	 * Genera el enlace del mensaje {@code mensajeId} (VERIFICACION_CONTACTO, PENDIENTE, a ESE contacto: lo exige el
	 * trigger) y devuelve su ruta solo al proceso de envío. Anula los enlaces anteriores del mismo contacto.
	 */
	@PreAuthorize("hasRole('SISTEMA_MENSAJERIA')")
	public String generarParaMensaje(Long colegioId, Long mensajeId, Long apoderadoId, boolean whatsapp, String contacto,
			Duration vigencia) {
		Objects.requireNonNull(colegioId, "colegioId");
		Duration duracion = vigencia == null || vigencia.compareTo(EnlacesActivacion.VIGENCIA_MAXIMA) > 0
				? EnlacesActivacion.VIGENCIA_MAXIMA : vigencia;
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		verificaciones.findByApoderadoIdOrderByIdDesc(apoderadoId).stream()
				.filter(v -> v.porWhatsapp() == whatsapp && v.getContacto().equals(contacto) && v.vigente(ahora))
				.forEach(v -> {
					v.anular(ahora);
					verificaciones.save(v);
				});
		byte[] bytes = new byte[32];
		AZAR.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		verificaciones.saveAndFlush(VerificacionContacto.paraMensaje(apoderadoId, whatsapp, contacto,
				EnlacesActivacion.hash(token), mensajeId, ahora.plus(duracion)));
		return RUTA + "/" + colegioId + "/" + token;
	}
}
