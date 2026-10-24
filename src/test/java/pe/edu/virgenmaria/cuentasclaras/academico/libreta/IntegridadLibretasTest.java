package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10, tanda 3 (E29, C1): la integridad de las libretas. Si a una matrícula ACTIVA o RESERVADA de un periodo cerrado
 * le falta su libreta (la emisión se la saltó), Promotoría recibe una alerta CRÍTICA (sale al celular) con la cantidad y
 * sin nombres; al emitirse, la alerta se va. Caja y Dirección no la calculan.
 */
class IntegridadLibretasTest extends PruebaLibretas {

	@Test
	void unaMatriculaSinLibretaAlertaAPromotoria() {
		anaPoneA();
		cerrarPeriodo();
		// La emisión todavía no corrió y pasaron más de 30 minutos: faltan las 3.
		hoyEs(LocalDate.of(2026, 5, 5));
		List<AlertaRevision> alertas = como(EscenarioCobranza.PROMOTORIA, () -> integridad.alertas());
		assertThat(alertas).singleElement().satisfies(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
			assertThat(a.aviso().tipo()).isEqualTo(TipoAviso.INTEGRIDAD_LIBRETAS);
			assertThat(a.texto()).contains("3 matrícula(s)").doesNotContain("Mateo").doesNotContain("Quispe");
			assertThat(a.difundible()).isTrue();
		});
		emitir();
		assertThat(como(EscenarioCobranza.PROMOTORIA, () -> integridad.alertas())).isEmpty();
		// Una alumna nueva (o una RESERVADA) sin libreta vuelve a alertar hasta la pasada siguiente.
		Long nueva = como(EscenarioCobranza.ADMINISTRACION, () -> alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(
				"71234571", "Ríos", "Vela", "Lucía", LocalDate.of(2015, 6, 1), "41234571", "Vela", "Paz", "Julia",
				"987777888", "julia.vela@gmail.com", seccion)).alumnoId());
		jdbc.update("UPDATE matricula SET estado = 'RESERVADA' WHERE id = ?", matricula(nueva));
		assertThat(como(EscenarioCobranza.PROMOTORIA, () -> integridad.alertas())).singleElement()
				.satisfies(a -> assertThat(a.texto()).contains("1 matrícula(s)"));
		emitir();
		assertThat(contar("SELECT COUNT(*) FROM libreta_emitida WHERE matricula_id = ?", matricula(nueva))).isEqualTo(1);
		assertThat(como(EscenarioCobranza.PROMOTORIA, () -> integridad.alertas())).isEmpty();
	}
}
