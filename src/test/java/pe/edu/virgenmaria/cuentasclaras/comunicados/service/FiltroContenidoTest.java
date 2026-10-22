package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 3 (sección 3.8, decisión 155, E21, E22, B4): lo que un comunicado no puede decir, sin excepciones. Los
 * 30 casos del diseño (tildes, mayúsculas, números por letras, letras separadas, montos, «S/.», medios de pago, cuentas,
 * contactos y enlaces) y los de la auditoría en papel (B4): ancho cero, guion suave, letras de ancho completo, cirílico,
 * dígitos árabes-índicos y acentos combinados.
 */
class FiltroContenidoTest {

	private final FiltroContenido filtro = new FiltroContenido("https://cuentas.colegio.pe", "minedu.gob.pe");

	@ParameterizedTest
	@ValueSource(strings = {
			// Cobranza (con tildes, mayúsculas, números por letras y letras separadas).
			"Mateo debe 3 pensiones", "Recuerden la DEUDA pendiente", "Familias morosas", "La morosidad del aula",
			"Pague su cuota", "Último día para pagar", "Su pensión de marzo", "Las cuotas vencidas", "p3nsión de abril",
			"d e u d a", "d.e.u.d.a", "Cobranza de octubre", "Adeudan dos meses", "atrasados en pagos", "Deben pagar hoy",
			"deuuuuda",
			// Montos.
			"Traer S/ 20 para la excursión", "s/.350", "Son 350.00 por alumno", "Cuesta 50 soles", "US$ 30 de inscripción",
			// Medios de pago y cuentas.
			"Yapeen al número del docente", "Por Plin o transferencia", "Depositen en el BCP", "Cuenta BBVA del comité",
			"CCI 00212345678901234567", "1234567890123 es la cuenta",
			// Contactos, documentos y enlaces.
			"Escríbanme al 987 654 321", "DNI 45678912", "mi correo profe.ana@gmail.com", "Entren a www.pagos-rapidos.com",
			"https://bit.ly/abc"
	})
	void loQueNoPuedeDecir(String texto) {
		FiltroContenido.Resultado r = filtro.revisar(texto);
		assertThat(r.aceptado()).as(texto).isFalse();
		assertThat(r.motivo()).as("motivo claro").isNotBlank().doesNotContain("null");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// B4: ancho cero, guion suave y otros caracteres de formato.
			"de​uda pendiente", "pen­sión de mayo", "deu‍da", "pa⁠go de la cuota",
			// B4: letras de ancho completo (NFKC).
			"ｄｅｕｄａ del mes", "ＹＡＰＥ al docente",
			// B4: cirílico y griego que se ven latinos.
			"реnsión de abril", "Υаре al número", "dеudа",
			// B4: dígitos árabes-índicos y de ancho completo.
			"Son ٣٥٠.٠٠ por familia", "Llamen al ９８７６５４３２１",
			// B4: acentos combinados (NFD).
			"pensión atrasada"
	})
	void lasTrampasDeEscrituraTambienSeRechazan(String texto) {
		assertThat(filtro.revisar(texto).aceptado()).as(texto).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Reunión de padres/madres el martes a las 7.45 a.m.", "El Día del Logro es el viernes de 9:00 a 11:30.",
			"Los alumnos deben traer su uniforme de educación física.", "Visitaremos el museo de ciencias.",
			"La tutora de 3.° B les saluda.", "Más información en https://cuentas.colegio.pe/familia",
			"Revisen la norma en https://www.minedu.gob.pe/normas", "Feliz Día de la Madre a todas las familias.",
			"La salida es a las 12:30 h."
	})
	void loAcademicoPasa(String texto) {
		FiltroContenido.Resultado r = filtro.revisar(texto);
		assertThat(r.aceptado()).as(texto + " → " + r.motivo()).isTrue();
	}

	@Test
	void elMotivoDiceQueReglaYElFragmento() {
		FiltroContenido.Resultado r = filtro.revisar("Por favor depositen en el BCP antes del viernes");
		assertThat(r.regla()).isEqualTo(FiltroContenido.Regla.MEDIO_DE_PAGO);
		assertThat(r.regla().deCobranza()).isTrue();
		assertThat(r.fragmento()).isNotBlank();
		assertThat(filtro.revisar("mi correo profe.ana@gmail.com").regla()).isEqualTo(FiltroContenido.Regla.CONTACTO);
		assertThat(filtro.revisar("mi correo profe.ana@gmail.com").regla().deCobranza()).isFalse();
	}

	@Test
	void celularesCuentasYEnlaces() {
		assertThat(filtro.revisar("+51 987654321").regla()).isEqualTo(FiltroContenido.Regla.CONTACTO);
		assertThat(filtro.revisar("00212345678901234567").regla()).isEqualTo(FiltroContenido.Regla.MEDIO_DE_PAGO);
		assertThat(filtro.revisar("visiten tienda.example.com").regla()).isEqualTo(FiltroContenido.Regla.ENLACE);
	}

	@Test
	void laNormalizacionQuitaLoInvisibleYLlevaALatino() {
		assertThat(FiltroContenido.normalizar("ре​nsión ٣")).isEqualTo("pension 3");
		assertThat(FiltroContenido.sinCaracteresDeFormato("a­b​c")).isEqualTo("abc");
	}
}
