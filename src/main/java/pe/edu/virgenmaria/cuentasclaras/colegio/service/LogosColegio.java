package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.colegio.config.PropiedadesColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.LogoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.LogoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Optional;

/**
 * Sprint 8, tanda 1 (sección 3.7): el logo del colegio en contexto. {@link #guardar} valida el archivo (PNG o JPEG por sus
 * bytes mágicos, hasta 1 MB y {@value #MAX_LADO_ORIGINAL} × {@value #MAX_LADO_ORIGINAL} px leídos de la cabecera antes
 * de decodificar), lo decodifica con {@code ImageIO}, lo reescala a 400 px como máximo y lo vuelve a codificar como PNG
 * (se pierden los metadatos y cualquier contenido políglota) antes de guardarlo con su SHA-256. SVG no se acepta. Lo
 * llama el alta del colegio (dentro de su transacción) y la pantalla de marca.
 */
@Service
public class LogosColegio {

	public static final long MAX_SUBIDA = PropiedadesColegio.MAX_SUBIDA;

	/** Tope de las dimensiones declaradas en la cabecera: por encima no se decodifica (bomba de descompresión). */
	public static final int MAX_LADO_ORIGINAL = 4000;

	private static final byte[] MAGIA_PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	private static final byte[] MAGIA_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	/** Si el PNG a 400 px pasa de 200 KB, se intenta con estos lados antes de rechazarlo. */
	private static final int[] LADOS_DE_RESERVA = { 300, 200 };

	private final LogoColegioRepository logos;

	private final PropiedadesColegio propiedades;

	public LogosColegio(LogoColegioRepository logos, PropiedadesColegio propiedades) {
		this.logos = logos;
		this.propiedades = propiedades;
	}

	/** El logo vigente del colegio en contexto. */
	@Transactional(readOnly = true)
	public Optional<LogoColegio> vigente() {
		return logos.findByVigenteTrue();
	}

	/** El PNG vigente del colegio en contexto (para servirlo: sin exponer la entidad al controlador). */
	@Transactional(readOnly = true)
	public Optional<byte[]> pngVigente() {
		return logos.findByVigenteTrue().map(LogoColegio::getContenido);
	}

	/**
	 * Valida, reescribe y guarda el logo como el vigente del colegio en contexto (el anterior queda reemplazado). Debe
	 * llamarse con una transacción abierta (la del alta o la de la pantalla de marca).
	 *
	 * @param contenido los bytes del archivo subido
	 * @param nombreOriginal el nombre del archivo, solo para los mensajes (el tipo se decide por los bytes, nunca por la
	 *            extensión)
	 * @throws ReglaNegocioException si el archivo no es un PNG o JPEG válido de hasta 1 MB
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public LogoColegio guardar(byte[] contenido, String nombreOriginal) {
		if (contenido == null || contenido.length == 0) {
			throw new ReglaNegocioException("Elige el archivo del logo (PNG o JPEG).");
		}
		if (contenido.length > propiedades.logoMaxSubida().toBytes()) {
			throw new ReglaNegocioException("El logo pesa " + enKb(contenido.length) + " KB: el máximo es "
					+ enKb(propiedades.logoMaxSubida().toBytes()) + " KB.");
		}
		String formato = formatoPorMagia(contenido);
		exigirQueTermineConLaImagen(contenido, formato);
		BufferedImage original = decodificar(contenido, formato);
		ImagenPng png = reescalarYCodificar(original);
		LogoColegio anterior = logos.findByVigenteTrue().orElse(null);
		if (anterior != null) {
			// UNIQUE (colegio_id, vigente): el anterior deja de estar vigente ANTES de insertar el nuevo.
			anterior.reemplazar();
			logos.saveAndFlush(anterior);
		}
		return logos.saveAndFlush(LogoColegio.vigente(png.bytes(), sha256(png.bytes()), png.ancho(), png.alto()));
	}

	// ------------------------------------------------------------------ validación y decodificación

	/** PNG o JPEG por sus bytes mágicos; cualquier otra cosa (SVG, HTML, GIF, WebP) se rechaza sin mirar la extensión. */
	private static String formatoPorMagia(byte[] contenido) {
		if (empiezaCon(contenido, MAGIA_PNG)) {
			return "png";
		}
		if (empiezaCon(contenido, MAGIA_JPEG)) {
			return "jpeg";
		}
		throw new ReglaNegocioException("El logo debe ser una imagen PNG o JPEG. No se aceptan SVG ni otros formatos.");
	}

