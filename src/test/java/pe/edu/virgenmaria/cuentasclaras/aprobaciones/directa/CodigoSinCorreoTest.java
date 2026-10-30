package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (A1c-7): el código de las acciones sobre roles directivos (y de los conectores) llega SOLO por
 * WhatsApp, nunca por correo: una cuenta sin celular no lo recibe y la acción no se aplica. El correo de la cuenta es lo
 * primero que cambiaría quien la robó.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CodigoSinCorreoTest extends BaseDirecta {

	@Test
	void sinCelularNoHayCodigoAunqueTengaCorreo() {
		Long solicitud = pedirRoles(e.promotora(), e.docente(), EnumSet.of(Rol.DIRECTOR));
		jdbc.update("UPDATE usuario SET telefono_whatsapp = NULL, correo = 'duena@gmail.com' WHERE id = ?",
				e.promotora().getId());

		assertThatThrownBy(() -> como(e.promotora(), () -> bandeja.pedirCodigo(solicitud)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("solo por WhatsApp");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'CODIGO_PROMOTORIA'", Long.class)).isZero();
		assertThat(estado(solicitud)).isEqualTo("PENDIENTE");
	}

	@Test
	void elCodigoSaleSoloPorWhatsapp() {
		Long solicitud = pedirRoles(e.promotora(), e.docente(), EnumSet.of(Rol.DIRECTOR));
		jdbc.update("UPDATE usuario SET correo = 'duena@gmail.com' WHERE id = ?", e.promotora().getId());
		como(e.promotora(), () -> bandeja.pedirCodigo(solicitud));
		assertThat(jdbc.queryForList("SELECT canal FROM mensaje WHERE tipo = 'CODIGO_PROMOTORIA'", String.class))
				.containsExactly("WHATSAPP");
	}
}
