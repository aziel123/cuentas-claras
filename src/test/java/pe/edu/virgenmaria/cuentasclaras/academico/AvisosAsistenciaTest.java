package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.ConsentimientoRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.JustificarRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Canal;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Finalidad;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MotivoJustificacion;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 2 (sección 3.6; A1, A2, E37, E41, E51, E52): un aviso por familia y día, a la hora fija, con los hermanos
 * juntos; por WhatsApp solo con el «sí» del apoderado (si no, por correo); también la falta justificada; y lo que cambia
 * después del último envío sale al día siguiente (marca de agua).
 */
class AvisosAsistenciaTest extends PruebaAsistencia {

	private List<String> canales(String tipo) {
		return jdbc.queryForList("SELECT canal FROM mensaje WHERE tipo = ? ORDER BY id", String.class, tipo);
	}

	@Test
	void unoPorFamiliaYDia() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA, datos.valeria(), EstadoAsistencia.TARDE));
		alas(0, 10, 30);
		pasadaDeAvisos();
		// Un mensaje para la familia de Rosa (con los dos hermanos) y nada para la de Pedro (Lucas estuvo presente).
		assertThat(parametros("ASISTENCIA_DIA")).singleElement().asString().contains("Mateo: falta")
				.contains("Valeria: llegó tarde").doesNotContain("Quispe").doesNotContain("Lucas");
		assertThat(jdbc.queryForObject("SELECT apoderado_id FROM mensaje WHERE tipo = 'ASISTENCIA_DIA'", Long.class))
				.isEqualTo(datos.rosa());
		assertThat(jdbc.queryForObject("SELECT plantilla FROM mensaje WHERE tipo = 'ASISTENCIA_DIA'", String.class))
				.isEqualTo("ASISTENCIA");
		// Correr de nuevo no duplica (el envío de esa hora ya salió; y la clave es idempotente).
		alas(0, 10, 35);
		assertThat(pasadaDeAvisos()).isZero();
		assertThat(mensajes("ASISTENCIA_DIA")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT creado_por FROM mensaje WHERE tipo = 'ASISTENCIA_DIA'", String.class))
				.isEqualTo("sistema.asistencia");
	}

	@Test
	void respetaElHorario() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		alas(0, 10, 29);
		assertThat(pasadaDeAvisos()).isZero();
		// Sábado: nada.
		alas(4, 10, 30);
		assertThat(pasadaDeAvisos()).isZero();
		assertThat(mensajes("ASISTENCIA_DIA")).isZero();
		// Fuera de 07:00–20:00: nada.
		alas(0, 20, 30);
		assertThat(pasadaDeAvisos()).isZero();
		alas(0, 6, 59);
		assertThat(pasadaDeAvisos()).isZero();
		assertThat(mensajes("ASISTENCIA_DIA")).isZero();
	}

	@Test
	void sinConsentimientoVaPorCorreo() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA, datos.lucas(), EstadoAsistencia.FALTA));
		// Pedro sí dio el «sí» a WhatsApp en el portal; Rosa no.
		como(datos.pedroEnLinea(), () -> consentimientos.registrarEnPortal(new ConsentimientoRequest(
				Finalidad.ASISTENCIA, Canal.WHATSAPP, true)));
		alas(0, 10, 30);
		pasadaDeAvisos();
		assertThat(jdbc.queryForObject("SELECT canal FROM mensaje WHERE tipo = 'ASISTENCIA_DIA' AND apoderado_id = ?",
				String.class, datos.rosa())).isEqualTo("CORREO");
		assertThat(jdbc.queryForObject("SELECT canal FROM mensaje WHERE tipo = 'ASISTENCIA_DIA' AND apoderado_id = ?",
				String.class, datos.pedro())).isEqualTo("WHATSAPP");
	}

	@Test
	void quienSeOponeAlCorreoNoRecibeNada() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		como(datos.rosaEnLinea(), () -> consentimientos.registrarEnPortal(new ConsentimientoRequest(
				Finalidad.ASISTENCIA, Canal.CORREO, false)));
		alas(0, 10, 30);
		pasadaDeAvisos();
		assertThat(mensajes("ASISTENCIA_DIA")).isZero();
	}

	@Test
	void laFaltaJustificadaTambienSeAvisa() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		Long falta = EscenarioAsistencia.marcaVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES);
		alas(0, 9, 0);
		como(datos.rosaEnLinea(), () -> justificaciones.justificar(falta, new JustificarRequest(MotivoJustificacion.FAMILIAR,
				null)));
		alas(0, 10, 30);
		pasadaDeAvisos();
		assertThat(parametros("ASISTENCIA_DIA")).singleElement().asString().contains("Mateo: falta justificada");
	}

	@Test
	void loDeDespuesDelUltimoEnvioSaleAlDiaSiguiente() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		Long falta = EscenarioAsistencia.marcaVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES);
		alas(0, 10, 30);
		pasadaDeAvisos();
		alas(0, 16, 0);
		pasadaDeAvisos();
		assertThat(mensajes("ASISTENCIA_CORREGIDA")).isZero();
		// A las 17:00 (después del último envío) la familia justifica: sale a las 10:30 del día siguiente.
		alas(0, 17, 0);
		como(datos.rosaEnLinea(), () -> justificaciones.justificar(falta, new JustificarRequest(MotivoJustificacion.SALUD,
				null)));
		alas(0, 19, 0);
		assertThat(pasadaDeAvisos()).isZero();
		alas(1, 10, 30);
		pasadaDeAvisos();
		assertThat(parametros("ASISTENCIA_CORREGIDA")).singleElement().asString().contains("Mateo")
				.contains("06/10/2026").contains("falta justificada").contains("su familia");
	}

	@Test
	void unaFaltaRegistradaDespuesDelPrimerEnvioSaleEnElSegundo() {
		alas(0, 10, 30);
		assertThat(pasadaDeAvisos()).isZero();
		alas(0, 10, 40);
		registrar(Map.of(datos.lucas(), EstadoAsistencia.FALTA));
		alas(0, 16, 0);
		pasadaDeAvisos();
		assertThat(parametros("ASISTENCIA_DIA")).singleElement().asString().contains("Lucas: falta");
		assertThat(jdbc.queryForObject("SELECT clave FROM mensaje WHERE tipo = 'ASISTENCIA_DIA'", String.class))
				.endsWith(":SEGUNDO");
	}
}
