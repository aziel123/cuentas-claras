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

	private final pe.edu.virgenmaria.cuentasclaras.pasarela.service.Pasarelas pasarelas;

	public SimuladorPagos(PasarelaSimulada pasarela, ServicioPagoEnLinea pagos, RecepcionAvisos recepcion,
			pe.edu.virgenmaria.cuentasclaras.pasarela.service.Pasarelas pasarelas) {
		this.pasarela = pasarela;
		this.pagos = pagos;
		this.recepcion = recepcion;
		this.pasarelas = pasarelas;
	}

	/** Solo una orden de SU familia (si no, 404). */
	public RecepcionAvisos.Resultado simular(String referencia, PasarelaSimulada.Accion accion) {
		String idEnPasarela = pagos.idEnPasarela(referencia);
		PasarelaSimulada.AvisoFirmado aviso = pasarela.simular(idEnPasarela, accion);
		// Sprint 8, tanda 2 (E15): con un conector de pasarela vigente, la «pasarela» firma con SU secreto de avisos y envía
		// el aviso a la ruta de SU clave pública, como lo haría la pasarela real configurada por el colegio.
		var conector = pasarelas.conectorVigente();
		if (conector.isPresent()) {
			var firmado = pasarela.firmadoCon(aviso, conector.get().secreto(
					pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente.SECRETO_AVISO));
			return recepcion.recibirPorClave(conector.get().clavePublica(), firmado.cuerpo(), firmado.cabeceras());
		}
		return recepcion.recibir("SIMULADA", ContextoColegio.actual(), aviso.cuerpo(), aviso.cabeceras());
	}
}
