package pe.edu.virgenmaria.cuentasclaras.familias;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
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

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 5, tanda 2: el padre es el auditor (sección 14, G1, G3 y G4). Lo que la familia ve que no cuadra —un pago que
 * no aparece, una anulación o un descuento que no pidió— le llega a Promotoría como alerta CRÍTICA, nunca a quien cobra
 * ni a Administración.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EscenariosFraudeFamiliasTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioAvisosFamilia avisos;

	@Autowired
	private AlertasFamilias alertas;

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
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioCaja.Familias f;

	private UsuarioAutenticado rosa;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "director", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
		SecurityContextHolder.clearContext();
		rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private void esCriticaSoloParaPromotoriaYDireccion(String referencia) throws Exception {
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).anySatisfy(a -> assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA));
		SecurityContextHolder.clearContext();
		mvc.perform(get("/avisos-familias").with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString(referencia)));
		for (UsuarioAutenticado quien : List.of(EscenarioCaja.CAJA, ADMINISTRACION)) {
			mvc.perform(get("/avisos-familias").with(UsuariosDePrueba.como(quien))).andExpect(status().isForbidden());
		}
	}

	/** G1, el fraude original: la cajera cobra en efectivo y no registra. La familia no recibe su mensaje y avisa. */
	@Test
	void pagoNoRegistradoLoReportaLaFamiliaYSoloLoVePromotoria() throws Exception {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(contar(jdbc, "mensaje WHERE tipo = 'PAGO_REGISTRADO'")).isZero();
		como(rosa);
		avisos.enviar(new AvisoRequest(TipoAvisoFamilia.PAGUE_Y_NO_APARECE, null, marzo,
				"Pagué la pensión de marzo en efectivo y no me llegó el mensaje"));
		SecurityContextHolder.clearContext();
		esCriticaSoloParaPromotoriaYDireccion("Pensión marzo 2027");
	}

	/** G3: anulación «por error» después de entregar la boleta. La familia la ve y la reporta con el pago. */
	@Test
	void anulacionNoPedidaLaReportaLaFamilia() throws Exception {
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00",
				"450.00"));
		anulaciones.solicitarDevolucion(pago, EscenarioAprobaciones.MOTIVO_ANULACION);
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);
		SecurityContextHolder.clearContext();
		assertThat(contar(jdbc, "mensaje WHERE tipo = 'PAGO_ANULADO' AND entidad_id = " + pago)).isEqualTo(2);
		String boleta = jdbc.queryForObject("SELECT CONCAT(c.serie, '-', LPAD(c.numero, 8, '0')) FROM pago p "
				+ "JOIN comprobante c ON c.id = p.comprobante_id WHERE p.id = ?", String.class, pago);
		// En su estado de cuenta ve la anulación con su motivo y quién la aprobó.
		mvc.perform(get("/familia/estado-de-cuenta").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Aprobó: Dirección")));
		como(rosa);
		avisos.enviar(new AvisoRequest(TipoAvisoFamilia.NO_RECONOZCO_ANULACION_O_DESCUENTO, pago, null,
				"Yo no pedí anular mi pago"));
		SecurityContextHolder.clearContext();
		esCriticaSoloParaPromotoriaYDireccion(boleta);
	}

	/** G4: descuento fantasma. La familia recibe el aviso del descuento que no pidió y lo reporta. */
	@Test
	void descuentoNoPedidoLoReportaLaFamilia() throws Exception {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		como(ADMINISTRACION);
		Long id = descuentos.solicitar(EscenarioAprobaciones.descuento(f.mateo(), TipoDescuento.HERMANOS, "10",
				List.of(marzo)));
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "descuento", id);
		SecurityContextHolder.clearContext();
		assertThat(contar(jdbc, "mensaje WHERE tipo = 'DESCUENTO_APROBADO'")).isEqualTo(2);
		mvc.perform(get("/familia/estado-de-cuenta").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
				.andExpect(content().string(containsString("aprobó: Dirección")));
		como(rosa);
		avisos.enviar(new AvisoRequest(TipoAvisoFamilia.NO_RECONOZCO_ANULACION_O_DESCUENTO, null, marzo,
				"No pedí ningún descuento: pagué completo"));
		SecurityContextHolder.clearContext();
		esCriticaSoloParaPromotoriaYDireccion("No pedí ningún descuento");
	}
}
