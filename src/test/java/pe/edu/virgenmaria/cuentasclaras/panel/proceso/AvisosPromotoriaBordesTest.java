package pe.edu.virgenmaria.cuentasclaras.panel.proceso;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AvisosPromotoriaListos;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA sprint 6: el tope diario, el aviso una sola vez y los domingos y feriados de las alertas al celular (decisiones 70
 * y 71, P19). Se publica el evento como lo haría {@code DifusionAvisos} (como {@code sistema.panel}, en una transacción).
 * <ul>
 *   <li>Dado un feriado que cae lunes, cuando hay una alerta crítica, entonces no sale ese día y sale el martes a las
 *       07:00, una sola vez.</li>
 *   <li>Dadas 12 alertas el domingo, cuando llega el lunes, entonces el tope del lunes está entero (10 y un «2 más»).</li>
 *   <li>Dada una alerta ya avisada por WhatsApp, cuando la persona se queda solo con correo, entonces no se repite por
 *       correo.</li>
 *   <li>Dadas dos personas de Promotoría, cuando una llega al tope, entonces la otra recibe las suyas.</li>
 *   <li>Dado que la cajera agotó el tope con anulaciones por aprobar (ATENCIÓN), cuando cierra su caja con faltante
 *       (CRÍTICA), entonces la promotora recibe el aviso del faltante ese mismo día (QA-S6-7).</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AvisosPromotoriaBordesTest {

	private static final LocalDate FERIADO_LUNES = LocalDate.of(2027, 6, 7);

	@Autowired
	private ApplicationEventPublisher eventos;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario promotora;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		promotora = guardar("promotora", Rol.PROMOTOR);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Usuario guardar(String nombre, Rol rol) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, rol);
	}

	private void a(LocalDate dia, int hora, int minuto) {
		reloj.fijar(dia.atTime(hora, minuto).atZone(reloj.getZone()).toInstant());
	}

	private void publicar(LocalDate fecha, List<Aviso> lista) {
		EjecucionComoSistema.como(ActorSistema.PANEL, 1L, () -> new TransactionTemplate(transacciones)
				.executeWithoutResult(t -> eventos.publishEvent(new AvisosPromotoriaListos(1L, fecha, lista))));
	}

	private long alertasDe(Usuario quien, String plantilla) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE usuario_id = ? AND plantilla = ?", Long.class,
				quien.getId(), plantilla);
	}

	private static List<Aviso> criticas(String prefijo, int cuantas) {
		return IntStream.rangeClosed(1, cuantas).mapToObj(i -> new Aviso(TipoAviso.OTRA_CRITICA, prefijo + i)).toList();
	}

	@Test
	void noDebeAvisarEnUnFeriadoYDebeAvisarUnaVezElSiguienteDiaDeMensajes() {
		Aviso faltante = new Aviso(TipoAviso.CIERRE_CON_DIFERENCIA, "C:77", "-S/ 50.00");
		a(FERIADO_LUNES, 9, 0);
		publicar(FERIADO_LUNES, List.of(faltante));
		assertThat(alertasDe(promotora, "ALERTA_PROMOTORIA")).as("Día de la Bandera: no sale nada").isZero();

		LocalDate martes = FERIADO_LUNES.plusDays(1);
		a(martes, 7, 0);
		publicar(martes, List.of(faltante));
		a(martes, 7, 15);
		publicar(martes, List.of(faltante));
		assertThat(jdbc.queryForList("SELECT parametros FROM mensaje WHERE usuario_id = ? AND plantilla = "
				+ "'ALERTA_PROMOTORIA'", String.class, promotora.getId()))
				.containsExactly("Cierre de caja con diferencia\n-S/ 50.00");
	}

	@Test
	void lasAlertasDelDomingoNoConsumenElTopeDelLunes() {
		LocalDate domingo = LocalDate.of(2027, 4, 18);
		a(domingo, 10, 0);
		publicar(domingo, criticas("D", 12));
		assertThat(alertasDe(promotora, "ALERTA_PROMOTORIA")).isZero();

		LocalDate lunes = domingo.plusDays(1);
		a(lunes, 7, 0);
		publicar(lunes, criticas("D", 12));
		assertThat(alertasDe(promotora, "ALERTA_PROMOTORIA")).isEqualTo(10);
		assertThat(jdbc.queryForList("SELECT parametros FROM mensaje WHERE usuario_id = ? AND plantilla = 'ALERTA_MAS'",
				String.class, promotora.getId())).containsExactly("2");
	}

	@Test
	void noDebeRepetirPorCorreoUnaAlertaYaAvisadaPorWhatsapp() {
		LocalDate jueves = LocalDate.of(2027, 4, 15);
		a(jueves, 9, 0);
		Aviso aviso = new Aviso(TipoAviso.CAJA_SIN_CERRAR, "K:5", "14/04/2027");
		publicar(jueves, List.of(aviso));
		jdbc.update("UPDATE usuario SET telefono_whatsapp = NULL, correo = 'promotora@colegio.pe' WHERE id = ?",
				promotora.getId());
		a(jueves, 9, 15);
		publicar(jueves, List.of(aviso));

		assertThat(jdbc.queryForList("SELECT canal FROM mensaje WHERE usuario_id = ? AND plantilla = 'ALERTA_PROMOTORIA'",
				String.class, promotora.getId())).containsExactly("WHATSAPP");
	}

	@Test
	void elTopeDeUnaPersonaNoFrenaLasAlertasDeOtra() {
		LocalDate jueves = LocalDate.of(2027, 4, 15);
		a(jueves, 9, 0);
		publicar(jueves, criticas("A", 10));
		Usuario segunda = guardar("promotora.dos", Rol.PROMOTOR);
		a(jueves, 9, 15);
		publicar(jueves, criticas("B", 3));

		assertThat(alertasDe(promotora, "ALERTA_PROMOTORIA")).isEqualTo(10);
		assertThat(alertasDe(promotora, "ALERTA_MAS")).isEqualTo(1);
		assertThat(alertasDe(segunda, "ALERTA_PROMOTORIA")).isEqualTo(3);
		assertThat(alertasDe(segunda, "ALERTA_MAS")).isZero();
	}

	/**
	 * QA-S6-7 (P1 + P19). Las anulaciones por aprobar son ATENCIÓN y cada solicitud nueva cambia la referencia del aviso
	 * («S:id más reciente»): la cajera que pide una anulación por pasada llena el tope de 10 y fuerza el único «N alertas
	 * más» del día. Cuando después cierra con faltante (CRÍTICA), ese aviso queda retenido sin ningún mensaje hasta el día
	 * siguiente a las 07:00.
	 */
	@Disabled("QA-S6-7: el tope diario no reserva lugar para las CRÍTICAS: las ATENCIÓN (anulaciones por aprobar) lo "
			+ "agotan y el cierre con faltante no llega ese día (MensajesPromotoria.alAvisos)")
	@Test
	void debeAvisarElCierreConFaltanteAunqueLasAnulacionesPendientesHayanAgotadoElTope() {
		LocalDate jueves = LocalDate.of(2027, 4, 15);
		for (int i = 1; i <= 11; i++) {
			a(jueves, 8 + (i * 15) / 60, (i * 15) % 60);
			publicar(jueves, List.of(new Aviso(TipoAviso.ANULACION_PAGO_PENDIENTE, "S:" + (100 + i), "S/ 350.00")));
		}
		assertThat(alertasDe(promotora, "ALERTA_PROMOTORIA")).isEqualTo(10);
		assertThat(alertasDe(promotora, "ALERTA_MAS")).isEqualTo(1);

		a(jueves, 19, 15);
		publicar(jueves, List.of(new Aviso(TipoAviso.CIERRE_CON_DIFERENCIA, "C:9", "-S/ 1,200.00")));

		assertThat(jdbc.queryForList("SELECT clave FROM mensaje WHERE usuario_id = ? AND creado_en >= ?", String.class,
				promotora.getId(), jueves.atTime(19, 15)))
				.as("el faltante de caja debe llegar al celular el mismo día").isNotEmpty();
	}
}
