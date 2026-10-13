package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.comun.config.PropiedadesPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.CifradoAesGcm.SecretoIlegibleException;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprint 8, tanda 2 (sección 3.6): el conector VIGENTE de cada tipo del colegio en contexto, descifrado, para los módulos
 * que lo usan (comprobantes, pasarela, caja, recaudación y conciliación). Guarda en memoria el descifrado por
 * {@code (id, version)}: un conector nuevo (otra fila) o uno que cambió (otra versión) se vuelve a leer.
 * <p>
 * Sin conector vigente, cada módulo decide (como dice el diseño): en una instalación DEDICADA usa sus propiedades del
 * entorno (como hasta el sprint 7); en una COMPARTIDA el entorno solo admite proveedores simulados o ninguno
 * ({@code VerificadorConfiguracion}), así que el colegio sin conector usa el OSE simulado y no tiene pago en línea.
 * <p>
 * Sin permisos propios: lee el colegio del contexto (la persona en sesión o el proceso que corre en ese colegio). Los
 * secretos nunca salen de aquí hacia una pantalla ni un log ({@link ConectorVigente#toString()} no los lleva).
 */
@Service
public class ConectoresColegio {

	private static final Logger LOG = LoggerFactory.getLogger(ConectoresColegio.class);

	private final ConectorColegioRepository conectores;

	private final SecretosConectores secretos;

	private final PropiedadesPlataforma plataforma;

	/** id del conector → (versión, conector descifrado). */
	private final Map<Long, ConectorVigente> cache = new ConcurrentHashMap<>();

	public ConectoresColegio(ConectorColegioRepository conectores, SecretosConectores secretos,
			PropiedadesPlataforma plataforma) {
		this.conectores = conectores;
		this.secretos = secretos;
		this.plataforma = plataforma;
	}

	/** Si la instalación es COMPARTIDA (sin conector, nada del entorno salvo lo simulado). */
	public boolean compartida() {
		return plataforma.compartida();
	}

	/** El vigente de ese tipo en el colegio del contexto. */
	public Optional<ConectorVigente> vigente(TipoConector tipo) {
		Long colegio = ContextoColegio.actual();
		if (colegio == null || colegio <= 0) {
			return Optional.empty();
		}
		return conectores.findFirstByTipoAndEstado(tipo, EstadoConector.VIGENTE).map(this::descifrado);
	}

	/** El vigente de ese tipo en otro colegio (los procesos que recorren colegios, sin transacción abierta). */
	public Optional<ConectorVigente> vigente(long colegioId, TipoConector tipo) {
		Long actual = ContextoColegio.actual();
		if (actual != null && actual == colegioId) {
			return vigente(tipo);
		}
		return ContextoColegio.en(colegioId, () -> vigente(tipo));
	}

	/** Un OSE que el colegio tuvo en vigencia (sin secretos): su proveedor y su configuración. */
	public record OseRegistrado(pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector proveedor,
			ConfiguracionConector.Ose configuracion) {
	}

	/**
	 * Todos los OSE que tuvo el colegio (vigente y anteriores, del más nuevo al más viejo): sus series ya son «conocidas»
	 * (alertas de series) y la nota de crédito de un comprobante antiguo sale con una serie de SU proveedor.
	 */
	public List<OseRegistrado> osesDelColegio() {
		return conectores.findByTipoOrderByIdDesc(TipoConector.OSE).stream()
				.filter(c -> c.getEstado() == EstadoConector.VIGENTE || c.getEstado() == EstadoConector.REEMPLAZADO)
				.map(c -> new OseRegistrado(c.getProveedor(), (ConfiguracionConector.Ose) ConfiguracionConector
						.deJson(TipoConector.OSE, c.getConfiguracion())))
				.toList();
	}

	/**
	 * El conector de la pasarela de esa clave pública opaca (la ruta {@code /webhooks/pasarela/{clave}}), solo si está
	 * VIGENTE. Busca en todos los colegios: la clave pública es lo único que trae el aviso; nunca devuelve datos del
	 * colegio fuera de su id.
	 */
	public Optional<ConectorVigente> pasarelaPorClavePublica(String clavePublica) {
		if (clavePublica == null || !clavePublica.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
			return Optional.empty();
		}
		Optional<ConectorColegio> conector = BusquedaPorClavePublica.buscar(conectores, clavePublica);
		return conector.filter(c -> c.getTipo() == TipoConector.PASARELA && c.getEstado() == EstadoConector.VIGENTE)
				.map(this::descifrado);
	}

	/** El conector (de cualquier estado) descifrado: para probar uno PROPUESTO. */
	ConectorVigente descifradoParaPrueba(ConectorColegio conector) {
		return descifrar(conector);
	}

	/** Olvida lo descifrado (al cambiar un conector). */
	void olvidar(Long conectorId) {
		cache.remove(conectorId);
	}

	private ConectorVigente descifrado(ConectorColegio conector) {
		ConectorVigente enMemoria = cache.get(conector.getId());
		if (enMemoria != null && enMemoria.version().equals(conector.getVersion())) {
			return enMemoria;
		}
		ConectorVigente nuevo = descifrar(conector);
		cache.put(conector.getId(), nuevo);
		return nuevo;
	}

	private ConectorVigente descifrar(ConectorColegio conector) {
		Map<String, String> claros;
		try {
			claros = secretos.descifrar(conector);
		}
		catch (SecretoIlegibleException e) {
			// Sin el secreto (por ejemplo, la clave maestra cambió sin rotar): el conector no sirve hasta proponerlo de nuevo.
			LOG.error("El secreto del conector {} ({}) del colegio {} no se descifra: {}", conector.getId(),
					conector.getTipo(), conector.getColegioId(), e.getMessage());
			claros = Map.of();
		}
		return new ConectorVigente(conector.getId(), conector.getVersion(), conector.getColegioId(), conector.getTipo(),
				conector.getProveedor(), conector.getModo(), conector.getClavePublica(),
				ConfiguracionConector.deJson(conector.getTipo(), conector.getConfiguracion()), claros);
	}
}
