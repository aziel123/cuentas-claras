package pe.edu.virgenmaria.cuentasclaras.recaudacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.Archivo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LineaPrevia;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.VistaPreviaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.AplicacionLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ConsultaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioExcepcionesRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.archivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.confirmar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.idLinea;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.registrar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.version;

/**
 * F24: el colegio B no ve ni toca los lotes, las líneas ni los archivos del A (recibe 404), un código de pago de un
 * alumno del A no es de nadie en el B, y la base rechaza referencias cruzadas (FK compuestas con {@code colegio_id}).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoRecaudacionTest {

	@Autowired
	private ServicioRecaudacion servicio;

	@Autowired
	private ConsultaRecaudacion consulta;

	@Autowired
	private ServicioExcepcionesRecaudacion excepciones;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private Clock reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private Long lote;

	private UsuarioAutenticado promotorB;

	private UsuarioAutenticado administracionB;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		Archivo banco = archivo().pago(f.mateo(), null, "350.00", "BCP70001").pago(f.valeria(), null, "9999.00",
				"BCP70002");
		lote = registrar(servicio, ADMINISTRACION, banco);
		confirmar(servicio, jdbc, PROMOTORIA, lote, banco.total().toPlainString());
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		promotorB = UsuariosDePrueba.autenticado(colegioB, 92L, "promotor.b", "Promotor B", false, EnumSet.of(Rol.PROMOTOR));
		administracionB = UsuariosDePrueba.autenticado(colegioB, 93L, "administracion.b", "Administración B", false,
				EnumSet.of(Rol.ADMINISTRACION));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void colegioBNoVeNiTocaLaRecaudacionDelA() {
		Long linea = idLinea(jdbc, lote, 2);
		UsuariosDePrueba.iniciarSesion(promotorB);
		assertThat(consulta.lista().lotes()).isEmpty();
		assertThat(consulta.lista().excepciones()).isEmpty();
		assertThatThrownBy(() -> consulta.detalle(lote)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> consulta.archivo(lote)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.paraConfirmar(lote)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.confirmar(lote, version(jdbc, lote), java.math.BigDecimal.ONE))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> excepciones.detalle(linea, null)).isInstanceOf(RecursoNoEncontradoException.class);
		UsuariosDePrueba.iniciarSesion(administracionB);
		assertThatThrownBy(() -> servicio.descartar(lote, "Intento desde otro colegio con motivo"))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> excepciones.solicitarDevolucion(linea, "Intento desde otro colegio con motivo"))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> excepciones.solicitarAplicacion(linea, new AplicacionLineaRequest(f.quispe(),
				List.of(1L), "Intento desde otro colegio con motivo"))).isInstanceOf(RecursoNoEncontradoException.class);
	}

	/** Un archivo del B con el código de un alumno del A: ese código no es de nadie en el B. */
	@Test
	void unCodigoDelANoEsDeNadieEnElB() {
		byte[] banco = ("fecha_pago;codigo_alumno;referencia_deuda;monto;moneda;numero_operacion\n2026-10-01;"
				+ CodigoPago.deAlumno(f.mateo()) + ";;350.00;PEN;BCP70011\n").getBytes();
		UsuariosDePrueba.iniciarSesion(administracionB);

		VistaPreviaRecaudacion previa = servicio.previsualizar("banco-b.csv", banco, banco.length);

		assertThat(previa.detalle()).singleElement().satisfies(l -> {
			assertThat(l.resultado()).isEqualTo(LineaPrevia.EXCEPCION);
			assertThat(l.alumno()).isNull();
		});
		byte[] conReferencia = ("fecha_pago;codigo_alumno;referencia_deuda;monto;moneda;numero_operacion\n2026-10-01;"
				+ CodigoPago.deAlumno(f.mateo()) + ";" + CodigoPago.deCuota(jdbc.queryForObject("SELECT MIN(id) FROM cuota",
						Long.class)) + ";350.00;PEN;BCP70012\n").getBytes();
		assertThat(servicio.previsualizar("banco-b2.csv", conReferencia, conReferencia.length).errores()).hasSize(1);
	}

	@Test
	void laBaseRechazaReferenciasCruzadas() {
		Long archivo = jdbc.queryForObject("SELECT archivo_id FROM lote_recaudacion WHERE id = ?", Long.class, lote);
		String sha = jdbc.queryForObject("SELECT archivo_sha256 FROM lote_recaudacion WHERE id = ?", String.class, lote);
		long colegioB = promotorB.colegioId();
		// Un lote del B con el archivo del A, y una línea del B en el lote del A: las FK compuestas lo impiden.
		assertThatThrownBy(() -> jdbc.update("INSERT INTO lote_recaudacion (colegio_id, archivo_id, archivo_sha256, "
				+ "sha_vigente, banco, formato, fecha_proceso, desde, hasta, lineas, total, estado, intentos_confirmacion, "
				+ "lineas_aplicadas, lineas_excepcion, monto_aplicado, monto_excepcion, creado_en, creado_por, "
				+ "actualizado_en) VALUES (?, ?, ?, ?, 'GENERICO', 'x', DATE '2026-10-01', DATE '2026-10-01', "
				+ "DATE '2026-10-01', 1, 1, 'CARGADO', 0, 0, 0, 0, 0, CURRENT_TIMESTAMP, 'x', CURRENT_TIMESTAMP)", colegioB,
				archivo, sha, sha)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("INSERT INTO linea_recaudacion (colegio_id, lote_id, numero, fecha_pago, "
				+ "codigo, monto, moneda, numero_operacion, estado, creado_en, creado_por, actualizado_en) VALUES (?, ?, 9, "
				+ "DATE '2026-10-01', '0', 1, 'PEN', 'BCP70099', 'PENDIENTE', CURRENT_TIMESTAMP, 'x', CURRENT_TIMESTAMP)",
				colegioB, lote)).isInstanceOf(DataIntegrityViolationException.class);
	}
}
