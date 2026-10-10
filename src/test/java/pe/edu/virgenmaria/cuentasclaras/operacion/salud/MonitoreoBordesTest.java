package pe.edu.virgenmaria.cuentasclaras.operacion.salud;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import pe.edu.virgenmaria.cuentasclaras.auditoria.proceso.HuellaDiaria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.EstadoCadena;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.HuellasDiarias;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorIntegridadAuditoria;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.Latidos;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ObservadorLatidos;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.log.ContadorErrores;
import pe.edu.virgenmaria.cuentasclaras.operacion.repository.RespaldoRepository;
import pe.edu.virgenmaria.cuentasclaras.operacion.proceso.AlertasTecnicas;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QA del sprint 7 (monitoreo, sección 10; E34 y E35): latidos de los procesos, ventanas de los crons, alertas técnicas y
 * las dos conexiones a la base. Pruebas unitarias con reloj fijo en Lima.
 */
class MonitoreoBordesTest {

	/** Jueves 8 de octubre de 2026, 10:00 en Lima. */
	private static final Instant JUEVES_10 = Instant.parse("2026-10-08T15:00:00Z");

	private final RelojAjustable reloj = new RelojAjustable(JUEVES_10, ConfiguracionTiempo.ZONA_LIMA);

	private final RespaldoRepository respaldos = mock(RespaldoRepository.class);

	private final JavaMailSender correo = mock(JavaMailSender.class);

	private final Latidos latidos = new Latidos(reloj);

	private final ContadorErrores errores = new ContadorErrores(reloj);

	private ScheduledTaskRegistrar registro;

	@AfterEach
	void cerrar() {
		if (registro != null) {
			registro.destroy();
		}
	}

	// ------------------------------------------------------------------ latidos

	/**
	 * QA-S7-2. Dado que la huella de la hora falla en TODOS los colegios (por ejemplo, cc_sistema perdió su GRANT), cuando
	 * corre la tarea programada, entonces no debe dejar latido: si lo deja, «Proceso atrasado» (CRÍTICA) nunca sale. Hoy
	 * HuellaDiaria (como DespachoMensajes, ResumenDiarioTarea y RecorridoColegios) atrapa la excepción de cada colegio, la
	 * tarea termina «bien» y ObservadorLatidos registra el latido.
	 */
	@Test
	void noDebeLatirLaHuellaDeLaHoraSiFalloEnTodosLosColegios() throws Exception {
		HuellasDiarias huellas = mock(HuellasDiarias.class);
		when(huellas.registrarHora(any(LocalDateTime.class))).thenThrow(new IllegalStateException("1142: sin permiso"));
		RecorridoColegios colegios = mock(RecorridoColegios.class);
		when(colegios.activos()).thenReturn(List.of(1L, 2L));
		HuellaDiaria tarea = new HuellaDiaria(huellas, mock(VerificadorIntegridadAuditoria.class), colegios, reloj, 400);

		ejecutarComoTareaProgramada(tarea, HuellaDiaria.class.getMethod("cadaHora"));

		verify(huellas, times(2)).registrarHora(any(LocalDateTime.class));
		assertThat(latidos.ultimos()).as("la huella no se registró en ningún colegio").doesNotContainKey(
				"HuellaDiaria.cadaHora");
	}

	@Test
	void debeLatirLaHuellaDeLaHoraCuandoTerminaBienEnTodosLosColegios() throws Exception {
		HuellasDiarias huellas = mock(HuellasDiarias.class);
		when(huellas.registrarHora(any(LocalDateTime.class))).thenReturn(java.util.Optional.empty());
		RecorridoColegios colegios = mock(RecorridoColegios.class);
		when(colegios.activos()).thenReturn(List.of(1L, 2L));
		HuellaDiaria tarea = new HuellaDiaria(huellas, mock(VerificadorIntegridadAuditoria.class), colegios, reloj, 400);

		ejecutarComoTareaProgramada(tarea, HuellaDiaria.class.getMethod("cadaHora"));

		assertThat(latidos.ultimos()).containsEntry("HuellaDiaria.cadaHora", JUEVES_10);
	}

	// ------------------------------------------------------------------ ventanas

	@Test
	void debeDarPorAtrasadaLaHuellaDeLasSeisRecienPasadosLosQuinceMinutos() {
		Instant listo = Instant.parse("2026-10-01T00:00:00Z");
		Instant ayer = lima(2026, 10, 7, 6, 0);
		assertThat(EstadoTecnico.cronAtrasado("0 0 6 * * *", ayer, lima(2026, 10, 8, 6, 14), listo))
				.as("06:14: todavía en su margen").isFalse();
		assertThat(EstadoTecnico.cronAtrasado("0 0 6 * * *", ayer, lima(2026, 10, 8, 6, 16), listo))
				.as("06:16: ya debía haber terminado").isTrue();
		assertThat(EstadoTecnico.cronAtrasado("0 0 6 * * *", lima(2026, 10, 8, 6, 1), lima(2026, 10, 8, 6, 16), listo))
				.as("terminó a las 06:01").isFalse();
	}

