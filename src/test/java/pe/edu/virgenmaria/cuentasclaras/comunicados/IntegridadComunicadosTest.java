package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.ResultadoVerificacion;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorIntegridadAuditoria;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.IntegridadComunicados;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 3 (E19, B11): «Verificar integridad» detecta un comunicado cambiado por el DBA (su huella ya no coincide
 * con la del evento de la bitácora) y, al enviarse, los destinatarios que no coinciden con el alcance quedan resaltados.
 */
class IntegridadComunicadosTest extends PruebaComunicados {

	@Autowired
	IntegridadComunicados integridad;

	@Autowired
	VerificadorIntegridadAuditoria verificador;

	@Autowired
	ComunicadoRepository comunicados;

	@Autowired
	PlatformTransactionManager transacciones;

	@Test
	void unTextoCambiadoPorElDbaSeDetecta() {
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aTodoElColegio(EmisorRol.DIRECCION,
				"Día del Logro"), null));
		conPdfAprobado(aTodoElColegio(EmisorRol.DIRECCION, "Simulacro de sismo"));
		assertThat(como(EscenarioCobranza.PROMOTORIA, () -> integridad.problemas())).isEmpty();
		ResultadoVerificacion antes = como(EscenarioCobranza.PROMOTORIA, () -> verificador.verificar());
		assertThat(antes.integra()).as(antes.detalle()).isTrue();
		// El DBA cambia el texto (en MySQL, con la clave de cc_app es 1143; aquí, como el DBA, sin GRANT).
		jdbc.update("UPDATE comunicado SET cuerpo = 'Traigan el aporte para el paseo, por favor.' WHERE id = ?", id);
		assertThat(como(EscenarioCobranza.PROMOTORIA, () -> integridad.problemas()))
				.singleElement().asString().contains("comunicado " + id);
		ResultadoVerificacion despues = como(EscenarioCobranza.PROMOTORIA, () -> verificador.verificar());
		assertThat(despues.integra()).isFalse();
		assertThat(despues.detalle()).contains("comunicado " + id);
	}

	@Test
	void unPdfCambiadoPorElDbaSeDetecta() {
		Long id = conPdfAprobado(aTodoElColegio(EmisorRol.DIRECCION, "Simulacro de sismo"));
		Long adjunto = jdbc.queryForObject("SELECT adjunto_id FROM comunicado WHERE id = ?", Long.class, id);
		jdbc.update("UPDATE comunicado_adjunto SET contenido = ? WHERE id = ?", PdfsDePrueba.conPocoTexto(), adjunto);
		assertThat(como(EscenarioCobranza.PROMOTORIA, () -> integridad.problemas())).singleElement().asString()
				.contains("PDF del comunicado " + id);
	}

	/** B11: si los destinatarios congelados no coinciden con las familias del alcance, queda resaltado. */
	@Test
	void destinatariosQueNoCoincidenSeResaltan() {
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aLaSeccion(EmisorRol.DIRECCION, seccionA,
				"Reunión de 5.° A"), null));
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = "
				+ "'COMUNICADO_DESTINATARIOS_DESCUADRADOS'")).isZero();
		// Alguien quitó una familia de la lista con acceso directo a la base.
		jdbc.update("DELETE FROM comunicado_destinatario WHERE comunicado_id = ? AND familia_id = ?", id, familiaPedro);
		Boolean coinciden = como(EscenarioCobranza.DIRECCION, () -> new TransactionTemplate(transacciones)
				.execute(t -> integridad.revisarDestinatarios(comunicados.findById(id).orElseThrow())));
		assertThat(coinciden).isFalse();
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = "
				+ "'COMUNICADO_DESTINATARIOS_DESCUADRADOS'")).isEqualTo(1);
	}
}
