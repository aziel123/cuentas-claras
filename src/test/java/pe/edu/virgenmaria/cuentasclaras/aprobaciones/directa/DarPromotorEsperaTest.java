package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.FrenosOperador;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.time.Duration;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.CORREO_CONTROL;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (A1c-3): dar Promotoría en producción es directa con código, pero NO se aplica al instante: espera
 * 24 horas con freno (otra Promotoría con 30 días en el rol, la persona afectada o un operador) y la aplica
 * {@code sistema.roles} al vencer. En preparación, sin espera.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class DarPromotorEsperaTest extends BaseDirecta {

	@Autowired
	private FrenosOperador frenos;

	@Test
	void esperaVeinticuatroHorasYLaAplicaSistemaRoles() {
		Long solicitud = pedirRoles(e.promotora(), e.docente(), EnumSet.of(Rol.PROMOTOR));
		aplicarConCodigo(e.promotora(), solicitud);

		assertThat(jdbc.queryForMap("SELECT estado, directa FROM solicitud_cambio WHERE id = ?", solicitud))
				.containsEntry("estado", "APROBADA").containsEntry("directa", true);
		assertThat(roles(e.docente())).as("todavía no").containsExactly("DOCENTE");
		// Una vez en el colegio y otra en la bitácora de la plataforma (el aviso a los operadores).
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'DIRECTA_EN_ESPERA' AND "
				+ "entidad = 'solicitud_cambio'", Long.class)).isEqualTo(1L);
		// El aviso: a las Promotorías, al correo de control y a la persona afectada.
		assertThat(EscenarioDirecta.avisados(jdbc, "DIRECTA_EN_ESPERA")).contains(e.promotora().getId(),
				e.socia().getId(), e.docente().getId());
		assertThat(EscenarioDirecta.destinos(jdbc, "DIRECTA_EN_ESPERA")).contains(CORREO_CONTROL);

		avanzar(Duration.ofHours(23));
		assertThat(esperas.aplicar(1L)).isZero();
		assertThat(roles(e.docente())).containsExactly("DOCENTE");
		avanzar(Duration.ofHours(2));
		assertThat(esperas.aplicar(1L)).isEqualTo(1);
		assertThat(roles(e.docente())).containsExactly("PROMOTOR");
		assertThat(EscenarioDirecta.eventos(jdbc, "DIRECTA_APLICADA_AL_VENCER")).isEqualTo(1L);
		// Una sola vez.
		assertThat(esperas.aplicar(1L)).isZero();
	}

	@Test
	void laPersonaAfectadaLaFrenaYNoSeAplica() {
		Long solicitud = pedirRoles(e.promotora(), e.docente(), EnumSet.of(Rol.PROMOTOR));
		aplicarConCodigo(e.promotora(), solicitud);
		// Quien la pidió no la frena.
		assertThatThrownBy(() -> como(e.promotora(), () -> bandeja.frenar(solicitud, "Me equivoqué de persona")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Tú lo pediste");
		como(e.docente(), () -> bandeja.frenar(solicitud, "Yo no pedí ser Promotoría del colegio"));

		assertThat(jdbc.queryForMap("SELECT frenado_por, motivo_freno FROM solicitud_cambio WHERE id = ?", solicitud))
				.containsEntry("frenado_por", "docente.dir")
				.containsEntry("motivo_freno", "Yo no pedí ser Promotoría del colegio");
		assertThat(EscenarioDirecta.destinos(jdbc, "DIRECTA_FRENADA")).contains(CORREO_CONTROL);
		avanzar(Duration.ofHours(25));
		assertThat(esperas.aplicar(1L)).isZero();
		assertThat(roles(e.docente())).containsExactly("DOCENTE");
	}

	@Test
	void elOperadorLaFrena() {
		Long solicitud = pedirRoles(e.promotora(), e.docente(), EnumSet.of(Rol.PROMOTOR));
		aplicarConCodigo(e.promotora(), solicitud);
		Usuario operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.roles");
		UsuariosDePrueba.iniciarSesion(operador);
		try {
			assertThat(frenos.esperas()).anyMatch(x -> x.espera().id().equals(solicitud));
			frenos.frenarSolicitud(1L, solicitud, "El titular llamó: no reconoce este cambio");
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(jdbc.queryForObject("SELECT frenado_por FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("operador.roles");
		avanzar(Duration.ofHours(25));
		assertThat(esperas.aplicar(1L)).isZero();
		assertThat(roles(e.docente())).containsExactly("DOCENTE");
	}

	@Test
	void enPreparacionNoEspera() {
		var antes = jdbc.queryForMap("SELECT produccion_desde, produccion_por FROM colegio WHERE id = 1");
		jdbc.update("UPDATE colegio SET estado = 'PREPARACION', produccion_desde = NULL, produccion_por = NULL WHERE id = 1");
		try {
			Long solicitud = pedirRoles(e.promotora(), e.docente(), EnumSet.of(Rol.PROMOTOR));
			aplicarConCodigo(e.promotora(), solicitud);
			assertThat(roles(e.docente())).containsExactly("PROMOTOR");
			assertThat(EscenarioDirecta.eventos(jdbc, "DIRECTA_EN_ESPERA")).isZero();
		}
		finally {
			jdbc.update("UPDATE colegio SET estado = 'PRODUCCION', produccion_desde = ?, produccion_por = ? WHERE id = 1",
					antes.get("produccion_desde"), antes.get("produccion_por"));
		}
	}
}
