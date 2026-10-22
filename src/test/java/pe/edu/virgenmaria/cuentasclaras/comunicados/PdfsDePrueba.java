package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Sprint 9, tanda 3 (E26, A14, B3, B5): los PDF de las pruebas, generados con PDFBox en la prueba (sin binarios en el
 * repositorio): uno normal con texto, uno de imagen y los hostiles (JavaScript, OpenAction, Launch, embebido, AcroForm y
 * XFA, cifrado, políglotos, malformado, enorme por bytes y por páginas, bomba de objetos y de Flate, recursión profunda,
 * anotación que no es Link, capa OCG, enlace a otro dominio y extensión falsa).
 */
public final class PdfsDePrueba {

	public static final String TEXTO_NORMAL = "Estimadas familias: el viernes celebraremos el Día del Logro en el patio "
			+ "principal, de 9:00 a 11:30. Los alumnos asisten con el uniforme de educación física.";

	private PdfsDePrueba() {
	}

	/** Un PDF con texto suficiente en cada página (no requiere revisión). */
	public static byte[] normal() {
		return conTexto(1, TEXTO_NORMAL, d -> { });
	}

	public static byte[] conTexto(int paginas, String texto, Consumer<PDDocument> cambio) {
		return generar(d -> {
			for (int i = 0; i < paginas; i++) {
				PDPage pagina = new PDPage(PDRectangle.A4);
				d.addPage(pagina);
				escribir(d, pagina, texto);
			}
			cambio.accept(d);
		});
	}

