package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.BorradorComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.ComunicadoParaFamilia;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.VistaPrevia;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 9, tanda 3 (secciones 3.8 y 3.9; E16 a E22, E27, E28; B1, B2, B3): enviar comunicados. Nadie se hace pasar por
 * Dirección; el docente solo a sus secciones; el auxiliar no envía; el filtro no tiene excepciones y su rechazo queda en
 * la bitácora; el PDF de un docente o con poco texto espera al otro órgano; la fe de erratas llega a todos los del
 * original y no lo toca; retirar no borra; el mismo contenido no sale dos veces.
 */
class ComunicadosTest extends PruebaComunicados {

	@Test
	void elDocenteNoEscribeComoDireccion() {
		assertThatThrownBy(() -> como(tutora, () -> envio.enviar(aLaSeccion(EmisorRol.DIRECCION, seccionA, "Aviso de la"
				+ " semana"), null))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("rol de tu cuenta");
		assertThatThrownBy(() -> como(tutora, () -> envio.enviar(aTodoElColegio(EmisorRol.DOCENTE, "Aviso a todos"), null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("una de sus secciones");
		assertThatThrownBy(() -> como(tutora, () -> envio.enviar(new BorradorComunicado(EmisorRol.DOCENTE,
				AlcanceComunicado.SECCION, null, null, seccionA, "Urgente de la tutora", TEXTO, true, true), null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("urgentes");
		Long id = como(tutora, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionA, "Salida al museo"), null));
		Map<String, Object> fila = jdbc.queryForMap("SELECT emisor_rol, emisor_etiqueta, emisor_usuario_id FROM comunicado "
				+ "WHERE id = ?", id);
		assertThat(fila.get("emisor_rol")).isEqualTo("DOCENTE");
		assertThat(String.valueOf(fila.get("emisor_etiqueta"))).startsWith("Prof. ").contains("tutor(a) de")
				.doesNotContain("Dirección");
		assertThat(((Number) fila.get("emisor_usuario_id")).longValue()).isEqualTo(tutora.getId());
	}

	@Test
	void seccionAjenaResponde404() {
		assertThatThrownBy(() -> como(tutora, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionB, "Aviso a 5.° B"),
				null))).isInstanceOf(RecursoNoEncontradoException.class);
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aLaSeccion(EmisorRol.DIRECCION, seccionB,
				"Aviso de Dirección a 5.° B"), null));
		assertThatThrownBy(() -> como(tutora, () -> consulta.detalle(id))).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void elAuxiliarNoEnvia() {
		assertThat(como(auxiliar, () -> envio.opciones()).puedeEscribir()).isFalse();
		assertThatThrownBy(() -> como(auxiliar, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionA, "Aviso del "
				+ "auxiliar"), null))).isInstanceOf(ReglaNegocioException.class);
		assertThat(contar("SELECT COUNT(*) FROM comunicado")).isZero();
	}

