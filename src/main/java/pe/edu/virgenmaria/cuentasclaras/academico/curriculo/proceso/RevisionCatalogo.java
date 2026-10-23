package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.config.VerificadorCatalogoCurriculo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.IntegridadCatalogo;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;

import java.util.List;

/**
 * Sprint 10, tanda 1 (M10): además del arranque, cada día a las 05:00 de Lima se vuelve a comparar el catálogo de la base
 * con el del jar (las dos direcciones). Si no coincide, {@code sistema.academico} lo deja en la bitácora de cada colegio y
 * Promotoría recibe la alerta CRÍTICA {@code INTEGRIDAD_CATALOGO} (en prod, además, el siguiente arranque no sube).
 */
@Component
public class RevisionCatalogo {

	private static final Logger LOG = LoggerFactory.getLogger(RevisionCatalogo.class);

	private final VerificadorCatalogoCurriculo verificador;

	private final IntegridadCatalogo integridad;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	public RevisionCatalogo(VerificadorCatalogoCurriculo verificador, IntegridadCatalogo integridad,
			RecorridoColegios colegios, PlatformTransactionManager transacciones) {
		this.verificador = verificador;
		this.integridad = integridad;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Scheduled(cron = "${cuentasclaras.curriculo.revision-catalogo:0 0 5 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		revisar();
	}

	/** @return en cuántos colegios dejó el evento (0 si el catálogo coincide) */
	public int revisar() {
		List<String> diferencias = verificador.diferencias();
		return avisar(diferencias);
	}

	/** Deja el evento en la bitácora de cada colegio activo (visible para las pruebas con diferencias simuladas). */
	public int avisar(List<String> diferencias) {
		if (diferencias.isEmpty()) {
			return 0;
		}
		LOG.error("El catálogo del currículo no coincide con el del jar: {}", String.join(" ", diferencias));
		int avisados = 0;
		for (Long colegio : colegios.activos()) {
			try {
				Boolean registrado = EjecucionComoSistema.como(ActorSistema.ACADEMICO, colegio,
						() -> transaccion.execute(t -> integridad.registrarDiferencias(diferencias)));
				if (Boolean.TRUE.equals(registrado)) {
					avisados++;
				}
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo registrar la diferencia del catálogo en el colegio {}: {}", colegio,
						e.getClass().getSimpleName());
			}
		}
		return avisados;
	}
}
