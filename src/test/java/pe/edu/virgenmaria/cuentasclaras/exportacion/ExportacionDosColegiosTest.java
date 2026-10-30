package pe.edu.virgenmaria.cuentasclaras.exportacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.MotivoExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.proceso.GeneracionExportaciones;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.Exportador;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.TablasExportacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;

/**
 * Sprint 11, tanda 2 (AP-12): con DOS colegios cargados (alumnos, familias, pensiones, pagos, comprobantes, bitácora), la
 * exportación del A trae, en CADA fila de CADA CSV, su {@code colegio_id} (o, en {@code usuario_rol}, una cuenta del A), y
 * ningún valor propio del B aparece en el ZIP del A. Tampoco va ningún secreto (AP-12 y 3.9).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ExportacionDosColegiosTest {

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private GeneracionExportaciones generacion;

	@Autowired
	private Exportador exportador;

	@Autowired
	private SecretosConectores secretos;

	@Autowired
	private JdbcTemplate jdbc;

	private long colegioB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		EscenarioCaja.Familias a = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		UsuariosDePrueba.iniciarSesion(EscenarioCaja.CAJA);
		cobro.cobrar(efectivo(a.quispe(), List.of(cuota(jdbc, a.mateo(), "PEN-2027-03")), "450.00", "500.00"));

		colegioB = colegios.save(new Colegio("Colegio Unico Bravo")).getId();
		UsuarioAutenticado administracionB = UsuariosDePrueba.autenticado(colegioB, 91L, "administracion.bravo",
				"Administración Bravo", false, EnumSet.of(Rol.ADMINISTRACION));
		UsuarioAutenticado directorB = UsuariosDePrueba.autenticado(colegioB, 90L, "director.bravo", "Director Bravo", false,
				EnumSet.of(Rol.DIRECTOR));
		UsuarioAutenticado cajaB = UsuariosDePrueba.autenticado(colegioB, 92L, "caja.bravo", "Caja Bravo", false,
				EnumSet.of(Rol.CAJA));
		UsuariosDePrueba.iniciarSesion(administracionB);
		var escuelaB = EscenarioEscolar.crearEstructura(estructura);
		var alumnoB = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("79999991", "Zubizarreta", "Bravo", "Xiomara",
				java.time.LocalDate.of(2015, 6, 1), "49999991", "Bravo", "Unico", "Yolanda", "987999991",
				"yolanda.bravo.unico@gmail.com", escuelaB.primaria6A2027()));
		Long planB = planes.crearBorrador(escuelaB.anio2027(), Nivel.PRIMARIA, EscenarioCobranza.plan(2027, "381", "301",
				null));
		planes.enviar(planB);
		UsuariosDePrueba.iniciarSesion(directorB);
		planes.aprobar(planB, planes.obtener(planB).version());
		UsuariosDePrueba.iniciarSesion(cajaB);
		cobro.cobrar(efectivo(alumnoB.familiaId(), List.of(cuota(jdbc, alumnoB.alumnoId(), "PEN-2027-03")), "381.00",
				"400.00"));
		SecurityContextHolder.clearContext();
		// A quién avisar en el A (el correo de control).
		jdbc.update("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en, creado_por) "
				+ "SELECT 1, 'huella_correo_externo', 'control@contador.pe', CURRENT_TIMESTAMP, 'prueba' "
				+ "WHERE NOT EXISTS (SELECT 1 FROM configuracion_colegio WHERE colegio_id = 1 AND clave = "
				+ "'huella_correo_externo')");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void cadaFilaDeCadaCsvEsDelColegioAYNadaDelB() throws Exception {
		Long id = generacion.exportar(1L, MotivoExportacion.PEDIDO, null);
		Map<String, List<List<String>>> tablas = ZipDePrueba.csvs(jdbc, exportador, secretos, id);

		Set<String> cuentasA = new HashSet<>(jdbc.queryForList("SELECT CAST(id AS VARCHAR) FROM usuario WHERE colegio_id = 1",
				String.class));
		long filasRevisadas = 0;
		assertThat(tablas.keySet()).containsAll(List.of("alumno", "apoderado", "pago", "cuota", "evento_auditoria"));
		for (Map.Entry<String, List<List<String>>> t : tablas.entrySet()) {
			List<String> encabezado = t.getValue().getFirst();
			int colegio = encabezado.indexOf("colegio_id");
			int usuario = encabezado.indexOf("usuario_id");
			for (List<String> fila : t.getValue().subList(1, t.getValue().size())) {
				assertThat(fila).as(t.getKey()).hasSameSizeAs(encabezado);
				if (colegio >= 0) {
					assertThat(fila.get(colegio)).as(t.getKey() + " " + fila).isEqualTo("1");
				}
				else {
					assertThat(usuario).as(t.getKey() + " sin colegio_id ni usuario_id").isNotNegative();
					assertThat(cuentasA).as(t.getKey() + " " + fila).contains(fila.get(usuario));
				}
				// Ningún valor propio del B.
				assertThat(String.join("|", fila)).as(t.getKey())
						.doesNotContain("Unico Bravo", "Zubizarreta", "49999991", "79999991", "yolanda.bravo.unico",
								"director.bravo", "administracion.bravo", "caja.bravo", "381.00");
				filasRevisadas++;
			}
		}
		assertThat(filasRevisadas).isGreaterThan(20);
		assertThat(tablas.get("pago")).hasSize(2);
		assertThat(tablas.get("alumno").size()).isGreaterThan(1);
		// Las tablas vacías también van, con su encabezado.
		assertThat(tablas).hasSize(TablasExportacion.cargar().size());
	}

	/** {@code ExportacionSinSecretosTest} (3.9): ningún encabezado es una clave, un token, una firma o una sesión. */
	@Test
	void ningunEncabezadoEsUnSecreto() throws Exception {
		Long id = generacion.exportar(1L, MotivoExportacion.PEDIDO, null);
		Map<String, List<List<String>>> tablas = ZipDePrueba.csvs(jdbc, exportador, secretos, id);
		assertThat(tablas.keySet()).doesNotContain("sesion_usuario", "firma_operacion", "enlace_activacion",
				"verificacion_contacto", "semilla_muestreo", "exportacion_colegio", "acta_supresion");
		for (Map.Entry<String, List<List<String>>> t : tablas.entrySet()) {
			for (String columna : t.getValue().getFirst()) {
				assertThat(TablasExportacion.NUNCA).as(t.getKey()).doesNotContain(columna);
				// (comprobante.codigo_hash es el «código hash» de SUNAT que sale impreso en la boleta: no es un secreto.)
				assertThat(columna).as(t.getKey()).doesNotContain("clave_hash", "token", "secreto",
						"codigo_sha256");
			}
		}
	}
}
