package pe.edu.virgenmaria.cuentasclaras.operacion.salud;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.ComparacionRespaldo;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Sprint 7 (E35): cuándo un proceso programado está atrasado, con la gracia del arranque. */
class EstadoTecnicoTest {

	/** 07:00 en Lima. */
	private static final Instant ARRANQUE = Instant.parse("2026-10-08T12:00:00Z");

	private static final Instant LISTO = ARRANQUE.plus(Duration.ofMinutes(10));

	@Test
	void elDespachoCada30SegundosSeAtrasaA2MinutosDeSuUltimoExito() {
		Duration cada = Duration.ofSeconds(30);
		Instant ultimo = LISTO.plus(Duration.ofMinutes(5));

		assertThat(EstadoTecnico.intervaloAtrasado(cada, ultimo, ultimo.plusSeconds(119), LISTO)).isFalse();
		assertThat(EstadoTecnico.intervaloAtrasado(cada, ultimo, ultimo.plusSeconds(121), LISTO)).isTrue();
		// Sin ningún latido: tras el arranque hay 10 minutos de gracia más la ventana.
		assertThat(EstadoTecnico.intervaloAtrasado(cada, null, ARRANQUE.plus(Duration.ofMinutes(11)), LISTO)).isFalse();
		assertThat(EstadoTecnico.intervaloAtrasado(cada, null, ARRANQUE.plus(Duration.ofMinutes(13)), LISTO)).isTrue();
	}

	@Test
	void laHuellaDeLas6SeAtrasaSiNoCorrioYSeEsperan15Minutos() {
		String seisAm = "0 0 6 * * *";
		Instant listo = Instant.parse("2026-10-07T12:10:00Z");
		Instant hoy0610 = Instant.parse("2026-10-08T11:10:00Z");
		Instant hoy0616 = Instant.parse("2026-10-08T11:16:00Z");

		assertThat(EstadoTecnico.cronAtrasado(seisAm, null, hoy0610, listo)).isFalse();
		assertThat(EstadoTecnico.cronAtrasado(seisAm, null, hoy0616, listo)).isTrue();
		assertThat(EstadoTecnico.cronAtrasado(seisAm, Instant.parse("2026-10-08T11:00:30Z"), hoy0616, listo)).isFalse();
		// Si la aplicación arrancó después de las 06:00 de hoy, todavía no le tocaba correr.
		assertThat(EstadoTecnico.cronAtrasado(seisAm, null, hoy0616, Instant.parse("2026-10-08T11:05:00Z"))).isFalse();
	}

	@Test
	void laEjecucionPreviaDeUnCronSeEncuentraAunqueSeaSemanal() {
		ZonedDateTime limite = ZonedDateTime.of(2026, 10, 8, 10, 0, 0, 0, ConfiguracionTiempo.ZONA_LIMA);

		assertThat(EstadoTecnico.previaEjecucion(CronExpression.parse("0 0 6 * * MON"), limite))
				.isEqualTo(ZonedDateTime.of(2026, 10, 5, 6, 0, 0, 0, ConfiguracionTiempo.ZONA_LIMA));
		assertThat(EstadoTecnico.previaEjecucion(CronExpression.parse("0 0 8-19 * * MON-SAT"), limite))
				.isEqualTo(limite);
	}

	@Test
	void respaldoParaElVigilanteSinFechasNiDatos() {
		assertThat(EstadoRespaldo.ninguno().paraVigilante()).isEqualTo("ATRASADO");
		assertThat(new EstadoRespaldo(true, null, "b2", "a", ComparacionRespaldo.IGUAL, null, true).paraVigilante())
				.isEqualTo("OK");
		assertThat(new EstadoRespaldo(true, null, "b2", "a", ComparacionRespaldo.IGUAL, null, false).paraVigilante())
				.isEqualTo("ATRASADO");
		assertThat(new EstadoRespaldo(true, null, "b2", "a", ComparacionRespaldo.FALTAN_FILAS, "pago (1)", true)
				.paraVigilante()).isEqualTo("REVISAR");
	}

	@Test
	void duracionLegible() {
		assertThat(EstadoTecnico.legible(Duration.ofSeconds(30))).isEqualTo("30 s");
		assertThat(EstadoTecnico.legible(Duration.ofMinutes(10))).isEqualTo("10 min");
		assertThat(EstadoTecnico.legible(Duration.ofHours(1))).isEqualTo("1 h");
	}
}
