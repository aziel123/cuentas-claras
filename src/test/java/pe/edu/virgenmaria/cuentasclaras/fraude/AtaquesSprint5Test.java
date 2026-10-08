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

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadosNacionales;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Auditoría del sprint 5 (auditor-seguridad-antifraude): los ataques S5-A1 y S5-M1 a S5-M3. Las aserciones que antes
 * demostraban el hueco («HUECO») se invirtieron con las correcciones (V20): cada prueba en verde significa que el ataque
 * ya NO funciona («CORREGIDO»).
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

	/** Pagos registrados con su aviso, a qué destinos. */
	private List<String> destinosDelPago(Long pago) {
		return jdbc.queryForList("SELECT destino FROM mensaje WHERE tipo = 'PAGO_REGISTRADO' AND entidad_id = ?",
				String.class, pago);
	}

	/**
	 * S5-A1 (G6), CORREGIDO: el contacto se compara NORMALIZADO, así que el alias de Gmail de la cajera es su correo: el
	 * registro pide la aprobación de otra persona y, aunque alguien lo diera por verificado, ni el aviso de pago ni el enlace
	 * del portal llegan a la cajera.
	 */
	@Test
	void s5A1_aliasDelCorreoDeLaCajeraRecibeElAvisoDePagoYElEnlaceDelPortal() {
		String alias = "Lucia.Caja+ramos@googlemail.com"; // Gmail lo entrega en lucia.caja@gmail.com
		como(EscenarioCobranza.CAJA);
		assertThat(usuarios.esContactoDelPersonal(CORREO_CAJERA)).as("control: el correo exacto sí se detecta").isTrue();
		assertThat(usuarios.esContactoDelPersonal(alias)).as("CORREGIDO: el alias también se detecta").isTrue();

		Long lucas = familiaNueva("72345611", "40000012", null, alias);
		assertThat(jdbc.queryForObject("SELECT resumen FROM solicitud_cambio WHERE tipo = 'CAMBIO_CONTACTO_APODERADO' "
				+ "AND estado = 'PENDIENTE' AND entidad_id = ?", String.class, responsable(lucas)))
				.as("CORREGIDO: el registro con un contacto del personal pide la aprobación de otra persona")
				.contains("Coincide con el contacto de alguien del personal");
		// Aunque el alias figurara como verificado, la regla del personal (normalizada) lo bloquea.
		jdbc.update("UPDATE apoderado SET correo_verificado = correo WHERE id = ?", responsable(lucas));
		como(EscenarioCobranza.CAJA);
		Long pago = cobro.cobrar(efectivo(familiaDe(lucas), List.of(cuota(jdbc, lucas, "PEN-2027-03")), "450.00",
				"450.00"));
		assertThat(destinosDelPago(pago)).as("CORREGIDO: el aviso de pago no va al buzón de la cajera").isEmpty();

		como(ADMINISTRACION);
		assertThatThrownBy(() -> accesos.darAcceso(responsable(lucas))).isInstanceOf(ReglaNegocioException.class);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForList("SELECT destino FROM mensaje WHERE tipo = 'ACTIVACION_CUENTA'", String.class))
				.as("CORREGIDO: el enlace del portal no va al buzón de la cajera").isEmpty();
	}

	/**
	 * S5-M1, CORREGIDO: aprobar un cambio de SOLO el correo no blanquea el celular del personal. La aprobación vale para el
	 * contacto concreto aprobado (contacto_aprobado_correo); el celular de la cajera sigue sin aprobar y no recibe nada.
	 */
	@Test
	void s5M1_cambiarSoloElCorreoBlanqueaElCelularDelPersonal() {
		String celularCajera = UsuariosDePrueba.celular("caja"); // +51966XXXXXX
		Long lucas = familiaNueva("72345622", "40000023", celularCajera.substring(3), "ana.ramos@gmail.com");
		Long ana = responsable(lucas);
		// El registro pidió aprobar el celular del personal: Dirección lo rechaza (no es de la familia).
		Long aprobarCelular = EscenarioAprobaciones.pendiente(jdbc, "apoderado", ana);
		como(DIRECCION);
		bandeja.rechazar(aprobarCelular, "No es el celular de la madre: es de la cajera");
		EscenarioEscolar.contactosConfirmados(jdbc);
		como(EscenarioCobranza.CAJA);
		Long pago1 = cobro.cobrar(efectivo(familiaDe(lucas), List.of(cuota(jdbc, lucas, "PEN-2027-03")), "450.00",
				"450.00"));
		assertThat(destinosDelPago(pago1)).as("control: el WhatsApp a la cajera se omite y el aviso sale por correo")
				.containsExactly("ana.ramos@gmail.com");

		// Administración pide corregir SOLO el correo (cambio legítimo); Dirección lo aprueba.
		como(ADMINISTRACION);
		familias.actualizarApoderado(ana, new ApoderadoRequest(TipoDocumento.DNI, "40000023", "Ramos", "Soto", "Ana",
				Parentesco.MADRE, celularCajera.substring(3), "ana.ramos.soto@gmail.com", "Corrige el correo de la madre"));
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "apoderado", ana);
		assertThat(jdbc.queryForMap("SELECT contacto_aprobado_telefono, contacto_aprobado_correo FROM apoderado "
				+ "WHERE id = ?", ana)).containsEntry("contacto_aprobado_telefono", null)
				.containsEntry("contacto_aprobado_correo", "ana.ramos.soto@gmail.com");
		EscenarioEscolar.contactosConfirmados(jdbc);

		como(EscenarioCobranza.CAJA);
		Long pago2 = cobro.cobrar(efectivo(familiaDe(lucas), List.of(cuota(jdbc, lucas, "PEN-2027-04")), "450.00",
				"450.00"));
		SecurityContextHolder.clearContext();
		assertThat(destinosDelPago(pago2)).as("CORREGIDO: el WhatsApp no va al celular de la cajera")
				.doesNotContain(celularCajera).containsExactly("ana.ramos.soto@gmail.com");
	}

	/**
	 * S5-M2, CORREGIDO: Dirección aprobó la anulación y no puede atender la queja de la familia sobre ella; la alerta
	 * CRÍTICA sigue en el inicio de Promotoría.
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
		assertThatThrownBy(() -> avisos.atender(aviso, "Revisado: fue un error de caja, ya está corregido."))
				.as("CORREGIDO: quien aprobó la anulación no cierra la queja").isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("otra persona");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "AUTOAPROBACION_RECHAZADA")).containsEntry("entidad",
				"aviso_familia");

		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertasFamilias.alertas()).as("CORREGIDO: Promotoría sigue viendo la alerta crítica")
				.anyMatch(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA);
	}

	/**
	 * S5-M3 (G20), CORREGIDO: Dirección sola ya no congela un mes. Cada día queda PROPUESTO (no cuenta hasta que Promotoría
	 * lo apruebe), con 3 por mes como máximo y no más de 2 días hábiles seguidos.
	 */
	@Test
	void s5M3_direccionCongelaUnMesDeAlertasConFeriados() {
		LocalDate hoy = LocalDate.of(2026, 10, 2); // reloj de ConfiguracionRelojAjustable (viernes)
		como(DIRECCION);
		int registrados = 0;
		int rechazados = 0;
		for (LocalDate d = hoy.plusDays(1); d.isBefore(LocalDate.of(2026, 11, 1)); d = d.plusDays(1)) {
			if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY || FeriadosNacionales.es(d)) {
				continue;
			}
			try {
				feriados.registrar(new FeriadoRequest(d, "Jornada de capacitación docente"));
				registrados++;
			}
			catch (ReglaNegocioException e) {
				rechazados++;
			}
		}
		assertThat(registrados).as("CORREGIDO: tope por mes y días seguidos").isLessThanOrEqualTo(3);
		assertThat(rechazados).isGreaterThanOrEqualTo(16);
		assertThat(calendario.siguienteDiaHabil(hoy)).as("CORREGIDO: sin la aprobación de otra persona nada cambia")
				.isEqualTo(LocalDate.of(2026, 10, 5));
	}
}
