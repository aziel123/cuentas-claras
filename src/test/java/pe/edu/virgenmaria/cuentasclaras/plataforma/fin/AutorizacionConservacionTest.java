package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.model.PasoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.ConservacionDatos;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 11, tanda 2 (AP-11 y AP2-9): la autorización de conservación es el paso 16 de la Puesta en marcha, obligatorio
 * (16 pasos, 14 obligatorios). La da y la retira SOLO el titular, con su firma (y aviso al correo de control); otra
 * Promotoría no puede. Desde SUSPENDIDO queda congelada.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AutorizacionConservacionTest extends BaseFinColegio {

	@Autowired
	private ConservacionDatos conservacion;

	@Autowired
	private EstadoPuestaEnMarcha estadoPuestaEnMarcha;

	@Autowired
	private PasswordEncoder codificadorClaves;

	@Test
	void elPaso16LoFirmaSoloElTitularYSeRetiraConSuFirma() {
		assertThat(PasoPuestaEnMarcha.TOTAL).isEqualTo(16);
		assertThat(PasoPuestaEnMarcha.OBLIGATORIOS).isEqualTo(14);
		assertThat(PasoPuestaEnMarcha.CONSERVACION.numero()).isEqualTo(16);
		assertThat(PasoPuestaEnMarcha.CONSERVACION.obligatorio()).isTrue();

		long colegio = colegioEnProduccion("Colegio Conserva", EscenarioPlataforma.RUC_A, "titular.conserva", "45678981",
				"987650081");
		assertThat(punto(colegio)).isFalse();

		// Otra Promotoría del colegio no la da.
		Usuario otra = ContextoColegio.en(colegio, () -> UsuariosDePrueba.guardar(usuarios, codificadorClaves, colegio,
				"promotoria.otra", UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR));
		ingresar(otra);
		assertThatThrownBy(() -> ContextoColegio.en(colegio, () -> conservacion.autorizar()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("solo el titular");

		Usuario titular = usuario(colegio, "titular.conserva");
		ingresar(titular);
		ContextoColegio.en(colegio, () -> conservacion.autorizar());
		assertThat(punto(colegio)).isTrue();
		assertThat(jdbc.queryForObject("SELECT conservacion_autorizada_por FROM colegio WHERE id = ?", String.class, colegio))
				.isEqualTo("titular.conserva");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE colegio_id = ? AND clave = ?", Long.class,
				colegio, "colegio:" + colegio + ":CONSERVACION")).isEqualTo(1L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND plantilla = 'CONSERVACION' "
				+ "AND destinatario_tipo = 'EXTERNO'", Long.class, colegio)).isPositive();

		ingresar(titular);
		ContextoColegio.en(colegio, () -> conservacion.retirar());
		assertThat(jdbc.queryForObject("SELECT conservacion_autorizada_en FROM colegio WHERE id = ?", Object.class, colegio))
				.isNull();
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, colegio))
				.contains("CONSERVACION_AUTORIZADA", "CONSERVACION_RETIRADA");
	}

	@Test
	void desdeLaSuspensionQuedaCongelada() {
		long colegio = colegioEnProduccion("Colegio Congela", EscenarioPlataforma.RUC_B, "titular.congela", "45678982",
				"987650082");
		suspender(colegio);
		Usuario titular = usuario(colegio, "titular.congela");
		ingresar(titular);
		assertThatThrownBy(() -> ContextoColegio.en(colegio, () -> conservacion.autorizar()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("suspensión");
		SecurityContextHolder.clearContext();
	}

	private boolean punto(long colegio) {
		return estadoPuestaEnMarcha.paraOperador(colegio).puntos().stream()
				.filter(p -> p.clave().equals(EstadoPuestaEnMarcha.CONSERVACION)).findFirst().orElseThrow().hecho();
	}
}
