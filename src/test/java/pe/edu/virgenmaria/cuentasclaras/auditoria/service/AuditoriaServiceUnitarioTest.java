package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EslabonCadena;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EslabonCadenaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.PrincipalConColegio;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceUnitarioTest {

	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-02T13:45:30.123456789Z"),
			ZoneId.of("America/Lima"));

	@Mock
	private EventoAuditoriaRepository eventos;

	@Mock
	private EslabonCadenaRepository cadena;

	private final SelladorAuditoria sellador = new SelladorAuditoria(SelladorAuditoriaTest.CLAVE);

	private AuditoriaService servicio;

	private EslabonCadena eslabon;

	@BeforeEach
	void preparar() throws Exception {
		servicio = new AuditoriaService(eventos, cadena, sellador, RELOJ);
		Constructor<EslabonCadena> constructor = EslabonCadena.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		eslabon = constructor.newInstance();
		ReflectionTestUtils.setField(eslabon, "id", EslabonCadena.ID);
		ReflectionTestUtils.setField(eslabon, "ultimaSecuencia", 41L);
		ReflectionTestUtils.setField(eslabon, "ultimoHash", "a".repeat(64));
		when(cadena.bloquear()).thenReturn(eslabon);
		when(eventos.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void sellaConElHashAnteriorAsignaLaSecuenciaSiguienteYAvanzaElEslabon() {
		EventoAuditoria evento = servicio.registrar(Actor.sistema(1L), AccionAuditoria.USUARIO_CREADO, "usuario", "5",
				null, "activo", null);

		assertThat(evento.getSecuencia()).isEqualTo(42);
		assertThat(evento.getHash()).isEqualTo(sellador.sellar("a".repeat(64), evento));
		assertThat(eslabon.getUltimaSecuencia()).isEqualTo(42);
		assertThat(eslabon.getUltimoHash()).isEqualTo(evento.getHash());
		assertThat(evento.getOcurridoEn()).isEqualTo(LocalDateTime.of(2026, 10, 2, 8, 45, 30, 123_456_000));
	}

	@Test
	void elActorEsElUsuarioAutenticadoConSusRolesSuColegioYLaIpDeLaPeticion() {
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				new UsuarioDePrueba(3L, 7L, "director.a"), null,
				AuthorityUtils.createAuthorityList("ROLE_DIRECTOR", "FACTOR_PASSWORD", "ROLE_ADMINISTRACION")));
		MockHttpServletRequest peticion = new MockHttpServletRequest();
		peticion.setRemoteAddr("190.40.1.2");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));

		EventoAuditoria evento = servicio.registrar(AccionAuditoria.ROLES_CAMBIADOS, "usuario", "9", "CAJA", "DOCENTE",
				"Cambio de funciones");

		assertThat(evento.getColegioId()).isEqualTo(3L);
		assertThat(evento.getUsuarioId()).isEqualTo(7L);
		assertThat(evento.getNombreUsuario()).isEqualTo("director.a");
		assertThat(evento.getRoles()).isEqualTo("ADMINISTRACION,DIRECTOR");
		assertThat(evento.getIp()).isEqualTo("190.40.1.2");
	}

	@Test
	void sinAutenticacionElActorEsElSistemaSinColegio() {
		EventoAuditoria evento = servicio.registrar(AccionAuditoria.INTEGRIDAD_VERIFICADA, null, null, null, null, null);
		assertThat(evento.getNombreUsuario()).isEqualTo(Actor.SISTEMA);
		assertThat(evento.getColegioId()).isNull();
		assertThat(evento.getUsuarioId()).isNull();
		assertThat(evento.getIp()).isNull();
	}

	@Test
	void unVisitanteNoIdentificadoQuedaComoAnonimo() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("clave", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
		EventoAuditoria evento = servicio.registrar(AccionAuditoria.ACCESO_DENEGADO, null, null, null, null, "GET /x");
		assertThat(evento.getNombreUsuario()).isEqualTo(Actor.ANONIMO);
		assertThat(evento.getRoles()).isNull();
	}

	private record UsuarioDePrueba(Long colegioId, Long usuarioId, String nombre)
			implements PrincipalConColegio, Principal {

		@Override
		public String getName() {
			return nombre;
		}
	}
}
