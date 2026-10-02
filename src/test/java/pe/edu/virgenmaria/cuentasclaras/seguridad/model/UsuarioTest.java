package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 2, 9, 0);

	@Test
	void nuevoNormalizaElNombreYObligaACambiarLaClave() {
		Usuario usuario = Usuario.nuevo("  Ana.Torres ", "Ana Torres", " ", "{bcrypt}x", Set.of(Rol.CAJA));
		assertThat(usuario.getNombreUsuario()).isEqualTo("ana.torres");
		assertThat(usuario.getCorreo()).isNull();
		assertThat(usuario.isDebeCambiarClave()).isTrue();
		assertThat(usuario.isActivo()).isTrue();
	}

	@Test
	void nuevoAplicaLaSegregacionDeRoles() {
		assertThatThrownBy(() -> Usuario.nuevo("x", "X", null, "{bcrypt}x", EnumSet.of(Rol.CAJA, Rol.DIRECTOR)))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void alLlegarAlMaximoDeIntentosSeBloqueaYSeDesbloqueaSolo() {
		Usuario usuario = Usuario.nuevo("caja", "Caja", null, "{bcrypt}x", Set.of(Rol.CAJA));
		for (int i = 0; i < 4; i++) {
			assertThat(usuario.registrarIngresoFallido(5, Duration.ofMinutes(15), AHORA)).isFalse();
		}
		assertThat(usuario.registrarIngresoFallido(5, Duration.ofMinutes(15), AHORA)).isTrue();
		assertThat(usuario.estaBloqueado(AHORA.plusMinutes(14))).isTrue();
		assertThat(usuario.estaBloqueado(AHORA.plusMinutes(15))).isFalse();
	}

	@Test
	void ingresoExitosoReiniciaElContador() {
		Usuario usuario = Usuario.nuevo("caja", "Caja", null, "{bcrypt}x", Set.of(Rol.CAJA));
		usuario.registrarIngresoFallido(5, Duration.ofMinutes(15), AHORA);
		usuario.registrarIngresoExitoso(AHORA);
		assertThat(usuario.getIntentosFallidos()).isZero();
		assertThat(usuario.getUltimoIngresoEn()).isEqualTo(AHORA);
	}

	@Test
	void desactivarYReactivarDejanRastro() {
		Usuario usuario = Usuario.nuevo("caja", "Caja", null, "{bcrypt}x", Set.of(Rol.CAJA));
		usuario.desactivar("director", AHORA);
		assertThat(usuario.isActivo()).isFalse();
		assertThat(usuario.getDesactivadoPor()).isEqualTo("director");
		assertThatThrownBy(() -> usuario.desactivar("director", AHORA)).isInstanceOf(ReglaNegocioException.class);
		usuario.reactivar();
		assertThat(usuario.isActivo()).isTrue();
		assertThat(usuario.getDesactivadoEn()).isNull();
	}

	@Test
	void cambiarClaveDefinitivaQuitaLaObligacionDeCambiarla() {
		Usuario usuario = Usuario.nuevo("caja", "Caja", null, "{bcrypt}x", Set.of(Rol.CAJA));
		usuario.cambiarClave("{bcrypt}y", AHORA, false);
		assertThat(usuario.isDebeCambiarClave()).isFalse();
		assertThat(usuario.getClaveCambiadaEn()).isEqualTo(AHORA);
	}

	@Test
	void cambiarRolesAplicaLaSegregacionYNoExponeLaColeccion() {
		Usuario usuario = Usuario.nuevo("doc", "Docente", null, "{bcrypt}x", Set.of(Rol.DOCENTE));
		assertThatThrownBy(() -> usuario.cambiarRoles(EnumSet.of(Rol.APODERADO, Rol.DOCENTE)))
				.isInstanceOf(ReglaNegocioException.class);
		usuario.cambiarRoles(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR));
		assertThat(usuario.getRoles()).containsExactlyInAnyOrder(Rol.DOCENTE, Rol.DIRECTOR);
		assertThatThrownBy(() -> usuario.getRoles().add(Rol.CAJA)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void toStringNoIncluyeElHashDeLaClave() {
		Usuario usuario = Usuario.nuevo("caja", "Caja", null, "{bcrypt}secreto", Set.of(Rol.CAJA));
		assertThat(usuario.toString()).doesNotContain("secreto");
	}
}
