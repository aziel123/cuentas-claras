package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Sella cada evento con HMAC-SHA256 encadenado con el hash del evento anterior. Sin la clave
 * (variable {@code AUDITORIA_CLAVE_HMAC}) no se puede recalcular la cadena desde la base, así
 * que editar, borrar o insertar eventos directamente se detecta.
 * <p>
 * Forma canónica: cada campo como {@code longitud:valor|} ({@code -1:|} si es nulo), en este orden fijo:
 * hashAnterior, secuencia, colegioId, ocurridoEn, usuarioId, nombreUsuario, roles, accion, entidad,
 * entidadId, valorAnterior, valorNuevo, detalle, ip. La longitud evita ambigüedades entre campos.
 * <b>No cambies este formato</b>: invalidaría toda la cadena existente.
 */
@Component
public class SelladorAuditoria {

	public static final int LONGITUD_MINIMA_CLAVE = 32;

	private static final String ALGORITMO = "HmacSHA256";

	private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS");

	private final SecretKeySpec clave;

	public SelladorAuditoria(@Value("${cuentasclaras.auditoria.clave-hmac}") String clave) {
		if (clave == null || clave.length() < LONGITUD_MINIMA_CLAVE) {
			throw new IllegalStateException("La clave HMAC de auditoría (AUDITORIA_CLAVE_HMAC) debe tener al menos "
					+ LONGITUD_MINIMA_CLAVE + " caracteres");
		}
		this.clave = new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), ALGORITMO);
	}

	/** Hash hexadecimal (64 caracteres) del evento encadenado con {@code hashAnterior}. */
	public String sellar(String hashAnterior, EventoAuditoria evento) {
		return sellar(hashAnterior, CamposSellados.de(evento));
	}

	/**
	 * Lo mismo, desde los campos leídos de la base sin JPA. Sprint 7: el modo {@code verificar-respaldo} recorre con JDBC
	 * la cadena de una copia restaurada, sin levantar la aplicación. Misma forma canónica.
	 */
	public String sellar(String hashAnterior, CamposSellados campos) {
		try {
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(clave);
			byte[] firma = mac.doFinal(formaCanonica(hashAnterior, campos).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(firma);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo calcular el HMAC de auditoría", e);
		}
	}

	/**
	 * Código corto (12 hexadecimales) que identifica un texto sin revelarlo: HMAC con la misma clave.
	 * Se usa para registrar el nombre que escribió alguien que no es usuario (podría ser su clave tecleada
	 * en el campo equivocado): los intentos repetidos con el mismo texto tienen el mismo código.
	 */
	public String codigoDeTexto(String texto) {
		try {
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(clave);
			byte[] firma = mac.doFinal(("texto-intentado|" + (texto == null ? "" : texto)).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(firma).substring(0, 12);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo calcular el HMAC de auditoría", e);
		}
	}

	/** Comparación en tiempo constante del hash guardado contra el recalculado. */
	public boolean esValido(String hashAnterior, EventoAuditoria evento) {
		String esperado = sellar(hashAnterior, evento);
		String guardado = evento.getHash() == null ? "" : evento.getHash();
		return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.US_ASCII),
				guardado.getBytes(StandardCharsets.US_ASCII));
	}

	static String formaCanonica(String hashAnterior, EventoAuditoria e) {
		return formaCanonica(hashAnterior, CamposSellados.de(e));
	}

	static String formaCanonica(String hashAnterior, CamposSellados e) {
		StringBuilder texto = new StringBuilder(256);
		campo(texto, hashAnterior);
		campo(texto, Long.toString(e.secuencia()));
		campo(texto, e.colegioId() == null ? null : e.colegioId().toString());
		campo(texto, e.ocurridoEn() == null ? null : FORMATO_FECHA.format(e.ocurridoEn()));
		campo(texto, e.usuarioId() == null ? null : e.usuarioId().toString());
		campo(texto, e.nombreUsuario());
		campo(texto, e.roles());
		campo(texto, e.accion());
		campo(texto, e.entidad());
		campo(texto, e.entidadId());
		campo(texto, e.valorAnterior());
		campo(texto, e.valorNuevo());
		campo(texto, e.detalle());
		campo(texto, e.ip());
		return texto.toString();
	}

	private static void campo(StringBuilder texto, String valor) {
		if (valor == null) {
			texto.append("-1:|");
		}
		else {
			texto.append(valor.length()).append(':').append(valor).append('|');
		}
	}
}
