package pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.FirmaOperacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.FirmaOperacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Sprint 7, tanda 2 (sección 3.4, paso 3): una aprobación se firma con el secreto de la sesión HTTP de quien aprueba.
 * Sin esa sesión (o con la de otra persona) no se aprueba nada; un actor de sistema no firma (su conexión lo identifica).
 */
class FirmaSesionTest {

	private static final String TOKEN = "b".repeat(64);

	private final FirmaOperacionRepository firmas = mock(FirmaOperacionRepository.class);

	private final FirmaSesion firma = new FirmaSesion(new TokenDeSesionHttp(), firmas, new MockEnvironment());

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
	}

	private static void peticionCon(SesionAbierta sesion) {
		MockHttpServletRequest peticion = new MockHttpServletRequest();
		MockHttpSession http = new MockHttpSession();
		if (sesion != null) {
			TokenDeSesionHttp.guardar(http, sesion);
		}
		peticion.setSession(http);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
	}

	@Test
	void conLaSesionDeQuienApruebaFirma() {
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(1L, 7L, "director", "Director", false,
				EnumSet.of(Rol.DIRECTOR)));
		peticionCon(new SesionAbierta(30L, 7L, 1L, TOKEN));

		firma.firmar("descuento:5:APROBADO");

		ArgumentCaptor<FirmaOperacion> guardada = ArgumentCaptor.forClass(FirmaOperacion.class);
		verify(firmas).saveAndFlush(guardada.capture());
		assertThat(guardada.getValue().getSesionId()).isEqualTo(30L);
		assertThat(guardada.getValue().getUsuarioId()).isEqualTo(7L);
		assertThat(guardada.getValue().getClave()).isEqualTo("descuento:5:APROBADO");
	}

	@Test
	void sinSesionOConLaDeOtraPersonaNoSeApruebaNada() {
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(1L, 7L, "director", "Director", false,
				EnumSet.of(Rol.DIRECTOR)));
		assertThatThrownBy(() -> firma.firmar("descuento:5:APROBADO")).as("sin petición")
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("vuelve a ingresar");

		peticionCon(null);
		assertThatThrownBy(() -> firma.firmar("descuento:5:APROBADO")).as("sin sesión de la base")
				.isInstanceOf(ReglaNegocioException.class);

		peticionCon(new SesionAbierta(31L, 8L, 1L, TOKEN));
		assertThatThrownBy(() -> firma.firmar("descuento:5:APROBADO")).as("la sesión de otra persona")
				.isInstanceOf(ReglaNegocioException.class);

		peticionCon(new SesionAbierta(32L, 7L, 2L, TOKEN));
		assertThatThrownBy(() -> firma.firmar("descuento:5:APROBADO")).as("de otro colegio")
				.isInstanceOf(ReglaNegocioException.class);
		verify(firmas, never()).saveAndFlush(any());
	}

	@Test
	void unActorDeSistemaNoFirma() {
		EjecucionComoSistema.como(ActorSistema.RECAUDACION, 1L, () -> firma.firmar("lote_recaudacion:3:APLICADO"));

		verify(firmas, never()).saveAndFlush(any());
	}

	/** Fuera del perfil test el secreto sale SOLO de la sesión HTTP; y la demo solo existe en dev y test. */
	@Test
	void fueraDeLasPruebasNoHayAtajos() {
		MockEnvironment prod = new MockEnvironment();
		prod.setActiveProfiles("prod");
		assertThatThrownBy(() -> new FirmaSesion(java.util.Optional::empty, firmas, prod))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("TokenDeSesionHttp");

		PersonaDemo demo = new PersonaDemo(mock(UsuarioRepository.class), mock(SesionesFirmadas.class),
				mock(org.springframework.transaction.PlatformTransactionManager.class), Clock.systemUTC(), prod);
		assertThatThrownBy(() -> demo.como(1L, "promotor", () -> null)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("dev y test");
	}
}
