package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.AnioEscolarDetalle;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.SeccionOpcion;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.SeccionVista;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/** Años escolares y secciones sobre la base real, con la seguridad por método activa. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioEstructuraTest {

	private static final String MOTIVO = "Se fusionó con la sección A por pocos alumnos";

	@Autowired
	private ServicioEstructura servicio;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void soloPuedeHaberUnAnioEnCurso() {
		Long anio2026 = servicio.crearAnio(anio(2026, true));

		// En el servicio: mensaje claro.
		assertThatThrownBy(() -> servicio.crearAnio(anio(2027, true)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Ya hay un año en curso (2026)");
		assertThat(servicio.anioEnCurso()).hasValueSatisfying(a -> assertThat(a.id()).isEqualTo(anio2026));

		// En la base: aunque el código fallara, no entra un segundo año EN_CURSO.
		Long anio2027 = servicio.crearAnio(anio(2027, false));
		assertThatThrownBy(() -> jdbc.update("UPDATE anio_escolar SET estado = 'EN_CURSO', vigente = TRUE WHERE id = ?",
				anio2027)).isInstanceOf(DataIntegrityViolationException.class);
		// Y el CHECK ata "vigente" al estado: EN_CURSO sin vigente no pasa.
		assertThatThrownBy(() -> jdbc.update("UPDATE anio_escolar SET estado = 'EN_CURSO' WHERE id = ?", anio2027))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM anio_escolar WHERE estado = 'EN_CURSO'", Long.class))
				.isEqualTo(1);
	}

	@Test
	void anioDuplicadoMuestraMensajeClaro() {
		servicio.crearAnio(anio(2026, true));

		assertThatThrownBy(() -> servicio.crearAnio(anio(2026, false)))
				.isInstanceOf(ReglaNegocioException.class).hasMessage(
						"El año 2026 ya existe. Ábrelo en la lista para agregarle secciones.");
		assertThatThrownBy(() -> servicio.crearAnio(new CrearAnioEscolarRequest(2027, LocalDate.of(2027, 12, 1),
				LocalDate.of(2027, 3, 1), false)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("antes del fin de clases");
		assertThat(servicio.listarAnios()).hasSize(1);
	}

	@Test
	void seccionDuplicadaEnElMismoAnioEsRechazada() {
		Long anio = servicio.crearAnio(anio(2026, true));
		servicio.crearSeccion(anio, new CrearSeccionRequest(Grado.PRIMARIA_5, "A"));

		// «a» y « A » son la misma sección (MySQL tampoco distingue mayúsculas).
		for (String nombre : new String[] { "A", "a", " A " }) {
			assertThatThrownBy(() -> servicio.crearSeccion(anio, new CrearSeccionRequest(Grado.PRIMARIA_5, nombre)))
					.isInstanceOf(ReglaNegocioException.class)
					.hasMessage("Ya existe la sección 5.° Primaria A en 2026.");
		}
		// El mismo nombre en otro grado sí se puede.
		servicio.crearSeccion(anio, new CrearSeccionRequest(Grado.PRIMARIA_6, "A"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seccion", Long.class)).isEqualTo(2);
	}

	@Test
	void mismaSeccionEnOtroAnioEsPermitida() {
		Long anio2026 = servicio.crearAnio(anio(2026, true));
		Long anio2027 = servicio.crearAnio(anio(2027, false));

		Long en2026 = servicio.crearSeccion(anio2026, new CrearSeccionRequest(Grado.PRIMARIA_5, "A"));
		Long en2027 = servicio.crearSeccion(anio2027, new CrearSeccionRequest(Grado.PRIMARIA_5, "A"));

		assertThat(en2026).isNotEqualTo(en2027);
		assertThat(servicio.seccionesParaMatricular()).extracting(SeccionOpcion::etiquetaConAnio)
				.containsExactly("2027 · 5.° Primaria A", "2026 · 5.° Primaria A");
	}

	@Test
	void seccionSeDesactivaSinBorrarse() {
		Long anio = servicio.crearAnio(anio(2026, true));
		Long seccion = servicio.crearSeccion(anio, new CrearSeccionRequest(Grado.PRIMARIA_5, "B"));

		assertThatThrownBy(() -> servicio.desactivarSeccion(seccion, "corto"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("entre 10 y 500");
		assertThat(servicio.desactivarSeccion(seccion, MOTIVO)).isEqualTo(anio);

		assertThat(jdbc.queryForObject("SELECT activa FROM seccion WHERE id = ?", Boolean.class, seccion)).isFalse();
		AnioEscolarDetalle detalle = servicio.obtenerAnio(anio);
		SeccionVista vista = detalle.niveles().getFirst().grados().getFirst().secciones().getFirst();
		assertThat(vista.activa()).isFalse();
		assertThat(servicio.seccionesParaMatricular()).isEmpty();
		assertThatThrownBy(() -> servicio.desactivarSeccion(seccion, MOTIVO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está desactivada");
		assertThat(ultimoEvento(jdbc, "SECCION_DESACTIVADA")).containsEntry("valor_anterior", "activa")
				.containsEntry("valor_nuevo", "inactiva");
		assertThat((String) ultimoEvento(jdbc, "SECCION_DESACTIVADA").get("detalle")).contains(MOTIVO);
	}

	@Test
	void crearAnioYSeccionQuedanAuditados() {
		Long anio = servicio.crearAnio(anio(2026, true));
		Long seccion = servicio.crearSeccion(anio, new CrearSeccionRequest(Grado.INICIAL_3, "Celeste"));

		Map<String, Object> eventoAnio = ultimoEvento(jdbc, "ANIO_ESCOLAR_CREADO");
		assertThat(eventoAnio).containsEntry("entidad", "anio_escolar").containsEntry("entidad_id", anio.toString())
				.containsEntry("nombre_usuario", "usuario.prueba").containsEntry("colegio_id", 1L);
		assertThat((String) eventoAnio.get("valor_nuevo")).contains("año 2026", "en curso", "02/03/2026", "18/12/2026");
		Map<String, Object> eventoSeccion = ultimoEvento(jdbc, "SECCION_CREADA");
		assertThat(eventoSeccion).containsEntry("entidad_id", seccion.toString())
				.containsEntry("valor_nuevo", "Inicial 3 años Celeste 2026");
	}

	@Test
	void promotoriaConsultaPeroNoCreaNiDesactiva() {
		Long anio = servicio.crearAnio(anio(2026, true));
		Long seccion = servicio.crearSeccion(anio, new CrearSeccionRequest(Grado.PRIMARIA_1, "A"));
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.PROMOTOR));

		assertThat(servicio.listarAnios()).hasSize(1);
		assertThat(servicio.obtenerAnio(anio).anio().secciones()).isEqualTo(1);
		assertThatThrownBy(() -> servicio.crearAnio(anio(2027, false))).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> servicio.crearSeccion(anio, new CrearSeccionRequest(Grado.PRIMARIA_1, "B")))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> servicio.desactivarSeccion(seccion, MOTIVO)).isInstanceOf(AccessDeniedException.class);

		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.CAJA));
		assertThatThrownBy(() -> servicio.listarAnios()).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void anioInexistenteODeOtroColegioDa404() {
		assertThatThrownBy(() -> servicio.obtenerAnio(999_999L)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.crearSeccion(999_999L, new CrearSeccionRequest(Grado.PRIMARIA_1, "A")))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	private static CrearAnioEscolarRequest anio(int anio, boolean enCurso) {
		return new CrearAnioEscolarRequest(anio, LocalDate.of(anio, 3, 2), LocalDate.of(anio, 12, 18), enCurso);
	}
}
