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
import pe.edu.virgenmaria.cuentasclaras.panel.model.LlamadaControl;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada;
import pe.edu.virgenmaria.cuentasclaras.panel.repository.LlamadaControlRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Llamada de control semanal (sprint 6, tanda 3; decisión 77, P17). Cierra el residual del sprint 5: una familia con un
 * solo apoderado y sin portal no tiene quién vea que su efectivo no se registró.
 * <ul>
 *   <li><b>Muestra secreta y estable</b>: cada semana (lunes a domingo, hora de Lima) se eligen
 *       {@code llamadas-por-semana} familias con algún pago en EFECTIVO (vigente o anulado) en los 35 días anteriores al
 *       lunes, con la semilla de {@code semilla_muestreo} (ámbito {@code LLAMADA_CONTROL}, fecha = el lunes). Las de la
 *       semana en curso entran solo si con las anteriores no alcanza (los primeros días del sistema). Las ya llamadas
 *       siguen en la muestra aunque cambien las candidatas.</li>
 *   <li><b>Primero pregunta, después compara</b>: los pagos registrados se muestran al tocar «Ya me dijo».</li>
 *   <li><b>Solo inserción</b>: un resultado por familia y semana; «No confirma» exige nota, queda resaltado en la bitácora
 *       y es una alerta CRÍTICA que sale al celular de Promotoría ({@link AlertasPanel}).</li>
 *   <li>Solo se registra una familia de la muestra de la semana (si no, 404); la base exige el resto (trigger).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class LlamadasControl {

	/** Cuántos días antes del lunes se miran los pagos en efectivo. */
	static final int DIAS_ATRAS = 35;

	/** Cuánto tiempo sigue a la vista (como CRÍTICA) una familia que no confirmó. */
	static final int DIAS_ALERTA_NO_CONFIRMA = 30;

	private static final int MIN_NOTA = 10;

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final CifrasCaja caja;

	private final FamiliasParaLlamada familias;

	private final SemillasMuestreo semillas;

	private final LlamadaControlRepository llamadas;

	private final AuditoriaService auditoria;

	private final PropiedadesPanel propiedades;

	private final Clock reloj;

	public LlamadasControl(CifrasCaja caja, FamiliasParaLlamada familias, SemillasMuestreo semillas,
			LlamadaControlRepository llamadas, AuditoriaService auditoria, PropiedadesPanel propiedades, Clock reloj) {
		this.caja = caja;
		this.familias = familias;
		this.semillas = semillas;
		this.llamadas = llamadas;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	/** El lunes de la semana de esa fecha. */
	static LocalDate lunes(LocalDate dia) {
		return dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	/** La muestra de esta semana con sus contactos, lo ya registrado y (aparte) los pagos para comparar. */
	public LlamadasSemana deEstaSemana() {
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate semana = lunes(hoy);
		List<LlamadaControl> hechas = llamadas.findBySemanaOrderByIdAsc(semana);
		Map<Long, LlamadaControl> porFamilia = hechas.stream()
				.collect(Collectors.toMap(LlamadaControl::getFamiliaId, Function.identity(), (a, b) -> a));
		List<FamiliaParaLlamada> muestra = muestra(semana, hoy, hechas);
		LocalDate desde = semana.minusDays(DIAS_ATRAS);
		List<LlamadasSemana.Familia> vista = new ArrayList<>();
		for (FamiliaParaLlamada f : muestra) {
			List<LlamadasSemana.Pago> pagos = caja.pagosDeFamilia(f.familiaId(), desde, hoy).stream()
					.map(LlamadasControl::pago).toList();
			LlamadaControl hecha = porFamilia.get(f.familiaId());
			vista.add(new LlamadasSemana.Familia(f.familiaId(), f.nombre(), f.contactos().stream()
					.map(c -> new LlamadasSemana.Contacto(c.nombre(), c.parentesco(), c.celular())).toList(),
					!f.conPortal(), f.apoderadosActivos() <= 1, pagos, hecha == null ? null : registrada(hecha)));
		}
		return new LlamadasSemana(Calendario.formatear(semana), Calendario.formatear(desde),
				Math.max(hechas.size(), muestra.size()), hechas.size(), vista);
	}

	/**
	 * Registra el resultado de la llamada a una familia de la muestra de esta semana. Una sola vez por familia y semana;
	 * «No confirma» exige qué dijo la familia (de 10 a 300 caracteres).
	 */
	@Transactional
	public Long registrar(Long familiaId, LlamadaRequest solicitud) {
		Objects.requireNonNull(familiaId, "familiaId");
		if (solicitud == null || solicitud.resultado() == null) {
			throw new ReglaNegocioException("Elige qué respondió la familia.");
		}
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate semana = lunes(hoy);
		List<LlamadaControl> hechas = llamadas.findBySemanaOrderByIdAsc(semana);
		boolean enLaMuestra = muestra(semana, hoy, hechas).stream().anyMatch(f -> f.familiaId().equals(familiaId));
		if (!enLaMuestra) {
			throw new RecursoNoEncontradoException("Esa familia no está en la llamada de control de esta semana.");
		}
		if (llamadas.existsBySemanaAndFamiliaId(semana, familiaId)) {
			throw new ReglaNegocioException("Ya registraste la llamada a esta familia esta semana. No se cambia.");
		}
		ResultadoLlamada resultado = solicitud.resultado();
		String nota = nota(resultado, solicitud.nota());
		LlamadaControl guardada = llamadas.saveAndFlush(LlamadaControl.registrar(semana, familiaId, resultado, nota));
		boolean noConfirma = resultado == ResultadoLlamada.NO_CONFIRMA;
		auditoria.registrar(noConfirma ? AccionAuditoria.LLAMADA_CONTROL_NO_CONFIRMA
				: AccionAuditoria.LLAMADA_CONTROL_REGISTRADA, "llamada_control", String.valueOf(guardada.getId()), null,
				resultado.name(), "Familia (código " + familiaId + ") · semana del " + Calendario.formatear(semana) + " · "
						+ resultado.etiqueta() + (noConfirma ? ". Revisa sus pagos y la caja de quien los cobró." : "."));
		return guardada.getId();
	}

	/**
	 * Cuántas llamadas van y cuántas tocan esta semana (para el panel y la alerta del sábado). No crea la semilla: el
	 * conteo no depende de quiénes salen en la muestra.
	 */
	@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
	public AvanceLlamadas avance() {
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate semana = lunes(hoy);
		List<LlamadaControl> hechas = llamadas.findBySemanaOrderByIdAsc(semana);
		Set<Long> posibles = new LinkedHashSet<>(candidatas(semana, hoy));
		hechas.forEach(l -> posibles.add(l.getFamiliaId()));
		int esperadas = Math.max(hechas.size(), Math.min(propiedades.llamadasPorSemana(), posibles.size()));
		return new AvanceLlamadas(semana, hechas.size(), esperadas);
	}

	/** Las «No confirma» de los últimos 30 días (la alerta CRÍTICA de {@link AlertasPanel}), de la más antigua a la última. */
	@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
	public List<LlamadaControl> noConfirmanRecientes() {
		return llamadas.findByResultadoAndCreadoEnGreaterThanEqualOrderByIdAsc(ResultadoLlamada.NO_CONFIRMA,
				LocalDateTime.now(reloj).minusDays(DIAS_ALERTA_NO_CONFIRMA));
	}

	/**
	 * Las familias con algún pago en efectivo en los 35 días anteriores al lunes (por id); si no alcanzan para la muestra,
	 * se agregan las que pagaron en efectivo esta semana hasta hoy (los primeros días del sistema).
	 */
	private List<Long> candidatas(LocalDate semana, LocalDate hoy) {
		Set<Long> ids = new LinkedHashSet<>(caja.familiasConEfectivo(semana.minusDays(DIAS_ATRAS), semana.minusDays(1)));
		if (ids.size() < propiedades.llamadasPorSemana()) {
			ids.addAll(caja.familiasConEfectivo(semana, hoy));
		}
		return List.copyOf(ids);
	}

	/** Las ya llamadas esta semana (en su orden) y después las elegidas con la semilla, hasta completar la muestra. */
	private List<FamiliaParaLlamada> muestra(LocalDate semana, LocalDate hoy, List<LlamadaControl> hechas) {
		List<Long> candidatas = candidatas(semana, hoy);
		if (candidatas.isEmpty() && hechas.isEmpty()) {
			return List.of();
		}
		Set<Long> todas = new LinkedHashSet<>(candidatas);
		hechas.forEach(l -> todas.add(l.getFamiliaId()));
		Map<Long, FamiliaParaLlamada> perfiles = familias.de(todas).stream()
				.collect(Collectors.toMap(FamiliaParaLlamada::familiaId, Function.identity()));
		List<FamiliaParaLlamada> elegibles = candidatas.stream().map(perfiles::get).filter(Objects::nonNull).toList();
		List<FamiliaParaLlamada> elegidas = elegibles.isEmpty() ? List.of()
				: MuestraLlamadas.elegir(semillas.de(SemillaMuestreo.Ambito.LLAMADA_CONTROL, semana), elegibles,
						propiedades.llamadasPorSemana());
		List<FamiliaParaLlamada> resultado = new ArrayList<>();
		hechas.stream().map(l -> perfiles.get(l.getFamiliaId())).filter(Objects::nonNull).forEach(resultado::add);
		int tope = Math.max(propiedades.llamadasPorSemana(), resultado.size());
		for (FamiliaParaLlamada f : elegidas) {
			if (resultado.size() >= tope) {
				break;
			}
			if (resultado.stream().noneMatch(r -> r.familiaId().equals(f.familiaId()))) {
				resultado.add(f);
			}
		}
		return resultado;
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
		return new LlamadasSemana.Registrada(l.getResultado().etiqueta(), l.getResultado().variante(), l.getNota(),
				l.getCreadoPor(), l.getCreadoEn() == null ? "" : l.getCreadoEn().format(HORA));
	}
}
