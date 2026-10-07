package pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso;

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
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/**
 * Sprint 5, tanda 3 (decisiones 44 y 45; G15): los recordatorios de vencimiento. La pensión de marzo de 2027 vence el
 * miércoles 31: el recordatorio «3 días antes» caería el domingo 28 (Pascua) y sale el sábado 27; el de «vencida» sale
 * el jueves 1 de abril (día hábil siguiente). Nada más. Uno por familia y fecha, solo de lunes a sábado de 8:00 a 20:00.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class RecordatoriosTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	private static final LocalDate SABADO_27 = LocalDate.of(2027, 3, 27);

	private static final LocalDate JUEVES_1 = LocalDate.of(2027, 4, 1);

	@Autowired
	private Recordatorios recordatorios;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private CalendarioHabil calendario;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		calendario.invalidarTodo();
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private List<Map<String, Object>> recordatoriosDe(Long familia) {
		return jdbc.queryForList("SELECT tipo, clave, parametros, apoderado_id, estado FROM mensaje WHERE familia_id = ? "
				+ "AND tipo IN ('RECORDATORIO_VENCIMIENTO', 'CUOTA_VENCIDA') ORDER BY id", familia);
	}

	@Test
	void unoAntesYUnoDespuesNadaMas() {
		List<LocalDate> conMensajes = new ArrayList<>();
		for (LocalDate dia = LocalDate.of(2027, 3, 20); !dia.isAfter(LocalDate.of(2027, 4, 15)); dia = dia.plusDays(1)) {
			if (recordatorios.enColegio(1L, dia) > 0) {
				conMensajes.add(dia);
			}
		}
		// Correr el proceso otra vez el mismo día no duplica nada.
		recordatorios.enColegio(1L, SABADO_27);
		recordatorios.enColegio(1L, JUEVES_1);

		assertThat(conMensajes).containsExactly(SABADO_27, JUEVES_1);
		assertThat(recordatoriosDe(f.quispe())).extracting(m -> m.get("tipo"))
				.containsExactly("RECORDATORIO_VENCIMIENTO", "CUOTA_VENCIDA");
		assertThat(recordatoriosDe(f.flores())).extracting(m -> m.get("tipo"))
				.containsExactly("RECORDATORIO_VENCIMIENTO", "CUOTA_VENCIDA");
		assertThat(ultimoEvento(jdbc, "RECORDATORIOS_ENVIADOS")).containsEntry("nombre_usuario", "sistema.mensajeria");
	}

	@Test
	void unMensajePorFamiliaYFecha() {
		recordatorios.enColegio(1L, SABADO_27);

		// Mateo y Valeria (hermanos, responsable Rosa) vencen el mismo día: UN mensaje con las dos cuotas.
		assertThat(recordatoriosDe(f.quispe())).singleElement().satisfies(m -> {
			assertThat(m).containsEntry("apoderado_id", f.rosa()).containsEntry("estado", "PENDIENTE");
			assertThat(m.get("clave")).asString().endsWith(":2027-03-31");
			String texto = PlantillaMensaje.RECORDATORIO.componer(List.of(((String) m.get("parametros")).split("\n", -1)));
			assertThat(m.get("parametros")).asString().contains("2 cuotas (Pensión marzo 2027 de Mateo, Pensión marzo "
					+ "2027 de Valeria)", "31/03/2027");
			assertThat(texto).doesNotContainIgnoringCase("examen").doesNotContainIgnoringCase("nota");
		});
	}

	@Test
	void noSaleDomingoNiFeriadoNiFueraDeHora() {
		// Domingo de Pascua y Jueves Santo: el proceso no prepara nada.
		assertThat(recordatorios.enColegio(1L, LocalDate.of(2027, 3, 28))).isZero();
		assertThat(recordatorios.enColegio(1L, LocalDate.of(2027, 3, 25))).isZero();

		recordatorios.enColegio(1L, SABADO_27);
		// El sábado 27 a las 20:30 ya es tarde: espera al lunes 29 a las 08:00 (el domingo no sale).
		reloj.fijar(ZonedDateTime.of(2027, 3, 27, 20, 30, 0, 0, LIMA).toInstant());
		despacho.despacharColegio(1L);
		List<Map<String, Object>> pendientes = jdbc.queryForList("SELECT estado, proximo_intento_en, intentos FROM mensaje "
				+ "WHERE tipo = 'RECORDATORIO_VENCIMIENTO'");
		assertThat(pendientes).isNotEmpty().allSatisfy(m -> {
			assertThat(m).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 0);
			assertThat(((java.sql.Timestamp) m.get("proximo_intento_en")).toLocalDateTime())
					.isEqualTo(LocalDateTime.of(2027, 3, 29, 8, 0));
		});
		// El lunes a las 08:30 sí sale.
		reloj.fijar(ZonedDateTime.of(2027, 3, 29, 8, 30, 0, 0, LIMA).toInstant());
		despacho.despacharColegio(1L);
		assertThat(jdbc.queryForList("SELECT estado FROM mensaje WHERE tipo = 'RECORDATORIO_VENCIMIENTO'", String.class))
				.isNotEmpty().allMatch("ENVIADO"::equals);
	}

	@Test
	void respetaLaPreferenciaDelApoderado() {
		jdbc.update("UPDATE apoderado SET recordatorios_activos = FALSE WHERE id = ?", f.rosa());

		recordatorios.enColegio(1L, SABADO_27);

		assertThat(recordatoriosDe(f.quispe())).isEmpty();
		assertThat(recordatoriosDe(f.flores())).hasSize(1);
	}

	@Test
	void losAvisosDePagoNoSeApagan() {
		jdbc.update("UPDATE apoderado SET recordatorios_activos = FALSE WHERE id = ?", f.rosa());

		como(CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "450.00"));

		assertThat(jdbc.queryForList("SELECT apoderado_id FROM mensaje WHERE tipo = 'PAGO_REGISTRADO' AND entidad_id = ?",
				Long.class, pago)).containsExactly(f.rosa());
		// Y la cuota pagada ya no se recuerda: Valeria sigue en el recordatorio, Mateo no.
		SecurityContextHolder.clearContext();
		recordatorios.enColegio(1L, SABADO_27);
		jdbc.update("UPDATE apoderado SET recordatorios_activos = TRUE WHERE id = ?", f.rosa());
		recordatorios.enColegio(1L, JUEVES_1);
		assertThat(recordatoriosDe(f.quispe())).singleElement().satisfies(m -> assertThat(m.get("parametros")).asString()
				.contains("Pensión marzo 2027 de Valeria").doesNotContain("Mateo"));
	}
}
