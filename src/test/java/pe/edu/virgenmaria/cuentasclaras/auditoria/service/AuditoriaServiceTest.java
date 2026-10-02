package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EslabonCadenaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.PrincipalConColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaJpa;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registro de eventos sobre la base real (H2 en modo MySQL). Sin transacción de prueba: cada
 * registro confirma su propia transacción, como en la aplicación.
 */
@PruebaJpa
@Import({ AuditoriaService.class, SelladorAuditoria.class, VerificadorIntegridadAuditoria.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditoriaServiceTest {

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private VerificadorIntegridadAuditoria verificador;

	@Autowired
	private SelladorAuditoria sellador;

	@Autowired
	private EventoAuditoriaRepository eventos;

	@Autowired
	private EslabonCadenaRepository cadena;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaccion;

	@BeforeEach
	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void asignaSecuenciaConsecutiva() {
		List<Long> secuencias = LongStream.range(0, 3)
				.mapToObj(i -> registrarDeSistema(AccionAuditoria.USUARIO_CREADO).getSecuencia())
				.toList();

		assertThat(secuencias).containsExactly(1L, 2L, 3L);
		assertThat(cadena.leer().getUltimaSecuencia()).isEqualTo(3);
	}

	@Test
	void encadenaElHashConElAnterior() {
		registrarDeSistema(AccionAuditoria.USUARIO_CREADO);
		registrarDeSistema(AccionAuditoria.ROLES_CAMBIADOS);

		List<EventoAuditoria> guardados = todos();
		EventoAuditoria primero = guardados.get(0);
		EventoAuditoria segundo = guardados.get(1);
		assertThat(primero.getHash()).isEqualTo(sellador.sellar(VerificadorIntegridadAuditoria.HASH_INICIAL, primero));
		assertThat(segundo.getHash()).isEqualTo(sellador.sellar(primero.getHash(), segundo));
		assertThat(cadena.leer().getUltimoHash()).isEqualTo(segundo.getHash());
	}

	@Test
	void guardaQuienRolColegioAccionEntidadIpYFecha() {
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				new UsuarioDePrueba(1L, 7L, "director.a"), null,
				AuthorityUtils.createAuthorityList("ROLE_DIRECTOR", "FACTOR_PASSWORD")));
		MockHttpServletRequest peticion = new MockHttpServletRequest();
		peticion.setRemoteAddr("190.40.1.2");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
		LocalDateTime antes = LocalDateTime.now(java.time.Clock.system(java.time.ZoneId.of("America/Lima")))
				.truncatedTo(ChronoUnit.MICROS);

		auditoria.registrar(AccionAuditoria.USUARIO_DESACTIVADO, "usuario", "9", "activo", "inactivo",
				"Dejó de trabajar en el colegio");

		EventoAuditoria evento = todos().get(0);
		assertThat(evento.getNombreUsuario()).isEqualTo("director.a");
		assertThat(evento.getUsuarioId()).isEqualTo(7L);
		assertThat(evento.getRoles()).isEqualTo("DIRECTOR");
		assertThat(evento.getColegioId()).isEqualTo(1L);
		assertThat(evento.getAccion()).isEqualTo(AccionAuditoria.USUARIO_DESACTIVADO);
		assertThat(evento.getEntidad()).isEqualTo("usuario");
		assertThat(evento.getEntidadId()).isEqualTo("9");
		assertThat(evento.getValorAnterior()).isEqualTo("activo");
		assertThat(evento.getValorNuevo()).isEqualTo("inactivo");
		assertThat(evento.getDetalle()).isEqualTo("Dejó de trabajar en el colegio");
		assertThat(evento.getIp()).isEqualTo("190.40.1.2");
		assertThat(evento.getOcurridoEn()).isBetween(antes, antes.plusMinutes(1));
	}

	@Test
	void siLaOperacionFallaElEventoNoSeGuarda() {
		assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> {
			registrarDeSistema(AccionAuditoria.USUARIO_CREADO);
			throw new IllegalStateException("la operación auditada falló");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria", Long.class)).isZero();
		assertThat(cadena.leer().getUltimaSecuencia()).isZero();
	}

	@Test
	void recortaTextosLargos() {
		auditoria.registrar(Actor.sistema(1L), AccionAuditoria.ROLES_CAMBIADOS, "e".repeat(60), "i".repeat(60),
				"a".repeat(3000), "n".repeat(3000), "d".repeat(900));

		EventoAuditoria evento = todos().get(0);
		assertThat(evento.getEntidad()).hasSize(EventoAuditoria.MAX_ENTIDAD);
		assertThat(evento.getEntidadId()).hasSize(EventoAuditoria.MAX_ENTIDAD_ID);
		assertThat(evento.getValorAnterior()).hasSize(EventoAuditoria.MAX_VALOR);
		assertThat(evento.getValorNuevo()).hasSize(EventoAuditoria.MAX_VALOR);
		assertThat(evento.getDetalle()).hasSize(EventoAuditoria.MAX_DETALLE);
		assertThat(sellador.esValido(VerificadorIntegridadAuditoria.HASH_INICIAL, evento)).isTrue();
	}

	@Test
	void eventosConcurrentesNoRepitenSecuencia() throws Exception {
		int hilos = 8;
		int porHilo = 5;
		ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
		CountDownLatch largada = new CountDownLatch(1);
		try {
			List<Callable<List<Long>>> tareas = LongStream.range(0, hilos).mapToObj(h -> (Callable<List<Long>>) () -> {
				largada.await();
				return LongStream.range(0, porHilo)
						.mapToObj(i -> registrarDeSistema(AccionAuditoria.INGRESO_EXITOSO).getSecuencia())
						.toList();
			}).toList();
			List<Future<List<Long>>> resultados = tareas.stream().map(ejecutor::submit).toList();
			largada.countDown();

			List<Long> secuencias = new java.util.ArrayList<>();
			for (Future<List<Long>> resultado : resultados) {
				secuencias.addAll(resultado.get(60, TimeUnit.SECONDS));
			}
			assertThat(secuencias).doesNotHaveDuplicates()
					.containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, hilos * porHilo).boxed().toList());
		}
		finally {
			ejecutor.shutdownNow();
		}
		assertThat(verificador.verificar().integra()).isTrue();
	}

	@Test
	void laConsultaPorColegioSoloDevuelveLosEventosDeEseColegio() {
		jdbc.update("INSERT INTO colegio (nombre) VALUES ('Colegio de Prueba B')");
		long colegioB = jdbc.queryForObject("SELECT id FROM colegio WHERE nombre = 'Colegio de Prueba B'", Long.class);
		registrarDeSistema(AccionAuditoria.USUARIO_CREADO);
		auditoria.registrar(Actor.sistema(colegioB), AccionAuditoria.USUARIO_CREADO, "usuario", "2", null, null, null);
		auditoria.registrar(new Actor(null, null, "desconocido", null, "10.0.0.9"), AccionAuditoria.INGRESO_FALLIDO,
				null, null, null, null, null);
		LocalDateTime desde = LocalDateTime.now().minusDays(2);
		LocalDateTime hasta = LocalDateTime.now().plusDays(2);

		assertThat(eventos.findByColegioIdAndOcurridoEnBetweenOrderBySecuenciaDesc(1L, desde, hasta,
				PageRequest.of(0, 10)).getContent())
				.extracting(EventoAuditoria::getColegioId).containsExactly(1L);
		assertThat(eventos.findByColegioIdAndOcurridoEnBetweenOrderBySecuenciaDesc(colegioB, desde, hasta,
				PageRequest.of(0, 10)).getContent())
				.extracting(EventoAuditoria::getColegioId).containsExactly(colegioB);
	}

	private EventoAuditoria registrarDeSistema(AccionAuditoria accion) {
		return auditoria.registrar(Actor.sistema(1L), accion, "usuario", "1", null, null, null);
	}

	private List<EventoAuditoria> todos() {
		return eventos.findBySecuenciaGreaterThanOrderBySecuenciaAsc(0, Limit.unlimited());
	}

	private record UsuarioDePrueba(Long colegioId, Long usuarioId, String nombre)
			implements PrincipalConColegio, Principal {

		@Override
		public String getName() {
			return nombre;
		}
	}
}
