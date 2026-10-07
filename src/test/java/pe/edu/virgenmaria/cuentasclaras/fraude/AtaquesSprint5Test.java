package pe.edu.virgenmaria.cuentasclaras.fraude;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAccesoApoderados;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioFamilias;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.FeriadoRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioFeriados;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoRequest;
import pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.service.AlertasFamilias;
import pe.edu.virgenmaria.cuentasclaras.familias.service.ServicioAvisosFamilia;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadosNacionales;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Auditoría del sprint 5 (auditor-seguridad-antifraude): ataques que PASAN con el código actual. Cada prueba está en
 * verde porque demuestra el hueco; al corregirlo, la aserción marcada «HUECO» debe invertirse.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AtaquesSprint5Test {

	@Autowired private ServicioAlumnos alumnos;
	@Autowired private ServicioFamilias familias;
	@Autowired private ServicioAccesoApoderados accesos;
	@Autowired private ServicioEstructura estructura;
	@Autowired private ServicioPlanesPension planes;
	@Autowired private ServicioCobro cobro;
	@Autowired private ServicioAnulacionPagos anulaciones;
	@Autowired private BandejaAprobaciones bandeja;
	@Autowired private ServicioAvisosFamilia avisos;
	@Autowired private AlertasFamilias alertasFamilias;
	@Autowired private ServicioFeriados feriados;
	@Autowired private CalendarioHabil calendario;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private PasswordEncoder codificador;
	@Autowired private JdbcTemplate jdbc;

	private EscenarioCaja.Familias f;

	private Long seccion;

	private static final String CORREO_CAJERA = "lucia.caja@gmail.com";


	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		seccion = jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE alumno_id = ?", Long.class, f.mateo());
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "director", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotor", UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		// La cajera tiene registrado su correo personal en su cuenta del personal.
		jdbc.update("UPDATE usuario SET correo = ? WHERE nombre_usuario = ?", CORREO_CAJERA, "caja");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** Administración registra (o importa) una familia nueva con el contacto que ella quiera. */
	private Long familiaNueva(String dniAlumno, String dniApoderado, String celular, String correo) {
		como(ADMINISTRACION);
		var r = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(dniAlumno, "Ramos", "Vega", "Lucas",
				LocalDate.of(2015, 3, 3), dniApoderado, "Ramos", "Soto", "Ana", celular, correo, seccion));
		return r.alumnoId();
	}

	private Long responsable(Long alumno) {
		return jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class, alumno);
	}

	private Long familiaDe(Long alumno) {
		return jdbc.queryForObject("SELECT familia_id FROM alumno WHERE id = ?", Long.class, alumno);
	}

	private List<String> destinosDelPago(Long pago) {
		return jdbc.queryForList("SELECT destino FROM mensaje WHERE tipo = 'PAGO_REGISTRADO' AND entidad_id = ?",
				String.class, pago);
	}

	/**
	 * S5-A1 (G6): el control compara el contacto por igualdad exacta. Con un alias de Gmail (o con un segundo chip) el
	 * aviso de pago Y el enlace de activación del portal de la familia llegan a la cajera.
	 */
	@Test
	void s5A1_aliasDelCorreoDeLaCajeraRecibeElAvisoDePagoYElEnlaceDelPortal() {
		String alias = "lucia.caja+ramos@gmail.com"; // Gmail lo entrega en lucia.caja@gmail.com
		como(EscenarioCobranza.CAJA);
		assertThat(usuarios.esContactoDelPersonal(CORREO_CAJERA)).as("control: el correo exacto sí se detecta").isTrue();
		assertThat(usuarios.esContactoDelPersonal(alias)).as("HUECO: el alias no se detecta").isFalse();

		Long lucas = familiaNueva("72345611", "40000012", null, alias);
		como(EscenarioCobranza.CAJA);
		Long pago = cobro.cobrar(efectivo(familiaDe(lucas), List.of(cuota(jdbc, lucas, "PEN-2027-03")), "450.00",
				"450.00"));
		assertThat(destinosDelPago(pago)).as("HUECO: el aviso de pago va al buzón de la cajera").containsExactly(alias);

		como(ADMINISTRACION);
		accesos.darAcceso(responsable(lucas));
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForList("SELECT destino FROM mensaje WHERE tipo = 'ACTIVACION_CUENTA'", String.class))
				.as("HUECO: el enlace del portal de la familia va al buzón de la cajera").containsExactly(alias);
	}

	/**
	 * S5-M1 (G6): cualquier solicitud de contacto aprobada (aunque solo cambie el correo) llena contacto_solicitud_id y
	 * desde ese momento el celular del personal, que nadie aprobó, recibe los avisos.
	 */
	@Test
	void s5M1_cambiarSoloElCorreoBlanqueaElCelularDelPersonal() {
		String celularCajera = UsuariosDePrueba.celular("caja"); // +51966XXXXXX
		Long lucas = familiaNueva("72345622", "40000023", celularCajera.substring(3), "ana.ramos@gmail.com");
		como(EscenarioCobranza.CAJA);
		Long pago1 = cobro.cobrar(efectivo(familiaDe(lucas), List.of(cuota(jdbc, lucas, "PEN-2027-03")), "450.00",
				"450.00"));
		assertThat(destinosDelPago(pago1)).as("control: el WhatsApp a la cajera se omite (y el correo tampoco sale)")
				.isEmpty();

		// Administración pide corregir SOLO el correo (cambio legítimo); Dirección lo aprueba.
		Long ana = responsable(lucas);
		como(ADMINISTRACION);
		familias.actualizarApoderado(ana, new ApoderadoRequest(TipoDocumento.DNI, "40000023", "Ramos", "Soto", "Ana",
				Parentesco.MADRE, celularCajera.substring(3), "ana.ramos.soto@gmail.com", "Corrige el correo de la madre"));
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "apoderado", ana);

		como(EscenarioCobranza.CAJA);
		Long pago2 = cobro.cobrar(efectivo(familiaDe(lucas), List.of(cuota(jdbc, lucas, "PEN-2027-04")), "450.00",
				"450.00"));
		SecurityContextHolder.clearContext();
		assertThat(destinosDelPago(pago2)).as("HUECO: ahora el WhatsApp va al celular de la cajera")
				.contains(celularCajera);
	}

	/**
	 * S5-M2: Dirección aprueba la anulación y luego ella misma «atiende» la queja de la familia sobre esa anulación. La
	 * alerta CRÍTICA desaparece de Promotoría sin que Promotoría la haya visto.
	 */
	@Test
	void s5M2_direccionCierraLaQuejaSobreLaAnulacionQueElMismoAprobo() {
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00",
				"450.00"));
		anulaciones.solicitarDevolucion(pago, EscenarioAprobaciones.MOTIVO_ANULACION);
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);

		UsuarioAutenticado rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());
		como(rosa);
		Long aviso = avisos.enviar(new AvisoRequest(TipoAvisoFamilia.NO_RECONOZCO_ANULACION_O_DESCUENTO, pago, null,
				"Yo no pedí anular mi pago"));

		como(DIRECCION);
		avisos.atender(aviso, "Revisado: fue un error de caja, ya está corregido.");

		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertasFamilias.alertas()).as("HUECO: Promotoría ya no ve ninguna alerta crítica")
				.noneMatch(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA);
	}

	/**
	 * S5-M3 (G20): Dirección sola registra todos los días hábiles del mes siguiente como «no laborables». Nada lo limita
	 * ni lo aprueba otra persona; el siguiente día hábil (base de las alertas de depósito y de abono) salta un mes.
	 */
	@Test
	void s5M3_direccionCongelaUnMesDeAlertasConFeriados() {
		LocalDate hoy = LocalDate.of(2026, 10, 2); // reloj de ConfiguracionRelojAjustable (viernes)
		como(DIRECCION);
		int registrados = 0;
		for (LocalDate d = hoy.plusDays(1); d.isBefore(LocalDate.of(2026, 11, 1)); d = d.plusDays(1)) {
			if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY || FeriadosNacionales.es(d)) {
				continue;
			}
			feriados.registrar(new FeriadoRequest(d, "Jornada de capacitación docente"));
			registrados++;
		}
		assertThat(registrados).isEqualTo(19);
		assertThat(calendario.siguienteDiaHabil(hoy)).as("HUECO: la caja de hoy recién es crítica en noviembre")
				.isAfterOrEqualTo(LocalDate.of(2026, 11, 2));
	}
}
