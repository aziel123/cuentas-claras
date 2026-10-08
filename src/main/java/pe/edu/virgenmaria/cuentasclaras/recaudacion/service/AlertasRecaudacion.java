package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLinea;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLote;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LineaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LoteRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LineaRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LoteRecaudacionRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Alertas de la recaudación bancaria para «Para revisar» (sección 13 del diseño del sprint 4):
 * <ul>
 *   <li>CRÍTICA: archivo RECHAZADO por dos totales a ciegas distintos (últimos 30 días); líneas por revisar con la
 *       operación ya registrada en otro pago o en otra moneda.</li>
 *   <li>ATENCIÓN: líneas por revisar (código errado, sin deuda, exceso...); archivo cargado sin confirmar a las 12:00
 *       del día hábil siguiente; lote confirmado que el sistema no terminó de aplicar en 10 minutos.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
public class AlertasRecaudacion implements AlertasRevision {

	static final String MODULO = "Recaudación bancaria";

	static final String ENLACE = "/recaudacion";

	private static final LocalTime HORA_LIMITE = LocalTime.NOON;

	private final LoteRecaudacionRepository lotes;

	private final LineaRecaudacionRepository lineas;

	private final Clock reloj;

	private final CalendarioHabil calendario;

	public AlertasRecaudacion(LoteRecaudacionRepository lotes, LineaRecaudacionRepository lineas, Clock reloj, CalendarioHabil calendario) {
		this.calendario = calendario;
		this.lotes = lotes;
		this.lineas = lineas;
		this.reloj = reloj;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AlertaRevision> alertas() {
		List<AlertaRevision> alertas = new ArrayList<>();
		LocalDateTime ahora = LocalDateTime.now(reloj);
		List<LoteRecaudacion> rechazados = lotes.findByEstadoAndRechazadoEnAfterOrderByIdDesc(EstadoLote.RECHAZADO,
				ahora.minusDays(30));
		if (!rechazados.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, rechazados.size() + " archivo(s) de recaudación "
					+ "RECHAZADO(S) porque el total escrito a ciegas no coincidió dos veces con el archivo (el último lo subió "
					+ rechazados.getFirst().getCreadoPor() + "). No se aplicó ningún pago: revisa con el banco y con quien "
					+ "lo subió.", ENLACE));
		}
		List<LineaRecaudacion> porRevisar = lineas.findByEstadoOrderByIdAsc(EstadoLinea.EXCEPCION);
		long criticas = porRevisar.stream().filter(l -> l.getMotivoExcepcion().critico()).count();
		if (criticas > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, criticas + " pago(s) por banco con una operación ya "
					+ "registrada en otro pago o en otra moneda: revisa si alguien registró dos veces el mismo dinero.",
					ENLACE));
		}
		if (porRevisar.size() > criticas) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, (porRevisar.size() - criticas) + " pago(s) por banco "
					+ "por revisar (código errado, alumno sin deuda o monto de más). Administración pide aplicarlos o "
					+ "devolverlos.", ENLACE));
		}
		for (LoteRecaudacion lote : lotes.findByEstadoOrderByIdAsc(EstadoLote.CARGADO)) {
			LocalDateTime limite = calendario.siguienteDiaHabil(lote.getCreadoEn().toLocalDate()).atTime(HORA_LIMITE);
			if (ahora.isAfter(limite)) {
				alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "El archivo de recaudación del "
						+ Calendario.formatear(lote.getDesde()) + " al " + Calendario.formatear(lote.getHasta()) + " ("
						+ lote.getLineas() + " pagos) sigue sin confirmar: escribe a ciegas el total que ves en el banco.",
						ENLACE + "/lotes/" + lote.getId() + "/confirmar"));
			}
		}
		for (LoteRecaudacion lote : lotes.findByEstadoOrderByIdAsc(EstadoLote.CONFIRMADO)) {
			if (lote.getConfirmadoEn() != null && Duration.between(lote.getConfirmadoEn(), ahora).toMinutes() >= 10) {
				alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "El sistema no terminó de aplicar los pagos de la "
						+ "recaudación del " + Calendario.formatear(lote.getDesde()) + " (lote " + lote.getId() + "). Se "
						+ "reintenta cada 5 minutos; si sigue así, revisa con soporte.", ENLACE + "/lotes/" + lote.getId()));
			}
		}
		return alertas;
	}
}
