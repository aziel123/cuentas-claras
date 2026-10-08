package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Primera barrera: archivos maliciosos o equivocados, todos generados aquí mismo. */
class ValidadorArchivoXlsxTest {

	private final ValidadorArchivoXlsx validador = new ValidadorArchivoXlsx(PropiedadesExcel.porDefecto());

	private final byte[] libroNormal = XlsxDePrueba.libro("Alumnos", List.of(List.of("Hola", 1)));

	@Test
	void rechazaArchivoDeMasDe2Mb() {
		byte[] grande = Arrays.copyOf(libroNormal, 2 * 1024 * 1024 + 1);
		assertThatThrownBy(() -> validador.validar("alumnos.xlsx", grande))
				.isInstanceOf(ArchivoNoValidoException.class)
				.hasMessageContaining("el máximo es 2 MB")
				.hasMessageContaining("un archivo por nivel");
	}

	@Test
	void rechazaPdfRenombradoAXlsx() {
		byte[] pdf = "%PDF-1.4\n%âãÏÓ\n1 0 obj << >> endobj".getBytes(StandardCharsets.ISO_8859_1);
		assertThatThrownBy(() -> validador.validar("alumnos.xlsx", pdf))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessage(ValidadorArchivoXlsx.MENSAJE_NO_XLSX);
		assertThatThrownBy(() -> validador.validar("alumnos.pdf", libroNormal))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining(".xlsx");
		assertThatThrownBy(() -> validador.validar("alumnos.xlsx", new byte[0]))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("vacío");
	}

	@Test
	void rechazaArchivoConClaveOXlsConMensajeClaro() throws Exception {
		byte[] xls;
		try (HSSFWorkbook antiguo = new HSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			antiguo.createSheet("Alumnos").createRow(0).createCell(0).setCellValue("DNI");
			antiguo.write(salida);
			xls = salida.toByteArray();
		}
		// Un .xlsx con contraseña también es un contenedor OLE2: misma firma, mismo mensaje.
		byte[] renombrado = xls;
		assertThatThrownBy(() -> validador.validar("alumnos.xlsx", renombrado))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("tiene contraseña o es .xls");
		assertThatThrownBy(() -> validador.validar("alumnos.xls", renombrado))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("tiene contraseña o es .xls");
	}

	@Test
	void rechazaZipBomb() {
		byte[] bomba = XlsxDePrueba.zipBomb(libroNormal, 60);
		assertThatCode(() -> {
			if (bomba.length > 2 * 1024 * 1024) {
				throw new AssertionError("la bomba debería pesar poco comprimida: " + bomba.length);
			}
		}).doesNotThrowAnyException();
		assertThatThrownBy(() -> validador.validar("alumnos.xlsx", bomba))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("demasiado grande al abrirlo");
	}

	@Test
	void rechazaZipConMasDe200Entradas() {
		assertThatThrownBy(() -> validador.validar("alumnos.xlsx", XlsxDePrueba.zipConEntradas(201)))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("demasiadas partes");
		assertThatCode(() -> validador.validar("alumnos.xlsx", XlsxDePrueba.zipConEntradas(150)))
				.doesNotThrowAnyException();
	}

	@Test
	void rechazaXlsmConMacros() {
		byte[] conMacros = XlsxDePrueba.conParte(libroNormal, "xl/vbaProject.bin", new byte[] { 1, 2, 3 });
		assertThatThrownBy(() -> validador.validar("alumnos.xlsx", conMacros))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("macros");
		assertThatThrownBy(() -> validador.validar("alumnos.xlsm", libroNormal))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("macros");
	}

	@Test
	void rechazaVinculosExternos() {
		byte[] conVinculo = XlsxDePrueba.conParte(libroNormal, "xl/externalLinks/externalLink1.xml",
				"<externalLink/>".getBytes(StandardCharsets.UTF_8));
		assertThatThrownBy(() -> validador.validar("alumnos.xlsx", conVinculo))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("vínculos a otros archivos");
		byte[] conObjeto = XlsxDePrueba.conParte(libroNormal, "xl/embeddings/oleObject1.bin", new byte[] { 1 });
		assertThatThrownBy(() -> validador.validar("alumnos.xlsx", conObjeto))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("incrustados");
	}

	@Test
	void aceptaLaPlantillaOficial() {
		byte[] plantilla = new PlantillaXlsx().crear("Alumnos",
				List.of(new ColumnaPlantilla("Tipo", true, List.of("DNI", "CE"), "Ayuda", 10)), List.of("Instrucción"),
				List.of("DNI"));
		assertThatCode(() -> validador.validar("plantilla-alumnos.xlsx", plantilla)).doesNotThrowAnyException();
		assertThatCode(() -> validador.validar("ALUMNOS.XLSX", libroNormal)).doesNotThrowAnyException();
	}
}
