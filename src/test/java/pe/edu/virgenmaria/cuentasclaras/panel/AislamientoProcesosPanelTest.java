package pe.edu.virgenmaria.cuentasclaras.panel;

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
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResumenDiario;
import pe.edu.virgenmaria.cuentasclaras.panel.proceso.AvisosPromotoria;
import pe.edu.virgenmaria.cuentasclaras.panel.proceso.ResumenDiarioTarea;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA sprint 6 (P20): aislamiento entre colegios en los procesos que corren como {@code sistema.panel} (resumen diario y
 * alertas al celular), que no tienen a una persona en sesión.
 * <ul>
 *   <li>Dados el colegio A (con cobros) y el B (sin cobros), cuando sale el resumen de cada uno, entonces cada foto
 *       lleva solo sus cifras y cada resumen llega solo a su Promotoría.</li>
 *   <li>Dada una caja sin cerrar en el A, cuando corren los avisos del A, entonces la Promotoría del B no recibe nada.</li>
 *   <li>Dado el correo del contador que dejó el DBA, cuando sale el resumen del colegio B, entonces las cifras del B no
 *       van al contador (QA-S6-6).</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoProcesosPanelTest {

	private static final LocalDate JUEVES = LocalDate.of(2027, 4, 15);

	@Autowired
	private ResumenDiarioTarea tarea;

	@Autowired
	private AvisosPromotoria avisos;

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
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private long colegioB;

	private Usuario promotoraA;

	private Usuario promotorB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos, bandeja, reloj, jdbc);
		colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		promotoraA = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.a", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		promotorB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "promotor.b", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private void a(LocalDate dia, int hora, int minuto) {
		reloj.fijar(dia.atTime(hora, minuto).atZone(reloj.getZone()).toInstant());
	}

	private Map<String, Object> mensajesDeLaFoto(ResumenDiario foto) {
		return jdbc.queryForMap("SELECT COUNT(*) AS cuantos, MIN(usuario_id) AS usuario, MIN(colegio_id) AS colegio FROM "
				+ "mensaje WHERE tipo = 'RESUMEN_DIARIO' AND entidad_id = ?", foto.getId());
	}

	@Test
	void cadaResumenLlevaSoloLasCifrasDeSuColegioYLlegaSoloASuPromotoria() {
		a(JUEVES, 19, 30);
		ResumenDiario deA = tarea.enColegio(1L, JUEVES).orElseThrow();
		ResumenDiario deB = tarea.enColegio(colegioB, JUEVES).orElseThrow();

		assertThat(deA.getCobradoTotal()).isEqualByComparingTo("800.00");
		assertThat(deA.getDeudaVencida()).isEqualByComparingTo("1600.00");
		assertThat(deB.getCobradoTotal()).isEqualByComparingTo("0.00").hasScaleOf(2);
		assertThat(deB.getCobradoMes()).isEqualByComparingTo("0.00");
		assertThat(deB.getDeudaVencida()).isEqualByComparingTo("0.00");
		assertThat(deB.getFamiliasMorosas()).isZero();
		assertThat(mensajesDeLaFoto(deA)).containsEntry("cuantos", 1L).containsEntry("usuario", promotoraA.getId())
				.containsEntry("colegio", 1L);
		assertThat(mensajesDeLaFoto(deB)).containsEntry("cuantos", 1L).containsEntry("usuario", promotorB.getId())
				.containsEntry("colegio", colegioB);
	}

	@Test
	void lasAlertasDelColegioANoLleganALaPromotoriaDelColegioB() {
		LocalDate viernes = JUEVES.plusDays(1);
		a(viernes, 8, 0);
		avisos.enColegio(1L, viernes);
		avisos.enColegio(colegioB, viernes);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'ALERTA_PROMOTORIA' AND usuario_id = ?",
				Long.class, promotoraA.getId())).as("la caja del jueves sigue abierta en el A").isPositive();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'ALERTA_PROMOTORIA' AND (usuario_id = ? "
				+ "OR colegio_id = ?)", Long.class, promotorB.getId(), colegioB)).isZero();
	}

	/**
	 * QA-S6-6. {@code configuracion_bd} no tiene colegio: la fila {@code resumen_correo_externo} que el DBA deja para el
	 * contador de un colegio hace que el resumen de TODOS los colegios (cobrado, deuda vencida, huella) salga a ese
	 * correo. En una instalación con varios colegios, el contador del A recibe las cifras del B.
	 */
	@Test
	void elCorreoDelContadorNoDebeRecibirElResumenDeOtroColegio() {
		jdbc.update("INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('resumen_correo_externo', "
				+ "'contador@estudio-del-colegio-a.pe', CURRENT_TIMESTAMP)");
		a(JUEVES, 19, 30);
		tarea.enColegio(1L, JUEVES);
		ResumenDiario deB = tarea.enColegio(colegioB, JUEVES).orElseThrow();

		assertThat(jdbc.queryForList("SELECT destino FROM mensaje WHERE tipo = 'RESUMEN_DIARIO' AND destinatario_tipo = "
				+ "'EXTERNO' AND colegio_id = ? AND entidad_id = ?", String.class, colegioB, deB.getId()))
				.as("las cifras del colegio B no salen al contador del A").isEmpty();
	}
}
