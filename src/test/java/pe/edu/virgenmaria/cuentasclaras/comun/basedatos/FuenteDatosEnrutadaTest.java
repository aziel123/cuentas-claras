package pe.edu.virgenmaria.cuentasclaras.comun.basedatos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.sql.Connection;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 7, tanda 2 (sección 3.2, H3): la conexión se elige al pedirla. Las personas van por {@code cc_app}; los actores
 * {@code sistema.*} y la ruta de identidad, por {@code cc_sistema}. Fuera de la ruta de identidad, una persona nunca
 * obtiene la conexión de {@code cc_sistema}.
 */
class FuenteDatosEnrutadaTest {

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void laRutaDependeDeQuienPideLaConexion() {
		assertThat(RutaConexion.actual()).isEqualTo(RutaConexion.APP);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.PROMOTOR));
		assertThat(RutaConexion.actual()).isEqualTo(RutaConexion.APP);

		assertThat(RutaConexion.identidad(() -> RutaConexion.identidad(RutaConexion::actual))).isEqualTo(RutaConexion.SISTEMA);
		assertThat(RutaConexion.identidad(RutaConexion::enIdentidad)).isTrue();
		// Al salir (también de la ruta anidada) vuelve a la de la persona.
		assertThat(RutaConexion.enIdentidad()).isFalse();
		assertThat(RutaConexion.actual()).isEqualTo(RutaConexion.APP);

		assertThat(EjecucionComoSistema.como(ActorSistema.PANEL, 1L, RutaConexion::actual)).isEqualTo(RutaConexion.SISTEMA);
		assertThat(RutaConexion.actual()).isEqualTo(RutaConexion.APP);
	}

	@Test
	void cadaRutaUsaSuPool() throws Exception {
		DataSource app = mock(DataSource.class);
		DataSource sistema = mock(DataSource.class);
		Connection deApp = mock(Connection.class);
		Connection deSistema = mock(Connection.class);
		when(app.getConnection()).thenReturn(deApp);
		when(sistema.getConnection()).thenReturn(deSistema);
		FuenteDatosEnrutada fuente = new FuenteDatosEnrutada(app, sistema);

		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.CAJA));
		assertThat(fuente.getConnection()).isSameAs(deApp);
		assertThat(RutaConexion.identidad(() -> {
			try {
				return fuente.getConnection();
			}
			catch (java.sql.SQLException e) {
				throw new IllegalStateException(e);
			}
		})).isSameAs(deSistema);
		assertThat(fuente.separadas()).isTrue();
	}

	@Test
	void enDevYTestEsUnSoloPoolYAlCerrarSeCierraUnaVez() throws Exception {
		DataSource unico = mock(DataSource.class, org.mockito.Mockito.withSettings().extraInterfaces(AutoCloseable.class));
		FuenteDatosEnrutada fuente = new FuenteDatosEnrutada(unico, unico);

		assertThat(fuente.separadas()).isFalse();
		fuente.destroy();
		verify((AutoCloseable) unico).close();

		DataSource app = mock(DataSource.class);
		DataSource sistema = mock(DataSource.class);
		new FuenteDatosEnrutada(app, sistema).getConnection();
		verify(app).getConnection();
		verify(sistema, never()).getConnection();
	}
}
