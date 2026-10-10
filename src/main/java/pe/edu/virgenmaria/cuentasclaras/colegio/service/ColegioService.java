package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.LogoColegioRepository;
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

	public ColegioService(ColegioRepository colegios, LogoColegioRepository logos) {
		this.colegios = colegios;
		this.logos = logos;
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

	/** La fila PLATAFORMA (V28): ahí viven los operadores. */
	public Colegio plataforma() {
		return colegios.findFirstByTipo(TipoColegio.PLATAFORMA)
				.orElseThrow(() -> new IllegalStateException("Falta el colegio de plataforma (migración V28)."));
	}

	public long plataformaId() {
		return plataforma().getId();
	}
}
