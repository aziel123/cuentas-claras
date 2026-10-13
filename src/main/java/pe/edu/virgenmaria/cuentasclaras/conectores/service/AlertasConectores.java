package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.conectores.config.PropiedadesConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sprint 8, tanda 2 (sección 3.6, E12): en «Para revisar» de Promotoría, una alerta CRÍTICA por cada conector que entró
 * en vigencia en los últimos días ({@code cuentasclaras.conectores.aviso-cambio-dias}, 7 por defecto): quién lo propuso,
 * quién lo aprobó y cuándo, sin secretos ni el número completo de la cuenta. Sale también una vez al celular de la
 * Promotoría (además del mensaje del cambio). Si alguien desvió el dinero con colusión, la Promotoría lo ve cada día de
 * esa semana; y la conciliación muestra en rojo lo que no llega al extracto de la cuenta del colegio.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
@Transactional(readOnly = true)
public class AlertasConectores implements AlertasRevision {

	static final String MODULO = "Conectores";

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final ConectorColegioRepository conectores;

	private final PropiedadesConectores propiedades;

	private final Clock reloj;

	public AlertasConectores(ConectorColegioRepository conectores, PropiedadesConectores propiedades, Clock reloj) {
		this.conectores = conectores;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	@Override
	public List<AlertaRevision> alertas() {
		LocalDateTime desde = LocalDateTime.now(reloj).minusDays(propiedades.avisoCambioDias());
		return conectores.findByResueltoEnGreaterThanEqualAndEstadoInOrderByResueltoEnDesc(desde,
				List.of(EstadoConector.VIGENTE, EstadoConector.REEMPLAZADO)).stream().map(this::alerta).toList();
	}

	private AlertaRevision alerta(ConectorColegio c) {
		return new AlertaRevision(Gravedad.CRITICA, MODULO, "Cambió " + c.getTipo().descripcion() + " ("
				+ c.getProveedor().etiqueta() + ") el " + FECHA.format(c.getResueltoEn()) + ": lo propuso "
				+ c.getPropuestoPor() + " y lo aprobó " + c.getResueltoPor() + ". Si no lo reconoces, avisa hoy al "
				+ "contador y revisa la conciliación.", "/conectores",
				new Aviso(TipoAviso.CONECTOR_CAMBIADO, "CON:" + c.getId()));
	}
}
