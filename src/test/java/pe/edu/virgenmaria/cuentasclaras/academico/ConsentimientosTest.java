package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.virgenmaria.cuentasclaras.academico.api.Consentimientos;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.AvisosDelApoderado;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.ConsentimientoFichaRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.ConsentimientoRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Canal;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Finalidad;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 9, tanda 2 (sección 3.6, H2, decisión 152; E38): sin ninguna fila, WhatsApp vale «no» y correo «sí»; la familia
 * da o retira el suyo en el portal; Administración lo registra desde la ficha firmada SOLO con su código; la familia lo ve
 * y lo revoca. Vale la última fila (solo inserción).
 */
class ConsentimientosTest extends PruebaAsistencia {

	@Autowired
	private Consentimientos vigentes;

	private boolean recibe(Long apoderado, boolean whatsapp) {
		return como(datos.rosaEnLinea(), () -> vigentes.recibeAsistencia(apoderado, whatsapp));
	}

	@Test
	void sinFilasWhatsappNoYCorreoSi() {
		assertThat(recibe(datos.rosa(), true)).isFalse();
		assertThat(recibe(datos.rosa(), false)).isTrue();
		List<AvisosDelApoderado> avisos = como(datos.rosaEnLinea(), () -> consentimientos.avisos());
		assertThat(avisos).singleElement().satisfies(a -> {
			assertThat(a.yo()).isTrue();
			// Sprint 9, tanda 3: cada finalidad (asistencia y comunicados) con sus dos canales.
			assertThat(a.consentimientos()).extracting(c -> c.finalidad() + ":" + c.canal() + ":" + c.otorgado() + ":"
					+ c.porDefecto()).containsExactly("ASISTENCIA:WHATSAPP:false:true", "ASISTENCIA:CORREO:true:true",
							"COMUNICADOS:WHATSAPP:false:true", "COMUNICADOS:CORREO:true:true");
		});
	}

	/** La ficha de matrícula registrada de la familia del apoderado (la que muestra la ficha del alumno). */
	private String fichaDe(Long apoderado) {
		return jdbc.queryForObject("SELECT CONCAT('FM-', a.anio, '-', LPAD(m.id, 4, '0')) FROM matricula m JOIN anio_escolar a "
				+ "ON a.id = m.anio_escolar_id JOIN alumno al ON al.id = m.alumno_id JOIN apoderado p ON p.familia_id = "
				+ "al.familia_id WHERE p.id = ? ORDER BY m.id LIMIT 1", String.class, apoderado);
	}

	/** Correcciones del sprint 9 (auditoría, 6): un código inventado o de otra familia no sirve de evidencia. */
	@Test
	void desdeFichaExigeLaFichaRegistradaDeLaFamilia() {
		assertThatThrownBy(() -> como(EscenarioCobranza.ADMINISTRACION, () -> consentimientos.registrarDesdeFicha(
				new ConsentimientoFichaRequest(datos.rosa(), Finalidad.ASISTENCIA, Canal.WHATSAPP, "FM-2026-999999"))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("matrícula registrada");
		assertThatThrownBy(() -> como(EscenarioCobranza.ADMINISTRACION, () -> consentimientos.registrarDesdeFicha(
				new ConsentimientoFichaRequest(datos.rosa(), Finalidad.ASISTENCIA, Canal.WHATSAPP, fichaDe(datos.pedro())))))
				.as("la ficha de otra familia").isInstanceOf(ReglaNegocioException.class);
		assertThat(EscenarioEscolar.contar(jdbc, "consentimiento_aviso")).isZero();
	}

	@Test
	void desdeFichaExigeEvidencia() {
		assertThatThrownBy(() -> como(EscenarioCobranza.ADMINISTRACION, () -> consentimientos.registrarDesdeFicha(
				new ConsentimientoFichaRequest(datos.rosa(), Finalidad.ASISTENCIA, Canal.WHATSAPP, "  "))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("código de la ficha");
		assertThatThrownBy(() -> como(EscenarioCobranza.CAJA, () -> consentimientos.registrarDesdeFicha(
				new ConsentimientoFichaRequest(datos.rosa(), Finalidad.ASISTENCIA, Canal.WHATSAPP, fichaDe(datos.rosa())))))
				.isInstanceOf(AccessDeniedException.class);
		String ficha = fichaDe(datos.rosa());
		como(EscenarioCobranza.ADMINISTRACION, () -> consentimientos.registrarDesdeFicha(new ConsentimientoFichaRequest(
				datos.rosa(), Finalidad.ASISTENCIA, Canal.WHATSAPP, ficha)));
		assertThat(recibe(datos.rosa(), true)).isTrue();
		assertThat(jdbc.queryForObject("SELECT evidencia FROM consentimiento_aviso", String.class)).isEqualTo(ficha);
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CONSENTIMIENTO_AVISO").get("DETALLE").toString())
				.contains(ficha);
	}

	@Test
	void laFamiliaLoRevoca() {
		String ficha = fichaDe(datos.rosa());
		como(EscenarioCobranza.ADMINISTRACION, () -> consentimientos.registrarDesdeFicha(new ConsentimientoFichaRequest(
				datos.rosa(), Finalidad.ASISTENCIA, Canal.WHATSAPP, ficha)));
		como(datos.rosaEnLinea(), () -> consentimientos.registrarEnPortal(new ConsentimientoRequest(Finalidad.ASISTENCIA,
				Canal.WHATSAPP, false)));
		assertThat(recibe(datos.rosa(), true)).isFalse();
		// Solo inserción: las dos filas quedan (vale la última).
		assertThat(EscenarioEscolar.contar(jdbc, "consentimiento_aviso")).isEqualTo(2);
		// Otra familia no ve ni cambia lo de Rosa: su portal muestra solo a Pedro.
		List<AvisosDelApoderado> dePedro = como(datos.pedroEnLinea(), () -> consentimientos.avisos());
		assertThat(dePedro).extracting(AvisosDelApoderado::apoderadoId).containsExactly(datos.pedro());
	}
}
