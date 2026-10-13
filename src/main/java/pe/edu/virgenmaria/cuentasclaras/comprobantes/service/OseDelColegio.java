package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.config.PropiedadesComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.AfectacionIgv;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.ConectoresColegio;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprint 8, tanda 2 (sección 3.6, H4 y H9): el OSE del colegio en contexto. Las series, la afectación al IGV y el emisor
 * salen de su conector OSE vigente; sin conector, de {@code cuentasclaras.comprobantes} (en una instalación COMPARTIDA el
 * entorno solo admite el emisor SIMULADO: {@code VerificadorConfiguracion}).
 * <p>
 * El emisor de un comprobante depende de su PROVEEDOR (el de su serie), no del conector de hoy: un comprobante simulado
 * se «envía» siempre al simulado, y uno de Nubefact a la cuenta de Nubefact vigente del colegio (cada colegio con su RUC
 * y su token, H4). Los emisores de Nubefact se guardan en memoria por {@code (conector, version)}.
 */
@Component
public class OseDelColegio {

	/** Lo que usa la emisión: proveedor, afectación y series. */
	public record OseVigente(ProveedorComprobantes proveedor, AfectacionIgv afectacion, String serieBoleta,
			String serieFactura, String serieNotaBoleta, String serieNotaFactura, Long conectorId) {

		public String serieDe(TipoComprobante tipo) {
			return switch (tipo) {
				case BOLETA -> serieBoleta;
				case FACTURA -> serieFactura;
				case NOTA_CREDITO -> throw new IllegalArgumentException("La serie de una nota depende del comprobante");
			};
		}

		public boolean simulado() {
			return proveedor == ProveedorComprobantes.SIMULADO;
		}
	}

	private record EnMemoria(Long version, EmisorElectronico emisor) {
	}

	private final ConectoresColegio conectores;

	private final PropiedadesComprobantes propiedades;

	private final ObjectProvider<EmisorElectronico> delEntorno;

	private final EmisorSimulado simulado = new EmisorSimulado();

	private final Map<Long, EnMemoria> emisores = new ConcurrentHashMap<>();

	public OseDelColegio(ConectoresColegio conectores, PropiedadesComprobantes propiedades,
			ObjectProvider<EmisorElectronico> delEntorno) {
		this.conectores = conectores;
		this.propiedades = propiedades;
		this.delEntorno = delEntorno;
	}

	/** El OSE del colegio en contexto: el de su conector vigente o, sin conector, el del entorno. */
	public OseVigente actual() {
		return conectores.vigente(TipoConector.OSE).map(OseDelColegio::deConector).orElseGet(() -> new OseVigente(
				propiedades.proveedor(), propiedades.afectacionIgv(), propiedades.serieBoleta(), propiedades.serieFactura(),
				propiedades.serieNotaBoleta(), propiedades.serieNotaFactura(), null));
	}

	static OseVigente deConector(ConectorVigente conector) {
		ConfiguracionConector.Ose ose = conector.configuracionDe(ConfiguracionConector.Ose.class);
		return new OseVigente(proveedorDe(conector.proveedor()), AfectacionIgv.valueOf(ose.afectacionIgv()),
				ose.serieBoleta(), ose.serieFactura(), ose.serieNotaBoleta(), ose.serieNotaFactura(), conector.id());
	}

	static ProveedorComprobantes proveedorDe(ProveedorConector proveedor) {
		return proveedor == ProveedorConector.NUBEFACT ? ProveedorComprobantes.NUBEFACT : ProveedorComprobantes.SIMULADO;
	}

	/**
	 * Las series que el colegio usó o usa legítimamente: las del entorno y las de todos sus conectores OSE (vigente y
	 * anteriores). Una serie fuera de esta lista es una alerta crítica (alguien emitió fuera del sistema).
	 */
	public Set<String> seriesConocidas() {
		Set<String> series = new TreeSet<>(Set.of(propiedades.serieBoleta(), propiedades.serieFactura(),
				propiedades.serieNotaBoleta(), propiedades.serieNotaFactura()));
		conectores.osesDelColegio().forEach(o -> series.addAll(o.configuracion().series()));
		return series;
	}

