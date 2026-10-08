package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.FamiliaParaLlamada;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.FamiliasParaLlamada;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.PagoDeFamilia;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillaMuestreo;
import pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillasMuestreo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.TextoSeguro;
import pe.edu.virgenmaria.cuentasclaras.panel.config.PropiedadesPanel;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.AvanceLlamadas;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadaRequest;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadasSemana;
import pe.edu.virgenmaria.cuentasclaras.panel.model.DelegacionLlamada;
import pe.edu.virgenmaria.cuentasclaras.panel.model.LlamadaControl;
import pe.edu.virgenmaria.cuentasclaras.panel.model.MotivoMuestra;
import pe.edu.virgenmaria.cuentasclaras.panel.model.MuestraLlamada;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada;
import pe.edu.virgenmaria.cuentasclaras.panel.repository.DelegacionLlamadaRepository;
import pe.edu.virgenmaria.cuentasclaras.panel.repository.LlamadaControlRepository;
import pe.edu.virgenmaria.cuentasclaras.panel.repository.MuestraLlamadaRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Llamada de control semanal (sprint 6, tanda 3; decisión 77, P17). Cierra el residual del sprint 5: una familia con un
 * solo apoderado y sin portal no tiene quién vea que su efectivo no se registró. Correcciones del sprint 6:
 * <ul>
 *   <li><b>Candidatas</b> (S6-A2): las familias con algún pago en EFECTIVO (vigente o anulado) en las 5 semanas anteriores
 *       al lunes y, además, las que tienen DEUDA VENCIDA al lunes: el efectivo que nunca se registró no deja un pago, pero
 *       sí deuda. Pesan más las que pagaban en efectivo y dejaron de pagar, y una plaza queda reservada para la deuda.</li>
 *   <li><b>Muestra secreta, ponderada y congelada</b> (S6-B3, QA-S6-2): se elige con la semilla de {@code semilla_muestreo}
 *       (ámbito {@code LLAMADA_CONTROL}, fecha = el lunes) la primera vez que se consulta en la semana y queda en
 *       {@code muestra_llamada} (solo inserción): no cambia a mitad de semana aunque cambien las candidatas.</li>
 *   <li><b>Quién registra</b> (S6-M2): Promotoría. Dirección, solo la semana que Promotoría se la delega
 *       ({@link #delegarADireccion}); cada llamada que registra Dirección se avisa al celular de Promotoría.</li>
 *   <li><b>«No contesta» no cierra la plaza</b> (S6-M2): se vuelve a llamar (una hora después como mínimo) y, si tampoco
 *       contesta, la familia se reemplaza por otra de la muestra y sale un aviso de ATENCIÓN.</li>
 *   <li><b>Primero pregunta, después compara</b>: los pagos registrados se muestran al tocar «Ya me dijo».</li>
 *   <li>«No confirma» exige nota, queda resaltado en la bitácora y es una alerta CRÍTICA ({@link AlertasPanel}).</li>
 * </ul>
 * La base exige el resto (triggers de {@code muestra_llamada}, {@code delegacion_llamada} y {@code llamada_control}).
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class LlamadasControl {

	/** Cuántos días antes del lunes se miran los pagos en efectivo. */
	static final int DIAS_ATRAS = 35;

	/** S6-A2: hasta cuántos días antes se mira si la familia «pagaba en efectivo». */
	static final int DIAS_HISTORIA = 365;

	/** Cuánto tiempo sigue a la vista (como CRÍTICA) una familia que no confirmó. */
	static final int DIAS_ALERTA_NO_CONFIRMA = 30;

	/** S6-M2: cuánto tiempo siguen a la vista (ATENCIÓN) los reemplazos y las llamadas de Dirección. */
	static final int DIAS_ALERTA_ATENCION = 7;

	/** S6-M2: el segundo intento es al menos esto después del primero. */
	static final Duration ENTRE_INTENTOS = Duration.ofHours(1);

	private static final int MIN_NOTA = 10;

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final CifrasCaja caja;

	private final CifrasCobranza cobranza;

	private final FamiliasParaLlamada familias;

	private final SemillasMuestreo semillas;

	private final LlamadaControlRepository llamadas;

	private final MuestraLlamadaRepository muestras;

	private final DelegacionLlamadaRepository delegaciones;

	private final AuditoriaService auditoria;

	private final PropiedadesPanel propiedades;

	private final Clock reloj;

	public LlamadasControl(CifrasCaja caja, CifrasCobranza cobranza, FamiliasParaLlamada familias,
			SemillasMuestreo semillas, LlamadaControlRepository llamadas, MuestraLlamadaRepository muestras,
			DelegacionLlamadaRepository delegaciones, AuditoriaService auditoria, PropiedadesPanel propiedades,
			Clock reloj) {
		this.caja = caja;
		this.cobranza = cobranza;
		this.familias = familias;
		this.semillas = semillas;
		this.llamadas = llamadas;
		this.muestras = muestras;
		this.delegaciones = delegaciones;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	/** El lunes de la semana de esa fecha. */
	static LocalDate lunes(LocalDate dia) {
		return dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	/**
	 * La muestra de esta semana con sus contactos, lo ya registrado y (aparte) los pagos para comparar. La primera consulta
	 * de la semana la congela (S6-B3).
	 */
	@Transactional
	public LlamadasSemana deEstaSemana() {
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate semana = lunes(hoy);
		Estado estado = estado(semana, congelar(semana));
		LocalDate desde = semana.minusDays(DIAS_ATRAS);
		Map<Long, FamiliaParaLlamada> perfiles = familias.de(estado.muestra().stream().map(MuestraLlamada::getFamiliaId)
				.toList()).stream().collect(Collectors.toMap(FamiliaParaLlamada::familiaId, Function.identity()));
		List<LlamadasSemana.Familia> vista = new ArrayList<>();
		for (MuestraLlamada m : estado.muestra()) {
			FamiliaParaLlamada f = perfiles.get(m.getFamiliaId());
			if (f == null) {
				continue;
			}
			List<LlamadasSemana.Pago> pagos = caja.pagosDeFamilia(f.familiaId(), desde, hoy).stream()
					.map(LlamadasControl::pago).toList();
			List<LlamadaControl> deEsta = estado.llamadasDe(f.familiaId());
			LlamadaControl ultima = deEsta.isEmpty() ? null : deEsta.getLast();
			vista.add(new LlamadasSemana.Familia(f.familiaId(), f.nombre(), f.contactos().stream()
					.map(c -> new LlamadasSemana.Contacto(c.nombre(), c.parentesco(), c.celular())).toList(),
					!f.conPortal(), f.apoderadosActivos() <= 1, pagos, ultima == null ? null : registrada(ultima),
					!estado.cerrada(f.familiaId()), estado.reintento(f.familiaId()),
					estado.reemplazadas().contains(f.familiaId()), motivo(m)));
		}
		boolean delegada = delegaciones.findBySemana(semana).isPresent();
		boolean promotoria = Formato.tieneAlgunRol("PROMOTOR");
		return new LlamadasSemana(Calendario.formatear(semana), Calendario.formatear(desde), estado.esperadas(),
				estado.hechas(), vista, delegada, promotoria || delegada, promotoria && !delegada);
	}

	/**
	 * S6-M2: Promotoría delega a Dirección las llamadas de ESTA semana (una vez; no se revoca). Queda resaltado en la
	 * bitácora y cada llamada que registre Dirección se avisa al celular de Promotoría.
	 */
	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional
	public void delegarADireccion() {
		LocalDate semana = lunes(LocalDate.now(reloj));
		if (delegaciones.findBySemana(semana).isPresent()) {
			throw new ReglaNegocioException("Las llamadas de esta semana ya están delegadas a Dirección.");
		}
		DelegacionLlamada d = delegaciones.saveAndFlush(DelegacionLlamada.de(semana));
		auditoria.registrar(AccionAuditoria.LLAMADAS_DELEGADAS, "delegacion_llamada", String.valueOf(d.getId()), null,
				"semana " + semana, "Promotoría delegó a Dirección las llamadas de control de la semana del "
						+ Calendario.formatear(semana) + ". Cada llamada que registre Dirección se avisa a Promotoría.");
	}

	/**
	 * Registra el resultado de una llamada a una familia de la muestra de esta semana. «No confirma» exige qué dijo la
	 * familia (de 10 a 300 caracteres). «Confirma» y «No confirma» cierran la plaza; «No contesta» deja volver a llamar una
	 * vez y, al segundo, la familia se reemplaza (S6-M2).
	 */
	@Transactional
	public Long registrar(Long familiaId, LlamadaRequest solicitud) {
		Objects.requireNonNull(familiaId, "familiaId");
		if (solicitud == null || solicitud.resultado() == null) {
			throw new ReglaNegocioException("Elige qué respondió la familia.");
		}
		LocalDateTime ahora = LocalDateTime.now(reloj);
		LocalDate semana = lunes(ahora.toLocalDate());
		boolean porDelegacion = !Formato.tieneAlgunRol("PROMOTOR");
		if (porDelegacion && delegaciones.findBySemana(semana).isEmpty()) {
			throw new ReglaNegocioException("Las llamadas de control las registra Promotoría. Dirección las registra solo "
					+ "la semana que Promotoría se las delega.");
		}
		Estado estado = estado(semana, congelar(semana));
		if (!estado.activas().contains(familiaId)) {
			throw new RecursoNoEncontradoException("Esa familia no está en la llamada de control de esta semana.");
		}
		if (estado.cerrada(familiaId)) {
			throw new ReglaNegocioException("Ya registraste la llamada a esta familia esta semana. No se cambia.");
		}
		List<LlamadaControl> anteriores = estado.llamadasDe(familiaId);
		int intento = anteriores.size() + 1;
		if (intento == 2 && anteriores.getFirst().getCreadoEn() != null
				&& ahora.isBefore(anteriores.getFirst().getCreadoEn().plus(ENTRE_INTENTOS))) {
			throw new ReglaNegocioException("La primera vez no contestó: vuelve a llamar desde las "
					+ anteriores.getFirst().getCreadoEn().plus(ENTRE_INTENTOS).toLocalTime().withSecond(0).withNano(0)
					+ " (una hora después como mínimo).");
		}
		ResultadoLlamada resultado = solicitud.resultado();
		String nota = nota(resultado, solicitud.nota());
		LlamadaControl guardada = llamadas.saveAndFlush(LlamadaControl.registrar(semana, familiaId, resultado, nota, intento,
				porDelegacion));
		boolean noConfirma = resultado == ResultadoLlamada.NO_CONFIRMA;
		auditoria.registrar(noConfirma ? AccionAuditoria.LLAMADA_CONTROL_NO_CONFIRMA
				: AccionAuditoria.LLAMADA_CONTROL_REGISTRADA, "llamada_control", String.valueOf(guardada.getId()), null,
				resultado.name(), "Familia (código " + familiaId + ") · semana del " + Calendario.formatear(semana) + " · "
						+ resultado.etiqueta() + (intento == 2 ? " (segundo intento)" : "")
						+ (porDelegacion ? " · registrada por Dirección con la semana delegada" : "")
						+ (noConfirma ? ". Revisa sus pagos y la caja de quien los cobró." : "."));
		if (intento == 2 && resultado == ResultadoLlamada.NO_CONTESTA) {
			reemplazar(semana, familiaId, estado);
		}
		return guardada.getId();
	}

	/** S6-M2: la familia no contestó dos veces; otra de las candidatas (con la misma semilla) ocupa su plaza. */
	private void reemplazar(LocalDate semana, Long familiaId, Estado estado) {
		Set<Long> yaEstan = estado.muestra().stream().map(MuestraLlamada::getFamiliaId).collect(Collectors.toSet());
		Optional<MuestraLlamadas.Candidata> otra = MuestraLlamadas.reemplazo(semilla(semana), candidatas(semana, true),
				yaEstan);
		otra.ifPresent(c -> muestras.saveAndFlush(MuestraLlamada.reemplazo(semana, c.familiaId(), familiaId)));
		auditoria.registrar(AccionAuditoria.LLAMADA_CONTROL_REEMPLAZADA, "muestra_llamada", String.valueOf(familiaId), null,
				otra.map(c -> "reemplazada").orElse("sin reemplazo"), "La familia (código " + familiaId + ") no contestó "
						+ "dos veces en la semana del " + Calendario.formatear(semana) + ". "
						+ otra.map(c -> "Se eligió otra familia de la muestra con la semilla de la semana.")
								.orElse("No quedan candidatas para reemplazarla."));
	}

	/**
	 * Cuántas llamadas van y cuántas tocan esta semana, y sus resultados (para el panel, la alerta del sábado y el resumen
	 * diario). No congela la muestra ni crea la semilla.
	 */
	@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
	public AvanceLlamadas avance() {
		return avanceDe(LocalDate.now(reloj));
	}

	/** Lo mismo para la semana de un día (el resumen diario de ese día). */
	@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
	public AvanceLlamadas avanceDe(LocalDate dia) {
		LocalDate semana = lunes(dia);
		List<MuestraLlamada> muestra = muestras.findBySemanaOrderByIdAsc(semana);
		if (muestra.isEmpty()) {
			Set<Long> posibles = new LinkedHashSet<>(caja.familiasConEfectivo(semana.minusDays(DIAS_ATRAS),
					semana.minusDays(1)));
			posibles.addAll(cobranza.familiasConDeudaVencida(semana));
			return new AvanceLlamadas(semana, 0, Math.min(propiedades.llamadasPorSemana(), posibles.size()));
		}
		Estado estado = estado(semana, muestra);
		List<LlamadaControl> todas = estado.llamadas();
		return new AvanceLlamadas(semana, estado.hechas(), estado.esperadas(),
				cuenta(todas, ResultadoLlamada.CONFIRMA), cuenta(todas, ResultadoLlamada.NO_CONFIRMA),
				cuenta(todas, ResultadoLlamada.NO_CONTESTA), estado.reemplazadas().size());
	}

	/** Las «No confirma» de los últimos 30 días (la alerta CRÍTICA de {@link AlertasPanel}), de la más antigua a la última. */
	@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
	public List<LlamadaControl> noConfirmanRecientes() {
		return llamadas.findByResultadoAndCreadoEnGreaterThanEqualOrderByIdAsc(ResultadoLlamada.NO_CONFIRMA,
				LocalDateTime.now(reloj).minusDays(DIAS_ALERTA_NO_CONFIRMA));
	}

	/** S6-M2: los segundos «No contesta» de los últimos 7 días (la familia se reemplazó): ATENCIÓN con aviso. */
	@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
	public List<LlamadaControl> sinRespuestaRecientes() {
		return llamadas.findByResultadoAndCreadoEnGreaterThanEqualOrderByIdAsc(ResultadoLlamada.NO_CONTESTA,
				LocalDateTime.now(reloj).minusDays(DIAS_ALERTA_ATENCION)).stream().filter(l -> l.getIntento() == 2).toList();
	}

	/** S6-M2: las llamadas que registró Dirección (semana delegada) en los últimos 7 días: ATENCIÓN con aviso. */
	@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
	public List<LlamadaControl> registradasPorDireccion() {
		return llamadas.findByPorDelegacionTrueAndCreadoEnGreaterThanEqualOrderByIdAsc(LocalDateTime.now(reloj)
				.minusDays(DIAS_ALERTA_ATENCION));
	}

	// ------------------------------------------------------------------ muestra

	/** La muestra congelada de la semana; si aún no existe, la elige con la semilla y la guarda (S6-B3). */
	private List<MuestraLlamada> congelar(LocalDate semana) {
		List<MuestraLlamada> muestra = muestras.findBySemanaOrderByIdAsc(semana);
		if (!muestra.isEmpty()) {
			return muestra;
		}
		List<MuestraLlamadas.Candidata> candidatas = candidatas(semana, false);
		if (candidatas.isEmpty()) {
			return List.of();
		}
		Set<Long> conEfectivo = new HashSet<>(caja.familiasConEfectivo(semana.minusDays(DIAS_ATRAS), semana.minusDays(1)));
		List<MuestraLlamada> guardadas = new ArrayList<>();
		for (MuestraLlamadas.Candidata c : MuestraLlamadas.elegirCandidatas(semilla(semana), candidatas,
				propiedades.llamadasPorSemana())) {
			MotivoMuestra motivo = conEfectivo.contains(c.familiaId()) ? MotivoMuestra.EFECTIVO : MotivoMuestra.DEUDA;
			guardadas.add(muestras.saveAndFlush(MuestraLlamada.elegida(semana, c.familiaId(), motivo)));
		}
		auditoria.registrar(AccionAuditoria.MUESTRA_LLAMADAS_FIJADA, "muestra_llamada", null, null,
				guardadas.size() + " familia(s)", "Quedó fija la muestra de la llamada de control de la semana del "
						+ Calendario.formatear(semana) + " (" + guardadas.size() + " de " + candidatas.size()
						+ " candidatas). No cambia durante la semana.");
		return guardadas;
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
		Set<Long> conEfectivo = new HashSet<>(caja.familiasConEfectivo(semana.minusDays(DIAS_ATRAS), hasta));
		Set<Long> conDeuda = new HashSet<>(cobranza.familiasConDeudaVencida(semana));
		Set<Long> todas = new java.util.TreeSet<>(conEfectivo);
		todas.addAll(conDeuda);
		if (todas.isEmpty()) {
			return List.of();
		}
		Set<Long> pagabanEnEfectivo = new HashSet<>(caja.familiasConEfectivo(semana.minusDays(DIAS_HISTORIA),
				semana.minusDays(DIAS_ATRAS + 1)));
		Set<Long> pagaronHace5Semanas = new HashSet<>(caja.familiasConPagos(semana.minusDays(DIAS_ATRAS),
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

	/** Lo que pasó en la semana: la muestra, las llamadas y qué plazas siguen abiertas. */
	private Estado estado(LocalDate semana, List<MuestraLlamada> muestra) {
		List<LlamadaControl> deLaSemana = muestra.isEmpty() ? List.of() : llamadas.findBySemanaOrderByIdAsc(semana);
		return new Estado(muestra, deLaSemana);
	}

	/** La muestra de la semana con sus llamadas. */
	private record Estado(List<MuestraLlamada> muestra, List<LlamadaControl> llamadas) {

		List<LlamadaControl> llamadasDe(Long familiaId) {
			return llamadas.stream().filter(l -> l.getFamiliaId().equals(familiaId)).toList();
		}

		/** Las familias reemplazadas (no contestaron dos veces y otra ocupa su plaza). */
		Set<Long> reemplazadas() {
			return muestra.stream().map(MuestraLlamada::getReemplazaFamiliaId).filter(Objects::nonNull)
					.collect(Collectors.toSet());
		}

		/** Las plazas vigentes: las de la muestra menos las reemplazadas. */
		Set<Long> activas() {
			Set<Long> reemplazadas = reemplazadas();
			Set<Long> activas = new LinkedHashSet<>();
			muestra.stream().map(MuestraLlamada::getFamiliaId).filter(id -> !reemplazadas.contains(id))
					.forEach(activas::add);
			return activas;
		}

		/** Confirma, no confirma o no contestó dos veces. */
		boolean cerrada(Long familiaId) {
			List<LlamadaControl> de = llamadasDe(familiaId);
			return de.stream().anyMatch(l -> l.getResultado() != ResultadoLlamada.NO_CONTESTA) || de.size() >= 2;
		}

		boolean reintento(Long familiaId) {
			List<LlamadaControl> de = llamadasDe(familiaId);
			return de.size() == 1 && de.getFirst().getResultado() == ResultadoLlamada.NO_CONTESTA;
		}

		int esperadas() {
			return activas().size();
		}

		int hechas() {
			return (int) activas().stream().filter(this::cerrada).count();
		}
	}

	private static int cuenta(List<LlamadaControl> todas, ResultadoLlamada resultado) {
		return (int) todas.stream().filter(l -> l.getResultado() == resultado).count();
	}

	private static String motivo(MuestraLlamada m) {
		return switch (m.getMotivo()) {
			case EFECTIVO -> "Pagó en efectivo en las últimas semanas.";
			case DEUDA -> "Tiene deuda vencida: pregunta si pagó algo que no aparece.";
			case REEMPLAZO -> "Reemplaza a una familia que no contestó dos veces.";
		};
	}

	private static String nota(ResultadoLlamada resultado, String nota) {
		String texto = Normalizador.limpiar(nota);
		if (resultado == ResultadoLlamada.NO_CONFIRMA && (texto == null || texto.length() < MIN_NOTA)) {
			throw new ReglaNegocioException("Escribe qué te dijo la familia (cuánto y cuándo pagó), con " + MIN_NOTA
					+ " caracteres como mínimo.");
		}
		if (texto != null && texto.length() > LlamadaControl.MAX_NOTA) {
			throw new ReglaNegocioException("La nota tiene como máximo " + LlamadaControl.MAX_NOTA + " caracteres.");
		}
		return texto == null ? null : TextoSeguro.exigir(texto, "la nota");
	}

	private static LlamadasSemana.Pago pago(PagoDeFamilia p) {
		boolean anulado = p.estado() == EstadoPago.ANULADO;
		return new LlamadasSemana.Pago(Calendario.formatear(p.fecha()), p.medio().etiqueta(), Dinero.formatear(p.total()),
				anulado ? "Anulado" : "Vigente", anulado, p.comprobante(), p.registradoPor());
	}

	private static LlamadasSemana.Registrada registrada(LlamadaControl l) {
		return new LlamadasSemana.Registrada(l.getResultado().etiqueta() + (l.getIntento() == 2 ? " (segundo intento)" : ""),
				l.getResultado().variante(), l.getNota(), l.getCreadoPor(),
				l.getCreadoEn() == null ? "" : l.getCreadoEn().format(HORA));
	}
}
