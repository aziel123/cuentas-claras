package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;

/**
 * Datos del colegio para mostrar en pantalla.
 */
@Service
@Transactional(readOnly = true)
public class ColegioService {

	/** Nombre que se muestra si todavía no hay colegio configurado. */
	public static final String NOMBRE_PLATAFORMA = "Cuentas Claras";

	private final ColegioRepository colegios;

	public ColegioService(ColegioRepository colegios) {
		this.colegios = colegios;
	}

	/** Nombre del colegio del usuario. */
	public String nombreDe(Long colegioId) {
		if (colegioId == null) {
			return NOMBRE_PLATAFORMA;
		}
		return colegios.findById(colegioId).map(Colegio::getNombre).orElse(NOMBRE_PLATAFORMA);
	}

	/** Nombre para las páginas públicas (login y errores): el primer colegio activo. */
	public String nombreInstitucional() {
		return colegios.findByActivoTrueOrderByIdAsc().stream()
				.findFirst()
				.map(Colegio::getNombre)
				.orElse(NOMBRE_PLATAFORMA);
	}
}