	/** Un PDF con Info y metadatos XMP, y una revisión incremental agregada al final. */
	public static byte[] conInfoXmpYRevision() {
		byte[] base = conTexto(1, TEXTO_NORMAL, d -> {
			d.getDocumentInformation().setAuthor("Autor de prueba");
			d.getDocumentInformation().setTitle("Titulo secreto");
			try {
				var metadatos = new org.apache.pdfbox.pdmodel.common.PDMetadata(d);
				metadatos.importXMPMetadata("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"></x:xmpmeta>"
						.getBytes(StandardCharsets.UTF_8));
				d.getDocumentCatalog().setMetadata(metadatos);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		try (PDDocument d = org.apache.pdfbox.Loader.loadPDF(base)) {
			d.getDocumentInformation().setSubject("Revision incremental");
			d.getDocumentInformation().getCOSObject().setNeedToBeUpdated(true);
			d.getDocument().getTrailer().setNeedToBeUpdated(true);
			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			salida.write(base);
			ByteArrayOutputStream incremental = new ByteArrayOutputStream();
			d.saveIncremental(incremental);
			return incremental.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Un PDF que es solo una imagen (sin texto): requiere revisión del otro órgano (B3). */
	public static byte[] deImagen() {
		return generar(d -> {
			PDPage pagina = new PDPage(PDRectangle.A4);
			d.addPage(pagina);
			BufferedImage imagen = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
			try {
				PDImageXObject x = LosslessFactory.createFromImage(d, imagen);
				try (PDPageContentStream c = new PDPageContentStream(d, pagina)) {
					c.drawImage(x, 50, 600, 200, 200);
				}
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	/** Un PDF con poco texto (una línea): requiere revisión (B3). */
	public static byte[] conPocoTexto() {
		return conTexto(1, "Ver afiche.", d -> { });
	}

	public static byte[] conJavaScript() {
		return conTexto(1, TEXTO_NORMAL, d -> d.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert(1)")));
	}

	/** JavaScript en el árbol de nombres (/Names/JavaScript), sin OpenAction. */
	public static byte[] conJavaScriptEnNombres() {
		return conTexto(1, TEXTO_NORMAL, d -> {
			COSDictionary nombres = new COSDictionary();
			COSDictionary js = new COSDictionary();
			COSArray lista = new COSArray();
			lista.add(new org.apache.pdfbox.cos.COSString("x"));
			lista.add(new PDActionJavaScript("app.alert(2)").getCOSObject());
			js.setItem(COSName.NAMES, lista);
			nombres.setItem(COSName.JAVA_SCRIPT, js);
			d.getDocumentCatalog().getCOSObject().setItem(COSName.NAMES, nombres);
		});
	}

	public static byte[] conOpenActionDeAccion() {
		return conTexto(1, TEXTO_NORMAL, d -> {
			PDActionURI uri = new PDActionURI();
			uri.setURI("https://cuentas.colegio.pe");
			d.getDocumentCatalog().setOpenAction(uri);
		});
	}

	public static byte[] conLaunch() {
		return conTexto(1, TEXTO_NORMAL, d -> {
			PDAnnotationLink enlace = new PDAnnotationLink();
			enlace.setRectangle(new PDRectangle(50, 50, 100, 20));
			PDActionLaunch lanzar = new PDActionLaunch();
			lanzar.getCOSObject().setString(COSName.F, "cmd.exe");
			enlace.setAction(lanzar);
			agregarAnotacion(d, enlace);
		});
	}

	/** Un enlace a un dominio que no está en la lista permitida. */
	public static byte[] conEnlaceAjeno() {
		return conTexto(1, TEXTO_NORMAL, d -> {
			PDAnnotationLink enlace = new PDAnnotationLink();
			enlace.setRectangle(new PDRectangle(50, 50, 100, 20));
			PDActionURI uri = new PDActionURI();
			uri.setURI("https://pagos-rapidos.example.com/yape");
			enlace.setAction(uri);
			agregarAnotacion(d, enlace);
		});
	}

	/** Un enlace a la plataforma (permitido). */
	public static byte[] conEnlacePermitido() {
		return conTexto(1, TEXTO_NORMAL, d -> {
			PDAnnotationLink enlace = new PDAnnotationLink();
			enlace.setRectangle(new PDRectangle(50, 50, 100, 20));
			PDActionURI uri = new PDActionURI();
			uri.setURI("https://cuentas.colegio.pe/familia");
			enlace.setAction(uri);
			agregarAnotacion(d, enlace);
		});
	}

	public static byte[] conEmbebido() {
		return conTexto(1, TEXTO_NORMAL, d -> {
			try {
				PDEmbeddedFile archivo = new PDEmbeddedFile(d, new ByteArrayInputStream("hola".getBytes(StandardCharsets.UTF_8)));
				PDComplexFileSpecification especificacion = new PDComplexFileSpecification();
				especificacion.setFile("virus.exe");
				especificacion.setEmbeddedFile(archivo);
				PDEmbeddedFilesNameTreeNode arbol = new PDEmbeddedFilesNameTreeNode();
				arbol.setNames(java.util.Map.of("virus.exe", especificacion));
				PDDocumentNameDictionary nombres = new PDDocumentNameDictionary(d.getDocumentCatalog());
				nombres.setEmbeddedFiles(arbol);
				d.getDocumentCatalog().setNames(nombres);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	public static byte[] conFormulario() {
		return conTexto(1, TEXTO_NORMAL, d -> d.getDocumentCatalog().setAcroForm(new PDAcroForm(d)));
	}

	public static byte[] conXfa() {
		return conTexto(1, TEXTO_NORMAL, d -> {
			PDAcroForm formulario = new PDAcroForm(d);
			formulario.getCOSObject().setItem(COSName.getPDFName("XFA"), new org.apache.pdfbox.cos.COSString("<xdp/>"));
			d.getDocumentCatalog().setAcroForm(formulario);
		});
	}

	/** B3: una anotación que no es Link (una nota). */
	public static byte[] conAnotacionDeNota() {
		return conTexto(1, TEXTO_NORMAL, d -> {
			PDAnnotationText nota = new PDAnnotationText();
			nota.setRectangle(new PDRectangle(50, 50, 20, 20));
			nota.setContents("Pague al 987654321");
			agregarAnotacion(d, nota);
		});
	}

	/** B3: una capa (contenido opcional, OCG). */
	public static byte[] conCapa() {
		return conTexto(1, TEXTO_NORMAL, d -> {
			PDOptionalContentProperties propiedades = new PDOptionalContentProperties();
			propiedades.addGroup(new PDOptionalContentGroup("oculta"));
			d.getDocumentCatalog().setOCProperties(propiedades);
		});
	}

	public static byte[] cifrado() {
		return conTexto(1, TEXTO_NORMAL, d -> {
			try {
				StandardProtectionPolicy politica = new StandardProtectionPolicy("duenio", "clave", new AccessPermission());
				politica.setEncryptionKeyLength(128);
				d.protect(politica);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	public static byte[] deOncePaginas() {
		return conTexto(11, TEXTO_NORMAL, d -> { });
	}

	/** Más de 2 MB: empieza como un PDF y sigue con relleno. */
	public static byte[] enorme() {
		byte[] relleno = new byte[2 * 1024 * 1024 + 10];
		byte[] inicio = "%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1);
		System.arraycopy(inicio, 0, relleno, 0, inicio.length);
		return relleno;
	}

	public static byte[] malformado() {
		return "%PDF-1.7\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj garbage garbage %%EOF".getBytes(
				StandardCharsets.ISO_8859_1);
	}

	public static byte[] poliglotoHtml() {
		byte[] pdf = normal();
		byte[] html = "<html><script>alert(1)</script></html>\n".getBytes(StandardCharsets.ISO_8859_1);
		return concatenar("%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1), html, pdf);
	}

	public static byte[] poliglotoZip() {
		return concatenar("%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1), new byte[] { 'P', 'K', 3, 4, 0, 0 },
				normal());
	}

	/** Una imagen PNG con nombre .pdf (extensión falsa). */
	public static byte[] extensionFalsa() {
		return new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0 };
	}

	/** Bomba de objetos: un arreglo con 120,000 arreglos vacíos (pesa menos de 2 MB: lo detiene el tope de objetos). */
	public static byte[] bombaDeObjetos() {
		return conTexto(1, TEXTO_NORMAL, d -> {
			COSArray arreglo = new COSArray();
			for (int i = 0; i < 120_000; i++) {
				arreglo.add(new COSArray());
			}
			d.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("Bomba"), arreglo);
		});
	}

	/** Bomba de Flate: el contenido de la página se descomprime a 400 MB de ceros (pesa unos pocos cientos de KB). */
	public static byte[] bombaDeFlate() {
		return generar(d -> {
			PDPage pagina = new PDPage(PDRectangle.A4);
			d.addPage(pagina);
			COSStream contenido = d.getDocument().createCOSStream();
			try (OutputStream crudo = contenido.createRawOutputStream();
					DeflaterOutputStream comprimido = new DeflaterOutputStream(crudo, new Deflater(9))) {
				byte[] ceros = new byte[1024 * 1024];
				for (int i = 0; i < 400; i++) {
					comprimido.write(ceros);
				}
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			contenido.setItem(COSName.FILTER, COSName.FLATE_DECODE);
			pagina.getCOSObject().setItem(COSName.CONTENTS, contenido);
		});
	}

	/** Recursión profunda: 60,000 arreglos anidados escritos a mano (PDFBox no los guardaría). */
	public static byte[] recursionProfunda() {
		StringBuilder anidado = new StringBuilder();
		anidado.append("[".repeat(60_000)).append("]".repeat(60_000));
		String cuerpo = "%PDF-1.7\n"
				+ "1 0 obj << /Type /Catalog /Pages 2 0 R /Profundo " + anidado + " >> endobj\n"
				+ "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
				+ "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >> endobj\n"
				+ "trailer << /Root 1 0 R /Size 4 >>\n%%EOF\n";
		return cuerpo.getBytes(StandardCharsets.ISO_8859_1);
	}

	/** Todos los hostiles con su nombre (para la prueba parametrizada). */
	public static List<Object[]> hostiles() {
		return List.of(new Object[] { "malformado", malformado() }, new Object[] { "enorme por bytes", enorme() },
				new Object[] { "11 páginas", deOncePaginas() }, new Object[] { "bomba de objetos", bombaDeObjetos() },
				new Object[] { "bomba de Flate", bombaDeFlate() }, new Object[] { "JavaScript", conJavaScript() },
				new Object[] { "JavaScript en nombres", conJavaScriptEnNombres() },
				new Object[] { "OpenAction", conOpenActionDeAccion() }, new Object[] { "Launch", conLaunch() },
				new Object[] { "embebido", conEmbebido() }, new Object[] { "AcroForm", conFormulario() },
				new Object[] { "XFA", conXfa() }, new Object[] { "cifrado", cifrado() },
				new Object[] { "políglota HTML", poliglotoHtml() }, new Object[] { "políglota ZIP", poliglotoZip() },
				new Object[] { "extensión falsa", extensionFalsa() }, new Object[] { "anotación de nota", conAnotacionDeNota() },
				new Object[] { "capa OCG", conCapa() }, new Object[] { "enlace ajeno", conEnlaceAjeno() },
				new Object[] { "recursión profunda", recursionProfunda() });
	}

	private static void agregarAnotacion(PDDocument d, org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation a) {
		try {
			d.getPage(0).getAnnotations().add(a);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void escribir(PDDocument d, PDPage pagina, String texto) {
		try (PDPageContentStream c = new PDPageContentStream(d, pagina)) {
			c.beginText();
			c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
			c.newLineAtOffset(50, 750);
			c.showText(texto.length() > 110 ? texto.substring(0, 110) : texto);
			c.newLineAtOffset(0, -16);
			if (texto.length() > 110) {
				c.showText(texto.substring(110));
			}
			c.endText();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static byte[] generar(Consumer<PDDocument> armar) {
		try (PDDocument d = new PDDocument()) {
			armar.accept(d);
			d.getDocumentInformation().setCreationDate(Calendar.getInstance());
			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			d.save(salida);
			return salida.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static byte[] concatenar(byte[]... partes) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		for (byte[] p : partes) {
			salida.writeBytes(p);
		}
		return salida.toByteArray();
	}
}
