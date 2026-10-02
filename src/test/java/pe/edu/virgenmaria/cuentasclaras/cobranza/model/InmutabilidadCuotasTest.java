package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.MOTIVO;

/**
 * Una cuota no se borra y su monto, vencimiento, alumno, origen y clave no cambian: ni por la entidad (sin setters,
 * {@code updatable = false}, {@code @PreRemove}) ni por SQL (CHECK en la base; en MySQL, GRANT por columna).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class InmutabilidadCuotasTest {

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private CuotaRepository cuotas;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	private Long setiembre;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		Estructura escuela = EscenarioEscolar.crearEstructura(estructura);
		alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027()));
		EscenarioCobranza.planAprobado(planes, escuela.anio2027(), 2027, Nivel.PRIMARIA, "450", "350", null);
		setiembre = jdbc.queryForObject("SELECT id FROM cuota WHERE obligacion = 'PEN-2027-09'", Long.class);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void cuotaNoTieneSetters() {
		for (Class<?> entidad : new Class<?>[] { Cuota.class, PlanPension.class, LoteSaldoInicial.class,
				LineaSaldoInicial.class, BaseEntity.class }) {
			assertThat(Arrays.stream(entidad.getDeclaredMethods())
					.filter(m -> Modifier.isPublic(m.getModifiers()) && m.getName().startsWith("set"))
					.map(Method::getName)).as(entidad.getSimpleName()).isEmpty();
		}
	}

	@Test
	void columnasActualizablesCoincidenConElGrantPorColumna() throws IOException {
		Set<String> actualizables = Stream.concat(Arrays.stream(BaseEntity.class.getDeclaredFields()),
						Arrays.stream(Cuota.class.getDeclaredFields()))
				.filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isAnnotationPresent(Id.class))
				.filter(InmutabilidadCuotasTest::actualizable)
				.map(InmutabilidadCuotasTest::columna)
				.collect(Collectors.toCollection(TreeSet::new));

		String script = Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"));
		Matcher grant = Pattern.compile("GRANT UPDATE \\(([^)]*)\\) ON cuentasclaras\\.cuota ").matcher(script);
		assertThat(grant.find()).as("GRANT UPDATE por columna sobre cuota").isTrue();
		Set<String> concedidas = Arrays.stream(grant.group(1).split(",")).map(String::strip)
				.collect(Collectors.toCollection(TreeSet::new));

		assertThat(actualizables).isEqualTo(concedidas)
				.doesNotContain("monto", "fecha_vencimiento", "alumno_id", "clave", "matricula_id", "plan_pension_id");
		assertThat(script).doesNotContainPattern("(?i)GRANT[^;]*DELETE[^;]*ON cuentasclaras\\.(cuota|plan_pension|"
				+ "lote_saldo_inicial|linea_saldo_inicial)\\b");
		assertThat(script).doesNotContainPattern("(?i)GRANT[^;(]*UPDATE ON cuentasclaras\\.cuota\\b");
	}

	@Test
	void cambiarElMontoPorReflexionNoLlegaALaBase() {
		new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
			Cuota cuota = cuotas.findById(setiembre).orElseThrow();
			forzar(cuota, "monto", new BigDecimal("1.00"));
			forzar(cuota, "fechaVencimiento", cuota.getFechaVencimiento().plusYears(1));
			forzar(cuota, "clave", "OTRA");
		});

		Map<String, Object> fila = jdbc.queryForMap("SELECT monto, fecha_vencimiento, clave FROM cuota WHERE id = ?",
				setiembre);
		assertThat((BigDecimal) fila.get("monto")).isEqualByComparingTo("450.00");
		assertThat(fila.get("fecha_vencimiento")).hasToString("2027-09-30");
		assertThat(fila.get("clave")).asString().startsWith("PEN:").endsWith(":9");
	}

	@Test
	void borrarConEntityManagerLanzaExcepcion() {
		assertThatThrownBy(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado ->
				entityManager.remove(cuotas.findById(setiembre).orElseThrow())))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Las cuotas no se borran");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuota WHERE id = ?", Long.class, setiembre)).isEqualTo(1);
	}

	@Test
	void repositoriosDeCobranzaNoTienenModifying() {
		JavaClasses clases = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("pe.edu.virgenmaria.cuentasclaras.cobranza");
		noMethods().that().areDeclaredInClassesThat().resideInAPackage("..cobranza.repository..")
				.should().beAnnotatedWith(Modifying.class)
				.orShould().haveNameMatching("(?i)(delete|remove).*")
				.because("las cuotas, los planes y los lotes no se borran ni se cambian con SQL masivo")
				.check(clases);
	}

	@Test
	void anularExigeMotivoYAprobadorDistintoTambienEnLaBase() {
		Cuota cuota = cuotas.findById(setiembre).orElseThrow();
		assertThatThrownBy(() -> cuota.anular(MOTIVO, "administracion", "administracion", LocalDateTime.now()))
				.isInstanceOf(AutoaprobacionException.class);
		assertThatThrownBy(() -> cuota.anular("corto", "administracion", "director", LocalDateTime.now()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("entre 10 y 500");

		// En la base: el mismo solicitante y aprobador, o sin motivo, se rechaza.
		assertThatThrownBy(() -> jdbc.update("UPDATE cuota SET estado = 'ANULADA', obligacion = NULL, "
				+ "anulada_en = CURRENT_TIMESTAMP, anulacion_motivo = ?, anulacion_solicitada_por = 'administracion', "
				+ "anulacion_aprobada_por = 'administracion' WHERE id = ?", MOTIVO, setiembre))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("UPDATE cuota SET estado = 'ANULADA', obligacion = NULL, "
				+ "anulada_en = CURRENT_TIMESTAMP, anulacion_solicitada_por = 'administracion', "
				+ "anulacion_aprobada_por = 'director' WHERE id = ?", setiembre))
				.isInstanceOf(DataIntegrityViolationException.class);
		// ANULADA sin liberar la obligación tampoco.
		assertThatThrownBy(() -> jdbc.update("UPDATE cuota SET estado = 'ANULADA', anulada_en = CURRENT_TIMESTAMP, "
				+ "anulacion_motivo = ?, anulacion_solicitada_por = 'administracion', anulacion_aprobada_por = 'director' "
				+ "WHERE id = ?", MOTIVO, setiembre)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, setiembre))
				.isEqualTo("PENDIENTE");
	}

	@Test
	void anularCuotaConPagosEsRechazado() {
		// Simula un pago parcial (los pagos llegan en el sprint 3).
		jdbc.update("UPDATE cuota SET estado = 'PARCIAL', monto_pagado = 100.00 WHERE id = ?", setiembre);
		Cuota cuota = cuotas.findById(setiembre).orElseThrow();

		assertThatThrownBy(() -> cuota.anular(MOTIVO, "administracion", "director", LocalDateTime.now()))
				.isInstanceOf(ReglaNegocioException.class).hasMessage("La cuota tiene pagos: primero se anulan los pagos.");
		assertThatThrownBy(() -> cuota.solicitarAnulacion(MOTIVO, "administracion"))
				.hasMessage("La cuota tiene pagos: primero se anulan los pagos.");
		// En la base: ANULADA exige monto_pagado = 0.
		assertThatThrownBy(() -> jdbc.update("UPDATE cuota SET estado = 'ANULADA', obligacion = NULL, "
				+ "anulada_en = CURRENT_TIMESTAMP, anulacion_motivo = ?, anulacion_solicitada_por = 'administracion', "
				+ "anulacion_aprobada_por = 'director' WHERE id = ?", MOTIVO, setiembre))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void anularLiberaLaObligacionYConservaLaClave() {
		String clave = jdbc.queryForObject("SELECT clave FROM cuota WHERE id = ?", String.class, setiembre);
		new TransactionTemplate(transacciones).executeWithoutResult(estado -> cuotas.findById(setiembre).orElseThrow()
				.anular(MOTIVO, "administracion", "director", LocalDateTime.of(2026, 10, 2, 9, 0)));

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cuota WHERE id = ?", setiembre);
		assertThat(fila).containsEntry("estado", "ANULADA").containsEntry("clave", clave)
				.containsEntry("anulacion_solicitada_por", "administracion").containsEntry("anulacion_aprobada_por", "director");
		assertThat(fila.get("obligacion")).isNull();
		assertThat((BigDecimal) fila.get("monto")).isEqualByComparingTo("450.00");
	}

	@Test
	void laBaseRechazaGuardarEstadoVencida() {
		assertThatThrownBy(() -> jdbc.update("UPDATE cuota SET estado = 'VENCIDA' WHERE id = ?", setiembre))
				.isInstanceOf(DataIntegrityViolationException.class);
		// Ni estados incoherentes con lo pagado, ni pagar más que el monto.
		assertThatThrownBy(() -> jdbc.update("UPDATE cuota SET estado = 'PAGADA' WHERE id = ?", setiembre))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("UPDATE cuota SET estado = 'PAGADA', monto_pagado = 450.01 WHERE id = ?",
				setiembre)).isInstanceOf(DataIntegrityViolationException.class);
		// La misma deuda dos veces para el mismo alumno, tampoco.
		assertThatThrownBy(() -> jdbc.update("INSERT INTO cuota (colegio_id, alumno_id, anio_escolar_id, matricula_id, "
				+ "plan_pension_id, tipo, numero, descripcion, monto, fecha_vencimiento, estado, clave, obligacion, creado_en, "
				+ "creado_por, actualizado_en) SELECT colegio_id, alumno_id, anio_escolar_id, matricula_id, plan_pension_id, "
				+ "tipo, numero, descripcion, monto, fecha_vencimiento, estado, 'OTRA-CLAVE', obligacion, creado_en, creado_por, "
				+ "actualizado_en FROM cuota WHERE id = ?", setiembre)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private static boolean actualizable(Field campo) {
		Column columna = campo.getAnnotation(Column.class);
		JoinColumn union = campo.getAnnotation(JoinColumn.class);
		if (union != null) {
			return union.updatable();
		}
		return columna == null || columna.updatable();
	}

	private static String columna(Field campo) {
		Column columna = campo.getAnnotation(Column.class);
		if (columna != null && !columna.name().isEmpty()) {
			return columna.name();
		}
		return campo.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
	}

	private static void forzar(Object objeto, String campo, Object valor) {
		try {
			Field f = objeto.getClass().getDeclaredField(campo);
			f.setAccessible(true);
			f.set(objeto, valor);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
