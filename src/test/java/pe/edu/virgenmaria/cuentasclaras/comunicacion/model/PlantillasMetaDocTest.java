package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 8, tanda 3: el documento que se envía a Meta (docs/entrega/plantillas-whatsapp-meta.md) dice exactamente lo que
 * envía el código. Si alguien cambia una plantilla en {@link PlantillaMensaje} y no el documento (o al revés), falla: Meta
 * rechaza un mensaje cuyo texto no coincide con la plantilla aprobada.
 */
class PlantillasMetaDocTest {

	@Test
	void cadaPlantillaEstaEnElDocumentoConSuTextoYSinLaMarca() throws IOException {
		String documento = Files.readString(Path.of("docs/entrega/plantillas-whatsapp-meta.md"), StandardCharsets.UTF_8);
		for (PlantillaMensaje plantilla : PlantillaMensaje.values()) {
			String titulo = "`" + plantilla.nombreMeta() + "` · " + plantilla.asunto();
			assertThat(documento).as(plantilla.nombreMeta()).contains(titulo)
					.contains("```text\n" + plantilla.texto() + "\n```");
			// Los ejemplos de ESA plantilla: uno por variable, ni más ni menos.
			String seccion = documento.substring(documento.indexOf(titulo));
			seccion = seccion.substring(0, seccion.indexOf("\n## ") > 0 ? seccion.indexOf("\n## ") : seccion.length());
			assertThat(seccion.lines().filter(l -> l.startsWith("| `{{"))).as(plantilla.nombreMeta())
					.hasSize(plantilla.parametros());
			assertThat(plantilla.texto()).as("sin la marca ni un colegio fijo").startsWith("{{1}}")
					.doesNotContain("Cuentas Claras").doesNotContain("Virgen María");
		}
	}
}
