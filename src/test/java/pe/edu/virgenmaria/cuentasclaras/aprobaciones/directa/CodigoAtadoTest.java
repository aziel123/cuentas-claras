package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (A1c-5 y A1c-6): el código va atado a la acción por su huella
 * ({@code SHA2(tipo|entidad|entidad_id|datos)}): el WhatsApp dice qué autoriza y el código no sirve para otra cosa, ni
 * siquiera para la misma acción sobre otra cuenta. El mensaje guarda la huella y la SHA-256 del código, nunca el código.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CodigoAtadoTest extends BaseDirecta {

	@Test
	void elMensajeGuardaLaHuellaDeLaAccionYNoElCodigo() {
		Long solicitud = pedirRoles(e.promotora(), e.docente(), EnumSet.of(Rol.DIRECTOR));
		CodigoPromotoria.Pedido pedido = como(e.promotora(), () -> bandeja.pedirCodigo(solicitud));
		String codigo = EscenarioDirecta.escrito(jdbc, derivador, pedido).codigo();

		var mensaje = jdbc.queryForMap("SELECT huella_accion, codigo_sha256, parametros FROM mensaje WHERE id = ?",
				pedido.mensajeId());
		var fila = jdbc.queryForMap("SELECT tipo, entidad, entidad_id, datos FROM solicitud_cambio WHERE id = ?", solicitud);
		assertThat(mensaje.get("huella_accion")).isEqualTo(SolicitudCambio.huella(
				TipoSolicitud.valueOf((String) fila.get("tipo")),				(String) fila.get("entidad"), ((Number) fila.get("entidad_id")).longValue(), (String) fila.get("datos")));
		assertThat(mensaje.get("codigo_sha256")).isEqualTo(pe.edu.virgenmaria.cuentasclaras.comun.cripto.Sha256.hex(codigo));
		assertThat((String) mensaje.get("parametros")).doesNotContain(codigo).contains("docente.dir");
	}

	@Test
	void elCodigoDeUnaCuentaNoSirveParaOtra() {
		Long paraElDocente = pedirRoles(e.promotora(), e.docente(), EnumSet.of(Rol.DIRECTOR));
		var otroDocente = pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba.guardar(usuarios, codificador, 1L,
				"docente2.dir", pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba.CLAVE, false, Rol.DOCENTE);
		Long paraAdministracion = pedirRoles(e.promotora(), otroDocente, EnumSet.of(Rol.DIRECTOR));
		CodigoPromotoria.Pedido pedido = como(e.promotora(), () -> bandeja.pedirCodigo(paraElDocente));
		assertThatThrownBy(() -> como(e.promotora(), () -> bandeja.aprobar(paraAdministracion, null, false, List.of(),
				EscenarioDirecta.escrito(jdbc, derivador, pedido)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("otra acción");
		assertThat(estado(paraAdministracion)).isEqualTo("PENDIENTE");
		// Para la suya, sí.
		como(e.promotora(), () -> bandeja.aprobar(paraElDocente, null, false, List.of(), EscenarioDirecta.escrito(jdbc,
				derivador, pedido)));
		assertThat(estado(paraElDocente)).isEqualTo("APROBADA");
	}
}
