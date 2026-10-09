package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 7, tanda 2 (sección 3.3 y 3.4): las sesiones de la base solo se cierran (y solo cc_sistema), las firmas no se
 * editan ni se borran, y cambiar los roles toca solo la diferencia.
 */
class InmutabilidadIdentidadTest {

	private static final String HASH = "a".repeat(64);

	private static String script() throws IOException {
		return Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"), StandardCharsets.UTF_8);
	}

	@Test
	void deUnaSesionSoloCambiaSuCierreYEsoLoConcede02SoloACcSistema() throws IOException {
		assertThat(ColumnasActualizables.de(SesionUsuario.class))
				.isEqualTo(ColumnasActualizables.concedidas(script(), "sesion_usuario"))
				.containsExactlyInAnyOrder("cerrada_en", "motivo_cierre", "actualizado_en", "version");
		assertThat(script()).contains("ON cuentasclaras.sesion_usuario TO 'cc_sistema'@'%';")
				.doesNotContainPattern("(?m)^GRANT[^;]*ON cuentasclaras\\.sesion_usuario TO 'cc_negocio'")
				.doesNotContainPattern("(?m)^GRANT[^;]*DELETE[^;]*ON cuentasclaras\\.sesion_usuario ");
	}

	@Test
	void unaFirmaSoloSeInserta() throws IOException {
		assertThat(ColumnasActualizables.de(FirmaOperacion.class)).as("solo las de BaseEntity")
				.containsExactlyInAnyOrder("actualizado_en", "version");
		assertThat(script()).contains("GRANT INSERT ON cuentasclaras.firma_operacion TO 'cc_negocio';")
				.doesNotContainPattern("(?m)^GRANT[^;]*(UPDATE|DELETE)[^;]*ON cuentasclaras\\.firma_operacion ");
		assertThatThrownBy(() -> FirmaOperacion.de(1L, 2L, "plan_pension:1:APROBADO", HASH).impedirBorrado())
				.isInstanceOf(UnsupportedOperationException.class);
		// La clave canónica y el secreto con su formato (el CHECK de V25 y trg_firma_operacion_nace).
		assertThatThrownBy(() -> FirmaOperacion.de(1L, 2L, "plan pension 1", HASH))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> FirmaOperacion.de(1L, 2L, "plan_pension:1:APROBADO", "corto"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unaSesionSeCierraUnaSolaVezYNoSeBorra() {
		LocalDateTime ahora = LocalDateTime.of(2026, 10, 5, 8, 0);
		SesionUsuario sesion = SesionUsuario.abrir(5L, HASH, "10.0.0.1", ahora, ahora.plusHours(10));

		assertThat(sesion.vigente(ahora.plusHours(9))).isTrue();
		assertThat(sesion.vigente(ahora.plusHours(10))).as("vence a las 10 horas").isFalse();
		assertThat(sesion.cerrar(MotivoCierreSesion.SALIO, ahora.plusHours(1))).isTrue();
		assertThat(sesion.cerrar(MotivoCierreSesion.OTRA_SESION, ahora.plusHours(2))).isFalse();
		assertThat(sesion.getMotivoCierre()).isEqualTo(MotivoCierreSesion.SALIO);
		assertThat(sesion.vigente(ahora.plusHours(1))).isFalse();
		assertThatThrownBy(sesion::impedirBorrado).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> SesionUsuario.abrir(5L, "no-es-sha", null, ahora, ahora.plusHours(1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/** H2: cambiar los roles toca solo la diferencia (los que se quedan no se borran y se vuelven a insertar). */
	@Test
	void cambiarRolesTocaSoloLaDiferencia() {
		Usuario usuario = Usuario.nuevo("rosa.medina", "Rosa Medina", null, "hash", EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR));
		Object mismaColeccion = org.springframework.test.util.ReflectionTestUtils.getField(usuario, "roles");

		usuario.cambiarRoles(EnumSet.of(Rol.DIRECTOR, Rol.PROMOTOR));

		assertThat(usuario.getRoles()).containsExactlyInAnyOrder(Rol.DIRECTOR, Rol.PROMOTOR);
		assertThat(org.springframework.test.util.ReflectionTestUtils.getField(usuario, "roles")).isSameAs(mismaColeccion);
	}

	@Test
	void losRolesAprobadosLlevanUnaSolicitudMasNueva() {
		Usuario usuario = Usuario.nuevo("jorge.salazar", "Jorge Salazar", null, "hash", EnumSet.of(Rol.DOCENTE));

		usuario.aplicarRolesAprobados(EnumSet.of(Rol.DIRECTOR), 10L);
		assertThat(usuario.getRolesSolicitudId()).isEqualTo(10L);
		assertThat(usuario.getRoles()).containsExactly(Rol.DIRECTOR);
		// Reusar una solicitud ya aplicada (o una más vieja) no cambia nada.
		assertThatThrownBy(() -> usuario.aplicarRolesAprobados(EnumSet.of(Rol.PROMOTOR), 10L))
				.isInstanceOf(IllegalStateException.class);
		assertThat(usuario.getRoles()).containsExactly(Rol.DIRECTOR);
	}
}
