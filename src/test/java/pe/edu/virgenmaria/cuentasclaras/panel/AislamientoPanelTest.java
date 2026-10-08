package pe.edu.virgenmaria.cuentasclaras.panel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.VistaPanel;
import pe.edu.virgenmaria.cuentasclaras.panel.service.PanelPromotoria;
import pe.edu.virgenmaria.cuentasclaras.panel.service.ReportesCobranza;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.io.ByteArrayInputStream;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P20: el colegio B no ve el panel, los reportes ni el Excel del colegio A (@TenantId en todas las consultas). */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoPanelTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private PanelPromotoria panel;

	@Autowired
	private ReportesCobranza reportes;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioPanel.Datos a;

	private UsuarioAutenticado promotorB;

	private UsuarioAutenticado administracionB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		a = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos, bandeja, reloj, jdbc);
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		promotorB = UsuariosDePrueba.autenticado(colegioB, 93L, "promotor", "Promotor B", false, EnumSet.of(Rol.PROMOTOR));
		administracionB = UsuariosDePrueba.autenticado(colegioB, 94L, "administracion", "Administración B", false,
				EnumSet.of(Rol.ADMINISTRACION));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void colegioBNoVeLasCifrasNiLosMorososDelA() {
		UsuariosDePrueba.iniciarSesion(promotorB);
		VistaPanel p = panel.ver();
		assertThat(p.hoy().cobrado()).isEqualTo("S/ 0.00");
		assertThat(p.hoy().digital()).isEqualTo("—");
		assertThat(p.mes().anulado()).isEqualTo("S/ 0.00");
		assertThat(p.deuda().monto()).isEqualTo("S/ 0.00");
		assertThat(p.rebajas().descuentos()).isEqualTo("S/ 0.00");
		assertThat(p.porAprobar()).isZero();
		assertThat(reportes.familiasMorosas()).isEmpty();
		assertThat(reportes.ingresosPorMedio(null, null).total()).isEqualTo("S/ 0.00");
		assertThat(reportes.morosidadPorGrado(null).anio()).as("el B no tiene años").isNull();
		assertThatThrownBy(() -> reportes.morosidadPorGrado(a.f().anio2027()))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void elExcelDelColegioBNoTraePagosDelA() throws Exception {
		byte[] archivo = mvc.perform(post("/panel/reportes/ingresos.xlsx").param("desde", "2027-04-01")
				.param("hasta", "2027-04-30").with(csrf()).with(UsuariosDePrueba.como(administracionB)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
			assertThat(libro.getSheet("Ingresos").getLastRowNum()).isZero();
		}
		// El año del A, pedido desde el B: no existe para él (y no deja archivo).
		mvc.perform(post("/panel/reportes/morosidad.xlsx").param("anio", a.f().anio2027().toString()).with(csrf())
				.with(UsuariosDePrueba.como(administracionB))).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT colegio_id FROM evento_auditoria WHERE accion = 'REPORTE_EXPORTADO'",
				Long.class)).isEqualTo(administracionB.colegioId());
	}
}
