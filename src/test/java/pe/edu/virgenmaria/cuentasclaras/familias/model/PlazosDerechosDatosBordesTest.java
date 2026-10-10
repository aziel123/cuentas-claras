package pe.edu.virgenmaria.cuentasclaras.familias.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.DiasHabiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA del sprint 7 (Ley 29733, sección 8.3; decisión 97): el último día para responder un pedido sobre datos personales,
 * en días hábiles (lunes a viernes sin los 16 feriados nacionales), con 20 días para el acceso y 10 para lo demás.
 * Bordes: fin de semana, fin de mes, feriados de diciembre, Navidad y Año Nuevo, y Semana Santa.
 */
class PlazosDerechosDatosBordesTest {

	@ParameterizedTest(name = "{1} recibido el {0} vence el {2}")
	@CsvSource({
			// Lunes 30/11/2026: el 8 y el 9 de diciembre son feriados nacionales.
			"2026-11-30, RECTIFICACION, 2026-12-16",
			// Sábado: cuenta desde el lunes; el viernes anterior da el mismo día.
			"2026-10-10, OPOSICION, 2026-10-23",
			"2026-10-09, OPOSICION, 2026-10-23",
			// Domingo 4/10/2026: el jueves 8 es feriado (Combate de Angamos).
			"2026-10-04, CANCELACION, 2026-10-19",
			// Acceso (20 días hábiles) del viernes 18/12/2026: sin el 25/12 ni el 1/1.
			"2026-12-18, ACCESO, 2027-01-19",
			// Fin de febrero de 2027 (no bisiesto).
			"2027-02-26, RECTIFICACION, 2027-03-12",
			// Semana Santa 2027: Jueves y Viernes Santo el 25 y 26 de marzo.
			"2027-03-22, CANCELACION, 2027-04-07",
			// Fiestas Patrias (28 y 29 de julio de 2027) y la Batalla de Junín (viernes 6 de agosto).
			"2027-07-26, OPOSICION, 2027-08-12",
			// Último día hábil del año.
			"2026-12-31, ACCESO, 2027-01-29" })
	void debeContarSoloDiasHabilesDesdeElDiaSiguiente(LocalDate recibido, DerechoDatos derecho, LocalDate vence) {
		assertThat(derecho.vence(recibido, DiasHabiles.NACIONALES, 20, 10)).isEqualTo(vence);
	}

	@ParameterizedTest(name = "el plazo de {0} es de {1} días hábiles")
	@CsvSource({ "ACCESO, 20", "RECTIFICACION, 10", "CANCELACION, 10", "OPOSICION, 10" })
	void debeUsarElPlazoDeAccesoSoloParaElAcceso(DerechoDatos derecho, int dias) {
		assertThat(derecho.plazoDias(20, 10)).isEqualTo(dias);
	}

	@ParameterizedTest(name = "recibido el {0}: {1} días hábiles al {2}")
	@CsvSource({
			"2026-10-10, 0, 2026-10-11",
			"2026-10-10, 6, 2026-10-19",
			"2026-10-10, 7, 2026-10-20",
			"2026-10-07, 0, 2026-10-08",
			"2026-12-24, 1, 2026-12-28" })
	void debeContarLosDiasHabilesTranscurridosSinElDiaDeRecepcion(LocalDate recibido, int habiles, LocalDate hoy) {
		assertThat(DiasHabiles.NACIONALES.habilesEntre(recibido, hoy)).isEqualTo(habiles);
	}
}