	/**
	 * La serie de la nota de crédito que anula un comprobante de ese proveedor y tipo: la del OSE de hoy si es del mismo
	 * proveedor; si no (el colegio cambió de OSE), la del último OSE del colegio con ese proveedor o la del entorno. Así una
	 * boleta simulada nunca se anula con una nota real (ni al revés).
	 */
	public String serieNotaPara(ProveedorComprobantes proveedorOriginal, TipoComprobante tipoOriginal) {
		OseVigente hoy = actual();
		if (hoy.proveedor() == proveedorOriginal) {
			return notaDe(tipoOriginal, hoy.serieNotaBoleta(), hoy.serieNotaFactura());
		}
		Optional<ConfiguracionConector.Ose> anterior = conectores.osesDelColegio().stream()
				.filter(o -> proveedorDe(o.proveedor()) == proveedorOriginal).map(ConectoresColegio.OseRegistrado::configuracion)
				.findFirst();
		if (anterior.isPresent()) {
			return notaDe(tipoOriginal, anterior.get().serieNotaBoleta(), anterior.get().serieNotaFactura());
		}
		if (propiedades.proveedor() == proveedorOriginal) {
			return notaDe(tipoOriginal, propiedades.serieNotaBoleta(), propiedades.serieNotaFactura());
		}
		return notaDe(tipoOriginal, hoy.serieNotaBoleta(), hoy.serieNotaFactura());
	}

	private static String notaDe(TipoComprobante tipo, String deBoleta, String deFactura) {
		return switch (tipo) {
			case BOLETA -> deBoleta;
			case FACTURA -> deFactura;
			case NOTA_CREDITO -> throw new IllegalArgumentException("Una nota de crédito no se anula con otra");
		};
	}

	/** El emisor de los comprobantes de ese proveedor en el colegio en contexto. */
	public EmisorElectronico emisorPara(ProveedorComprobantes proveedor) {
		if (proveedor == ProveedorComprobantes.SIMULADO) {
			EmisorElectronico entorno = delEntorno.getIfAvailable();
			return entorno != null && entorno.proveedor() == ProveedorComprobantes.SIMULADO ? entorno : simulado;
		}
		Optional<ConectorVigente> conector = conectores.vigente(TipoConector.OSE)
				.filter(c -> c.proveedor() == ProveedorConector.NUBEFACT);
		if (conector.isPresent()) {
			return deNubefact(conector.get());
		}
		EmisorElectronico entorno = delEntorno.getIfAvailable();
		if (!conectores.compartida() && entorno != null && entorno.proveedor() == proveedor) {
			return entorno;
		}
		throw new IllegalStateException("El colegio no tiene una cuenta de " + proveedor + " vigente: el comprobante "
				+ "espera (se reintentará) hasta que se apruebe su conector OSE.");
	}

	/** El emisor de Nubefact de un conector (vigente o propuesto, para probarlo), con su ruta y su token. */
	EmisorElectronico deNubefact(ConectorVigente conector) {
		EnMemoria guardado = emisores.get(conector.id());
		if (guardado != null && guardado.version().equals(conector.version())) {
			return guardado.emisor();
		}
		EmisorElectronico nuevo = nubefactDe(conector);
		emisores.put(conector.id(), new EnMemoria(conector.version(), nuevo));
		return nuevo;
	}

	EmisorElectronico nubefactDe(ConectorVigente conector) {
		ConfiguracionConector.Ose ose = conector.configuracionDe(ConfiguracionConector.Ose.class);
		PropiedadesComprobantes.Nubefact cuenta = propiedades.nubefact();
		return EmisorNubefact.paraCuenta(new PropiedadesComprobantes.Nubefact(ose.ruta(),
				conector.secreto(ConectorVigente.TOKEN), cuenta.dominiosPermitidos(),
				cuenta.tiempoConexion() == null ? Duration.ofSeconds(5) : cuenta.tiempoConexion(),
				cuenta.tiempoLectura() == null ? Duration.ofSeconds(20) : cuenta.tiempoLectura()));
	}
}
