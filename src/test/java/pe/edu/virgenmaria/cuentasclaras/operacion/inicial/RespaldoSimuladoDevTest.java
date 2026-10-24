package pe.edu.virgenmaria.cuentasclaras.operacion.inicial;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.ComparacionRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.Respaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.repository.RespaldoRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RespaldoSimuladoDevTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	/** 24-oct-2026 10:15:30 en Lima. */
	private final Clock reloj = Clock.fixed(Instant.parse("2026-10-24T15:15:30Z"), LIMA);

	private final LocalDateTime ahora = LocalDateTime.now(reloj);

	private EntityManager entidades;

	private RespaldoRepository respaldos;

	@BeforeEach
	void preparar() {
		entidades = mock(EntityManager.class);
		respaldos = mock(RespaldoRepository.class);
	}

	private RespaldoSimuladoDev componente(boolean aceptaSimulado) {
		PropiedadesMonitoreo propiedades = new PropiedadesMonitoreo("", false, aceptaSimulado, 26, 5,
				Duration.ofMinutes(15), 0, Duration.ofMinutes(10), Duration.ofHours(1));
		@SuppressWarnings("unchecked")
		ObjectProvider<Flyway> sinFlyway = mock(ObjectProvider.class);
		return new RespaldoSimuladoDev(entidades, respaldos,
				new TransactionTemplate(mock(PlatformTransactionManager.class)), propiedades, sinFlyway, reloj);
	}

	private Respaldo guardado() {
		ArgumentCaptor<Respaldo> captor = ArgumentCaptor.forClass(Respaldo.class);
		verify(entidades).persist(captor.capture());
		return captor.getValue();
	}

	@Test
	void sinRespaldosRegistraElPrimeroSimuladoYAlDia() {
		when(respaldos.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());

		assertThat(componente(true).registrarSiHaceFalta()).isPresent();

		Respaldo r = guardado();
		assertThat(r.simulado()).isTrue();
		assertThat(r.getDestino()).isEqualTo(Respaldo.DESTINO_SIMULADO);
		assertThat(r.getComparacion()).isEqualTo(ComparacionRespaldo.PRIMERO);
		assertThat(r.getFin()).isEqualTo(ahora);
		assertThat(r.getArchivo()).isEqualTo("cc-20261024-101530.sql.gz.age")
				.matches("^cc-[0-9]{8}-[0-9]{6}[.]sql[.]gz[.]age$");
		assertThat(r.getSha256()).matches("^[0-9a-f]{64}$");
		assertThat(r.getDiferencias()).isNull();
	}

	@Test
	void conUnRespaldoRecienteNoRegistraOtro() {
		Respaldo reciente = Respaldo.simulado(ahora.minusHours(12), ahora.minusHours(12), "cc-20261023-221530.sql.gz.age",
				"a".repeat(64), "40", true);
		when(respaldos.findFirstByOrderByIdDesc()).thenReturn(Optional.of(reciente));

		assertThat(componente(true).registrarSiHaceFalta()).isEmpty();
		verify(entidades, never()).persist(any());
	}

	@Test
	void pasadaLaMitadDelMaximoRegistraOtroIgual() {
		Respaldo viejo = Respaldo.simulado(ahora.minusHours(14), ahora.minusHours(14), "cc-20261023-201530.sql.gz.age",
				"a".repeat(64), "40", true);
		when(respaldos.findFirstByOrderByIdDesc()).thenReturn(Optional.of(viejo));

		assertThat(componente(true).registrarSiHaceFalta()).isPresent();
		assertThat(guardado().getComparacion()).isEqualTo(ComparacionRespaldo.IGUAL);
	}

	@Test
	void siElSimuladoNoCuentaNoRegistraNada() {
		assertThat(componente(false).registrarSiHaceFalta()).isEmpty();
		verify(respaldos, never()).findFirstByOrderByIdDesc();
		verify(entidades, never()).persist(any());
	}
}
