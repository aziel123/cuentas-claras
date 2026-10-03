package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCierre;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CierreCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Revisión de un cierre de caja en la bandeja: TODOS los cierres los aprueba otra persona de Promotoría o Dirección
 * (nunca la cajera de esa caja, ni quien preparó su cuenta: {@link #involucrados}). Sin diferencia, con un clic; con
 * faltante o sobrante, aprobar exige un comentario (cómo se resolvió) y la tarjeta sale primera. Rechazar es
 * «Observar»: el cierre queda OBSERVADO con el comentario. El conteo no cambia nunca.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorCierreCaja implements ManejadorSolicitud {

	private final CierreCajaRepository cierres;

	private final AuditoriaService auditoria;

	private final NombresUsuarios nombres;

	private final Clock reloj;

	public ManejadorCierreCaja(CierreCajaRepository cierres, AuditoriaService auditoria, NombresUsuarios nombres,
			Clock reloj) {
		this.cierres = cierres;
		this.auditoria = auditoria;
		this.nombres = nombres;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.CIERRE_CAJA;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		aplicar(solicitud, aprobador, null);
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador, String comentario) {
		CierreCaja cierre = cierres.bloquear(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("El cierre ya no existe."));
		if (cierre.conDiferencia() && (comentario == null || comentario.isBlank())) {
			throw new ReglaNegocioException("Este cierre tiene " + ServicioCierreCaja.diferenciaTexto(cierre.getDiferencia())
					+ ": para aprobarlo escribe qué verificaste con la cajera y cómo se resolvió.");
		}
		if (cierre.isTrasReapertura() && (comentario == null || comentario.isBlank())) {
			throw new ReglaNegocioException("Es un cierre tras reapertura (no fue ciego): para aprobarlo escribe qué "
					+ "verificaste de los pagos agregados después de reabrir.");
		}
		cierre.aprobar(aprobador, ahora(), comentario);
		cierres.saveAndFlush(cierre);
		CajaDiaria caja = cierre.getCaja();
		auditoria.registrar(AccionAuditoria.CAJA_CIERRE_APROBADO, "cierre_caja", cierre.getId().toString(),
				EstadoCierre.POR_REVISAR.name(), EstadoCierre.APROBADO.name(), "Cierre N.° " + cierre.getNumero()
						+ " de la caja de " + caja.getCajero() + " del " + Calendario.formatear(caja.getFecha()) + ": "
						+ ServicioCierreCaja.diferenciaTexto(cierre.getDiferencia()) + ". Aprobado por " + aprobador
						+ (cierre.getComentarioRevision() == null ? "." : ". Comentario: " + cierre.getComentarioRevision()));
	}

	@Override
	public void alRechazar(SolicitudCambio solicitud) {
		CierreCaja cierre = cierres.bloquear(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("El cierre ya no existe."));
		cierre.observar(solicitud.getResueltoPor(), ahora(), solicitud.getComentario());
		cierres.saveAndFlush(cierre);
		CajaDiaria caja = cierre.getCaja();
		auditoria.registrar(AccionAuditoria.CAJA_CIERRE_OBSERVADO, "cierre_caja", cierre.getId().toString(),
				EstadoCierre.POR_REVISAR.name(), EstadoCierre.OBSERVADO.name(), "Cierre N.° " + cierre.getNumero()
						+ " de la caja de " + caja.getCajero() + " del " + Calendario.formatear(caja.getFecha()) + ": "
						+ ServicioCierreCaja.diferenciaTexto(cierre.getDiferencia()) + ". Observado por "
						+ solicitud.getResueltoPor() + ": " + solicitud.getComentario());
	}

	/** La cajera de la caja (aunque, en adelante, otra persona pida algo sobre su cierre). */
	@Override
	public Set<String> involucrados(SolicitudCambio solicitud) {
		return cierres.findById(solicitud.getEntidadId()).map(c -> Set.of(c.getCaja().getCajero())).orElse(Set.of());
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		CierreCaja c = cierres.findById(solicitud.getEntidadId()).orElse(null);
		if (c == null) {
			return List.of();
		}
		CajaDiaria caja = c.getCaja();
		List<String> lineas = new ArrayList<>();
		lineas.add("Caja de " + nombres.de(caja.getCajero()) + " (" + caja.getCajero() + ") del "
				+ Calendario.formatear(caja.getFecha()) + " · cierre N.° " + c.getNumero());
		lineas.add("Esperado " + Dinero.formatear(c.getEsperado()) + ": fondo " + Dinero.formatear(c.getFondoFijo())
				+ " + efectivo " + Dinero.formatear(c.getEfectivoCobrado()) + " en " + c.getPagosEfectivo() + " pago(s)");
		lineas.add("Contado " + Dinero.formatear(c.getContado()) + (c.huboReconteo() ? " al reconteo (primer conteo "
				+ Dinero.formatear(c.getPrimerConteo()) + ": no coincidió)" : " al primer conteo"));
		lineas.add("Diferencia: " + ServicioCierreCaja.diferenciaTexto(c.getDiferencia()));
		lineas.add("Digital (no entra al esperado): " + Dinero.formatear(c.getTotalDigital()) + " en "
				+ c.getPagosDigitales() + " pago(s)");
		if (c.getExplicacion() != null) {
			lineas.add("Explicación de la cajera: " + c.getExplicacion());
		}
		if (c.getDenominaciones() != null) {
			lineas.add("Billetes y monedas: " + c.getDenominaciones());
		}
		lineas.add("Detalle de la caja en Aprobaciones › Cajas del día");
		return lineas;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		CierreCaja cierre = cierres.findById(solicitud.getEntidadId()).orElse(null);
		if (cierre != null && cierre.isTrasReapertura() && !cierre.conDiferencia()) {
			return "Cierre tras reapertura: no fue ciego (la cajera ya había visto el esperado del cierre anterior). "
					+ "Revisa los pagos agregados después de reabrir y escribe tu comentario.";
		}
		return java.util.Optional.ofNullable(cierre).filter(CierreCaja::conDiferencia)
				.map(c -> (c.isTrasReapertura() ? "Cierre tras reapertura (no fue ciego). " : "") + (c.getDiferencia().signum() < 0
						? "Faltante de " + Dinero.formatear(c.getDiferencia().abs()) + ": habla con la cajera antes de "
								+ "aprobar y anota cómo se resolvió."
						: "Sobrante de " + Dinero.formatear(c.getDiferencia()) + ": puede ser un cobro sin registrar. "
								+ "Revisa los pagos de la caja antes de aprobar."))
				.orElse(null);
	}

	/** Un cierre con diferencia va primero en la bandeja. */
	@Override
	public int prioridad(SolicitudCambio solicitud) {
		return exigeComentario(solicitud) ? 0 : 2;
	}

	@Override
	public boolean exigeComentario(SolicitudCambio solicitud) {
		return cierres.findById(solicitud.getEntidadId()).filter(c -> c.conDiferencia() || c.isTrasReapertura())
				.isPresent();
	}

	@Override
	public String accionRechazo() {
		return "Observar";
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
