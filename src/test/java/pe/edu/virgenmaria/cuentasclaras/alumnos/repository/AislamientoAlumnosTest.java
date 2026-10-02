package pe.edu.virgenmaria.cuentasclaras.alumnos.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosApoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DocumentoIdentidad;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaJpa;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aislamiento entre colegios de familias, apoderados, alumnos, matrículas, años y secciones: {@code @TenantId}
 * filtra toda consulta, y las FK compuestas (x_id, colegio_id) hacen que la BASE rechace referencias cruzadas.
 */
@PruebaJpa
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AislamientoAlumnosTest {

	private static final long COLEGIO_A = 1L;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private AnioEscolarRepository anios;

	@Autowired
	private SeccionRepository secciones;

	@Autowired
	private FamiliaRepository familias;

	@Autowired
	private ApoderadoRepository apoderados;

	@Autowired
	private AlumnoRepository alumnos;

	@Autowired
	private MatriculaRepository matriculas;

	@Autowired
	private TransactionTemplate transaccion;

	@Autowired
	private JdbcTemplate jdbc;

	private long colegioB;

	private Datos deA;

	record Datos(Long anio, Long seccion, Long familia, Long apoderado, Long alumno, Long matricula) {
	}

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		deA = crearEn(COLEGIO_A, "78451236", "45678912");
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void colegioBNoVeAlumnosApoderadosFamiliasNiMatriculasDelA() {
		ContextoColegio.en(colegioB, () -> {
			assertThat(alumnos.findAll()).isEmpty();
			assertThat(apoderados.findAll()).isEmpty();
			assertThat(familias.findAll()).isEmpty();
			assertThat(matriculas.findAll()).isEmpty();
			assertThat(anios.findAll()).isEmpty();
			assertThat(secciones.findAll()).isEmpty();
			assertThat(alumnos.findById(deA.alumno())).isEmpty();
			assertThat(apoderados.findById(deA.apoderado())).isEmpty();
			assertThat(familias.findById(deA.familia())).isEmpty();
			assertThat(matriculas.findById(deA.matricula())).isEmpty();
			assertThat(secciones.findById(deA.seccion())).isEmpty();
			assertThat(anios.findById(deA.anio())).isEmpty();
			assertThat(alumnos.findByDocumentoTipoAndDocumentoNumero(TipoDocumento.DNI, "78451236")).isEmpty();
			assertThat(apoderados.findByDocumentoNumeroIn(java.util.List.of("45678912"))).isEmpty();
			assertThat(alumnos.buscar("%QUISPE%", null, null, null, null, null, null, PageRequest.of(0, 25))).isEmpty();
			assertThat(alumnos.buscar(null, null, null, "7845%", null, null, null, PageRequest.of(0, 25))).isEmpty();
			assertThat(matriculas.contarActivasPorSeccion(deA.anio())).isEmpty();
			assertThat(secciones.todasConAnio()).isEmpty();
		});
		ContextoColegio.en(COLEGIO_A, () -> {
			assertThat(alumnos.findAll()).hasSize(1);
			assertThat(alumnos.buscar("%QUISPE%", null, null, null, null, null, null, PageRequest.of(0, 25)))
					.hasSize(1);
		});
	}

	@Test
	void mismoDniEnDosColegiosSonDosAlumnos() {
		Datos deB = crearEn(colegioB, "78451236", "45678912");

		assertThat(deB.alumno()).isNotEqualTo(deA.alumno());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE numero_documento = '78451236'", Long.class))
				.isEqualTo(2);
		ContextoColegio.en(COLEGIO_A, () -> assertThat(alumnos.findByDocumentoTipoAndDocumentoNumero(TipoDocumento.DNI,
				"78451236")).hasValueSatisfying(a -> assertThat(a.getId()).isEqualTo(deA.alumno())));
		ContextoColegio.en(colegioB, () -> assertThat(alumnos.findByDocumentoTipoAndDocumentoNumero(TipoDocumento.DNI,
				"78451236")).hasValueSatisfying(a -> assertThat(a.getId()).isEqualTo(deB.alumno())));
	}

	@Test
	void laBaseRechazaMatriculaConSeccionDeOtroColegio() {
		Datos deB = crearEn(colegioB, "70000001", "40000001");
		String insertar = "INSERT INTO matricula (colegio_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula, "
				+ "estado, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, DATE '2026-03-02', 'ACTIVA', "
				+ "CURRENT_TIMESTAMP, 'intruso', CURRENT_TIMESTAMP)";
		// Alumno de B con año y sección de A: fk_matricula_anio (anio_escolar_id, colegio_id).
		assertThatThrownBy(() -> jdbc.update(insertar, colegioB, deB.alumno(), deA.anio(), deA.seccion()))
				.isInstanceOf(DataIntegrityViolationException.class);
		// Alumno de B con su propio año pero la sección de A: fk_matricula_seccion (seccion_id, anio_escolar_id).
		jdbc.update("DELETE FROM matricula WHERE id = ?", deB.matricula());
		assertThatThrownBy(() -> jdbc.update(insertar, colegioB, deB.alumno(), deB.anio(), deA.seccion()))
				.isInstanceOf(DataIntegrityViolationException.class);
		// Alumno de A matriculado "en B": fk_matricula_alumno (alumno_id, colegio_id).
		assertThatThrownBy(() -> jdbc.update(insertar, colegioB, deA.alumno(), deB.anio(), deB.seccion()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseRechazaApoderadoEnFamiliaDeOtroColegio() {
		assertThatThrownBy(() -> jdbc.update("INSERT INTO apoderado (colegio_id, familia_id, tipo_documento, "
				+ "numero_documento, apellido_paterno, nombres, parentesco, telefono_whatsapp, nombre_busqueda, "
				+ "creado_en, creado_por, actualizado_en) VALUES (?, ?, 'DNI', '40000002', 'Intruso', 'Ana', 'OTRO', "
				+ "'+51911111111', 'INTRUSO ANA', CURRENT_TIMESTAMP, 'intruso', CURRENT_TIMESTAMP)", colegioB,
				deA.familia())).isInstanceOf(DataIntegrityViolationException.class);
		// Ni una sección de B en un año de A.
		assertThatThrownBy(() -> jdbc.update("INSERT INTO seccion (colegio_id, anio_escolar_id, grado, nombre, creado_en, "
				+ "creado_por, actualizado_en) VALUES (?, ?, 'PRIMARIA_1', 'Z', CURRENT_TIMESTAMP, 'intruso', "
				+ "CURRENT_TIMESTAMP)", colegioB, deA.anio())).isInstanceOf(DataIntegrityViolationException.class);
	}

	private Datos crearEn(long colegioId, String dniAlumno, String dniApoderado) {
		return ContextoColegio.en(colegioId, () -> transaccion.execute(estado -> {
			AnioEscolar anio = anios.save(AnioEscolar.nuevo(2026, true, LocalDate.of(2026, 3, 2),
					LocalDate.of(2026, 12, 18)));
			Seccion seccion = secciones.save(Seccion.nueva(anio, Grado.PRIMARIA_5, "A"));
			Familia familia = familias.save(Familia.nueva("Familia Quispe Huamán"));
			Apoderado apoderado = apoderados.save(Apoderado.nuevo(familia, new DatosApoderado(
					new DocumentoIdentidad(TipoDocumento.DNI, dniApoderado), "Huamán", "Ccori", "Rosa",
					Parentesco.MADRE, "+51987654321", null)));
			Alumno alumno = alumnos.save(Alumno.nuevo(new DatosAlumno(new DocumentoIdentidad(TipoDocumento.DNI,
					dniAlumno), "Quispe", "Huamán", "Mateo", LocalDate.of(2015, 6, 14)), apoderado));
			Matricula matricula = matriculas.save(Matricula.nueva(alumno, seccion, LocalDate.of(2026, 3, 2)));
			return new Datos(anio.getId(), seccion.getId(), familia.getId(), apoderado.getId(), alumno.getId(),
					matricula.getId());
		}));
	}
}
