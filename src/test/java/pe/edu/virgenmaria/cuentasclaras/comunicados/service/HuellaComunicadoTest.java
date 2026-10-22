package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 3 (B2, E19): la huella de Java es la MISMA cadena que recalcula trg_comunicado_nace: alcance, nivel,
 * grado, sección, título, texto y SHA-256 del adjunto, separados por un salto de línea, con vacío por cada NULL, en UTF-8.
 */
class HuellaComunicadoTest {

	@Test
	void esLaCadenaDelTrigger() throws Exception {
		String script = Files.readString(Path.of("scripts/mysql/03-triggers.sql"));
		assertThat(script).contains("SHA2(CONCAT_WS(CHAR(10), NEW.alcance, IFNULL(NEW.nivel, ''), IFNULL(NEW.grado, ''),")
				.contains("IFNULL(NEW.seccion_id, ''), NEW.titulo, NEW.cuerpo, IFNULL(v_sha_adjunto, '')), 256)");
		String esperada = HuellaComunicado.sha256(String.join("\n", "SECCION", "", "", "15", "Día del Logro",
				"Línea 1\nLínea 2", "").getBytes(StandardCharsets.UTF_8));
		assertThat(HuellaComunicado.de(AlcanceComunicado.SECCION, null, null, 15L, "Día del Logro", "Línea 1\nLínea 2",
				null)).isEqualTo(esperada).hasSize(64);
	}

	@Test
	void cambiaConCadaParteDelContenido() {
		String base = HuellaComunicado.de(AlcanceComunicado.NIVEL, Nivel.PRIMARIA, null, null, "Título", "Texto largo", null);
		assertThat(HuellaComunicado.de(AlcanceComunicado.NIVEL, Nivel.SECUNDARIA, null, null, "Título", "Texto largo", null))
				.isNotEqualTo(base);
		assertThat(HuellaComunicado.de(AlcanceComunicado.NIVEL, Nivel.PRIMARIA, null, null, "Título", "Texto largo", "ab"))
				.isNotEqualTo(base);
		assertThat(HuellaComunicado.de(AlcanceComunicado.GRADO, null, Grado.PRIMARIA_3, null, "Título", "Texto largo", null))
				.isNotEqualTo(base);
	}

	@Test
	void normalizaTituloYTexto() {
		assertThat(HuellaComunicado.normalizarTitulo("  Día\r\ndel   Logro ")).isEqualTo("Día del Logro");
		assertThat(HuellaComunicado.normalizarCuerpo("Línea 1\r\nLínea 2\rLínea 3  ")).isEqualTo("Línea 1\nLínea 2\nLínea 3");
	}
}
