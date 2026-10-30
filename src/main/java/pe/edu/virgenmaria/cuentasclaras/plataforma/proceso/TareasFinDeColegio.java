package pe.edu.virgenmaria.cuentasclaras.plataforma.proceso;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.ResultadoVerificacion;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorIntegridadAuditoria;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.CadenaDelActa;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.MotivoExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.proceso.GeneracionExportaciones;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.EntregaExportacion;

/**
 * Sprint 11, tanda 2 (3.9 a 3.11): lo que un operador pide y hace un actor de sistema, sin persona detrás: generar la
 * exportación (la de cierre o la que el operador pidió por escrito), volver a mandar el enlace de la entrega
 * ({@code sistema.exportacion}) y la verificación completa de la cadena antes de ejecutar un acta
 * ({@code sistema.auditoria}). Quien lo pidió queda en la bitácora de la plataforma ({@code FinDeColegio}).
 */
@Component
public class TareasFinDeColegio {

	private final GeneracionExportaciones generacion;

	private final EntregaExportacion entrega;

	private final VerificadorIntegridadAuditoria verificador;

	private final CadenaDelActa cadena;

	public TareasFinDeColegio(GeneracionExportaciones generacion, EntregaExportacion entrega,
			VerificadorIntegridadAuditoria verificador, CadenaDelActa cadena) {
		this.generacion = generacion;
		this.entrega = entrega;
		this.verificador = verificador;
		this.cadena = cadena;
	}

	/** La exportación de cierre (colegio SUSPENDIDO): devuelve su id. */
	public Long exportarParaCierre(long colegioId, Long cambioSuspensionId) {
		return generacion.exportar(colegioId, MotivoExportacion.CIERRE, cambioSuspensionId);
	}

	/** Genera la exportación PEDIDO que ya creó el operador con su firma. */
	public void generarPedido(long colegioId, Long exportacionId) {
		generacion.generar(colegioId, exportacionId);
	}

	public boolean reenviarEntrega(long colegioId, Long exportacionId) {
		return EjecucionComoSistema.como(ActorSistema.EXPORTACION, colegioId, () -> entrega.reenviar(exportacionId));
	}

	/** AP2-8: verifica la cadena completa y, si sale íntegra, lo anota en el acta. */
	public ResultadoVerificacion verificarCadena(long colegioId, Long actaId) {
		ResultadoVerificacion resultado = EjecucionComoSistema.como(ActorSistema.AUDITORIA, colegioId,
				verificador::verificarComoSistema);
		if (!resultado.integra()) {
			throw new ReglaNegocioException("La cadena de la bitácora no salió íntegra: no se ejecuta el acta. "
					+ resultado.detalle());
		}
		EjecucionComoSistema.como(ActorSistema.AUDITORIA, colegioId, () -> cadena.anotar(actaId, resultado.detalle()));
		return resultado;
	}
}
