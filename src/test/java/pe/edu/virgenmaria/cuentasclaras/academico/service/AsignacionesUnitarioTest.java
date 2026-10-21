package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 9, tanda 1: las reglas de {@link Asignaciones#proponer} sin base de datos (JUnit 5 y Mockito). Solo se propone
 * una cuenta del personal activa con el rol DOCENTE que no combine Docente con Caja o Administración, en una sección
 * activa de un año abierto, sin duplicar y sin un segundo tutor; y la propuesta nace con su solicitud y su evento.
 */
class AsignacionesUnitarioTest {

	private final AsignacionDocenteRepository repositorio = mock(AsignacionDocenteRepository.class);

	private final SeccionRepository secciones = mock(SeccionRepository.class);

	private final AnioEscolarRepository anios = mock(AnioEscolarRepository.class);

	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

	private final RegistroSolicitudes solicitudes = mock(RegistroSolicitudes.class);

	private final AuditoriaService auditoria = mock(AuditoriaService.class);

	private final Asignaciones servicio = new Asignaciones(repositorio, secciones, anios, usuarios, solicitudes,
			mock(FirmaSesion.class), auditoria, Clock.fixed(Instant.parse("2027-03-01T13:00:00Z"), ZoneId.of("UTC")),
			mock(pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil.class));

	private Seccion seccion;

	@BeforeEach
	void preparar() throws Exception {
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		AnioEscolar anio = AnioEscolar.nuevo(2027, true, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 12, 17));
		poner(anio, "id", 5L);
		seccion = Seccion.nueva(anio, Grado.PRIMARIA_3, "A");
		poner(seccion, "id", 7L);
		when(secciones.findById(7L)).thenReturn(Optional.of(seccion));
		when(repositorio.saveAndFlush(any())).thenAnswer(i -> {
			AsignacionDocente a = i.getArgument(0);
			if (a.getId() == null) {
				poner(a, "id", 11L);
			}
			return a;
		});
		SolicitudCambio solicitud = mock(SolicitudCambio.class);
		when(solicitud.getId()).thenReturn(21L);
		when(solicitud.getMotivo()).thenReturn("Carga horaria del año");
		when(solicitudes.crear(eq(TipoSolicitud.ASIGNACION_DOCENTE), eq("asignacion_docente"), eq(11L), anyString(), anyMap(),
				anyString())).thenReturn(solicitud);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	private Usuario cuenta(long id, boolean activa, Rol... roles) throws Exception {
		Usuario u = Usuario.nuevo("cuenta" + id, "Cuenta " + id, null, "hash", Set.of(Rol.PROMOTOR));
		poner(u, "id", id);
		Field campoRoles = Usuario.class.getDeclaredField("roles");
		campoRoles.setAccessible(true);
		campoRoles.set(u, EnumSet.copyOf(Set.of(roles)));
		if (!activa) {
			Field activo = Usuario.class.getDeclaredField("activo");
			activo.setAccessible(true);
			activo.set(u, false);
		}
		when(usuarios.findById(id)).thenReturn(Optional.of(u));
		return u;
	}

	private static void poner(Object objeto, String campo, Object valor) throws Exception {
		Class<?> clase = objeto.getClass();
		while (clase != null) {
			try {
				Field f = clase.getDeclaredField(campo);
				f.setAccessible(true);
				f.set(objeto, valor);
				return;
			}
			catch (NoSuchFieldException e) {
				clase = clase.getSuperclass();
			}
		}
		throw new NoSuchFieldException(campo);
	}

	private ProponerAsignacionRequest pedido(long docente, TipoAsignacion tipo) {
		return new ProponerAsignacionRequest(7L, docente, tipo, null, "Carga horaria del año");
	}

	@Test
	void proponeConSuSolicitudYSuEvento() throws Exception {
		cuenta(3L, true, Rol.DOCENTE);

		assertThat(servicio.proponer(pedido(3L, TipoAsignacion.TUTOR))).isEqualTo(11L);

		verify(solicitudes).crear(eq(TipoSolicitud.ASIGNACION_DOCENTE), eq("asignacion_docente"), eq(11L), anyString(),
				anyMap(), eq("Carga horaria del año"));
		verify(auditoria).registrar(eq(AccionAuditoria.ASIGNACION_DOCENTE_PROPUESTA), eq("asignacion_docente"), eq("11"),
				eq(null), eq("PROPUESTA"), anyString());
	}

	@Test
	void soloUnaCuentaDocenteActivaQueNoVeDeudas() throws Exception {
		cuenta(3L, true, Rol.CAJA);
		cuenta(4L, false, Rol.DOCENTE);
		cuenta(5L, true, Rol.DOCENTE, Rol.ADMINISTRACION);

		assertThatThrownBy(() -> servicio.proponer(pedido(3L, TipoAsignacion.DOCENTE))).isInstanceOf(
				ReglaNegocioException.class).hasMessageContaining("rol Docente");
		assertThatThrownBy(() -> servicio.proponer(pedido(4L, TipoAsignacion.DOCENTE))).isInstanceOf(
				ReglaNegocioException.class).hasMessageContaining("desactivada");
		assertThatThrownBy(() -> servicio.proponer(pedido(5L, TipoAsignacion.DOCENTE))).isInstanceOf(
				ReglaNegocioException.class).hasMessageContaining("Caja o Administración");
		assertThatThrownBy(() -> servicio.proponer(pedido(99L, TipoAsignacion.DOCENTE))).isInstanceOf(
				RecursoNoEncontradoException.class);
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void sinDuplicadosNiSegundoTutorNiAnioCerrado() throws Exception {
		cuenta(3L, true, Rol.DOCENTE);
		when(repositorio.existsByUsuarioIdAndSeccionIdAndTipoAndEstadoIn(eq(3L), eq(7L), eq(TipoAsignacion.DOCENTE), any()))
				.thenReturn(true);
		when(repositorio.findFirstBySeccionIdAndTipoAndEstado(7L, TipoAsignacion.TUTOR, EstadoAsignacion.VIGENTE))
				.thenReturn(Optional.of(AsignacionDocente.propuesta(5L, 7L, 8L, TipoAsignacion.TUTOR, null, "x")));

		assertThatThrownBy(() -> servicio.proponer(pedido(3L, TipoAsignacion.DOCENTE))).isInstanceOf(
				ReglaNegocioException.class).hasMessageContaining("ya es");
		assertThatThrownBy(() -> servicio.proponer(pedido(3L, TipoAsignacion.TUTOR))).isInstanceOf(
				ReglaNegocioException.class).hasMessageContaining("ya tiene tutor");
		seccion.getAnioEscolar().cerrar();
		assertThatThrownBy(() -> servicio.proponer(pedido(3L, TipoAsignacion.AUXILIAR))).isInstanceOf(
				ReglaNegocioException.class).hasMessageContaining("cerrado");
		verify(repositorio, never()).saveAndFlush(any());
	}
}
