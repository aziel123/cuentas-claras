package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.JustificarRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MotivoJustificacion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 2 (E39, INDECOPI y Ley 29571): una familia con cuotas vencidas y otra al día reciben el MISMO aviso de
 * asistencia y justifican igual. El proceso ni siquiera puede leer las deudas (ArchUnit).
 */
class AsistenciaSinDeudaTest extends PruebaAsistencia {

	@Autowired
	private ServicioPlanesPension planes;

	@Test
	void laDeudaNoCambiaElAvisoNiLaJustificacion() {
		EscenarioCobranza.planAprobado(planes, datos.escuela().anio2026(), 2026, Nivel.PRIMARIA, "350.00", "300.00",
				LocalDate.of(2026, 3, 1));
		SecurityContextHolder.clearContext();
		// La familia de Pedro (Lucas) está al día; la de Rosa (Mateo y Valeria) debe.
		jdbc.update("UPDATE cuota SET estado = 'PAGADA', monto_pagado = monto WHERE alumno_id = ?", datos.lucas());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuota WHERE alumno_id = ? AND estado <> 'PAGADA' AND "
				+ "fecha_vencimiento < ?", Long.class, datos.mateo(), EscenarioAsistencia.MARTES)).isPositive();
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA, datos.lucas(), EstadoAsistencia.FALTA));
		alas(0, 10, 30);
		pasadaDeAvisos();
		List<String> textos = parametros("ASISTENCIA_DIA");
		assertThat(textos).hasSize(2);
		String deRosa = jdbc.queryForObject("SELECT parametros FROM mensaje WHERE tipo = 'ASISTENCIA_DIA' AND "
				+ "apoderado_id = ?", String.class, datos.rosa());
		String dePedro = jdbc.queryForObject("SELECT parametros FROM mensaje WHERE tipo = 'ASISTENCIA_DIA' AND "
				+ "apoderado_id = ?", String.class, datos.pedro());
		// El mismo texto salvo el nombre del hijo: sin montos, sin «deuda», sin «pensión».
		assertThat(deRosa.replace("Mateo", "X")).isEqualTo(dePedro.replace("Lucas", "X"));
		assertThat(deRosa + dePedro).doesNotContain("S/").doesNotContainIgnoringCase("deuda")
				.doesNotContainIgnoringCase("pensi");
		// Las dos justifican igual.
		Long deMateo = EscenarioAsistencia.marcaVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES);
		Long deLucas = EscenarioAsistencia.marcaVigente(jdbc, datos.lucas(), EscenarioAsistencia.MARTES);
		JustificarRequest salud = new JustificarRequest(MotivoJustificacion.SALUD, null);
		como(datos.rosaEnLinea(), () -> justificaciones.justificar(deMateo, salud));
		como(datos.pedroEnLinea(), () -> justificaciones.justificar(deLucas, salud));
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES))
				.isEqualTo("FALTA_JUSTIFICADA");
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.lucas(), EscenarioAsistencia.MARTES))
				.isEqualTo("FALTA_JUSTIFICADA");
	}
}