	@Test
	void debeSeguirAtrasadaTodaLaSemanaLaMuestraSemanalQueNoCorrioElLunes() {
		Instant listo = Instant.parse("2026-09-01T00:00:00Z");
		Instant lunesPasado = lima(2026, 9, 28, 0, 10);
		assertThat(EstadoTecnico.cronAtrasado("0 10 0 * * MON", lunesPasado, lima(2026, 10, 8, 10, 0), listo))
				.as("jueves sin la muestra de esta semana").isTrue();
		assertThat(EstadoTecnico.cronAtrasado("0 10 0 * * MON", lima(2026, 10, 5, 0, 11), lima(2026, 10, 8, 10, 0), listo))
				.as("el lunes sí corrió").isFalse();
	}

	@Test
	void noDebeDarPorAtrasadaLaHuellaDeLaHoraUnDomingo() {
		Instant listo = Instant.parse("2026-10-01T00:00:00Z");
		Instant sabado19 = lima(2026, 10, 10, 19, 0);
		assertThat(EstadoTecnico.cronAtrasado("0 0 8-19 * * MON-SAT", sabado19, lima(2026, 10, 11, 12, 0), listo))
				.isFalse();
	}

	@Test
	void debeRespetarLosDiezMinutosDeGraciaTrasElArranque() {
		Instant arranque = lima(2026, 10, 8, 10, 0);
		Instant listo = arranque.plus(Duration.ofMinutes(10));
		assertThat(EstadoTecnico.intervaloAtrasado(Duration.ofSeconds(30), null, arranque.plus(Duration.ofMinutes(11)),
				listo)).as("sin latido a los 11 minutos: dentro de la gracia más su ventana de 2").isFalse();
		assertThat(EstadoTecnico.intervaloAtrasado(Duration.ofSeconds(30), null, arranque.plus(Duration.ofMinutes(13)),
				listo)).as("sin latido a los 13 minutos").isTrue();
		assertThat(EstadoTecnico.cronAtrasado("0 0 6 * * *", null, lima(2026, 10, 9, 6, 30), listo))
				.as("arrancó ayer a las 10:00 y hoy a las 06:00 no corrió").isTrue();
	}

	// ------------------------------------------------------------------ dos conexiones

	/**
	 * QA-S7-3. Dado que el pool de cc_sistema (4 conexiones) está agotado desde hace más de un minuto, cuando la revisión
	 * técnica mira el pool, entonces debe dar la alerta CRÍTICA «POOL». Hoy EstadoTecnico solo mira la ruta de la
	 * conexión del hilo de la revisión, que no tiene actor: siempre la de cc_app.
	 */
	@Test
	void debeAvisarCuandoElPoolDeSistemaEstaAgotado() throws Exception {
		HikariDataSource app = hikari(0, true);
		HikariDataSource sistema = hikari(3, true);
		EstadoTecnico estado = estado(new FuenteDatosEnrutada(app, sistema), propiedades(false));

		estado.foto();
		reloj.avanzar(Duration.ofMinutes(2));

		assertThat(estado.foto().poolAgotado()).isTrue();
	}

	/**
	 * QA-S7-3 (mismo hallazgo). Dado que la conexión de cc_sistema no responde (clave cambiada, usuario bloqueado), cuando
	 * corre la revisión técnica, entonces la base no «responde»: los procesos y la identidad (ingreso) están caídos.
	 */
	@Test
	void debeAvisarCuandoLaConexionDeSistemaNoResponde() throws Exception {
		HikariDataSource app = hikari(0, true);
		HikariDataSource sistema = hikari(0, false);
		EstadoTecnico estado = estado(new FuenteDatosEnrutada(app, sistema), propiedades(false));

		assertThat(estado.foto().baseResponde()).isFalse();
	}

	@Test
	void debeAvisarCuandoElPoolDeLaAplicacionEstaAgotadoMasDeUnMinuto() throws Exception {
		HikariDataSource app = hikari(5, true);
		HikariDataSource sistema = hikari(0, true);
		EstadoTecnico estado = estado(new FuenteDatosEnrutada(app, sistema), propiedades(false));

		assertThat(estado.foto().poolAgotado()).as("recién empieza a esperar").isFalse();
		reloj.avanzar(Duration.ofSeconds(59));
		assertThat(estado.foto().poolAgotado()).as("59 segundos").isFalse();
		reloj.avanzar(Duration.ofSeconds(1));
		assertThat(estado.foto().poolAgotado()).as("un minuto").isTrue();
	}

