package pe.edu.virgenmaria.cuentasclaras.familias.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.familias.model.AvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.model.EstadoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.repository.AvisoFamiliaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Alertas de los avisos de las familias para «Para revisar» de Promotoría (sprint 5, sección 13): «Pagué y no aparece» o
 * «No reconozco…» abiertos son CRÍTICOS (G1: es el fraude original); «Otro», ATENCIÓN.
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
@Transactional(readOnly = true)
public class AlertasFamilias implements AlertasRevision {

	static final String MODULO = "Familias";

	private final AvisoFamiliaRepository avisos;

	public AlertasFamilias(AvisoFamiliaRepository avisos) {
		this.avisos = avisos;
	}

	@Override
	public List<AlertaRevision> alertas() {
		List<AvisoFamilia> abiertos = avisos.findByEstadoOrderByIdAsc(EstadoAvisoFamilia.ABIERTO);
		long criticos = abiertos.stream().filter(a -> a.getTipo().critico()).count();
		long otros = abiertos.size() - criticos;
		List<AlertaRevision> alertas = new ArrayList<>();
		if (criticos > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, criticos + " familia(s) avisan que pagaron y no "
					+ "aparece, o que no reconocen un pago, una anulación o un descuento. Revísalo hoy.", "/avisos-familias"));
		}
		if (otros > 0) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, otros + " aviso(s) de familias por responder.",
					"/avisos-familias"));
		}
		return alertas;
	}
}
