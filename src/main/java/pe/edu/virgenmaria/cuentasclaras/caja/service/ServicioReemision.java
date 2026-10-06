package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.DocumentoReceptor;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Receptor;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.ComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.AperturaSerie;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.ServicioComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Reemite un comprobante RECHAZADO por el OSE (sprint 4): mismo tipo, total y líneas, con un número NUEVO de la serie
 * vigente; el rechazado se queda con su número (la serie sigue sin huecos) y solo se reemite una vez (UNIQUE y trigger).
 * El receptor es el ACTUAL: el dato errado (por ejemplo, el documento del apoderado) se corrige antes con su flujo de
 * aprobación. Lo hace Administración y queda resaltado en la bitácora.
 */
@Service
@PreAuthorize("hasRole('ADMINISTRACION')")
public class ServicioReemision {

	private final ComprobanteRepository comprobantes;

	private final PagoRepository pagos;

	private final AplicacionPagoRepository aplicaciones;

	private final CuotaRepository cuotas;

	private final ApoderadoRepository apoderados;

	private final ServicioComprobantes servicio;

	private final AperturaSerie aperturaSerie;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	private final org.springframework.transaction.support.TransactionTemplate transaccion;

	public ServicioReemision(ComprobanteRepository comprobantes, PagoRepository pagos, AplicacionPagoRepository aplicaciones,
			CuotaRepository cuotas, ApoderadoRepository apoderados, ServicioComprobantes servicio, AperturaSerie aperturaSerie,
			AuditoriaService auditoria, Clock reloj,
			org.springframework.transaction.PlatformTransactionManager transacciones) {
		this.transaccion = new org.springframework.transaction.support.TransactionTemplate(transacciones);
		this.comprobantes = comprobantes;
		this.pagos = pagos;
		this.aplicaciones = aplicaciones;
		this.cuotas = cuotas;
		this.apoderados = apoderados;
		this.servicio = servicio;
		this.aperturaSerie = aperturaSerie;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** @return el id del comprobante nuevo */
	public Long reemitir(Long comprobanteId, String motivo) {
		String texto = Motivo.exigir(motivo);
		try {
			aperturaSerie.crearSiFalta(TipoComprobante.BOLETA);
			aperturaSerie.crearSiFalta(TipoComprobante.FACTURA);
		}
		catch (DataIntegrityViolationException otroLaCreo) {
			// Otra operación creó la serie a la vez: se usa esa.
		}
		return transaccion.execute(t -> reemitirEnTransaccion(comprobanteId, texto));
	}

	private Long reemitirEnTransaccion(Long comprobanteId, String motivo) {
		Comprobante rechazado = comprobantes.findById(comprobanteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Comprobante no encontrado"));
		if (rechazado.getEstadoEnvio() != EstadoEnvio.RECHAZADO) {
			throw new ReglaNegocioException("Solo se reemite un comprobante que el OSE rechazó.");
		}
		if (comprobantes.existsByReemplazaId(rechazado.getId())) {
			throw new ReglaNegocioException("Este comprobante ya se reemitió.");
		}
		Pago pago = pagoDe(rechazado);
		Receptor receptor = receptorActual(rechazado, pago);
		Comprobante nuevo = servicio.reemitir(rechazado, receptor, LocalDate.now(reloj));
		auditoria.registrar(AccionAuditoria.COMPROBANTE_REEMITIDO, "comprobante", nuevo.getId().toString(),
				rechazado.numeroCompleto() + " RECHAZADO", nuevo.numeroCompleto() + " PENDIENTE",
				rechazado.getTipo().etiqueta() + " " + rechazado.numeroCompleto() + " (rechazado"
						+ (rechazado.getCodigoRespuesta() == null ? "" : ", código " + rechazado.getCodigoRespuesta())
						+ ") se reemite como " + nuevo.numeroCompleto() + " por " + Dinero.formatear(nuevo.getTotal())
						+ " a nombre de " + receptor.nombre() + " (" + receptor.documentoEnmascarado() + ")"
						+ (pago == null ? "" : " del pago " + pago.getId()) + ". Motivo: " + motivo);
		return nuevo.getId();
	}

	/** El pago del comprobante (o del comprobante que reemitió, si es una reemisión de una reemisión). */
	private Pago pagoDe(Comprobante comprobante) {
		Comprobante actual = comprobante;
		for (int i = 0; i < 20 && actual != null; i++) {
			Pago pago = pagos.findByComprobanteId(actual.getId()).orElse(null);
			if (pago != null) {
				return pago;
			}
			actual = actual.getReemplazaId() == null ? null : comprobantes.findById(actual.getReemplazaId()).orElse(null);
		}
		return null;
	}

	/**
	 * Boleta: el responsable de pago ACTUAL del alumno de la cuota que vence primero (como en caja). Factura: el RUC
	 * registrado (y aprobado) de un apoderado activo de la familia, el mismo si sigue registrado.
	 */
	private Receptor receptorActual(Comprobante rechazado, Pago pago) {
		if (pago == null) {
			throw new ReglaNegocioException("El comprobante no tiene pago: no se puede reemitir. Revísalo con soporte.");
		}
		Long familiaId = pago.getFamilia().getId();
		if (rechazado.getTipo() == TipoComprobante.FACTURA) {
			List<Apoderado> conRuc = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familiaId).stream()
					.filter(a -> a.isActivo() && a.getRuc() != null).toList();
			Apoderado elegido = conRuc.stream().filter(a -> a.getRuc().equals(rechazado.getReceptor().numero())).findFirst()
					.orElse(conRuc.size() == 1 ? conRuc.getFirst() : null);
			if (elegido == null) {
				throw new ReglaNegocioException("La familia no tiene un RUC registrado y aprobado para la factura. Regístralo "
						+ "primero con su aprobación.");
			}
			return Receptor.de(DocumentoReceptor.RUC, elegido.getRuc(), elegido.getRazonSocial());
		}
		List<Long> ids = aplicaciones.cuotasDePago(pago.getId());
		Cuota primera = ids.stream().map(id -> cuotas.findById(id).orElse(null)).filter(Objects::nonNull)
				.min(java.util.Comparator.comparing(Cuota::getFechaVencimiento).thenComparing(Cuota::getId))
				.orElseThrow(() -> new ReglaNegocioException("El pago no tiene cuotas aplicadas: revísalo con soporte."));
		Apoderado responsable = primera.getAlumno().getResponsablePago();
		return Receptor.de(LibroPagos.documentoDe(responsable), responsable.getDocumento().numero(),
				responsable.nombreCompleto());
	}
}