	// ------------------------------------------------------------------ alertas por correo

	/**
	 * QA-S7-7. Dado que el servidor de correo falló al mandar una alerta CRÍTICA, cuando corre la revisión siguiente (5
	 * minutos después), entonces la alerta debe volver a intentarse. Hoy {@code porEnviar} la marca como enviada ANTES de
	 * mandarla: no se reintenta durante una hora.
	 */
	@Test
	void debeReintentarEnLaRevisionSiguienteUnaAlertaCuyoCorreoFallo() {
		PropiedadesMonitoreo propiedades = propiedades(true);
		doThrow(new MailSendException("SMTP caído")).doNothing().when(correo).send(any(SimpleMailMessage.class));
		AlertasTecnicas alertas = new AlertasTecnicas(estado(fuenteQueResponde(), propiedades), errores, propiedades,
				proveedor(correo), "avisos@colegio.pe", reloj);

		alertas.revisar();
		reloj.avanzar(Duration.ofMinutes(5));
		alertas.revisar();

		verify(correo, times(2)).send(any(SimpleMailMessage.class));
	}

	@Test
	void noDebeRepetirAntesDeUnaHoraUnaAlertaQueSiSalio() {
		PropiedadesMonitoreo propiedades = propiedades(true);
		AlertasTecnicas alertas = new AlertasTecnicas(estado(fuenteQueResponde(), propiedades), errores, propiedades,
				proveedor(correo), "avisos@colegio.pe", reloj);

		alertas.revisar();
		reloj.avanzar(Duration.ofMinutes(59));
		alertas.revisar();
		verify(correo, times(1)).send(any(SimpleMailMessage.class));
		reloj.avanzar(Duration.ofMinutes(1));
		alertas.revisar();
		verify(correo, times(2)).send(any(SimpleMailMessage.class));
	}

	/**
	 * QA-S7-7 (mismo grupo, Baja). Dado que el despacho está atrasado, cuando sale la alerta, entonces el «último éxito» se
	 * lee en hora de Lima (como todo el sistema). Hoy sale en UTC ({@code 2026-10-08T15:00:00Z}): el operador lee 5 horas
	 * de diferencia.
	 */
	@Test
	void debeMostrarEnHoraDeLimaElUltimoExitoDeUnProcesoAtrasado() throws Exception {
		programarDespacho();
		latidos.latido("DespachoMensajes.despachar");
		reloj.avanzar(Duration.ofMinutes(30));
		PropiedadesMonitoreo propiedades = propiedades(false);

		List<AlertasTecnicas.AlertaTecnica> detectadas = new AlertasTecnicas(estado(fuenteQueResponde(), propiedades),
				errores, propiedades, proveedor(correo), "avisos@colegio.pe", reloj).detectar(
						estado(fuenteQueResponde(), propiedades).foto());

		assertThat(detectadas).anySatisfy(a -> {
			assertThat(a.tipo()).isEqualTo("PROCESO_ATRASADO");
			assertThat(a.texto()).contains("10:00").doesNotContain("15:00:00Z");
		});
	}

	@Test
	void unErrorConDatosPersonalesNoLlegaAlCorreoDelOperador() {
		PropiedadesMonitoreo propiedades = propiedades(false);
		LoggerContext contexto = new LoggerContext();
		LoggingEvent evento = new LoggingEvent(MonitoreoBordesTest.class.getName(), contexto.getLogger("prueba"),
				Level.ERROR, "No se pudo avisar a rosa.huaman@gmail.com (DNI 45678912)",
				new IllegalStateException("celular 987654321, monto S/ 450.00"), null);
		evento.setMDCPropertyMap(java.util.Map.of());
		String huella = huellaDe(evento);
		errores.registrar(huella, "0a1b2c3d4e5f");
		AlertasTecnicas alertas = new AlertasTecnicas(estado(fuenteQueResponde(), propiedades), errores, propiedades,
				proveedor(correo), "avisos@colegio.pe", reloj);

		alertas.revisar();

		ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(correo).send(enviado.capture());
		assertThat(enviado.getValue().getText()).contains("ERROR_NUEVO").contains("0a1b2c3d4e5f")
				.doesNotContain("rosa.huaman").doesNotContain("45678912").doesNotContain("987654321")
				.doesNotContain("450.00");
	}

