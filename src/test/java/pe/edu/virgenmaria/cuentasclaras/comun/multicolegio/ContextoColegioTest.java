package pe.edu.virgenmaria.cuentasclaras.comun.multicolegio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextoColegioTest {

	private final ResolutorColegioActual resolutor = new ResolutorColegioActual();

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void sinNadaEnElContextoResuelveNinguno() {
		assertThat(ContextoColegio.actual()).isEqualTo(ContextoColegio.NINGUNO);
		assertThat(resolutor.resolveCurrentTenantIdentifier()).isEqualTo(ContextoColegio.NINGUNO);
	}

	@Test
	void tomaElColegioDelUsuarioAutenticado() {
		autenticar(new Principal(7L, 3L));
		assertThat(ContextoColegio.actual()).isEqualTo(7L);
	}

	@Test
	void unPrincipalSinColegioNoDaAccesoANingunColegio() {
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				"alguien", null, AuthorityUtils.createAuthorityList("ROLE_PROMOTOR")));
		assertThat(ContextoColegio.actual()).isEqualTo(ContextoColegio.NINGUNO);
	}

	@Test
	void elColegioFijadoEnElHiloTienePrioridadYSeRestaura() {
		autenticar(new Principal(7L, 3L));
		ContextoColegio.en(2L, () -> {
			assertThat(ContextoColegio.actual()).isEqualTo(2L);
			ContextoColegio.en(5L, () -> assertThat(ContextoColegio.actual()).isEqualTo(5L));
			assertThat(ContextoColegio.actual()).isEqualTo(2L);
		});
		assertThat(ContextoColegio.actual()).isEqualTo(7L);
	}

	@Test
	void seRestauraAunqueLaOperacionFalle() {
		assertThatThrownBy(() -> ContextoColegio.en(2L, () -> {
			throw new IllegalArgumentException("falla");
		})).isInstanceOf(IllegalArgumentException.class);
		assertThat(ContextoColegio.actual()).isEqualTo(ContextoColegio.NINGUNO);
	}

	@Test
	void soloSistemaEsRoot() {
		assertThat(ContextoColegio.comoSistema(ContextoColegio::actual)).isEqualTo(ContextoColegio.SISTEMA);
		assertThat(resolutor.isRoot(ContextoColegio.SISTEMA)).isTrue();
		assertThat(resolutor.isRoot(1L)).isFalse();
		assertThat(resolutor.isRoot(ContextoColegio.NINGUNO)).isFalse();
	}

	@Test
	void enNoAceptaColegiosInvalidosParaNoSaltarseComoSistema() {
		assertThatThrownBy(() -> ContextoColegio.en(-1L, () -> null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ContextoColegio.en(0L, () -> null)).isInstanceOf(IllegalArgumentException.class);
	}

	private static void autenticar(Principal principal) {
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				principal, null, AuthorityUtils.createAuthorityList("ROLE_DIRECTOR")));
	}

	private record Principal(Long colegioId, Long usuarioId) implements PrincipalConColegio {
	}
}
