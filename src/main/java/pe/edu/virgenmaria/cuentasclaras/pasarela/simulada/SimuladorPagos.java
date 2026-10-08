package pe.edu.virgenmaria.cuentasclaras.pasarela.simulada;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.pasarela.proceso.RecepcionAvisos;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;

/**
 * El «celular» del apoderado en la pasarela simulada (solo dev, test y piloto): paga con Yape o tarjeta simulados,
 * rechaza o paga S/ 1.00 menos, y la pasarela envía su aviso FIRMADO al mismo punto de entrada que una real
 * ({@link RecepcionAvisos}): desde ahí, todo es igual que con una pasarela real (firma, bandeja idempotente y consulta).
 */
@Service
@Profile({ "dev", "test", "piloto" })
@ConditionalOnProperty(name = "cuentasclaras.pasarela.proveedor", havingValue = "SIMULADA")
@PreAuthorize("hasRole('APODERADO')")
public class SimuladorPagos {

	private final PasarelaSimulada pasarela;

	private final ServicioPagoEnLinea pagos;

	private final RecepcionAvisos recepcion;

	public SimuladorPagos(PasarelaSimulada pasarela, ServicioPagoEnLinea pagos, RecepcionAvisos recepcion) {
		this.pasarela = pasarela;
		this.pagos = pagos;
		this.recepcion = recepcion;
	}

	/** Solo una orden de SU familia (si no, 404). */
	public RecepcionAvisos.Resultado simular(String referencia, PasarelaSimulada.Accion accion) {
		String idEnPasarela = pagos.idEnPasarela(referencia);
		PasarelaSimulada.AvisoFirmado aviso = pasarela.simular(idEnPasarela, accion);
		return recepcion.recibir("SIMULADA", ContextoColegio.actual(), aviso.cuerpo(), aviso.cabeceras());
	}
}
