package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioContactoPersonal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (P1 y A1c-1): el contacto propio NUNCA es directa. La Promotoría que cambia su celular deja la
 * solicitud PENDIENTE; antes de que otra Promotoría la apruebe, la confirma ella desde su contacto ANTERIOR (con el
 * código que le llega ahí). Quien robó la sesión no se queda con el canal de los códigos.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ContactoPropioNoDirectoTest extends BaseDirecta {

	@Autowired
	private ServicioContactoPersonal contactos;

	@Test
	void elCelularPropioEsperaLaConfirmacionYLaApruebaOtraPromotoria() {
		String anterior = jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class,
				e.promotora().getId());
		Long solicitud = como(e.promotora(), () -> contactos.solicitar(e.promotora().getId(), "987 654 321", null,
				"Cambié de número de celular"));
		assertThat(estado(solicitud)).isEqualTo("PENDIENTE");
		// Ella no la aprueba (ni directo).
		assertThatThrownBy(() -> como(e.promotora(), () -> bandeja.aprobar(solicitud, null)))
				.isInstanceOf(AutoaprobacionSolicitudException.class);
		// Otra Promotoría tampoco, hasta que la dueña confirme desde su contacto anterior.
		assertThatThrownBy(() -> como(e.socia(), () -> bandeja.aprobar(solicitud, null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Todavía no lo confirmó");

		CodigoPromotoria.Pedido pedido = como(e.promotora(), () -> contactos.pedirCodigoDelContactoAnterior(solicitud));
		assertThat(jdbc.queryForMap("SELECT tipo, destino FROM mensaje WHERE id = ?", pedido.mensajeId()))
				.containsEntry("tipo", "CODIGO_CONTACTO").containsEntry("destino", anterior);
		assertThatThrownBy(() -> como(e.promotora(), () -> contactos.confirmarDesdeContactoAnterior(solicitud,
				EscenarioDirecta.equivocado(jdbc, derivador, pedido)))).isInstanceOf(ReglaNegocioException.class);
		como(e.promotora(), () -> contactos.confirmarDesdeContactoAnterior(solicitud, EscenarioDirecta.escrito(jdbc,
				derivador, pedido)));
		assertThat(EscenarioDirecta.eventos(jdbc, "CONTACTO_CONFIRMADO")).isEqualTo(1L);

		como(e.socia(), () -> bandeja.aprobar(solicitud, null));
		assertThat(estado(solicitud)).isEqualTo("APROBADA");
		assertThat(jdbc.queryForObject("SELECT directa FROM solicitud_cambio WHERE id = ?", Boolean.class, solicitud))
				.as("no es directa").isNull();
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class,
				e.promotora().getId())).isNotEqualTo(anterior).endsWith("987654321");
	}

	@Test
	void elContactoDeOtraCuentaDelPersonalSiEsDirecta() {
		// El de la cuenta de Administración (no directiva): la Promotoría lo aplica ella desde la bandeja.
		Long solicitud = como(e.promotora(), () -> contactos.solicitar(e.administracion().getId(), "987 654 322", null,
				"Cambió de número de celular"));
		como(e.promotora(), () -> bandeja.aprobar(solicitud, null));
		assertThat(jdbc.queryForMap("SELECT estado, directa FROM solicitud_cambio WHERE id = ?", solicitud))
				.containsEntry("estado", "APROBADA").containsEntry("directa", true);
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class,
				e.administracion().getId())).endsWith("987654322");
	}
}
