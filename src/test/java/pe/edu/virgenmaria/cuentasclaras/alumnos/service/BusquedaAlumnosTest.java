package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.AlumnoResumen;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.BusquedaAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RetirarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoAlumno;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Búsqueda de la lista de alumnos: por nombre sin tildes y en cualquier orden, o por el inicio del DNI. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class BusquedaAlumnosTest {

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private JdbcTemplate jdbc;

	private Estructura escuela;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		escuela = EscenarioEscolar.crearEstructura(estructura);
		alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026()));
		alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(escuela.primaria2B2026()));
		alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("75330981", "Flores", "Rojas", "Sebastián",
				LocalDate.of(2013, 8, 21), "41235678", "Flores", "Díaz", "Pedro", "912345678", null,
				escuela.secundaria1A2026()));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void buscaPorApellidoSinTildes() {
		assertThat(nombres("huaman")).containsExactly("Mateo Quispe Huamán", "Valeria Quispe Huamán");
		assertThat(nombres("HUAMÁN")).containsExactly("Mateo Quispe Huamán", "Valeria Quispe Huamán");
		assertThat(nombres("sebastian")).containsExactly("Sebastián Flores Rojas");
		assertThat(nombres("  ")).hasSize(3);
	}

	@Test
	void buscaConNombreYApellidoEnCualquierOrden() {
		assertThat(nombres("mateo quispe")).containsExactly("Mateo Quispe Huamán");
		assertThat(nombres("quispe   MATEO")).containsExactly("Mateo Quispe Huamán");
		assertThat(nombres("huaman mat quis")).containsExactly("Mateo Quispe Huamán");
		assertThat(nombres("mateo flores")).isEmpty();
	}

	@Test
	void buscaPorPrefijoDeDni() {
		assertThat(nombres("7845")).containsExactly("Mateo Quispe Huamán");
		assertThat(nombres("80127745")).containsExactly("Valeria Quispe Huamán");
		assertThat(nombres("7")).containsExactly("Sebastián Flores Rojas", "Mateo Quispe Huamán");
		// El DNI se busca por el inicio, no por cualquier parte.
		assertThat(nombres("1236")).isEmpty();
		Page<AlumnoResumen> pagina = alumnos.buscar(new BusquedaAlumnos("7845", null, null, null), 0);
		assertThat(pagina.getContent().getFirst())
				.extracting(AlumnoResumen::documento, AlumnoResumen::anio, AlumnoResumen::seccion)
				.containsExactly("DNI 78451236", 2026, "5.° Primaria A");
	}

	@Test
	void porcentajeYGuionBajoSeEscapan() {
		assertThat(nombres("%")).isEmpty();
		assertThat(nombres("_")).isEmpty();
		assertThat(nombres("qu_spe")).isEmpty();
		assertThat(nombres("q%e")).isEmpty();
		assertThat(nombres("!")).isEmpty();
	}

	@Test
	void pagina25Resultados() {
		Long rosa = jdbc.queryForObject("SELECT id FROM apoderado WHERE numero_documento = ?", Long.class,
				EscenarioEscolar.DNI_ROSA);
		for (int i = 0; i < 27; i++) {
			String apellido = "Lote" + (char) ('a' + i % 26) + (char) ('a' + i / 26);
			alumnos.registrar(EscenarioEscolar.conApoderadoRegistrado(String.format("6%07d", i), apellido, null,
					"Prueba", LocalDate.of(2016, 1, 1), EscenarioEscolar.DNI_ROSA, null));
		}
		assertThat(rosa).isNotNull();

		Page<AlumnoResumen> primera = alumnos.buscar(BusquedaAlumnos.todos(), 0);
		Page<AlumnoResumen> segunda = alumnos.buscar(BusquedaAlumnos.todos(), 1);
		assertThat(primera.getContent()).hasSize(25);
		assertThat(segunda.getContent()).hasSize(5);
		assertThat(primera.getTotalElements()).isEqualTo(30);
		assertThat(primera.getTotalPages()).isEqualTo(2);
		assertThat(alumnos.buscar(new BusquedaAlumnos("lote", null, null, null), 1).getContent()).hasSize(2);
	}

	@Test
	void filtraPorAnioSeccionYEstado() {
		Long sebastian = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_documento = '75330981'", Long.class);
		alumnos.retirar(sebastian, new RetirarAlumnoRequest(LocalDate.of(2026, 9, 30), "Cambio de colegio por mudanza"));

		assertThat(nombres(new BusquedaAlumnos(null, escuela.anio2026(), null, null)))
				.containsExactly("Mateo Quispe Huamán", "Valeria Quispe Huamán");
		assertThat(nombres(new BusquedaAlumnos(null, null, escuela.primaria2B2026(), null)))
				.containsExactly("Valeria Quispe Huamán");
		assertThat(nombres(new BusquedaAlumnos(null, escuela.anio2027(), null, null))).isEmpty();
		assertThat(nombres(new BusquedaAlumnos(null, null, null, EstadoAlumno.RETIRADO)))
				.containsExactly("Sebastián Flores Rojas");
		assertThat(nombres(new BusquedaAlumnos("quispe", null, null, EstadoAlumno.ACTIVO))).hasSize(2);
	}

	private java.util.List<String> nombres(String texto) {
		return nombres(new BusquedaAlumnos(texto, null, null, null));
	}

	private java.util.List<String> nombres(BusquedaAlumnos busqueda) {
		return alumnos.buscar(busqueda, 0).map(AlumnoResumen::nombreCompleto).getContent();
	}
}
