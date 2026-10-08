package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Sprint 5, control 4 de la skill (el padre es el auditor): cada pago, anulación y descuento crea su aviso a la familia
 * en la MISMA transacción (G2, G3 y G4). La falta del aviso revierte el pago: lo prueba {@code SinMensajeNoHayPagoTest}.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AvisosFinancierosTest {

	private static final String CELULAR_ROSA = "+51" + EscenarioEscolar.CELULAR_ROSA;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private SimuladorPagos simulador;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		// La cajera existe con su nombre: el aviso dice quién registró el pago (nombre corto, decisión 43).
		var cajera = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		jdbc.update("UPDATE usuario SET nombre_completo = 'Lucía Ramos Vega' WHERE id = ?", cajera.getId());
		// Quien aprueba también: el aviso dice su ROL («Dirección»), nunca su usuario.
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "director", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void elMensajeDePagoLlevaMontoConceptoComprobanteYQuienRegistro() {
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03"),
				cuota(jdbc, f.valeria(), "PEN-2027-03")), "900.00", "900.00"));
		String comprobante = jdbc.queryForObject("SELECT CONCAT(c.serie, '-', LPAD(c.numero, 8, '0')) FROM pago p "
				+ "JOIN comprobante c ON c.id = p.comprobante_id WHERE p.id = ?", String.class, pago);

		// Un solo aviso a Rosa (responsable de pago de los dos hermanos), por WhatsApp: su contacto registrado.
		List<Map<String, Object>> mensajes = jdbc.queryForList("SELECT * FROM mensaje WHERE entidad = 'pago' AND "
				+ "entidad_id = ?", pago);
		assertThat(mensajes).singleElement().satisfies(m -> {
			assertThat(m).containsEntry("tipo", "PAGO_REGISTRADO").containsEntry("canal", "WHATSAPP")
					.containsEntry("apoderado_id", f.rosa()).containsEntry("familia_id", f.quispe())
					.containsEntry("destino", CELULAR_ROSA).containsEntry("estado", "PENDIENTE")
					.containsEntry("creado_por", "caja");
			String parametros = (String) m.get("parametros");
			assertThat(parametros.split("\n")).hasSize(6);
			assertThat(parametros).contains("S/ 900.00").contains("de Mateo").contains("de Valeria")
					.contains(comprobante).contains("Efectivo").endsWith("Lucía R.");
			// Ley 29733: ni el DNI ni el apellido del alumno.
			assertThat(parametros).doesNotContain(EscenarioEscolar.DNI_MATEO).doesNotContain("Quispe");
		});
	}

	@Test
	void pagoEnLineaYDeBancoTambienAvisan() {
		UsuarioAutenticado rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());
		como(rosa);
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(marzo))).total();
		String referencia = pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzo), total, false));
		simulador.simular(referencia, pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion.YAPE);
		SecurityContextHolder.clearContext();

		Long pago = jdbc.queryForObject("SELECT p.id FROM pago p JOIN orden_pago o ON o.id = p.orden_pago_id "
				+ "WHERE o.referencia = ?", Long.class, referencia);
		assertThat(jdbc.queryForMap("SELECT creado_por, parametros FROM mensaje WHERE entidad = 'pago' AND entidad_id = ?",
				pago)).satisfies(m -> {
					assertThat(m.get("creado_por")).isEqualTo("sistema.pasarela");
					assertThat((String) m.get("parametros")).endsWith("pago en línea").contains("Yape");
				});
		// Recaudación bancaria: el mismo oyente, con «banco» como quien registró (ver AvisosFinancierosUnitarioTest).
		assertThat(AvisosFinancieros.nombreCorto("Lucía Ramos Vega")).isEqualTo("Lucía R.");
	}

	@Test
	void laAnulacionAvisaATodosLosApoderadosPorAmbosCanales() {
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00",
				"500.00"));
		anulaciones.solicitarDevolucion(pago, EscenarioAprobaciones.MOTIVO_ANULACION);
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);
		SecurityContextHolder.clearContext();

		List<Map<String, Object>> avisos = jdbc.queryForList("SELECT canal, destino, parametros FROM mensaje "
				+ "WHERE tipo = 'PAGO_ANULADO' AND entidad_id = ? ORDER BY canal", pago);
		// Rosa tiene celular y correo: el aviso de anulación sale por los dos desde el inicio.
		assertThat(avisos).extracting(m -> m.get("canal")).containsExactly("CORREO", "WHATSAPP");
		assertThat(avisos).extracting(m -> m.get("destino")).containsExactly(EscenarioEscolar.CORREO_ROSA, CELULAR_ROSA);
		assertThat((String) avisos.getFirst().get("parametros")).contains("S/ 450.00")
				.contains(EscenarioAprobaciones.MOTIVO_ANULACION).endsWith("Dirección");
	}

	@Test
	void elDescuentoAprobadoAvisaALaFamilia() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		como(ADMINISTRACION);
		Long id = descuentos.solicitar(EscenarioAprobaciones.descuento(f.mateo(), TipoDescuento.HERMANOS, "10",
				List.of(marzo)));
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "descuento", id);
		SecurityContextHolder.clearContext();

		List<Map<String, Object>> avisos = jdbc.queryForList("SELECT canal, apoderado_id, parametros FROM mensaje "
				+ "WHERE tipo = 'DESCUENTO_APROBADO' AND entidad = 'descuento' AND entidad_id = ?", id);
		assertThat(avisos).hasSize(2).allSatisfy(m -> {
			assertThat(m.get("apoderado_id")).isEqualTo(f.rosa());
			assertThat((String) m.get("parametros")).startsWith("S/ 45.00").contains("de Mateo").endsWith("Dirección");
		});
	}

	@Test
	void unPagoDigitalTambienAvisaYElMedioVaEnElMensaje() {
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(EscenarioCaja.digital(f.flores(), List.of(cuota(jdbc, f.sebastian(), "PEN-2027-03")),
				MedioPago.YAPE, "Y778899", "450.00"));
		assertThat(jdbc.queryForMap("SELECT destino, parametros FROM mensaje WHERE entidad_id = ? AND tipo = "
				+ "'PAGO_REGISTRADO'", pago)).containsEntry("destino", "+51912345678")
				.satisfies(m -> assertThat((String) m.get("parametros")).contains("Yape").contains("de Sebastián"));
	}
}
