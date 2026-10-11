package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Imágenes generadas para las pruebas del logo del colegio (sprint 8, tanda 1). */
public final class ImagenesDePrueba {

	public static final byte[] FIRMA_PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	private ImagenesDePrueba() {
	}

	/** Un JPEG con un degradado (sin canal alfa). */
	public static byte[] jpeg(int ancho, int alto) {
		try {
			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			ImageIO.write(degradado(ancho, alto, BufferedImage.TYPE_INT_RGB), "jpeg", salida);
			return salida.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Un PNG válido con un chunk {@code tEXt} (metadatos) después del IHDR: lo que un logo real podría traer. */
	public static byte[] pngConTexto(int ancho, int alto, String texto) {
		try {
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(degradado(ancho, alto, BufferedImage.TYPE_INT_ARGB), "png", png);
			byte[] base = png.toByteArray();
			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			salida.write(base, 0, 33); // firma + IHDR
			salida.write(chunk("tEXt", ("Comment\0" + texto).getBytes(StandardCharsets.ISO_8859_1)));
			salida.write(base, 33, base.length - 33);
			return salida.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static BufferedImage degradado(int ancho, int alto, int tipo) {
		BufferedImage imagen = new BufferedImage(ancho, alto, tipo);
		Graphics2D g = imagen.createGraphics();
		g.setPaint(new GradientPaint(0, 0, new Color(0x1b4f9c), ancho, alto, new Color(0x38aee6)));
		g.fillRect(0, 0, ancho, alto);
		g.setColor(Color.WHITE);
		g.fillOval(ancho / 4, alto / 4, ancho / 2, alto / 2);
		g.dispose();
		return imagen;
	}

	private static byte[] chunk(String tipo, byte[] datos) throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		DataOutputStream d = new DataOutputStream(salida);
		d.writeInt(datos.length);
		byte[] t = tipo.getBytes(StandardCharsets.US_ASCII);
		d.write(t);
		d.write(datos);
		CRC32 crc = new CRC32();
		crc.update(t);
		crc.update(datos);
		d.writeInt((int) crc.getValue());
		return salida.toByteArray();
	}
}