	@Test
	void cobranzaSeRechazaYQuedaEnLaBitacora() {
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> envio.enviar(new BorradorComunicado(
				EmisorRol.DIRECCION, AlcanceComunicado.COLEGIO, null, null, null, "Recordatorio importante",
				"Recuerden pagar la pensión de octubre antes del viernes.", false, true), null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no se puede enviar");
		assertThatThrownBy(() -> como(tutora, () -> envio.enviar(new BorradorComunicado(EmisorRol.DOCENTE,
				AlcanceComunicado.SECCION, null, null, seccionA, "Para el paseo", "Yapeen al 987654321 el aporte del "
						+ "paseo, por favor.", false, true), null))).isInstanceOf(ReglaNegocioException.class);
		assertThat(contar("SELECT COUNT(*) FROM comunicado")).isZero();
		List<Map<String, Object>> eventos = jdbc.queryForList("SELECT valor_nuevo, detalle FROM evento_auditoria WHERE "
				+ "accion = 'COMUNICADO_RECHAZADO_POR_CONTENIDO' ORDER BY secuencia");
		assertThat(eventos).hasSize(2);
		assertThat(eventos.get(0).get("valor_nuevo")).isEqualTo("COBRANZA");
		assertThat(String.valueOf(eventos.get(1).get("detalle"))).contains("docente")
				.doesNotContain("987654321").doesNotContain("Yapeen");
		assertThat(String.valueOf(eventos.get(0).get("detalle"))).doesNotContain("pensión de octubre");
	}

	@Test
	void pdfDeDocenteQuedaPorAprobar() {
		Long id = como(tutora, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionA, "Programa del paseo"), pdf(false)));
		assertThat(estado(id)).isEqualTo("POR_APROBAR");
		assertThat(contar("SELECT COUNT(*) FROM comunicado_destinatario WHERE comunicado_id = ?", id)).isZero();
		assertThat(jdbc.queryForObject("SELECT tipo FROM solicitud_cambio WHERE entidad = 'comunicado' AND entidad_id = ?",
				String.class, id)).isEqualTo("PUBLICAR_COMUNICADO");
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "comunicado", id);
		assertThat(estado(id)).isEqualTo("ENVIADO");
		assertThat(contar("SELECT COUNT(*) FROM comunicado_destinatario WHERE comunicado_id = ?", id)).isEqualTo(2);
		assertThat(contar("SELECT destinatarios FROM comunicado WHERE id = ?", id)).isEqualTo(2);
	}

	/** B3: el PDF de imagen (o con poco texto) de Dirección lo aprueba Promotoría, nunca otra persona de Dirección. */
	@Test
	void pdfDeImagenDeDireccionLoApruebaPromotoria() {
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aTodoElColegio(EmisorRol.DIRECCION, "Afiche del "
				+ "Día del Logro"), pdf(true)));
		assertThat(estado(id)).isEqualTo("POR_APROBAR");
		assertThatThrownBy(() -> EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION_Y_ADMINISTRACION, bandeja, jdbc,
				"comunicado", id)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Promotoría");
		assertThat(estado(id)).isEqualTo("POR_APROBAR");
		EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "comunicado", id);
		assertThat(estado(id)).isEqualTo("ENVIADO");
		assertThat(contar("SELECT COUNT(*) FROM comunicado_destinatario WHERE comunicado_id = ?", id)).isEqualTo(3);
		// El PDF con texto de Dirección sale al momento.
		Long conTexto = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aTodoElColegio(EmisorRol.DIRECCION,
				"Programa del Día del Logro"), pdf(false)));
		assertThat(estado(conTexto)).isEqualTo("ENVIADO");
	}

	@Test
	void todoComunicadoLlevaElPie() {
		VistaPrevia previa = como(EscenarioCobranza.DIRECCION, () -> envio.previsualizar(aTodoElColegio(
				EmisorRol.DIRECCION, "Día del Logro"), null, null));
		assertThat(previa.pie()).contains("nunca le pedirá pagos ni datos personales");
		assertThat(previa.destinatarias()).isEqualTo(3);
		assertThat(contar("SELECT COUNT(*) FROM comunicado")).as("la vista previa no guarda nada").isZero();
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aTodoElColegio(EmisorRol.DIRECCION,
				"Día del Logro"), null));
		ComunicadoParaFamilia visto = como(rosaEnLinea(), () -> lectura.abrir(id));
		assertThat(visto.pie()).contains("nunca le pedirá pagos");
		assertThat(como(EscenarioCobranza.DIRECCION, () -> consulta.detalle(id)).pie()).contains("nunca le pedirá pagos");
	}

	@Test
	void laFeDeErratasNoTocaElOriginal() {
		Long original = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aTodoElColegio(EmisorRol.DIRECCION,
				"Día del Logro"), null));
		Map<String, Object> antes = jdbc.queryForMap("SELECT titulo, cuerpo, huella_contenido, estado FROM comunicado "
				+ "WHERE id = ?", original);
		Long fe = como(EscenarioCobranza.DIRECCION, () -> envio.enviarFeDeErratas(original, new BorradorComunicado(
				EmisorRol.DIRECCION, AlcanceComunicado.COLEGIO, null, null, null, "Fe de erratas: Día del Logro",
				"Corregimos la hora: el Día del Logro empieza a las 9:30.", false, true), null));
		assertThat(jdbc.queryForMap("SELECT titulo, cuerpo, huella_contenido, estado FROM comunicado WHERE id = ?",
				original)).isEqualTo(antes);
		assertThat(jdbc.queryForMap("SELECT tipo, corrige_id FROM comunicado WHERE id = ?", fe))
				.containsEntry("tipo", "FE_DE_ERRATAS");
		assertThat(contar("SELECT corrige_id FROM comunicado WHERE id = ?", fe)).isEqualTo(original);
		assertThat(como(rosaEnLinea(), () -> lectura.abrir(original)).feDeErratas()).containsExactly(fe);
	}

	/** B1: la fe de erratas llega a TODAS las familias del original, aunque el alcance haya cambiado. */
	@Test
	void laFeDeErratasLlegaATodos() {
		Long original = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aLaSeccion(EmisorRol.DIRECCION, seccionA,
				"Reunión de padres de 5.° A"), null));
		// Después del original llega una familia nueva a la sección: la fe de erratas no le llega (no recibió el original).
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("71234570", "Luna", "Paz", "Diego", LocalDate.of(2015, 4, 1),
				"41234570", "Luna", "Ríos", "Elena", "987555666", "elena.luna@gmail.com", seccionA));
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		Long fe = como(EscenarioCobranza.DIRECCION, () -> envio.enviarFeDeErratas(original, aLaSeccion(EmisorRol.DIRECCION,
				seccionA, "Fe de erratas: reunión de 5.° A"), null));
		List<Long> delOriginal = jdbc.queryForList("SELECT familia_id FROM comunicado_destinatario WHERE comunicado_id = ? "
				+ "ORDER BY familia_id", Long.class, original);
		List<Long> deLaFe = jdbc.queryForList("SELECT familia_id FROM comunicado_destinatario WHERE comunicado_id = ? "
				+ "ORDER BY familia_id", Long.class, fe);
		assertThat(deLaFe).isEqualTo(delOriginal).containsExactlyInAnyOrder(familiaRosa, familiaPedro);
		assertThat(contar("SELECT destinatarios FROM comunicado WHERE id = ?", fe)).isEqualTo(2);
	}

	/** E28 y B9: retirar no borra; la familia ve la constancia; la fe de erratas de un retirado, solo Dirección. */
	@Test
	void retirarNoBorra() {
		Long id = como(tutora, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionA, "Tarea de la semana"), null));
		assertThatThrownBy(() -> como(tutora, () -> {
			envio.retirar(id, "Se envió por error a la sección");
			return null;
		})).isInstanceOf(AccessDeniedException.class);
		como(EscenarioCobranza.DIRECCION, () -> {
			envio.retirar(id, "Se envió por error a la sección");
			return null;
		});
		Map<String, Object> fila = jdbc.queryForMap("SELECT estado, cuerpo, motivo_retiro, retirado_por FROM comunicado "
				+ "WHERE id = ?", id);
		assertThat(fila.get("estado")).isEqualTo("RETIRADO");
		assertThat(fila.get("cuerpo")).isEqualTo(TEXTO);
		assertThat(fila.get("motivo_retiro")).isEqualTo("Se envió por error a la sección");
		ComunicadoParaFamilia visto = como(rosaEnLinea(), () -> lectura.abrir(id));
		assertThat(visto.retirado()).isTrue();
		assertThat(visto.parrafos()).isEmpty();
		assertThat(visto.constanciaRetiro()).contains("fue retirado").contains("Se envió por error");
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'COMUNICADO_RETIRADO'")).isEqualTo(1);
		// B9: el docente no publica la fe de erratas de un retirado; Dirección, sí.
		assertThatThrownBy(() -> como(tutora, () -> envio.enviarFeDeErratas(id, aLaSeccion(EmisorRol.DOCENTE, seccionA,
				"Fe de erratas de la tarea"), null))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("retirado");
		Long fe = como(EscenarioCobranza.DIRECCION, () -> envio.enviarFeDeErratas(id, aLaSeccion(EmisorRol.DIRECCION,
				seccionA, "Aclaración sobre la tarea"), null));
		assertThat(estado(fe)).isEqualTo("ENVIADO");
	}

	/** B2: la huella es única por colegio (y la firma del envío, también): el mismo contenido no sale dos veces. */
	@Test
	void elMismoContenidoNoSaleDosVeces() {
		como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aTodoElColegio(EmisorRol.DIRECCION, "Día del Logro"), null));
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aTodoElColegio(EmisorRol.DIRECCION,
				"Día del Logro"), null))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("mismo contenido");
		assertThat(contar("SELECT COUNT(*) FROM comunicado")).isEqualTo(1);
	}

	/** E19: la huella guardada es la misma cadena que recalcula la base (HuellaComunicado). */
	@Test
	void laHuellaEsLaDelContenido() {
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aLaSeccion(EmisorRol.DIRECCION, seccionA,
				"Día del Logro"), null));
		String huella = jdbc.queryForObject("SELECT huella_contenido FROM comunicado WHERE id = ?", String.class, id);
		assertThat(huella).isEqualTo(pe.edu.virgenmaria.cuentasclaras.comunicados.service.HuellaComunicado.de(
				AlcanceComunicado.SECCION, null, null, seccionA, "Día del Logro", TEXTO, null));
		assertThat(jdbc.queryForObject("SELECT valor_nuevo FROM evento_auditoria WHERE accion = 'COMUNICADO_ENVIADO'",
				String.class)).isEqualTo(huella);
	}

	private String estado(Long id) {
		return jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, id);
	}
}
