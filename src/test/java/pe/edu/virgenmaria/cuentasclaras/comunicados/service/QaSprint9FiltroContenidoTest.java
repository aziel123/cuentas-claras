package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA del sprint 9 (QA-S9-1, B4): letras que se ven latinas y que el filtro de contenido no llevaba a la latina. Con ellas,
 * un comunicado que habla de pensiones, deudas o pagos pasaba el filtro de Java (y el de la base, que no normaliza). Los
 * casos van con escapes Unicode para que se vea qué carácter es cada uno.
 */
class QaSprint9FiltroContenidoTest {

	private final FiltroContenido filtro = new FiltroContenido("https://cuentas.colegio.pe", "minedu.gob.pe");

	@Test
	void debeRechazarPensionEscritaConIDeLaTurcaSinPunto() {
		assertThat(filtro.revisar("Recuerden la pensıón de octubre").aceptado()).isFalse();
	}

	@Test
	void debeRechazarPensionEscritaConIotaLatina() {
		assertThat(filtro.revisar("Recuerden la pensɩon de octubre").aceptado()).isFalse();
	}

	@Test
	void debeRechazarDeudaEscritaConUArmenia() {
		assertThat(filtro.revisar("Su deսda pendiente con el colegio").aceptado()).isFalse();
	}

	@Test
	void debeRechazarPensionEscritaConOArmenia() {
		assertThat(filtro.revisar("La pensiօn vence el viernes").aceptado()).isFalse();
	}

	@Test
	void debeRechazarPagoEscritoConAlfaLatina() {
		assertThat(filtro.revisar("Hagan su pɑgo antes del viernes").aceptado()).isFalse();
	}

	@Test
	void debeRechazarPensionEscritaConVersalitas() {
		assertThat(filtro.revisar("La ᴘᴇɴsɪᴏɴ de octubre").aceptado()).isFalse();
	}

	@Test
	void debeRechazarDeudaEscritaConUpsilonLatina() {
		assertThat(filtro.revisar("La deʋda del mes").aceptado()).isFalse();
	}

	@Test
	void debeRechazarPlinEscritoConPalochkaCirilica() {
		assertThat(filtro.revisar("Abonen por pӏin al profesor").aceptado()).isFalse();
	}

	@Test
	void debeRechazarPagoPartidoConRellenoHangulInvisible() {
		assertThat(filtro.revisar("El paㅤgo de la excursión").aceptado()).isFalse();
	}

	@Test
	void debeRechazarPagoPartidoConBlancoBraille() {
		assertThat(filtro.revisar("El pa⠀go de la excursión").aceptado()).isFalse();
	}

	@Test
	void debeLlevarALatinoLasLetrasParecidasNuevas() {
		assertThat(FiltroContenido.normalizar("pensıon deսda pɑgo ᴏ")).isEqualTo("pension deuda pago o");
	}

	@ParameterizedTest
	@ValueSource(strings = {"Reunión de padres/madres el martes a las 7.45 a.m.", "La salida es a las 12:30 h.",
			"Feliz Día de la Madre a todas las familias.", "Visitaremos el museo de ciencias con la tutora."})
	void debeSeguirAceptandoLoAcademicoConLaTablaAmpliada(String texto) {
		FiltroContenido.Resultado r = filtro.revisar(texto);
		assertThat(r.aceptado()).as(texto + " → " + r.motivo()).isTrue();
	}
}
