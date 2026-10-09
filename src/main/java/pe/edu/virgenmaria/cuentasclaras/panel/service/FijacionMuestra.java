package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.FamiliaParaLlamada;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.FamiliasParaLlamada;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillaMuestreo;
import pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillasMuestreo;
import pe.edu.virgenmaria.cuentasclaras.panel.config.PropiedadesPanel;
import pe.edu.virgenmaria.cuentasclaras.panel.model.LlamadaControl;
import pe.edu.virgenmaria.cuentasclaras.panel.model.MotivoMuestra;
import pe.edu.virgenmaria.cuentasclaras.panel.model.MuestraLlamada;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada;
import pe.edu.virgenmaria.cuentasclaras.panel.repository.LlamadaControlRepository;
import pe.edu.virgenmaria.cuentasclaras.panel.repository.MuestraLlamadaRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La muestra de la llamada de control de una semana (sprint 7, tanda 2; sección 3.6 y E10): la fija SOLO
 * {@code sistema.panel}, con la conexión de {@code cc_sistema} (con {@code cc_app}, 1142; en MySQL,
 * trg_muestra_llamada_registro exige además {@code creado_por = 'sistema.panel'}):
 * <ul>
 *   <li>el lunes a las 00:10 ({@code panel.proceso.MuestraSemanal}) o, si esa tarea no corrió, en la primera consulta de la
 *       semana (la pantalla se lo pide a {@code sistema.panel} en una transacción propia);</li>
 *   <li>el reemplazo de una familia que no contestó dos veces, después del commit de ese segundo «No contesta»
 *       ({@code panel.proceso.ReemplazosLlamadas}) y, si falla, en la pasada de cada 15 minutos.</li>
 * </ul>
 * La elección (con la semilla EFECTIVA de la semana, derivada con la clave del servidor) es la de siempre
 * ({@link MuestraLlamadas}).
 */
@Service
@PreAuthorize("hasRole('SISTEMA_PANEL')")
@Transactional(propagation = Propagation.MANDATORY)
public class FijacionMuestra {

	private final CifrasCaja caja;

	private final CifrasCobranza cobranza;

	private final FamiliasParaLlamada familias;

	private final SemillasMuestreo semillas;

	private final LlamadaControlRepository llamadas;

	private final MuestraLlamadaRepository muestras;

	private final AuditoriaService auditoria;

	private final PropiedadesPanel propiedades;

	private final Clock reloj;

