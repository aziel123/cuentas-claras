package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.LogoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.marca.Marca;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.util.Optional;

/**
 * Datos del colegio para mostrar en pantalla. Sprint 8, tanda 1: el nombre comercial del producto ya no vive aquí sino en
 * {@code comun.marca.Marca} ({@code CC_MARCA_NOMBRE}); este servicio solo conoce a los colegios.
 */
@Service
@Transactional(readOnly = true)
public class ColegioService {

	private final ColegioRepository colegios;

	private final LogoColegioRepository logos;

	private final Marca marca;

	public ColegioService(ColegioRepository colegios, LogoColegioRepository logos, Marca marca) {
		this.colegios = colegios;
		this.logos = logos;
		this.marca = marca;
	}

	/**
	 * Sprint 8, tanda 1 (sección 3.7): el nombre con el que un mensaje se presenta a su destinatario, el primer parámetro
	 * de toda plantilla: el nombre del colegio; para el colegio de plataforma (los operadores), la marca del producto.
	 */
	public String nombreParaMensajes(Long colegioId) {
		if (colegioId == null) {
			return marca.nombre();
		}
		return colegios.findById(colegioId).map(c -> c.esPlataforma() ? marca.nombre() : c.getNombre())
				.orElse(marca.nombre());
	}

	/** Nombre del colegio del usuario; vacío si no hay colegio. */
	public Optional<String> nombreOpcionalDe(Long colegioId) {
		if (colegioId == null) {
			return Optional.empty();
		}
		return colegios.findById(colegioId).map(Colegio::getNombre);
	}

	/** Nombre del colegio del usuario (o el id si no existe: nunca falla en una pantalla). */
	public String nombreDe(Long colegioId) {
		return nombreOpcionalDe(colegioId).orElse("Colegio " + colegioId);
	}

	/**
	 * Sprint 8, tanda 1: la marca del colegio en contexto (nombre, razón social, RUC, dirección y si tiene logo vigente).
	 * El logo se consulta con {@code @TenantId}, así que debe llamarse con el colegio en contexto (la sesión o
	 * {@code ContextoColegio.en}).
	 */
	public Optional<MarcaColegio> marcaDe(Long colegioId) {
		if (colegioId == null || colegioId <= 0) {
			return Optional.empty();
		}
		return colegios.findById(colegioId).map(c -> new MarcaColegio(c.getId(), c.getNombre(), c.getRazonSocial(),
				c.getRuc(), c.getDireccion(), colegioId.equals(ContextoColegio.actual()) && logos.existsByVigenteTrue()));
	}

	/** Sprint 8, tanda 1 (sección 3.4): si el colegio ya está en producción (en preparación no se registra dinero). */
	public boolean enProduccion(Long colegioId) {
		return colegioId != null && colegios.findById(colegioId).map(Colegio::enProduccion).orElse(false);
	}

	/** Sprint 11, tanda 1b (sección 3.14): el colegio está en PREPARACION (la guía de la puesta en marcha se muestra). */
	public boolean enPreparacion(Long colegioId) {
		return colegioId != null && colegios.findById(colegioId)
				.map(c -> c.getEstado() == pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio.PREPARACION)
				.orElse(false);
	}

	/** Sprint 8, tanda 1 (E9): la aplicación tampoco cobra en un colegio en preparación (en MySQL, trg_pago_registro). */
	public void exigirEnProduccion(Long colegioId) {
		if (!enProduccion(colegioId)) {
			throw new pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException("El colegio todavía no está en "
					+ "producción: no se registran pagos. La Promotoría lo pasa a producción desde «Puesta en marcha».");
		}
	}

	/**
	 * Correcciones del sprint 8 (S8-M2 y QA-S8-4): lo mismo, con el mensaje de quien lo pide (la familia que quiere pagar en
	 * línea, Administración que sube la recaudación o abre la renovación).
	 */
	public void exigirEnProduccion(Long colegioId, String mensaje) {
		if (!enProduccion(colegioId)) {
			throw new pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException(mensaje);
		}
	}

	/** La fila PLATAFORMA (V28): ahí viven los operadores. */
	public Colegio plataforma() {
		return colegios.findFirstByTipo(TipoColegio.PLATAFORMA)
				.orElseThrow(() -> new IllegalStateException("Falta el colegio de plataforma (migración V28)."));
	}

	public long plataformaId() {
		return plataforma().getId();
	}
}
