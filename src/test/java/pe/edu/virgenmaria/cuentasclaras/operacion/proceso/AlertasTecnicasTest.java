package pe.edu.virgenmaria.cuentasclaras.operacion.proceso;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.EstadoCadena;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.Latidos;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.log.ContadorErrores;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.Respaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.repository.RespaldoRepository;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoTecnico;
import pe.edu.virgenmaria.cuentasclaras.operacion.service.AlertasRespaldo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 7, sección 10.4 (E34 y E35): las alertas técnicas salen por correo directo al operador, sin datos personales,
 * una vez por hora como máximo; «sin respaldo» y «faltan filas» también le llegan a Promotoría en «Para revisar».
 */
class AlertasTecnicasTest {

	/** Un proceso con el nombre del despacho real: su clave es «DespachoMensajes.despachar» (crítico). */
	static class DespachoMensajes {

		public void despachar() {
			// tarea simulada: nunca se programa de verdad
		}
	}

	private final RelojAjustable reloj = new RelojAjustable(Instant.parse("2026-10-08T15:00:00Z"),
			ConfiguracionTiempo.ZONA_LIMA);

	private final RespaldoRepository respaldos = mock(RespaldoRepository.class);

	private final JavaMailSender correo = mock(JavaMailSender.class);

	private final Latidos latidos = new Latidos(reloj);

	private final ContadorErrores errores = new ContadorErrores(reloj);

	private ScheduledTaskRegistrar registro;

	@BeforeEach
	void programar() throws Exception {
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

	@AfterEach
	void cerrar() {
		registro.destroy();
	}

	@Test
	void elDespachoDetenidoAvisaEnMenosDe10Minutos() throws Exception {
		AlertasTecnicas alertas = alertas(propiedades(false, "operador@colegio.pe"));
		reloj.avanzar(Duration.ofMinutes(15));
		latidos.latido("DespachoMensajes.despachar");
		alertas.revisar();
		verify(correo, never()).send(any(SimpleMailMessage.class));

		// El despacho se detiene: en la revisión siguiente (cada 5 minutos) ya pasó su ventana de 2 minutos.
		reloj.avanzar(Duration.ofMinutes(5));
		alertas.revisar();

		ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(correo).send(enviado.capture());
		assertThat(enviado.getValue().getTo()).containsExactly("operador@colegio.pe");
		assertThat(enviado.getValue().getSubject()).contains("CRÍTICA");
		assertThat(enviado.getValue().getText()).contains("CRITICA · PROCESO_ATRASADO · DespachoMensajes.despachar");

		// La misma alerta no se repite antes de una hora.
		reloj.avanzar(Duration.ofMinutes(5));
		alertas.revisar();
		verify(correo, times(1)).send(any(SimpleMailMessage.class));
		reloj.avanzar(Duration.ofMinutes(55));
		alertas.revisar();
		verify(correo, times(2)).send(any(SimpleMailMessage.class));
	}

	@Test
	void sinRespaldoEn26HorasAvisaAlOperadorYAPromotoria() {
		PropiedadesMonitoreo propiedades = propiedades(true, "operador@colegio.pe");
		Respaldo viejo = mock(Respaldo.class);
		when(viejo.getFin()).thenReturn(java.time.LocalDateTime.of(2026, 10, 7, 2, 30));
		when(viejo.getComparacion()).thenReturn(pe.edu.virgenmaria.cuentasclaras.operacion.model.ComparacionRespaldo.IGUAL);
		when(respaldos.findFirstByOrderByIdDesc()).thenReturn(Optional.of(viejo));
		latidos.latido("DespachoMensajes.despachar");
		AlertasTecnicas alertas = alertas(propiedades);

		alertas.revisar();

		ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(correo).send(enviado.capture());
		assertThat(enviado.getValue().getText()).contains("CRITICA · SIN_RESPALDO").contains("26 horas");
		List<AlertaRevision> paraPromotoria = new AlertasRespaldo(estado(propiedades), propiedades).alertas();
		assertThat(paraPromotoria).singleElement().satisfies(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
			assertThat(a.texto()).contains("No hay un respaldo de las últimas 26 horas");
		});
	}