	public FijacionMuestra(CifrasCaja caja, CifrasCobranza cobranza, FamiliasParaLlamada familias,
			SemillasMuestreo semillas, LlamadaControlRepository llamadas, MuestraLlamadaRepository muestras,
			AuditoriaService auditoria, PropiedadesPanel propiedades, Clock reloj) {
		this.caja = caja;
		this.cobranza = cobranza;
		this.familias = familias;
		this.semillas = semillas;
		this.llamadas = llamadas;
		this.muestras = muestras;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	/** La muestra de la semana; si aún no existe, la elige con la semilla y la guarda (S6-B3). */
	public List<MuestraLlamada> fijar(LocalDate semana) {
		List<MuestraLlamada> muestra = muestras.findBySemanaOrderByIdAsc(semana);
		if (!muestra.isEmpty()) {
			return muestra;
		}
		List<MuestraLlamadas.Candidata> candidatas = candidatas(semana, false);
		if (candidatas.isEmpty()) {
			return List.of();
		}
		Set<Long> conEfectivo = new HashSet<>(caja.familiasConEfectivo(semana.minusDays(LlamadasControl.DIAS_ATRAS),
				semana.minusDays(1)));
		List<MuestraLlamada> guardadas = new ArrayList<>();
		for (MuestraLlamadas.Candidata c : MuestraLlamadas.elegirCandidatas(semilla(semana), candidatas,
				propiedades.llamadasPorSemana())) {
			MotivoMuestra motivo = conEfectivo.contains(c.familiaId()) ? MotivoMuestra.EFECTIVO : MotivoMuestra.DEUDA;
			guardadas.add(muestras.saveAndFlush(MuestraLlamada.elegida(semana, c.familiaId(), motivo)));
		}
		auditoria.registrar(AccionAuditoria.MUESTRA_LLAMADAS_FIJADA, "muestra_llamada", null, null,
				guardadas.size() + " familia(s)", "Quedó fija la muestra de la llamada de control de la semana del "
						+ Calendario.formatear(semana) + " (" + guardadas.size() + " de " + candidatas.size()
						+ " candidatas). La fijó el sistema; no cambia durante la semana.");
		return guardadas;
	}

	/**
	 * S6-M2: la familia no contestó dos veces (después del commit de ese segundo intento): otra de las candidatas, con la
	 * misma semilla, ocupa su plaza; si ya no queda ninguna, queda registrado. Si ya se reemplazó, no hace nada.
	 *
	 * @return si la reemplazó
	 */
	public boolean reemplazar(LocalDate semana, Long familiaId) {
		List<MuestraLlamada> muestra = muestras.findBySemanaOrderByIdAsc(semana);
		if (!pendienteDeReemplazo(semana, familiaId, muestra)) {
			return false;
		}
		Optional<MuestraLlamadas.Candidata> otra = MuestraLlamadas.reemplazo(semilla(semana), candidatas(semana, true),
				muestra.stream().map(MuestraLlamada::getFamiliaId).collect(Collectors.toSet()));
		otra.ifPresent(c -> muestras.saveAndFlush(MuestraLlamada.reemplazo(semana, c.familiaId(), familiaId)));
		auditoria.registrar(AccionAuditoria.LLAMADA_CONTROL_REEMPLAZADA, "muestra_llamada", String.valueOf(familiaId),
				null, otra.map(c -> "reemplazada").orElse("sin reemplazo"), "La familia (código " + familiaId
						+ ") no contestó dos veces en la semana del " + Calendario.formatear(semana) + ". "
						+ otra.map(c -> "El sistema eligió otra familia de la muestra con la semilla de la semana.")
								.orElse("No quedan candidatas para reemplazarla."));
		return otra.isPresent();
	}

	/**
	 * La pasada de cada 15 minutos (si el reemplazo después del commit falló): reemplaza a cada familia que no contestó
	 * dos veces y todavía no tiene reemplazo, si queda alguna candidata.
	 *
	 * @return cuántas reemplazó
	 */
	public int reemplazarPendientes(LocalDate semana) {
		List<MuestraLlamada> muestra = muestras.findBySemanaOrderByIdAsc(semana);
		Set<Long> familias = llamadas.findBySemanaOrderByIdAsc(semana).stream()
				.filter(l -> l.getResultado() == ResultadoLlamada.NO_CONTESTA).map(LlamadaControl::getFamiliaId)
				.collect(Collectors.toCollection(TreeSet::new));
		int hechos = 0;
		for (Long familiaId : familias) {
			if (pendienteDeReemplazo(semana, familiaId, muestra)
					&& MuestraLlamadas.reemplazo(semilla(semana), candidatas(semana, true), muestra.stream()
							.map(MuestraLlamada::getFamiliaId).collect(Collectors.toSet())).isPresent()
					&& reemplazar(semana, familiaId)) {
				hechos++;
				muestra = muestras.findBySemanaOrderByIdAsc(semana);
			}
		}
		return hechos;
	}

	/** De la muestra, con sus dos «No contesta» de la semana y sin reemplazo todavía. */
	private boolean pendienteDeReemplazo(LocalDate semana, Long familiaId, List<MuestraLlamada> muestra) {
		boolean enMuestra = muestra.stream().anyMatch(m -> m.getFamiliaId().equals(familiaId));
		boolean reemplazada = muestra.stream().anyMatch(m -> familiaId.equals(m.getReemplazaFamiliaId()));
		long noContesta = llamadas.findBySemanaOrderByIdAsc(semana).stream()
				.filter(l -> l.getFamiliaId().equals(familiaId) && l.getResultado() == ResultadoLlamada.NO_CONTESTA)
				.count();
		return enMuestra && !reemplazada && noContesta >= 2;
	}

	private long semilla(LocalDate semana) {
		return semillas.de(SemillaMuestreo.Ambito.LLAMADA_CONTROL, semana);
	}

	/**
	 * Las candidatas de la semana, por id: efectivo en las 5 semanas anteriores al lunes y deuda vencida al lunes (S6-A2).
	 * Con {@code reemplazo}, las de efectivo se miran hasta hoy (para no quedarse sin reemplazos los primeros días).
	 */
	private List<MuestraLlamadas.Candidata> candidatas(LocalDate semana, boolean reemplazo) {
		LocalDate hasta = reemplazo ? LocalDate.now(reloj) : semana.minusDays(1);
		Set<Long> conEfectivo = new HashSet<>(caja.familiasConEfectivo(semana.minusDays(LlamadasControl.DIAS_ATRAS),
				hasta));
		Set<Long> conDeuda = new HashSet<>(cobranza.familiasConDeudaVencida(semana));
		Set<Long> todas = new TreeSet<>(conEfectivo);
		todas.addAll(conDeuda);
		if (todas.isEmpty()) {
			return List.of();
		}
		Set<Long> pagabanEnEfectivo = new HashSet<>(caja.familiasConEfectivo(semana.minusDays(LlamadasControl.DIAS_HISTORIA),
				semana.minusDays(LlamadasControl.DIAS_ATRAS + 1)));
		Set<Long> pagaronHace5Semanas = new HashSet<>(caja.familiasConPagos(semana.minusDays(LlamadasControl.DIAS_ATRAS),
				semana.minusDays(1)));
		Map<Long, FamiliaParaLlamada> perfiles = familias.de(todas).stream()
				.collect(Collectors.toMap(FamiliaParaLlamada::familiaId, Function.identity()));
		List<MuestraLlamadas.Candidata> lista = new ArrayList<>();
		for (Long id : todas) {
			FamiliaParaLlamada perfil = perfiles.get(id);
			if (perfil != null) {
				boolean debe = conDeuda.contains(id);
				lista.add(new MuestraLlamadas.Candidata(perfil, debe, debe && pagabanEnEfectivo.contains(id)
						&& !pagaronHace5Semanas.contains(id)));
			}
		}
		return lista;
	}
}
