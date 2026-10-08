package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 5 (G15, INDECOPI y Ley 29571): ninguna plantilla menciona lo académico (evaluaciones, notas, exámenes,
 * libretas, certificados, constancias) ni amenaza con consecuencias; todas llevan el nombre del colegio o del sistema y
 * sus parámetros {{1}}..{{n}} completos.
 */
class PlantillasMensajeTest {

	private static final List<String> PROHIBIDAS = List.of("evaluacion", "examen", "nota", "libreta", "certificado",
			"constancia", "matricula bloqueada", "retir", "suspend", "impedi", "no podra", "sancion", "denuncia",
			"central de riesgo", "infocorp", "ultimo aviso", "consecuencia", "deud");

	private static String sinTildes(String texto) {
		return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	@Test
	void ningunaPlantillaMencionaLoAcademicoNiAmenaza() {
		for (PlantillaMensaje plantilla : PlantillaMensaje.values()) {
			String texto = sinTildes(plantilla.texto() + " " + plantilla.asunto());
			assertThat(PROHIBIDAS).as(plantilla.name()).noneMatch(texto::contains);
			assertThat(plantilla.texto()).as(plantilla.name()).containsAnyOf("Colegio Virgen María", "Cuentas Claras");
			assertThat(plantilla.nombreMeta()).as(plantilla.name()).matches("cc_[a-z_]+");
		}
	}

	@Test
	void cadaPlantillaTieneSusParametrosCompletos() {
		for (PlantillaMensaje plantilla : PlantillaMensaje.values()) {
			for (int i = 1; i <= plantilla.parametros(); i++) {
				assertThat(plantilla.texto()).as(plantilla.name()).contains("{{" + i + "}}");
			}
			assertThat(plantilla.texto()).as(plantilla.name()).doesNotContain("{{" + (plantilla.parametros() + 1) + "}}");
			assertThat(plantilla.componer(Collections.nCopies(plantilla.parametros(), "X"))).doesNotContain("{{");
		}
		assertThatThrownBy(() -> PlantillaMensaje.PAGO_REGISTRADO.componer(List.of("solo uno")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void losAvisosFinancierosInvitanAAvisarSiNoLosReconoce() {
		for (PlantillaMensaje plantilla : List.of(PlantillaMensaje.PAGO_REGISTRADO, PlantillaMensaje.PAGO_ANULADO,
				PlantillaMensaje.DESCUENTO_APROBADO)) {
			assertThat(plantilla.texto()).as(plantilla.name()).contains("avísenos desde el portal");
		}
	}

	/**
	 * Sprint 6, tanda 2 (P9 y sección 10.5): el resumen y las alertas al personal no llevan botón ni enlace (nada se
	 * aprueba desde un mensaje: el enlace sería un token) y su texto lo arma el sistema con cifras y tipos fijos.
	 */
	@Test
	void lasAlertasYElResumenNoLlevanTokensNiEnlaces() {
		for (PlantillaMensaje plantilla : List.of(PlantillaMensaje.RESUMEN_DIARIO, PlantillaMensaje.ALERTA_PROMOTORIA,
				PlantillaMensaje.ALERTA_MAS, PlantillaMensaje.CONTACTO_PERSONAL_CAMBIADO)) {
			assertThat(plantilla.botonPortal()).as(plantilla.name()).isFalse();
			assertThat(plantilla.texto()).as(plantilla.name()).doesNotContain("http", "/activar", "/verificar", "enlace");
		}
		assertThat(PlantillaMensaje.RESUMEN_DIARIO.parametros()).isEqualTo(11);
	}
}
