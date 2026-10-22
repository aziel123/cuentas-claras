package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.Comunicado;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Sprint 9, tanda 3 (B2, E19): la huella del contenido de un comunicado. Es LA MISMA cadena que recalcula
 * {@code trg_comunicado_nace}: {@code SHA2(CONCAT_WS(CHAR(10), alcance, IFNULL(nivel,''), IFNULL(grado,''),
 * IFNULL(seccion_id,''), titulo, cuerpo, IFNULL(sha256 del adjunto,'')), 256)} en UTF-8. El título va sin saltos de
 * línea y el texto con {@code LF} ({@link #normalizarTitulo} y {@link #normalizarCuerpo}, antes de guardar). La compara
 * {@code HuellaComunicadoTest} con la del trigger y la recalcula «Verificar integridad» (IntegridadComunicados).
 */
public final class HuellaComunicado {

	private HuellaComunicado() {
	}

	public static String de(AlcanceComunicado alcance, Nivel nivel, Grado grado, Long seccionId, String titulo,
			String cuerpo, String sha256Adjunto) {
		String cadena = String.join("\n", alcance.name(), nivel == null ? "" : nivel.name(),
				grado == null ? "" : grado.name(), seccionId == null ? "" : seccionId.toString(), titulo, cuerpo,
				sha256Adjunto == null ? "" : sha256Adjunto);
		return sha256(cadena.getBytes(StandardCharsets.UTF_8));
	}

	/** La huella de un comunicado guardado (con el SHA-256 de su adjunto, si tiene). */
	public static String de(Comunicado c, String sha256Adjunto) {
		return de(c.getAlcance(), c.getNivel(), c.getGrado(), c.getSeccionId(), c.getTitulo(), c.getCuerpo(),
				sha256Adjunto);
	}

	/** Título en una sola línea, sin espacios de más. */
	public static String normalizarTitulo(String titulo) {
		return titulo == null ? "" : titulo.replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").strip();
	}

	/** Texto con saltos de línea {@code LF} ({@code CRLF} y {@code CR} pasan a {@code LF}), sin espacios al final. */
	public static String normalizarCuerpo(String cuerpo) {
		return cuerpo == null ? "" : cuerpo.replace("\r\n", "\n").replace('\r', '\n').strip();
	}

	public static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