	private static boolean empiezaCon(byte[] contenido, byte[] magia) {
		if (contenido.length < magia.length) {
			return false;
		}
		for (int i = 0; i < magia.length; i++) {
			if (contenido[i] != magia[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Un archivo políglota (una imagen válida con HTML o un script pegado al final) se rechaza antes de decodificar: un
	 * PNG termina en su chunk IEND y un JPEG en su marca de fin (FF D9). Lo que va en chunks de texto se pierde al
	 * reescribir la imagen, así que no hace falta inspeccionarlo.
	 */
	private static void exigirQueTermineConLaImagen(byte[] contenido, String formato) {
		boolean termina = "png".equals(formato) ? pngTerminaEnIend(contenido)
				: contenido[contenido.length - 2] == (byte) 0xFF && contenido[contenido.length - 1] == (byte) 0xD9;
		if (!termina) {
			throw new ReglaNegocioException("El archivo del logo tiene datos escondidos después de la imagen: no se "
					+ "acepta. Vuelve a exportar el logo desde tu programa de dibujo.");
		}
	}

	/** Recorre los chunks del PNG: el último debe ser IEND y nada puede seguirlo. */
	private static boolean pngTerminaEnIend(byte[] contenido) {
		int posicion = MAGIA_PNG.length;
		while (posicion + 12 <= contenido.length) {
			long largo = ((contenido[posicion] & 0xFFL) << 24) | ((contenido[posicion + 1] & 0xFFL) << 16)
					| ((contenido[posicion + 2] & 0xFFL) << 8) | (contenido[posicion + 3] & 0xFFL);
			String tipo = new String(contenido, posicion + 4, 4, StandardCharsets.US_ASCII);
			long siguiente = posicion + 12 + largo;
			if (largo > Integer.MAX_VALUE || siguiente > contenido.length) {
				return false;
			}
			if ("IEND".equals(tipo)) {
				return siguiente == contenido.length;
			}
			posicion = (int) siguiente;
		}
		return false;
	}

	/**
	 * Lee las dimensiones de la cabecera ({@code ImageReader.getWidth/getHeight} no decodifican píxeles) y solo si caben
	 * en {@value #MAX_LADO_ORIGINAL} × {@value #MAX_LADO_ORIGINAL} decodifica la imagen.
	 */
	private static BufferedImage decodificar(byte[] contenido, String formato) {
		try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
			Iterator<ImageReader> lectores = ImageIO.getImageReadersByFormatName(formato);
			if (entrada == null || !lectores.hasNext()) {
				throw new ReglaNegocioException("No se pudo leer la imagen del logo.");
			}
			ImageReader lector = lectores.next();
			try {
				lector.setInput(entrada, true, true);
				int ancho = lector.getWidth(0);
				int alto = lector.getHeight(0);
				if (ancho < 1 || alto < 1) {
					throw new ReglaNegocioException("La imagen del logo está vacía o dañada.");
				}
				if (ancho > MAX_LADO_ORIGINAL || alto > MAX_LADO_ORIGINAL) {
					throw new ReglaNegocioException("El logo mide " + ancho + " × " + alto + " px: el máximo es "
							+ MAX_LADO_ORIGINAL + " × " + MAX_LADO_ORIGINAL + " px. Redúcelo antes de subirlo.");
				}
				BufferedImage imagen = lector.read(0);
				if (imagen == null || imagen.getWidth() < 1 || imagen.getHeight() < 1) {
					throw new ReglaNegocioException("La imagen del logo está vacía o dañada.");
				}
				return imagen;
			}
			finally {
				lector.dispose();
			}
		}
		catch (IOException | RuntimeException e) {
			if (e instanceof ReglaNegocioException regla) {
				throw regla;
			}
			throw new ReglaNegocioException("La imagen del logo está dañada o no es un PNG o JPEG válido.");
		}
	}

	// ------------------------------------------------------------------ reescritura

	private ImagenPng reescalarYCodificar(BufferedImage original) {
		int ladoMaximo = Math.min(propiedades.logoMaxLado(), LogoColegio.MAX_LADO);
		ImagenPng png = codificar(reescalar(original, ladoMaximo));
		for (int i = 0; png.bytes().length > LogoColegio.MAX_BYTES && i < LADOS_DE_RESERVA.length; i++) {
			if (LADOS_DE_RESERVA[i] < ladoMaximo) {
				png = codificar(reescalar(original, LADOS_DE_RESERVA[i]));
			}
		}
		if (png.bytes().length > LogoColegio.MAX_BYTES) {
			throw new ReglaNegocioException("El logo sigue pesando más de " + enKb(LogoColegio.MAX_BYTES)
					+ " KB aun reducido a " + png.ancho() + " × " + png.alto() + " px. Usa una imagen más simple (por "
					+ "ejemplo, el logo sobre fondo plano, sin fotografía).");
		}
		return png;
	}

	/** Reescala manteniendo la proporción para que ningún lado pase de {@code ladoMaximo}; nunca agranda. */
	private static BufferedImage reescalar(BufferedImage original, int ladoMaximo) {
		int ancho = original.getWidth();
		int alto = original.getHeight();
		int mayor = Math.max(ancho, alto);
		int nuevoAncho = ancho;
		int nuevoAlto = alto;
		if (mayor > ladoMaximo) {
			nuevoAncho = Math.max(1, (int) Math.round(ancho * (double) ladoMaximo / mayor));
			nuevoAlto = Math.max(1, (int) Math.round(alto * (double) ladoMaximo / mayor));
		}
		// Siempre se dibuja en un lienzo nuevo (aunque no cambie el tamaño): así se descartan metadatos, perfiles y
		// cualquier contenido ajeno a los píxeles.
		int tipo = original.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		BufferedImage destino = new BufferedImage(nuevoAncho, nuevoAlto, tipo);
		Graphics2D g = destino.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
		}
		finally {
			g.dispose();
		}
		return destino;
	}

	private static ImagenPng codificar(BufferedImage imagen) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try {
			if (!ImageIO.write(imagen, "png", salida)) {
				throw new ReglaNegocioException("No se pudo convertir el logo a PNG.");
			}
		}
		catch (IOException e) {
			throw new ReglaNegocioException("No se pudo convertir el logo a PNG.");
		}
		return new ImagenPng(salida.toByteArray(), imagen.getWidth(), imagen.getHeight());
	}

	static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}

	private static long enKb(long bytes) {
		return Math.round(bytes / 1024.0);
	}

	private record ImagenPng(byte[] bytes, int ancho, int alto) {
	}
}