	@Test
	void unRespaldoConFilasFaltantesEsCriticoAunqueEsteAlDia() {
		PropiedadesMonitoreo propiedades = propiedades(true, "operador@colegio.pe");
		Respaldo reciente = mock(Respaldo.class);
		when(reciente.getFin()).thenReturn(java.time.LocalDateTime.of(2026, 10, 8, 2, 30));
		when(reciente.getArchivo()).thenReturn("cc-20261008-023000.sql.gz.age");
		when(reciente.getDiferencias()).thenReturn("pago (1)");
		when(reciente.getComparacion())
				.thenReturn(pe.edu.virgenmaria.cuentasclaras.operacion.model.ComparacionRespaldo.FALTAN_FILAS);
		when(respaldos.findFirstByOrderByIdDesc()).thenReturn(Optional.of(reciente));
		// Correcciones del sprint 7 (QA-S7-1): la alerta sale mientras ese respaldo siga sin resolver.
		when(respaldos.sinResolver(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of(reciente));

		assertThat(alertas(propiedades).detectar(estado(propiedades).foto()))
				.anySatisfy(a -> assertThat(a.tipo()).isEqualTo("FALTAN_FILAS"))
				.noneSatisfy(a -> assertThat(a.tipo()).isEqualTo("SIN_RESPALDO"));
		assertThat(new AlertasRespaldo(estado(propiedades), propiedades).alertas()).singleElement()
				.satisfies(a -> assertThat(a.texto()).contains("Faltan filas"));
	}

	@Test
	void sinOperadorLasAlertasEstanApagadasYNoSeEnviaNada() {
		PropiedadesMonitoreo propiedades = propiedades(true, "");
		AlertasTecnicas alertas = alertas(propiedades);

		alertas.revisar();

		assertThat(estado(propiedades).alertasEncendidas()).isFalse();
		verify(correo, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void unErrorNuevoYUnaRafagaDeErroresSonAtencion() {
		PropiedadesMonitoreo propiedades = propiedades(false, "operador@colegio.pe");
		latidos.latido("DespachoMensajes.despachar");
		AlertasTecnicas alertas = alertas(propiedades);
		errores.registrar("java.lang.IllegalStateException en ServicioCobro.cobrar", "a1b2c3d4e5f6");

		alertas.revisar();
		for (int i = 0; i < 5; i++) {
			errores.registrar("java.lang.IllegalStateException en ServicioCobro.cobrar", "a1b2c3d4e5f6");
		}
		reloj.avanzar(Duration.ofMinutes(1));

		assertThat(alertas.detectar(estado(propiedades).foto()))
				.anySatisfy(a -> assertThat(a.tipo()).isEqualTo("ERRORES"));
		ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(correo).send(enviado.capture());
		assertThat(enviado.getValue().getText()).contains("ATENCION · ERROR_NUEVO").contains("a1b2c3d4e5f6");
	}

	private PropiedadesMonitoreo propiedades(boolean respaldoExigido, String operador) {
		return new PropiedadesMonitoreo(operador, respaldoExigido, true, 26, 5, Duration.ofMinutes(15), 0,
				Duration.ofMinutes(10), Duration.ofHours(1));
	}

	private AlertasTecnicas alertas(PropiedadesMonitoreo propiedades) {
		return new AlertasTecnicas(estado(propiedades), errores, propiedades, proveedor(correo), "avisos@colegio.pe",
				reloj);
	}

	private EstadoTecnico estado(PropiedadesMonitoreo propiedades) {
		EstadoCadena cadena = mock(EstadoCadena.class);
		when(cadena.alDia()).thenReturn(true);
		DataSource fuente = mock(DataSource.class);
		try {
			Connection conexion = mock(Connection.class);
			when(conexion.isValid(anyInt())).thenReturn(true);
			when(fuente.getConnection()).thenReturn(conexion);
		}
		catch (java.sql.SQLException e) {
			throw new IllegalStateException(e);
		}
		return new EstadoTecnico(respaldos, propiedades, latidos, proveedor((ScheduledTaskHolder) registro), errores, cadena, fuente,
				proveedor(correo), "avisos@colegio.pe", reloj);
	}

	@SuppressWarnings("unchecked")
	private static <T> ObjectProvider<T> proveedor(T valor) {
		ObjectProvider<T> proveedor = mock(ObjectProvider.class);
		when(proveedor.getIfAvailable()).thenReturn(valor);
		when(proveedor.orderedStream()).thenAnswer(i -> Stream.of(valor));
		return proveedor;
	}
}
