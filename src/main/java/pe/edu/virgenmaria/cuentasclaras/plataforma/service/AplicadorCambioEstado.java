package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AmbitoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.CambioEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.CambioEstadoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.SuspensionAplicada;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CierreSesionesColegio;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 11, tanda 1 (sección 3.3): aplica un cambio de estado APROBADO cuya fecha llegó, dentro de la transacción en
 * curso (colegio en contexto, {@code cc_sistema}): el colegio pasa al destino (o su facultad cambia), el cambio queda
 * APLICADO, se cierran las sesiones del personal si el colegio deja de operar y se avisa al colegio. Lo usan la aprobación
 * de los cambios inmediatos ({@link CambiosEstadoColegio}) y {@code sistema.plataforma} ({@code AplicacionCambiosEstado}).
 * Sin rol propio: quien lo llama ya lo exigió; en MySQL lo exigen trg_colegio_cambio y trg_cambio_estado_colegio_estado.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AplicadorCambioEstado {

	private final ColegioRepository colegios;

	private final CambioEstadoColegioRepository cambios;

	private final PrimeraPromotoriaRepository primeras;

	private final CierreSesionesColegio cierreSesiones;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	public AplicadorCambioEstado(ColegioRepository colegios, CambioEstadoColegioRepository cambios,
			PrimeraPromotoriaRepository primeras, CierreSesionesColegio cierreSesiones, AuditoriaService auditoria,
			ApplicationEventPublisher eventos) {
		this.colegios = colegios;
		this.cambios = cambios;
		this.primeras = primeras;
		this.cierreSesiones = cierreSesiones;
		this.auditoria = auditoria;
		this.eventos = eventos;
	}

	/** Aplica el cambio; devuelve las cuentas cuyas sesiones HTTP hay que expirar después de confirmar. */
	public List<Long> aplicar(CambioEstadoColegio cambio, LocalDateTime ahora) {
		Colegio colegio = colegios.findById(ContextoColegio.actual()).orElseThrow();
		List<Long> cerradas = List.of();
		AccionAuditoria accion;
		String texto;
		if (cambio.getAmbito() == AmbitoCambioEstado.FACULTAD) {
			boolean bloquear = cambio.getTipo() == TipoCambioEstado.BLOQUEO_FACULTAD;
			colegio.cambiarFacultad(bloquear, cambio.getId());
			colegios.saveAndFlush(colegio);
			accion = bloquear ? AccionAuditoria.FACULTAD_BLOQUEADA : AccionAuditoria.FACULTAD_DESBLOQUEADA;
			texto = bloquear ? "la facultad del titular quedó bloqueada (Promotoría y Dirección se piden con «Cambiar roles»)"
					: "la facultad del titular volvió a estar disponible";
		}
		else {
			colegio.aplicarCambioDeEstado(cambio.getId(), cambio.getEstadoOrigen(), cambio.getEstadoDestino(), ahora);
			colegios.saveAndFlush(colegio);
			switch (cambio.getTipo()) {
				case BAJA -> {
					accion = AccionAuditoria.COLEGIO_DADO_DE_BAJA;
					texto = "el colegio quedó dado de baja: nadie ingresa";
				}
				case CIERRE -> {
					accion = AccionAuditoria.COLEGIO_CERRADO;
					texto = "el colegio quedó cerrado: las familias consultan su estado de cuenta, sus comprobantes, sus "
							+ "libretas y sus comunicados hasta la fecha de supresión";
				}
				case SUSPENSION -> {
					accion = AccionAuditoria.COLEGIO_SUSPENDIDO;
					texto = "el acceso del personal quedó en pausa; las familias ven su estado de cuenta, sus comprobantes, "
							+ "sus libretas y sus comunicados";
				}
				default -> {
					accion = AccionAuditoria.COLEGIO_REACTIVADO;
					texto = "el colegio volvió a operar: el personal ingresa con su clave de siempre";
				}
			}
			if (!cambio.getEstadoDestino().opera()) {
				cerradas = cierreSesiones.cerrarPersonal();
			}
		}
		cambio.aplicar(ahora);
		cambios.saveAndFlush(cambio);
		if (cambio.getTipo() == TipoCambioEstado.SUSPENSION) {
			// Tanda 2 (3.9): tras el commit, sistema.exportacion genera la exportación de los datos del colegio.
			eventos.publishEvent(new SuspensionAplicada(colegio.getId(), cambio.getId()));
		}
		avisarAlColegio(colegio.getId(), AvisoPlataforma.Tipo.CAMBIO_ESTADO_APLICADO,
				List.of(texto, Calendario.formatear(ahora.toLocalDate())), cambio, "X");
		auditoria.registrar(accion, "colegio", colegio.getId().toString(), cambio.getEstadoOrigen().name(),
				cambio.getEstadoDestino().name(), "Se aplicó el cambio " + cambio.getId() + " ("
						+ CambiosEstadoColegio.queHace(cambio.getTipo()) + "), aprobado por dos operadores: " + texto + ".");
		return cerradas;
	}

	/** El aviso en la MISMA transacción: titular, Promotoría, correo institucional y correo de control. */
	public void avisarAlColegio(long colegioId, AvisoPlataforma.Tipo tipo, List<String> parametros,
			CambioEstadoColegio cambio, String sufijo) {
		Long titular = primeras.findFirstByIdNotNull().map(p -> p.getUsuarioId()).orElse(null);
		eventos.publishEvent(new AvisoPlataforma(colegioId, tipo, parametros, "cambio_estado_colegio", cambio.getId(),
				sufijo, titular, true, true, true));
	}
}
