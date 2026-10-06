package pe.edu.virgenmaria.cuentasclaras.pasarela.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.LiquidacionLeida;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.PasarelaPagos;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.Pasarelas;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.RegistroLiquidaciones;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Importa las liquidaciones de la pasarela por su API (sprint 4, tanda 3), como {@code sistema.pasarela}: cada día a las
 * 06:00 (hora de Lima) en cada colegio, las de los últimos 10 días (las ya registradas se saltan). Cada liquidación en
 * su propia transacción. Sin pasarela configurada no hace nada.
 * <p>
 * Si el proveedor que el colegio contrate no tiene API de liquidaciones, se agrega su lectura por archivo cuando se
 * conozca su formato (decisión anotada en el estado del proyecto).
 */
@Component
public class ImportadorLiquidaciones {

	private static final Logger LOG = LoggerFactory.getLogger(ImportadorLiquidaciones.class);

	static final int DIAS_ATRAS = 10;

	private final Pasarelas pasarelas;

	private final RegistroLiquidaciones registro;

	private final RecorridoColegios recorrido;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public ImportadorLiquidaciones(Pasarelas pasarelas, RegistroLiquidaciones registro, RecorridoColegios recorrido,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.pasarelas = pasarelas;
		this.registro = registro;
		this.recorrido = recorrido;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.pasarela.liquidaciones-cron:0 0 6 * * *}", zone = "America/Lima")
	public void importarDiario() {
		LocalDate hoy = LocalDate.now(reloj);
		recorrido.enCadaColegio(ActorSistema.PASARELA, colegio -> importarEnContexto(hoy.minusDays(DIAS_ATRAS), hoy));
	}

	/** Importa en ese colegio como {@code sistema.pasarela}. @return cuántas liquidaciones nuevas registró */
	public int importar(long colegioId, LocalDate desde, LocalDate hasta) {
		return EjecucionComoSistema.como(ActorSistema.PASARELA, colegioId, () -> importarEnContexto(desde, hasta));
	}

	private int importarEnContexto(LocalDate desde, LocalDate hasta) {
		Optional<PasarelaPagos> pasarela = pasarelas.configurada();
		if (pasarela.isEmpty()) {
			return 0;
		}
		List<LiquidacionLeida> leidas = pasarela.get().liquidaciones(desde, hasta);
		int nuevas = 0;
		for (LiquidacionLeida leida : leidas) {
			try {
				Optional<Long> id = transaccion.execute(e -> registro.registrar(pasarela.get().proveedor(), leida));
				if (id != null && id.isPresent()) {
					nuevas++;
				}
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo registrar la liquidación {}: {}", leida.referencia(), e.getClass().getSimpleName());
			}
		}
		return nuevas;
	}
}
