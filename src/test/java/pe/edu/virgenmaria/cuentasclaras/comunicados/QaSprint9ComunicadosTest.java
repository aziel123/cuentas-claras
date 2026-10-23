package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.ConsentimientoRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Canal;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Finalidad;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ConsentimientosAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.AvisosComunicados;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.BorradorComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 9 (comunicados): el contacto principal por defecto (QA-S9-2), el filtro de letras parecidas de punta a
 * punta (QA-S9-1), confirmar un retirado, la fe de erratas de un comunicado que no salió y el PDF por aprobar que la
 * familia no ve.
 */
class QaSprint9ComunicadosTest extends PruebaComunicados {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Autowired
	AvisosComunicados avisos;

	@Autowired
	ConsentimientosAviso consentimientos;

	private void pasadaALas(LocalDateTime ahora) {
		reloj.fijar(ahora.atZone(LIMA).toInstant());
		avisos.enColegio(1L, ahora);
	}

	private String canalDelUrgenteDe(Long apoderado) {
		return jdbc.queryForObject("SELECT canal FROM mensaje WHERE tipo = 'COMUNICADO_URGENTE' AND apoderado_id = ?",
				String.class, apoderado);
	}

	private Long urgenteDeDireccion() {
		return como(EscenarioCobranza.DIRECCION, () -> envio.enviar(new BorradorComunicado(EmisorRol.DIRECCION,
				AlcanceComunicado.COLEGIO, null, null, null, "Suspensión de clases mañana", TEXTO, true, false), null));
	}

	// ---- QA-S9-2: contacto principal por defecto ----

	@Test
	void debeEnviarElUrgentePorWhatsAppAQuienDioElSiAunqueNoLoMarcoComoPrincipal() {
		// Dado que Rosa aceptó los comunicados por WhatsApp en el portal, sin marcar «contacto principal».
		como(rosaEnLinea(), () -> {
			consentimientos.registrarEnPortal(new ConsentimientoRequest(Finalidad.COMUNICADOS, Canal.WHATSAPP, true));
			return null;
		});
		// Cuando Dirección envía un urgente y corre la pasada.
		urgenteDeDireccion();
		pasadaALas(LocalDateTime.of(2026, 10, 6, 8, 5));
		// Entonces le llega por WhatsApp (antes solo le llegaba por correo: sin principal, nadie recibía por WhatsApp).
		assertThat(canalDelUrgenteDe(rosa)).isEqualTo("WHATSAPP");
		// Y quien no dio el «sí» a WhatsApp sigue recibiendo por correo.
		assertThat(canalDelUrgenteDe(pedro)).isEqualTo("CORREO");
	}

	@Test
	void debeEnviarPorCorreoElUrgenteSiNadieDeLaFamiliaAceptoWhatsApp() {
		urgenteDeDireccion();
		pasadaALas(LocalDateTime.of(2026, 10, 6, 8, 5));
		assertThat(canalDelUrgenteDe(rosa)).isEqualTo("CORREO");
	}

	@Test
	void debeRespetarAlContactoPrincipalMarcado() {
		como(rosaEnLinea(), () -> {
			consentimientos.registrarEnPortal(new ConsentimientoRequest(Finalidad.COMUNICADOS, Canal.WHATSAPP, true, true));
			return null;
		});
		urgenteDeDireccion();
		pasadaALas(LocalDateTime.of(2026, 10, 6, 8, 5));
		assertThat(canalDelUrgenteDe(rosa)).isEqualTo("WHATSAPP");
	}

	// ---- QA-S9-1 de punta a punta ----

	@Test
	void debeRechazarUnComunicadoDeCobranzaEscritoConLetrasArmeniasYDejarloEnLaBitacora() {
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> envio.enviar(new BorradorComunicado(
				EmisorRol.DIRECCION, AlcanceComunicado.COLEGIO, null, null, null, "Aviso a las familias",
				"Recuerden ponerse al día con la deսda de la pensiօn antes del viernes.", false, false), null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no se puede enviar");
		assertThat(contar("SELECT COUNT(*) FROM comunicado")).isZero();
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'COMUNICADO_RECHAZADO_POR_CONTENIDO'"))
				.isEqualTo(1);
	}

	// ---- Confirmación, retiro y fe de erratas ----

	@Test
	void debeRechazarLaConfirmacionDeLecturaDeUnComunicadoRetirado() {
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aLaSeccion(EmisorRol.DIRECCION, seccionA,
				"Reunión de 5.° A"), null));
		como(EscenarioCobranza.DIRECCION, () -> {
			envio.retirar(id, "Se envió con la fecha equivocada");
			return null;
		});
		assertThatThrownBy(() -> como(rosaEnLinea(), () -> {
			lectura.confirmar(id);
			return null;
		})).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("retirado");
		assertThat(contar("SELECT COUNT(*) FROM confirmacion_lectura")).isZero();
	}

	@Test
	void debeRechazarLaFeDeErratasDeUnComunicadoQueTodaviaNoSalio() {
		Long porAprobar = como(tutora, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionA, "Tarea de la semana"),
				pdf(false)));
		assertThat(jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, porAprobar))
				.isEqualTo("POR_APROBAR");
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> envio.enviarFeDeErratas(porAprobar,
				aLaSeccion(EmisorRol.DIRECCION, seccionA, "Fe de erratas de la tarea"), null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya salió");
	}

	@Test
	void debeResponder404ALaFamiliaQueAbreUnComunicadoPorAprobar() {
		Long porAprobar = como(tutora, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionA, "Tarea de la semana"),
				pdf(false)));
		assertThatThrownBy(() -> como(rosaEnLinea(), () -> lectura.abrir(porAprobar)))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> como(rosaEnLinea(), () -> lectura.adjunto(porAprobar)))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void debeResponder404AlDocenteQueCorrigeElComunicadoDeOtroDocente() {
		Long deLuis = como(docenteB, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionB, "Tarea de Matemática"),
				null));
		assertThatThrownBy(() -> como(tutora, () -> envio.enviarFeDeErratas(deLuis, aLaSeccion(EmisorRol.DOCENTE,
				seccionA, "Fe de erratas de Matemática"), null))).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(contar("SELECT COUNT(*) FROM comunicado")).isEqualTo(1);
	}
}
