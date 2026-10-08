package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.MovimientoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ReglaPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.PartidaConciliacionRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Aprobación de {@code PARTIDA_MANUAL} (S4-C1): una pareja del extracto elegida a mano por Administración (siempre del
 * mismo monto) solo verifica en el banco cuando otra persona de Promotoría o Dirección la aprueba en la bandeja. Al
 * aprobarse queda CONFIRMADA por quien aprueba (que no cobró, registró, depositó ni subió lo emparejado) y el sistema
 * deja la verificación automática después del commit. Si se rechaza, la pareja se descarta y todo sigue en rojo. En
 * MySQL, {@code trg_partida_conciliacion_estado} exige esta solicitud APROBADA por quien confirma.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorPartidaManual implements ManejadorSolicitud {

	public static final String ENTIDAD = "partida_conciliacion";

	private final PartidaConciliacionRepository partidas;

	private final ResponsablesPartida responsables;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public ManejadorPartidaManual(PartidaConciliacionRepository partidas, ResponsablesPartida responsables,
			AuditoriaService auditoria, ApplicationEventPublisher eventos, Clock reloj) {
		this.partidas = partidas;
		this.responsables = responsables;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.PARTIDA_MANUAL;
	}

	@Override
	public String entidad() {
		return ENTIDAD;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		PartidaConciliacion partida = partidas.bloquear(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("La pareja de la solicitud no existe."));
		if (partida.getEstado() != EstadoPartida.PROPUESTA || partida.getRegla() != ReglaPartida.MANUAL) {
			throw new ReglaNegocioException("Esta pareja ya se resolvió: recházala.");
		}
		MovimientoBancario m = partida.getMovimiento();
		if (m.getExtracto().getEstado() != EstadoExtracto.CONFIRMADO) {
			throw new ReglaNegocioException("El extracto de este movimiento ya no está confirmado: recházala.");
		}
		if (partida.getDiferencia().signum() != 0 && partida.getObjetoTipo()
				!= pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida.LIQUIDACION) {
			throw new ReglaNegocioException("Los montos no coinciden: recházala.");
		}
		partida.confirmar(aprobador, LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		partidas.saveAndFlush(partida);
		auditoria.registrar(AccionAuditoria.PARTIDA_MANUAL_APROBADA, ENTIDAD, partida.getId().toString(),
				EstadoPartida.PROPUESTA.name(), EstadoPartida.CONFIRMADA.name(), aprobador + " aprobó la pareja manual del "
						+ m.getTipo().etiqueta().toLowerCase(Locale.ROOT) + " del " + Calendario.formatear(m.getFecha())
						+ " por " + Dinero.formatear(m.getMonto()) + " con " + partida.getObjetoTipo().etiqueta()
								.toLowerCase(Locale.ROOT) + " " + partida.objetoId() + " (pedida por "
						+ solicitud.getSolicitadoPor() + "). Nota: " + partida.getNota());
		eventos.publishEvent(new PartidaConfirmada(partida.getColegioId(), partida.getId()));
	}

	@Override
	public void alRechazar(SolicitudCambio solicitud) {
		partidas.bloquear(solicitud.getEntidadId()).filter(p -> p.getEstado() == EstadoPartida.PROPUESTA)
				.ifPresent(p -> {
					p.descartar(solicitud.getResueltoPor(), LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
					partidas.saveAndFlush(p);
				});
	}

	/** Quien cobró, registró, depositó o subió lo emparejado tampoco la aprueba. */
	@Override
	public Set<String> involucrados(SolicitudCambio solicitud) {
		return partidas.findById(solicitud.getEntidadId())
				.map(p -> responsables.de(p.getObjetoTipo(), p.objetoId())).orElse(Set.of());
	}

	@Override
	public int prioridad(SolicitudCambio solicitud) {
		return 1;
	}

	@Override
	public boolean exigeComentario(SolicitudCambio solicitud) {
		return true;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "Antes de aprobar, busca este movimiento en tu app del banco y confirma que es ESE pago o depósito (misma "
				+ "operación o el mismo pagador). Si no lo es, recházala: lo registrado seguirá en rojo.";
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		PartidaConciliacion p = partidas.findById(solicitud.getEntidadId()).orElse(null);
		if (p == null) {
			return List.of();
		}
		MovimientoBancario m = p.getMovimiento();
		List<String> texto = new ArrayList<>();
		texto.add("Banco: " + m.getTipo().etiqueta().toLowerCase(Locale.ROOT) + " del " + Calendario.formatear(m.getFecha())
				+ " por " + Dinero.formatear(m.getMonto()) + " · «" + m.getDescripcion() + "» · operación "
				+ (m.getNumeroOperacion() == null ? "sin número" : m.getNumeroOperacion()));
		texto.add("Registrado: " + p.getObjetoTipo().etiqueta() + " " + p.objetoId() + " por "
				+ Dinero.formatear(p.getMontoObjeto()));
		texto.add("Nota de quien lo pidió: " + p.getNota());
		return texto;
	}
}