	// ------------------------------------------------------------------ apoyo

	private void ejecutarComoTareaProgramada(Object tarea, java.lang.reflect.Method metodo) {
		ObservationRegistry observaciones = ObservationRegistry.create();
		observaciones.observationConfig().observationHandler(new ObservadorLatidos(latidos));
		new ScheduledMethodRunnable(tarea, metodo, null, () -> observaciones).run();
	}

	private void programarDespacho() throws Exception {
		TaskScheduler programador = mock(TaskScheduler.class);
		when(programador.getClock()).thenReturn(reloj);
		when(programador.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class)))
				.thenAnswer(i -> mock(ScheduledFuture.class));
		when(programador.scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class)))
				.thenAnswer(i -> mock(ScheduledFuture.class));
		registro = new ScheduledTaskRegistrar();
		registro.setTaskScheduler(programador);
		registro.addFixedDelayTask(new ScheduledMethodRunnable(new DespachoMensajes(),
				DespachoMensajes.class.getMethod("despachar")), Duration.ofSeconds(30));
		registro.afterPropertiesSet();
	}

	/** Un proceso con el nombre del despacho real (clave «DespachoMensajes.despachar», crítico). */
	static class DespachoMensajes {

		public void despachar() {
			// tarea simulada
		}
	}

	private static String huellaDe(LoggingEvent evento) {
		try {
			java.lang.reflect.Method m = ContadorErrores.class.getDeclaredMethod("huella",
					ch.qos.logback.classic.spi.ILoggingEvent.class);
			m.setAccessible(true);
			return (String) m.invoke(null, evento);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private PropiedadesMonitoreo propiedades(boolean respaldoExigido) {
		return new PropiedadesMonitoreo("operador@colegio.pe", respaldoExigido, true, 26, 5, Duration.ofMinutes(15), 0,
				Duration.ofMinutes(10), Duration.ofHours(1));
	}

	private EstadoTecnico estado(DataSource fuente, PropiedadesMonitoreo propiedades) {
		EstadoCadena cadena = mock(EstadoCadena.class);
		when(cadena.alDia()).thenReturn(true);
		ObjectProvider<ScheduledTaskHolder> tareas = registro == null ? vacio() : proveedor((ScheduledTaskHolder) registro);
		return new EstadoTecnico(respaldos, propiedades, latidos, tareas, errores, cadena, fuente, proveedor(correo),
				"avisos@colegio.pe", reloj);
	}

	private static DataSource fuenteQueResponde() {
		DataSource fuente = mock(DataSource.class);
		try {
			Connection conexion = mock(Connection.class);
			when(conexion.isValid(anyInt())).thenReturn(true);
			when(fuente.getConnection()).thenReturn(conexion);
		}
		catch (SQLException e) {
			throw new IllegalStateException(e);
		}
		return fuente;
	}

	/** Un pool Hikari simulado: cuántos hilos esperan conexión y si conecta. */
	private static HikariDataSource hikari(int esperando, boolean conecta) throws SQLException {
		HikariDataSource fuente = mock(HikariDataSource.class);
		HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
		when(pool.getThreadsAwaitingConnection()).thenReturn(esperando);
		when(fuente.isWrapperFor(HikariDataSource.class)).thenReturn(true);
		when(fuente.unwrap(HikariDataSource.class)).thenReturn(fuente);
		when(fuente.getHikariPoolMXBean()).thenReturn(pool);
		if (conecta) {
			Connection conexion = mock(Connection.class);
			when(conexion.isValid(anyInt())).thenReturn(true);
			when(fuente.getConnection()).thenReturn(conexion);
		}
		else {
			when(fuente.getConnection()).thenThrow(new SQLException("Access denied for user 'cc_sistema'"));
		}
		return fuente;
	}

	private static Instant lima(int anio, int mes, int dia, int hora, int minuto) {
		return LocalDateTime.of(anio, mes, dia, hora, minuto).atZone(ConfiguracionTiempo.ZONA_LIMA).toInstant();
	}

	@SuppressWarnings("unchecked")
	private static <T> ObjectProvider<T> proveedor(T valor) {
		ObjectProvider<T> proveedor = mock(ObjectProvider.class);
		when(proveedor.getIfAvailable()).thenReturn(valor);
		when(proveedor.orderedStream()).thenAnswer(i -> Stream.of(valor));
		return proveedor;
	}

	@SuppressWarnings("unchecked")
	private static <T> ObjectProvider<T> vacio() {
		ObjectProvider<T> proveedor = mock(ObjectProvider.class);
		when(proveedor.orderedStream()).thenAnswer(i -> Stream.empty());
		return proveedor;
	}
}
