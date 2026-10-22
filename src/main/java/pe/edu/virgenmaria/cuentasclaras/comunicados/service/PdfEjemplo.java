package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * Sprint 9, tanda 3 (sección 3.14, E48): el PDF de ejemplo de la demo (datos ficticios), empaquetado en el jar. En el
 * perfil {@code demo} es el ÚNICO PDF que se puede adjuntar a un comunicado (por su SHA-256, como la importación): nadie
 * sube a la demo un documento real con datos de alumnos.
 */
@Component
public class PdfEjemplo {

	public static final String ARCHIVO = "comunicado-ejemplo.pdf";

	private final boolean demo;

	private final byte[] contenido;

	private final String sha256;

	public PdfEjemplo(Environment entorno) {
		this.demo = Arrays.asList(entorno.getActiveProfiles()).contains("demo");
		try (InputStream entrada = new ClassPathResource("demo/" + ARCHIVO).getInputStream()) {
			this.contenido = entrada.readAllBytes();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Falta el PDF de ejemplo de la demo (demo/" + ARCHIVO + ")", e);
		}
		this.sha256 = HuellaComunicado.sha256(contenido);
	}

	/** En la demo solo se acepta el PDF de ejemplo. */
	public boolean soloElDeEjemplo() {
		return demo;
	}

	public boolean esElDeEjemplo(String sha256DelArchivo) {
		return sha256.equals(sha256DelArchivo);
	}

	public byte[] contenido() {
		return contenido.clone();
	}

	public String sha256() {
		return sha256;
	}
}
