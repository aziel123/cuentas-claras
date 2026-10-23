package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.service.AlertasAcademicas;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 2 (decisión 149; A4, A7, E47): a la hora de alerta, las secciones sin registro O con el registro
 * incompleto (menos marcas iniciales que matrículas activas), con los nombres de las secciones y sin alumnos; una sola al
 * día y solo a Dirección.
 */
class AlertasAcademicasTest extends PruebaAsistencia {

	@Autowired
	private AlertasAcademicas alertas;

	private List<AlertaRevision> alertasAhora() {
		return como(EscenarioCobranza.PROMOTORIA, () -> alertas.alertas());
	}

	@Test
	void seccionSinRegistroAvisaADireccion() {
		alas(0, 9, 29);
		assertThat(alertasAhora()).isEmpty();
		alas(0, 9, 30);
		assertThat(alertasAhora()).singleElement().satisfies(a -> {
			assertThat(a.texto()).contains("5.° Primaria A").doesNotContain("Mateo").doesNotContain("Quispe");
			assertThat(a.aviso().tipo()).isEqualTo(TipoAviso.SECCION_SIN_ASISTENCIA);
			assertThat(a.aviso().referencia()).isEqualTo("ASISTENCIA:20261006");
			assertThat(a.aviso().dato()).isEqualTo("1");
			assertThat(a.difundible()).isTrue();
		});
		assertThat(TipoAviso.SECCION_SIN_ASISTENCIA.soloDireccion()).isTrue();
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		assertThat(alertasAhora()).isEmpty();
	}

	@Test
	void registroIncompletoTambienAvisa() {
		registrar(Map.of());
		// Un alumno se matricula después del registro: la sección queda con menos marcas que matrículas activas (A4).
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("72345679", "Rojas", "Paz", "Sofía", LocalDate.of(2015, 5, 5),
				"42345678", "Rojas", "Luna", "Carmen", null, "carmen.rojas@gmail.com", datos.seccion()));
		SecurityContextHolder.clearContext();
		alas(0, 10, 0);
		assertThat(alertasAhora()).singleElement().satisfies(a -> assertThat(a.texto()).contains("5.° Primaria A")
				.doesNotContain("Sofía"));
	}

	/**
	 * QA-S9-6: se comparan ALUMNOS, no cantidades. El mismo día se retira un alumno ya marcado y se matricula otro: los
	 * números coinciden (3 marcas iniciales y 3 matrículas activas), pero el alumno nuevo no tiene marca.
	 */
	@Test
	void debeAvisarCuandoCuadraElNumeroPeroFaltaElAlumnoNuevo() {
		registrar(Map.of());
		jdbc.update("UPDATE matricula SET estado = 'RETIRADA', retirada_en = CURRENT_DATE WHERE alumno_id = ?",
				datos.valeria());
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("72345679", "Rojas", "Paz", "Sofía", LocalDate.of(2015, 5, 5),
				"42345678", "Rojas", "Luna", "Carmen", null, "carmen.rojas@gmail.com", datos.seccion()));
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matricula WHERE seccion_id = ? AND estado = 'ACTIVA'",
				Long.class, datos.seccion())).isEqualTo(3);
		alas(0, 10, 0);
		assertThat(alertasAhora()).singleElement().satisfies(a -> assertThat(a.texto()).contains("5.° Primaria A"));
	}
}
