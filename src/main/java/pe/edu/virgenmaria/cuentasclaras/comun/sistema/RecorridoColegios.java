package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Recorre los colegios activos para las tareas programadas: cada colegio con {@link ContextoColegio#en} y como el
 * actor de sistema (nunca con {@code comoSistema}, que vería todo). La falla de un colegio se registra en el log y no
 * corta a los demás. Lo usan solo los procesos ({@code ..proceso..}).
 * <p>
 * Sprint 8, tanda 1 (sección 3.4, E31): {@link #activos()} son solo los colegios clientes (tipo COLEGIO: nunca la
 * plataforma); {@link #enProduccion()} son los que ya cobran (los recordatorios, el resumen diario, las alertas y las
 * llamadas de control no se mandan a un colegio en preparación); {@link #conPlataforma()} suma el colegio de plataforma,
 * solo para el despacho de mensajes (los enlaces de activación de los operadores salen por ahí).
 * <p>
 * Sprint 11, tanda 1 (H2): {@link #activos()} ya no son «todos los colegios clientes»: son los que operan (PREPARACION y
 * PRODUCCION). Un colegio SUSPENDIDO o dado de baja no recibe la apertura de periodos, las libretas, la muestra de caja ni
 * los recordatorios. {@link #conBitacora()} suma los SUSPENDIDO, para la huella diaria y el resumen al correo de control.
 * El despacho ({@link #conPlataforma()}) recorre todos menos los SUPRIMIDO: los avisos de la baja o de la suspensión, y
 * el enlace de acceso de una familia en modo consulta, también salen.
 */
@Component
public class RecorridoColegios {

	private static final Logger LOG = LoggerFactory.getLogger(RecorridoColegios.class);

	private final ColegioRepository colegios;

	public RecorridoColegios(ColegioRepository colegios) {
		this.colegios = colegios;
	}

	/** Los colegios clientes que operan (en preparación o en producción), nunca la plataforma. */
	public List<Long> activos() {
		return colegios.findByTipoAndActivoTrueOrderByIdAsc(TipoColegio.COLEGIO).stream()
				.filter(c -> c.getEstado().opera()).map(Colegio::getId).toList();
	}

	/** Sprint 11, tanda 1 (H2): los que operan y los SUSPENDIDO (la huella diaria y el resumen al correo de control). */
	public List<Long> conBitacora() {
		return colegios.findByTipoAndActivoTrueOrderByIdAsc(TipoColegio.COLEGIO).stream()
				.filter(c -> c.getEstado().opera() || c.getEstado() == EstadoColegio.SUSPENDIDO).map(Colegio::getId).toList();
	}

	/** Sprint 11, tanda 1: los que todavía tienen mensajes que despachar (todos menos los SUPRIMIDO). */
	public List<Long> paraDespacho() {
		return colegios.findByTipoAndActivoTrueOrderByIdAsc(TipoColegio.COLEGIO).stream()
				.filter(c -> c.getEstado() != EstadoColegio.SUPRIMIDO).map(Colegio::getId).toList();
	}

	/** Los colegios clientes activos que ya están en producción. */
	public List<Long> enProduccion() {
		return colegios.findByTipoAndEstadoAndActivoTrueOrderByIdAsc(TipoColegio.COLEGIO, EstadoColegio.PRODUCCION)
				.stream().map(Colegio::getId).toList();
	}

	/** Los colegios clientes activos y, al final, el colegio de plataforma (solo para el despacho de mensajes). */
	public List<Long> conPlataforma() {
		List<Long> todos = new ArrayList<>(paraDespacho());
		colegios.findFirstByTipo(TipoColegio.PLATAFORMA).filter(Colegio::isActivo).map(Colegio::getId).ifPresent(todos::add);
		return todos;
	}

	/**
	 * Correcciones del sprint 7 (QA-S7-2): si falló en algún colegio (después de intentar en todos), la pasada queda
	 * marcada ({@link PasadaPorColegios}) y la tarea no deja latido.
	 */
	public void enCadaColegio(ActorSistema actor, Consumer<Long> tarea) {
		PasadaPorColegios.recorrer(LOG, "La tarea de " + actor.usuario(), activos(),
				colegio -> EjecucionComoSistema.como(actor, colegio, () -> tarea.accept(colegio)));
	}

	/** Igual que {@link #enCadaColegio}, pero solo en los colegios en producción (los que cobran). */
	public void enCadaColegioEnProduccion(ActorSistema actor, Consumer<Long> tarea) {
		PasadaPorColegios.recorrer(LOG, "La tarea de " + actor.usuario(), enProduccion(),
				colegio -> EjecucionComoSistema.como(actor, colegio, () -> tarea.accept(colegio)));
	}
}
