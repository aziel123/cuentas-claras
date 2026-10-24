package pe.edu.virgenmaria.cuentasclaras.operacion.inicial;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.Respaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.repository.RespaldoRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Demo local (perfil {@code dev}, H2): en desarrollo nadie corre {@code scripts/respaldo/respaldar.sh} (es contra MySQL),
 * así que la tabla {@code respaldo} quedaba vacía y el paso «Respaldo» de la Puesta en marcha nunca salía «Hecho»: un
 * colegio nuevo no podía pasar a producción en la demo. Este componente registra un respaldo al destino
 * {@link Respaldo#DESTINO_SIMULADO simulado} al arrancar y, después, cada vez que el último tiene más de la mitad de
 * {@code respaldo-max-horas}: así se mantiene «al día» también en la demo persistente (perfil {@code dev,persistente}).
 * <p>
 * Solo si {@code cuentasclaras.monitoreo.aceptar-respaldo-simulado} es {@code true} (en dev; en prod es {@code false} y el
 * simulado no cuenta). Solo existe con el perfil {@code dev}: ReglasArquitecturaTest lo exige y una prueba comprueba que
 * no es un bean fuera de dev. En una base real, además, la aplicación no tiene INSERT sobre {@code respaldo}.
 */
@Component
@Profile("dev")
public class RespaldoSimuladoDev {

	private static final Logger LOG = LoggerFactory.getLogger(RespaldoSimuladoDev.class);

	private static final DateTimeFormatter NOMBRE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final EntityManager entidades;

	private final RespaldoRepository respaldos;

	private final TransactionTemplate transaccion;

	private final PropiedadesMonitoreo propiedades;

	private final ObjectProvider<Flyway> flyway;

	private final Clock reloj;

	public RespaldoSimuladoDev(EntityManager entidades, RespaldoRepository respaldos, TransactionTemplate transaccion,
			PropiedadesMonitoreo propiedades, ObjectProvider<Flyway> flyway, Clock reloj) {
		this.entidades = entidades;
		this.respaldos = respaldos;
		this.transaccion = transaccion;
		this.propiedades = propiedades;
		this.flyway = flyway;
		this.reloj = reloj;
	}

	/** Después de los datos de demostración (que mueven el reloj de dev y lo devuelven a 0). */
	@EventListener(ApplicationReadyEvent.class)
	public void alArrancar() {
		registrarSiHaceFalta();
	}

	/** Cada hora revisa si el último respaldo ya pasó la mitad de {@code respaldo-max-horas}. */
	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
	public void cadaHora() {
		registrarSiHaceFalta();
	}

	/** @return el respaldo simulado registrado, o vacío si no hacía falta (o el simulado no cuenta) */
	Optional<Respaldo> registrarSiHaceFalta() {
		if (!propiedades.aceptarRespaldoSimulado()) {
			LOG.warn("No se registra el respaldo simulado de la demo: aceptar-respaldo-simulado es false.");
			return Optional.empty();
		}
		try {
			return Optional.ofNullable(transaccion.execute(estado -> registrar()));
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudo registrar el respaldo simulado de la demo: {}", e.getMessage());
			return Optional.empty();
		}
	}

	private Respaldo registrar() {
		LocalDateTime ahora = LocalDateTime.now(reloj.withZone(ConfiguracionTiempo.ZONA_LIMA));
		Optional<Respaldo> ultimo = respaldos.findFirstByOrderByIdDesc();
		LocalDateTime vigenteHasta = ahora.minusMinutes(propiedades.respaldoMaxHoras() * 30L);
		if (ultimo.isPresent() && ultimo.get().getFin().isAfter(vigenteHasta)) {
			return null;
		}
		String archivo = "cc-" + ahora.format(NOMBRE) + ".sql.gz.age";
		Respaldo nuevo = Respaldo.simulado(ahora.minusSeconds(1), ahora, archivo, sha256(archivo), versionEsquema(),
				ultimo.isEmpty());
		entidades.persist(nuevo);
		LOG.info("Respaldo simulado de la demo registrado ({}, {}): el paso «Respaldo» de la Puesta en marcha queda al día.",
				archivo, nuevo.getComparacion());
		return nuevo;
	}

	private String versionEsquema() {
		Flyway f = flyway.getIfAvailable();
		MigrationInfo actual = f == null ? null : f.info().current();
		return actual == null || actual.getVersion() == null ? "dev" : actual.getVersion().getVersion();
	}

	private static String sha256(String texto) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
