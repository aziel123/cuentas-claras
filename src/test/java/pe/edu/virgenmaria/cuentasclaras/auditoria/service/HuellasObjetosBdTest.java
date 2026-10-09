package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 7, tanda 2 (sección 3.7, H7): las huellas esperadas de los triggers y funciones salen de los scripts del jar y
 * no dependen de lo que el cliente {@code mysql} haga con los comentarios ni de los saltos de línea del archivo.
 */
class HuellasObjetosBdTest {

	private static String script(String nombre) throws IOException {
		return Files.readString(Path.of("scripts/mysql/" + nombre), StandardCharsets.UTF_8);
	}

	@Test
	void sonLos73TriggersYLas5FuncionesDeLosScripts() {
		Map<String, String> esperadas = HuellasObjetosBd.esperadas();

		assertThat(HuellasObjetosBd.triggersEsperados()).hasSize(73)
				.isEqualTo(VerificadorPermisosBaseDatos.TRIGGERS_ESPERADOS);
		assertThat(esperadas).hasSize(78).containsKeys("cc_contacto_normal", "cc_es_sistema", "cc_firma_valida",
				"triggers_instalados", "huellas_objetos", "trg_usuario_rol_alta", "trg_firma_operacion_nace");
		assertThat(esperadas.values()).allMatch(h -> h.matches("[0-9a-f]{64}"));
	}

	@Test
	void laHuellaNoCambiaSiElClienteQuitaLosComentariosOSiElArchivoTieneCrlf() throws IOException {
		String original = script("03-triggers.sql");
		String sinComentarios = original.replaceAll("--[^\n]*", "");
		String conCrlf = original.replace("\n", "\r\n");

		assertThat(HuellasObjetosBd.objetos(sinComentarios)).isEqualTo(HuellasObjetosBd.objetos(original));
		assertThat(HuellasObjetosBd.objetos(conCrlf)).isEqualTo(HuellasObjetosBd.objetos(original));
	}

	@Test
	void unCambioEnElCuerpoCambiaLaHuella() throws IOException {
		String original = script("03-triggers.sql");
		String debilitado = original.replace("CONCAT('solicitud_cambio:', NEW.id, ':', NEW.estado), NEW.resuelto_por)",
				"CONCAT('solicitud_cambio:', NEW.id, ':', NEW.estado), 'sistema.x')");
		Map<String, String> antes = mapa(HuellasObjetosBd.objetos(original));
		Map<String, String> despues = mapa(HuellasObjetosBd.objetos(debilitado));

		assertThat(despues.get("trg_solicitud_cambio_resuelta")).isNotEqualTo(antes.get("trg_solicitud_cambio_resuelta"));
		assertThat(despues.get("trg_descuento_resuelto")).isEqualTo(antes.get("trg_descuento_resuelto"));
	}

	/** Como MySQL guarda el cuerpo: el escape de un salto de línea dentro de un literal ya procesado. */
	@Test
	void normalizaComoMySqlLosSaltosDeLineaDeLosLiterales() {
		String comoEnElArchivo = "BEGIN\n    SET x = SUBSTRING_INDEX(t, '\\n', 2); -- comentario\nEND";
		String comoLoGuardaMySql = "BEGIN\n    SET x = SUBSTRING_INDEX(t, '\n', 2); \nEND";

		assertThat(HuellasObjetosBd.normalizar(comoEnElArchivo)).isEqualTo(HuellasObjetosBd.normalizar(comoLoGuardaMySql));
	}

	/**
	 * Regla de 03: ningún literal dentro de un cuerpo contiene dos guiones seguidos (la normalización quitaría desde ahí
	 * como si fuera un comentario y la huella no vería ese código).
	 */
	@Test
	void ningunLiteralDeLosScriptsTieneDosGuiones() throws IOException {
		for (String nombre : List.of("03-triggers.sql", "02-permisos-tablas.sql")) {
			int linea = 0;
			for (String texto : script(nombre).split("\n")) {
				linea++;
				boolean enLiteral = false;
				for (int i = 0; i < texto.length(); i++) {
					char c = texto.charAt(i);
					if (c == '\'') {
						enLiteral = !enLiteral;
					}
					else if (c == '-' && i + 1 < texto.length() && texto.charAt(i + 1) == '-') {
						assertThat(enLiteral).as(nombre + ", línea " + linea + ": dos guiones dentro de un literal").isFalse();
						break;
					}
				}
			}
		}
	}

	private static Map<String, String> mapa(List<HuellasObjetosBd.Objeto> objetos) {
		return objetos.stream().collect(java.util.stream.Collectors.toMap(HuellasObjetosBd.Objeto::nombre,
				HuellasObjetosBd.Objeto::huella));
	}
}
