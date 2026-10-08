package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.CuentaRequest;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.CuentaVista;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CuentaBancaria;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ExtractoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.CuentaBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.ExtractoBancarioRepository;

import java.util.List;

/**
 * Cuentas del colegio cuyo extracto se concilia (sprint 4, tanda 3). Las registra y desactiva solo Promotoría (una cuenta
 * falsa escondería los abonos reales); Dirección y Administración las ven. El número no cambia: si cambia la cuenta, se
 * desactiva y se registra otra. Todo queda resaltado en la bitácora.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioCuentasBancarias {

	private final CuentaBancariaRepository cuentas;

	private final ExtractoBancarioRepository extractos;

	private final AuditoriaService auditoria;

	public ServicioCuentasBancarias(CuentaBancariaRepository cuentas, ExtractoBancarioRepository extractos,
			AuditoriaService auditoria) {
		this.cuentas = cuentas;
		this.extractos = extractos;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<CuentaVista> lista() {
		return cuentas.findAllByOrderByActivaDescIdAsc().stream().map(c -> {
			List<ExtractoBancario> cadena = extractos.findByCuentaIdAndSecuenciaVigenteIsNotNullOrderBySecuenciaAsc(c.getId());
			return new CuentaVista(c.getId(), c.getBanco().etiqueta(), c.getNumero(), c.getAlias(), c.isActiva(),
					cadena.isEmpty() ? null : cadena.getLast().getHasta(),
					cadena.stream().filter(e -> e.getEstado() == EstadoExtracto.CONFIRMADO).map(ExtractoBancario::getHasta)
							.reduce((a, b) -> b).orElse(null),
					(int) cadena.stream().filter(e -> e.getEstado() == EstadoExtracto.CARGADO).count());
		}).toList();
	}

	@Transactional
	@PreAuthorize("hasRole('PROMOTOR')")
	public Long registrar(CuentaRequest pedido) {
		CuentaBancaria cuenta = CuentaBancaria.registrar(pedido.banco(), pedido.numero(), pedido.alias());
		if (cuentas.existsByBancoAndNumero(cuenta.getBanco(), cuenta.getNumero())) {
			throw new ReglaNegocioException("Esa cuenta ya está registrada.");
		}
		CuentaBancaria guardada = cuentas.save(cuenta);
		auditoria.registrar(AccionAuditoria.CUENTA_BANCARIA_REGISTRADA, "cuenta_bancaria", guardada.getId().toString(),
				null, guardada.descripcion(), "Registró la cuenta " + guardada.descripcion() + " (soles) para conciliar su "
						+ "extracto. Desde ahora Administración sube su extracto cada día y Promotoría o Dirección lo "
						+ "confirman a ciegas.");
		return guardada.getId();
	}

	@Transactional
	@PreAuthorize("hasRole('PROMOTOR')")
	public void desactivar(Long cuentaId, String motivo) {
		CuentaBancaria cuenta = cuentas.bloquear(cuentaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada"));
		String texto = Motivo.exigir(motivo);
		cuenta.desactivar();
		auditoria.registrar(AccionAuditoria.CUENTA_BANCARIA_DESACTIVADA, "cuenta_bancaria", cuentaId.toString(), "ACTIVA",
				"DESACTIVADA", "Desactivó la cuenta " + cuenta.descripcion() + ": ya no se suben ni se concilian sus "
						+ "extractos. Motivo: " + texto);
	}
}
