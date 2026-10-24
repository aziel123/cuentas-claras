package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.CambioEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.CambioEstadoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Sprint 11, tanda 1 (secciones 3.4 y 3.8): la franja ámbar que ve todo el personal del colegio cuando hay una baja o una
 * suspensión aprobada (con su fecha), y la que ven las familias cuando el colegio está en modo consulta. Solo lee: el
 * colegio de la sesión ({@code @TenantId}) y sus cambios pendientes. No dice el motivo (es del titular y los operadores).
 */
@Service
@Transactional(readOnly = true)
public class EstadoDelColegio {

	private final ColegioRepository colegios;

	private final CambioEstadoColegioRepository cambios;

	public EstadoDelColegio(ColegioRepository colegios, CambioEstadoColegioRepository cambios) {
		this.colegios = colegios;
		this.cambios = cambios;
	}

	/** La franja del colegio (vacía si opera sin cambios aprobados). {@code familia}: quien mira es una familia. */
	public Optional<String> franja(Long colegioId, boolean familia) {
		if (colegioId == null) {
			return Optional.empty();
		}
		Colegio colegio = colegios.findById(colegioId).orElse(null);
		if (colegio == null || colegio.esPlataforma()) {
			return Optional.empty();
		}
		if (colegio.getEstado().consultaFamilias()) {
			return Optional.of(familia
					? "El portal está en modo consulta: puedes ver tu estado de cuenta, tus boletas, las libretas y los "
							+ "comunicados. Por ahora no se puede pagar en línea ni cambiar datos desde aquí."
					: "El acceso del personal del colegio está en pausa.");
		}
		if (familia) {
			return Optional.empty();
		}
		return cambios.findByEstadoInOrderByIdAsc(EnumSet.of(EstadoCambioEstado.APROBADO)).stream()
				.filter(c -> c.getTipo() == TipoCambioEstado.SUSPENSION || c.getTipo() == TipoCambioEstado.BAJA)
				.findFirst().map(EstadoDelColegio::texto);
	}

	private static String texto(CambioEstadoColegio c) {
		String fecha = c.getEfectivaDesde() == null ? "su fecha" : Calendario.formatear(c.getEfectivaDesde().toLocalDate());
		return c.getTipo() == TipoCambioEstado.SUSPENSION
				? "Desde el " + fecha + " el acceso del personal quedará en pausa; las familias seguirán viendo su estado de "
						+ "cuenta, sus boletas, las libretas y los comunicados. Si tienes dudas, habla con la Promotoría."
				: "Desde el " + fecha + " el colegio quedará dado de baja y nadie podrá ingresar. Si tienes dudas, habla con "
						+ "la Promotoría.";
	}
}
