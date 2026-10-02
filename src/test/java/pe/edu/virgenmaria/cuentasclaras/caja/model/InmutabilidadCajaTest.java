package pe.edu.virgenmaria.cuentasclaras.caja.model;

import jakarta.persistence.EntityManager;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ComprobanteLinea;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Pagos, aplicaciones, comprobantes, series y cajas no se borran y sus datos financieros no cambian: ni por la entidad
 * (sin setters, {@code updatable = false}, {@code @Immutable}, {@code @PreRemove}) ni por SQL (GRANT por columna).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class InmutabilidadCajaTest {

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private PagoRepository pagos;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	private Long pagoId;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(EscenarioCaja.CAJA);
		pagoId = cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00",
				"500.00"));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void grantDeCadaTablaCoincideConColumnasActualizables() throws IOException {
		String script = Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"));
		assertThat(ColumnasActualizables.de(Pago.class)).isEqualTo(ColumnasActualizables.concedidas(script, "pago"))
				.containsExactlyInAnyOrder("estado", "operacion_vigente", "actualizado_en", "version");
		assertThat(ColumnasActualizables.de(CajaDiaria.class))
				.isEqualTo(ColumnasActualizables.concedidas(script, "caja_diaria"))
				.doesNotContain("cajero", "fecha", "fondo_fijo");
		assertThat(ColumnasActualizables.de(Comprobante.class))
				.isEqualTo(ColumnasActualizables.concedidas(script, "comprobante"))
				.doesNotContain("serie", "numero", "total", "receptor_numero_documento", "serie_id", "fecha_emision");
		assertThat(ColumnasActualizables.de(SerieComprobante.class))
				.isEqualTo(ColumnasActualizables.concedidas(script, "serie_comprobante"))
				.containsExactlyInAnyOrder("ultimo_numero", "actualizado_en", "version");
		assertThat(ColumnasActualizables.de(Cuota.class)).isEqualTo(ColumnasActualizables.concedidas(script, "cuota"))
				.contains("monto_pagado", "monto_descuento");
		// Solo inserción: ningún UPDATE en su GRANT y la entidad es @Immutable.
		for (String tabla : new String[] { "aplicacion_pago", "comprobante_linea" }) {
			assertThat(script).containsPattern("GRANT INSERT ON cuentasclaras\\." + tabla + " ")
					.doesNotContainPattern("(?i)GRANT[^;]*UPDATE[^;]*ON cuentasclaras\\." + tabla + "\\b");
		}
		assertThat(script).doesNotContainPattern("(?i)GRANT[^;]*DELETE[^;]*ON cuentasclaras\\.(serie_comprobante|"
				+ "comprobante|comprobante_linea|caja_diaria|pago|aplicacion_pago)\\b");
	}

	@Test
	void pagoYAplicacionNoTienenSetters() {
		for (Class<?> entidad : List.of(Pago.class, AplicacionPago.class, CajaDiaria.class, Comprobante.class,
				ComprobanteLinea.class, SerieComprobante.class)) {
			assertThat(Arrays.stream(entidad.getDeclaredMethods())
					.filter(m -> Modifier.isPublic(m.getModifiers()) && m.getName().startsWith("set"))
					.map(Method::getName)).as(entidad.getSimpleName()).isEmpty();
		}
	}

	@Test
	void borrarPagoConEntityManagerLanzaExcepcion() {
		assertThatThrownBy(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado ->
				entityManager.remove(pagos.findById(pagoId).orElseThrow())))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Los pagos no se borran");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE id = ?", Long.class, pagoId)).isEqualTo(1);
	}

	/** El libro de aplicaciones es @Immutable: un cambio en memoria (por reflexión) nunca llega a la base. */
	@Test
	void aplicacionEsImmutable() {
		assertThat(AplicacionPago.class.isAnnotationPresent(Immutable.class)).isTrue();
		assertThat(ComprobanteLinea.class.isAnnotationPresent(Immutable.class)).isTrue();
		Long aplicacion = jdbc.queryForObject("SELECT id FROM aplicacion_pago WHERE pago_id = ?", Long.class, pagoId);

		Throwable error = catchThrowable(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
			AplicacionPago encontrada = entityManager.find(AplicacionPago.class, aplicacion);
			forzar(encontrada, "monto", new BigDecimal("1.00"));
			entityManager.flush();
		}));

		assertThat(jdbc.queryForObject("SELECT monto FROM aplicacion_pago WHERE id = ?", BigDecimal.class, aplicacion))
				.as("error: %s", error).isEqualByComparingTo("450.00");
		assertThatThrownBy(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado ->
				entityManager.remove(entityManager.find(AplicacionPago.class, aplicacion))))
				.hasMessageContaining("no se borra");
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
