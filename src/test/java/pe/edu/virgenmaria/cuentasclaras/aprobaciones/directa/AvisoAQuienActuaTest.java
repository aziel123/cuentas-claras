package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.FeriadoRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioFeriados;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (A1c-6): el aviso de una directa le llega TAMBIÉN a quien la hizo, por WhatsApp: si le robaron la
 * sesión, se entera al momento. Y en lo que espera, también a la persona afectada.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AvisoAQuienActuaTest extends BaseDirecta {

	@Autowired
	private ServicioFeriados feriados;

	@Test
	void quienHizoLaDirectaRecibeElAvisoEnSuCelular() {
		como(e.promotora(), () -> feriados.registrar(new FeriadoRequest(LocalDate.of(2026, 10, 9), "Aniversario")));
		assertThat(jdbc.queryForList("SELECT canal FROM mensaje WHERE plantilla = 'ACCION_DIRECTA' AND usuario_id = ?",
				String.class, e.promotora().getId())).contains("WHATSAPP");
	}

	@Test
	void enLaEsperaTambienSeAvisaALaPersonaAfectada() {
		Long solicitud = pedirRoles(e.promotora(), e.socia(), EnumSet.of(Rol.DOCENTE));
		aplicarConCodigo(e.promotora(), solicitud);
		assertThat(EscenarioDirecta.avisados(jdbc, "DIRECTA_EN_ESPERA")).contains(e.promotora().getId(),
				e.socia().getId());
	}
}
