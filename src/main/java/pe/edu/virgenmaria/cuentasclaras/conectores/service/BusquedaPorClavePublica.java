package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;

import java.util.Optional;

/**
 * Sprint 8, tanda 2 (sección 3.6, E15): el aviso de una pasarela llega a {@code /webhooks/pasarela/{clave_publica}} sin
 * sesión ni colegio; la clave pública (un UUID opaco, único en toda la base) dice de qué conector y de qué colegio es.
 * Es la ÚNICA búsqueda de conectores en todos los colegios ({@code ContextoColegio.comoSistema}, clase autorizada en
 * {@code ReglasArquitecturaTest}): devuelve un solo conector por su clave exacta, y quien la llama valida después la
 * firma del aviso con el secreto de ESE conector.
 */
final class BusquedaPorClavePublica {

	private BusquedaPorClavePublica() {
	}

	static Optional<ConectorColegio> buscar(ConectorColegioRepository conectores, String clavePublica) {
		return ContextoColegio.comoSistema(() -> conectores.findByClavePublica(clavePublica));
	}
}
