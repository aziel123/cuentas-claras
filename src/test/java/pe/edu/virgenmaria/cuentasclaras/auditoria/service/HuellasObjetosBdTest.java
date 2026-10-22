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
	void sonLos88TriggersYLas11FuncionesDeLosScripts() {
		Map<String, String> esperadas = HuellasObjetosBd.esperadas();

		// Correcciones del sprint 7 (V27): 75 triggers (trg_solicitud_cambio_nace y trg_resolucion_respaldo_registro) y 7
		// funciones (cc_solicitud_firmada en 03 y permisos_objetos en 02). Sprint 8, tanda 1 (V28): 80 triggers (el colegio
		// nace y cambia, la primera Promotoría y el correo externo de control) y 9 funciones (cc_operador_activo y
		// cc_colegio_en_produccion): 89 huellas. Tanda 2 (V29): 82 triggers (los conectores nacen propuestos y cambian de
		// estado con la aprobación firmada): 91 huellas. Correcciones (V31): 86 triggers y cc_cuenta_activada: 96 huellas. Tanda 3 (V30): las mismas 91 (versión nueva de trg_respaldo_registro y
		// trg_resolucion_respaldo_registro: «faltan filas» por colegio).
		// Sprint 9, tanda 1 (V32): 88 triggers (la asignación docente nace y cambia de estado) y cc_docente_de_seccion: 99.
		// Sprint 9, tanda 2 (V33): 93 triggers (la asistencia, el consentimiento y la configuración académica) y
		// cc_hoy_lima y cc_sesion_abierta: 106.
		// Sprint 9, tanda 3 (V34): 98 triggers (los comunicados, sus destinatarios, la confirmación de lectura y la
		// exportación de la nómina) y las mismas 13 funciones: 111. Correcciones del sprint 9: + trg_comunicado_destinatario_cambio
		// (99 y 112).
		assertThat(HuellasObjetosBd.triggersEsperados()).hasSize(99)
				.isEqualTo(VerificadorPermisosBaseDatos.TRIGGERS_ESPERADOS);
		assertThat(esperadas).hasSize(112).containsKeys("trg_comunicado_destinatario_cambio", "trg_comunicado_nace", "trg_comunicado_estado",
				"trg_comunicado_destinatario_nace", "trg_confirmacion_lectura_nace", "trg_exportacion_nomina_nace",
				"trg_registro_asistencia_nace", "trg_marca_asistencia_nace",
				"trg_consentimiento_aviso_nace", "trg_configuracion_academica_nace", "trg_configuracion_academica_cambio",
				"cc_hoy_lima", "cc_sesion_abierta", "trg_asignacion_docente_nace", "trg_asignacion_docente_estado",
				"cc_docente_de_seccion", "trg_conector_colegio_nace", "trg_conector_colegio_estado","cc_contacto_normal", "cc_es_sistema", "cc_firma_valida",
				"cc_solicitud_firmada", "cc_operador_activo", "cc_colegio_en_produccion", "triggers_instalados",
				"huellas_objetos", "permisos_objetos", "trg_usuario_rol_alta", "trg_firma_operacion_nace",
				"trg_solicitud_cambio_nace", "trg_resolucion_respaldo_registro", "trg_colegio_nace", "trg_colegio_cambio",
				"trg_primera_promotoria_nace", "trg_configuracion_colegio_registro", "trg_configuracion_colegio_cambio");
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
