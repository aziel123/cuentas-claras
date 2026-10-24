package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.CalificacionesSiagie;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.DelegacionesSiagie;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10, tanda 3 (M12; decisión 189 ajustada por el coordinador): Dirección delega con su firma la exportación para el
 * SIAGIE SOLO a una persona de Administración (nunca a Caja, ni a una cuenta que combine Administración con Caja, ni a un
 * docente) por hasta 15 días. La delegada exporta con su sesión y queda registrado; sin delegación vigente, no.
 */
class DelegacionSiagieTest extends PruebaLibretas {

	@Autowired
	private DelegacionesSiagie delegaciones;

	@Autowired
	private CalificacionesSiagie calificaciones;

	private Usuario cuenta(String nombre, Rol... roles) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, roles);
	}

	@Test
	void soloAdministracionNuncaCaja() {
		cerrarYEmitir();
		Usuario secretaria = cuenta("secretaria.adm", Rol.ADMINISTRACION);
		Usuario cajera = cuenta("cajera.siagie", Rol.CAJA);
		// La aplicación no deja combinar Administración con Caja (quien cobra no aprueba); una cuenta así solo existiría por
		// SQL: en H2 se le agrega el rol directo y la delegación igual se rechaza.
		Usuario mixta = cuenta("adm.y.caja", Rol.ADMINISTRACION);
		jdbc.update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'CAJA')", mixta.getId());
		LocalDate hoy = LocalDate.of(2026, 5, 4);
		// Caja, una cuenta con Administración y Caja, y un docente: no.
		for (Usuario u : new Usuario[] { cajera, mixta, ana }) {
			assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> {
				delegaciones.delegar(u.getId(), hoy, hoy.plusDays(5));
				return null;
			})).as(u.getNombreUsuario()).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Administración");
		}
		assertThat(como(EscenarioCobranza.DIRECCION, () -> delegaciones.candidatas())).extracting(c -> c.id())
				.contains(secretaria.getId()).doesNotContain(cajera.getId(), mixta.getId(), ana.getId());
		// Más de 15 días o desde ayer: no.
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> {
			delegaciones.delegar(secretaria.getId(), hoy, hoy.plusDays(16));
			return null;
		})).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("15 días");
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> {
			delegaciones.delegar(secretaria.getId(), hoy.minusDays(1), hoy.plusDays(3));
			return null;
		})).isInstanceOf(ReglaNegocioException.class);
		// Sin delegación, la secretaria no exporta.
		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(secretaria), () -> calificaciones.exportar(seccion,
				periodo))).isInstanceOf(AccessDeniedException.class);
		// Con la delegación (firmada por Dirección), sí: con su sesión y su nombre.
		como(EscenarioCobranza.DIRECCION, () -> {
			delegaciones.delegar(secretaria.getId(), hoy, hoy.plusDays(15));
			return null;
		});
		assertThat(contar("SELECT COUNT(*) FROM firma_operacion WHERE clave = ?", "delegacion_siagie:" + secretaria.getId()
				+ ":" + hoy + ":DELEGADA")).isEqualTo(1);
		assertThat(eventos("DELEGACION_SIAGIE_OTORGADA")).hasSize(1);
		assertThat(como(UsuariosDePrueba.autenticado(secretaria), () -> calificaciones.exportar(seccion, periodo)).filas())
				.isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT creado_por FROM exportacion_calificaciones", String.class))
				.isEqualTo("secretaria.adm");
		// Vencida, ya no.
		hoyEs(hoy.plusDays(16));
		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(secretaria), () -> calificaciones.exportar(seccion,
				periodo))).isInstanceOf(AccessDeniedException.class);
		// La cajera no exporta nunca (ni con la URL: 403).
		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(cajera), () -> calificaciones.exportar(seccion, periodo)))
				.isInstanceOf(AccessDeniedException.class);
	}

	/** Solo Dirección delega (Administración no se delega a sí misma). */
	@Test
	void soloDireccionDelega() {
		Usuario secretaria = cuenta("secretaria.dos", Rol.ADMINISTRACION);
		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(secretaria), () -> {
			delegaciones.delegar(secretaria.getId(), LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 10));
			return null;
		})).isInstanceOf(AccessDeniedException.class);
	}
}
