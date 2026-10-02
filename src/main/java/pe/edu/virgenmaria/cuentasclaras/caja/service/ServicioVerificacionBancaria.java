package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.config.PropiedadesCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.VerificacionRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.VistaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.DepositoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.VerificacionBancaria;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.DepositoCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.VerificacionBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Verificación bancaria mínima (sprint 3): Administración marca cada pago digital y cada depósito como «Encontrado» o
 * «No aparece» frente al estado de cuenta del banco (o de Yape/Plin). Nunca lo verifica quien cobró o depositó (también
 * lo exige un trigger en MySQL). «No aparece» exige una nota y es una alerta crítica para Promotoría: detecta el Yape
 * inventado para quedarse el efectivo, que el cierre de caja solo no ve. La conciliación automática llega en el sprint 6.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','ADMINISTRACION')")
public class ServicioVerificacionBancaria {

	private final PagoRepository pagos;

	private final DepositoCajaRepository depositos;

	private final VerificacionBancariaRepository verificaciones;

	private final AuditoriaService auditoria;

	private final NombresUsuarios nombres;

	private final PropiedadesCaja propiedades;

	private final Clock reloj;

	public ServicioVerificacionBancaria(PagoRepository pagos, DepositoCajaRepository depositos,
			VerificacionBancariaRepository verificaciones, AuditoriaService auditoria, NombresUsuarios nombres,
			PropiedadesCaja propiedades, Clock reloj) {
		this.pagos = pagos;
		this.depositos = depositos;
		this.verificaciones = verificaciones;
		this.auditoria = auditoria;
		this.nombres = nombres;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	public VistaConciliacion vista() {
		LocalDate hoy = LocalDate.now(reloj);
		int limite = propiedades.diasSinVerificar();
		List<VistaConciliacion.PagoPorVerificar> porVerificar = pagos.digitalesSinVerificar().stream().map(p -> {
			long dias = ChronoUnit.DAYS.between(p.getFecha(), hoy);
			return new VistaConciliacion.PagoPorVerificar(p.getId(), p.getFecha(), p.getMedio().etiqueta(),
					p.getNumeroOperacion(), p.getTotal(), p.getFamilia().getNombre(), nombres.de(p.getCajero()),
					p.getComprobante().numeroCompleto(), dias, dias >= limite && limite > 0);
		}).toList();
		List<VistaConciliacion.DepositoPorVerificar> depositosPorVerificar = depositos.sinVerificar().stream().map(d -> {
			long dias = ChronoUnit.DAYS.between(d.getFechaDeposito(), hoy);
			return new VistaConciliacion.DepositoPorVerificar(d.getId(), d.getCaja().getFecha(), d.getFechaDeposito(),
					d.getCuenta(), d.getNumeroOperacion(), d.getMonto(), d.getEsperado(), d.distinto(), d.getExplicacion(),
					nombres.de(d.getCaja().getCajero()), dias, dias >= limite && limite > 0);
		}).toList();
		List<VistaConciliacion.Verificado> recientes = verificaciones.findTop30ByOrderByIdDesc().stream()
				.map(v -> new VistaConciliacion.Verificado(que(v), v.getResultado().etiqueta(),
						v.getResultado().variante(), v.getNota(), v.getCreadoPor(), v.getCreadoEn()))
				.toList();
		return new VistaConciliacion(hoy, esAdministracion(), limite, porVerificar, depositosPorVerificar, recientes);
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void verificarPago(Long pagoId, VerificacionRequest pedido) {
		Pago pago = pagos.bloquear(pagoId).orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado"));
		if (verificaciones.existsByPagoId(pago.getId())) {
			throw new ReglaNegocioException("Ese pago ya se verificó.");
		}
		VerificacionBancaria v = verificaciones.save(VerificacionBancaria.dePago(pago, pedido.resultado(), pedido.nota(),
				SesionCaja.usuario()));
		boolean encontrado = v.getResultado() == ResultadoVerificacion.ENCONTRADO;
		auditoria.registrar(encontrado ? AccionAuditoria.PAGO_VERIFICADO : AccionAuditoria.PAGO_NO_ENCONTRADO_BANCO,
				"pago", pago.getId().toString(), null, v.getResultado().name(), pago.getMedio().etiqueta() + " "
						+ pago.getNumeroOperacion() + " por " + Dinero.formatear(pago.getTotal()) + " del "
						+ Calendario.formatear(pago.getFecha()) + " (" + pago.getComprobante().numeroCompleto()
						+ ", cobrado por " + pago.getCajero() + "): " + v.getResultado().etiqueta().toLowerCase()
						+ " en el banco." + (v.getNota() == null ? "" : " Nota: " + v.getNota()));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void verificarDeposito(Long depositoId, VerificacionRequest pedido) {
		DepositoCaja deposito = depositos.findById(depositoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Depósito no encontrado"));
		if (verificaciones.existsByDepositoId(deposito.getId())) {
			throw new ReglaNegocioException("Ese depósito ya se verificó.");
		}
		VerificacionBancaria v = verificaciones.save(VerificacionBancaria.deDeposito(deposito, pedido.resultado(),
				pedido.nota(), SesionCaja.usuario()));
		boolean encontrado = v.getResultado() == ResultadoVerificacion.ENCONTRADO;
		auditoria.registrar(encontrado ? AccionAuditoria.DEPOSITO_VERIFICADO : AccionAuditoria.DEPOSITO_NO_ENCONTRADO,
				"deposito_caja", deposito.getId().toString(), null, v.getResultado().name(), "Depósito de la caja de "
						+ deposito.getCaja().getCajero() + " del " + Calendario.formatear(deposito.getCaja().getFecha())
						+ " en " + deposito.getCuenta() + ", operación " + deposito.getNumeroOperacion() + " por "
						+ Dinero.formatear(deposito.getMonto()) + ": " + v.getResultado().etiqueta().toLowerCase()
						+ " en el banco." + (v.getNota() == null ? "" : " Nota: " + v.getNota()));
	}

	private static String que(VerificacionBancaria v) {
		if (v.getPago() != null) {
			Pago p = v.getPago();
			return p.getMedio().etiqueta() + " " + p.getNumeroOperacion() + " · " + Dinero.formatear(p.getTotal());
		}
		DepositoCaja d = v.getDeposito();
		return "Depósito " + d.getNumeroOperacion() + " · " + Dinero.formatear(d.getMonto());
	}

	private static boolean esAdministracion() {
		var autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion != null && autenticacion.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.anyMatch("ROLE_ADMINISTRACION"::equals);
	}
}
