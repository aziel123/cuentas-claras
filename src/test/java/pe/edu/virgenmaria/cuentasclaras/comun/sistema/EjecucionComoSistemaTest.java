package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los actores de sistema (sprint 4): nombre reservado, solo su autoridad, en un colegio y sin dejar rastro al salir. */
class EjecucionComoSistemaTest {

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void corrreComoElActorEnSuColegioYDevuelveLaSesionAnterior() {
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.CAJA));
		Authentication antes = SecurityContextHolder.getContext().getAuthentication();

		String visto = EjecucionComoSistema.como(ActorSistema.PASARELA, 7L, () -> {
			Authentication dentro = SecurityContextHolder.getContext().getAuthentication();
			assertThat(dentro.getAuthorities()).extracting(GrantedAuthority::getAuthority)
					.containsExactly("ROLE_SISTEMA_PASARELA");
			return dentro.getName() + "@" + ContextoColegio.actual();
		});

		assertThat(visto).isEqualTo("sistema.pasarela@7");
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(antes);
	}

	@Test
	void laSesionSeRestauraAunqueLaTareaFalle() {
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		Authentication antes = SecurityContextHolder.getContext().getAuthentication();

		assertThatThrownBy(() -> EjecucionComoSistema.como(ActorSistema.OSE, 1L, () -> {
			throw new IllegalStateException("falla");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(antes);
	}

	@Test
	void nadiePuedeLlamarseComoUnActorDeSistema() {
		for (ActorSistema actor : ActorSistema.values()) {
			assertThat(ActorSistema.esReservado(actor.usuario())).isTrue();
			assertThatThrownBy(() -> Usuario.nuevo(actor.usuario(), "Impostor", null, "hash", Set.of(Rol.CAJA)))
					.as(actor.usuario()).isInstanceOf(RuntimeException.class);
		}
		assertThatThrownBy(() -> Usuario.nuevo("sistemas", "Impostor", null, "hash", Set.of(Rol.CAJA)))
				.isInstanceOf(RuntimeException.class);
		// Un APODERADO solo existe enlazado a su apoderado registrado.
		assertThatThrownBy(() -> Usuario.nuevo("rosa", "Rosa", null, "hash", Set.of(Rol.APODERADO)))
				.isInstanceOf(RuntimeException.class);
		assertThat(Usuario.deApoderado("45678912", "Rosa", null, "hash", 5L).getApoderadoId()).isEqualTo(5L);
	}
}
