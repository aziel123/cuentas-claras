package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConsentimientoAviso;
import pe.edu.virgenmaria.cuentasclaras.academico.model.OrigenConsentimiento;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.ConsentimientoAvisoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Correcciones del sprint 9 (auditoría del código, 6): quien registra más de 30 consentimientos desde la ficha firmada en
 * un día genera una alerta que va SOLO a Dirección, con su usuario y la cantidad.
 */
class AlertaConsentimientosFichaTest {

	private static ConsentimientoAviso de(String quien) {
		ConsentimientoAviso c = mock(ConsentimientoAviso.class);
		when(c.getCreadoPor()).thenReturn(quien);
		return c;
	}

	@Test
	void debeAvisarADireccionConMasDeTreintaPorPersonaEnElDia() {
		ConsentimientoAvisoRepository repositorio = mock(ConsentimientoAvisoRepository.class);
		List<ConsentimientoAviso> hoy = new ArrayList<>();
		for (int i = 0; i < 31; i++) {
			hoy.add(de("rosa.admin"));
		}
		for (int i = 0; i < 30; i++) {
			hoy.add(de("luis.direccion"));
		}
		when(repositorio.findByOrigenAndCreadoEnGreaterThanEqual(eq(OrigenConsentimiento.FICHA), any())).thenReturn(hoy);
		AlertasAcademicas alertas = new AlertasAcademicas(null, null, null, null, null, null, null, Clock.systemDefaultZone(),
				repositorio);
		List<AlertaRevision> resultado = alertas.consentimientosDesdeFicha(LocalDateTime.of(2026, 10, 22, 15, 0));
		assertThat(resultado).singleElement().satisfies(a -> {
			assertThat(a.texto()).contains("rosa.admin").contains("31");
			assertThat(a.aviso().tipo()).isEqualTo(TipoAviso.CONSENTIMIENTOS_DESDE_FICHA);
			assertThat(a.aviso().referencia()).isEqualTo("FICHAS:20261022:rosa-admin");
		});
		assertThat(TipoAviso.CONSENTIMIENTOS_DESDE_FICHA.soloDireccion()).isTrue();
	}
}
