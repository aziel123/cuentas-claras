package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ConsultaCuotasMatricula;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;

/**
 * Implementa el puerto de {@code alumnos}: una matrícula con cuotas (generadas o de saldo inicial) no se mueve a
 * otro nivel, porque cambiaría su pensión.
 */
@Component
public class ConsultaCuotasMatriculaJpa implements ConsultaCuotasMatricula {

	private final CuotaRepository cuotas;

	public ConsultaCuotasMatriculaJpa(CuotaRepository cuotas) {
		this.cuotas = cuotas;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean tieneCuotas(Long matriculaId) {
		return cuotas.existsByMatriculaId(matriculaId);
	}
}
