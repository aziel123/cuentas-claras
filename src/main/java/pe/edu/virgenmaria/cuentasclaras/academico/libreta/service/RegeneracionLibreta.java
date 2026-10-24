package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf.GeneradorLibretaPdf;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.LogosColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 10, tanda 3 (secciones 3.7 y 3.9; E40, A3, decisión 188): regenera una libreta desde su foto, comprueba su huella y
 * arma el PDF. Si la huella NO coincide (alguien tocó la base), ese PDF no se entrega: queda el evento
 * {@code LIBRETA_HUELLA_NO_COINCIDE} (una vez por libreta), la alerta {@code INTEGRIDAD_LIBRETAS} a Promotoría
 * ({@link IntegridadLibretas}) y la fecha en que el colegio la entregará a más tardar ({@value #DIAS_REEMISION_TXT} días
 * hábiles con los feriados del colegio). Nunca «sin libreta».
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class RegeneracionLibreta {

	static final String DIAS_REEMISION_TXT = "2";

	static final String ENTIDAD = "libreta_emitida";

	/** Una libreta regenerada: su contenido de hoy y si coincide con la huella que se guardó al emitirla. */
	public record Regenerada(LibretaEmitida libreta, ContenidoLibreta contenido, boolean coincide) {
	}

	private final FotoLibreta foto;

	private final GeneradorLibretaPdf generador;

	private final LogosColegio logos;

	private final AuditoriaService auditoria;

	private final CalendarioHabil calendario;

	private final Clock reloj;

	private final int diasReemision;

	public RegeneracionLibreta(FotoLibreta foto, GeneradorLibretaPdf generador, LogosColegio logos,
			AuditoriaService auditoria, CalendarioHabil calendario, Clock reloj,
			@Value("${cuentasclaras.libretas.reemision-dias-habiles:2}") int diasReemision) {
		this.foto = foto;
		this.generador = generador;
		this.logos = logos;
		this.auditoria = auditoria;
		this.calendario = calendario;
		this.reloj = reloj;
		this.diasReemision = diasReemision;
	}

	public Regenerada regenerar(LibretaEmitida libreta) {
		return regenerar(List.of(libreta)).get(0);
	}

	/** Regenera libretas de UNA sección y periodo cargando lo común una sola vez (la impresión de 45). */
	public List<Regenerada> regenerar(List<LibretaEmitida> deUnaSeccion) {
		if (deUnaSeccion.isEmpty()) {
			return List.of();
		}
		Set<Long> matriculas = new LinkedHashSet<>();
		deUnaSeccion.forEach(l -> matriculas.add(l.getMatriculaId()));
		LibretaEmitida primera = deUnaSeccion.get(0);
		FotoLibreta.DatosSeccion datos = foto.cargar(primera.getPeriodoId(), primera.getSeccionId(), matriculas);
		List<Regenerada> resultado = new ArrayList<>();
		for (LibretaEmitida l : deUnaSeccion) {
			ContenidoLibreta contenido = foto.armar(datos, l.donde(), l.marcas(), l.cabecera());
			boolean coincide = FotoLibreta.huella(contenido).equals(l.getHuellaContenido());
			Regenerada r = new Regenerada(l, contenido, coincide);
			if (!coincide) {
				marcarNoCoincide(r);
			}
			resultado.add(r);
		}
		return resultado;
	}

	/** E40: deja el evento (una vez por libreta) para la alerta a Promotoría y el plazo de la reemisión. */
	void marcarNoCoincide(Regenerada r) {
		String id = r.libreta().getId().toString();
		if (!auditoria.existeSobre(AccionAuditoria.LIBRETA_HUELLA_NO_COINCIDE, ENTIDAD, id)) {
			auditoria.registrar(AccionAuditoria.LIBRETA_HUELLA_NO_COINCIDE, ENTIDAD, id, r.libreta().getHuellaContenido(),
					FotoLibreta.huella(r.contenido()), "La libreta " + r.libreta().codigoVerificacion() + " (alumno "
							+ r.libreta().getAlumnoId() + ") no coincide con su huella: alguien cambió la base después de "
							+ "emitirla. No se entregó ese PDF. Dirección debe pedir su reemisión y Promotoría aprobarla.");
		}
	}

	/** Hasta cuándo el colegio entrega la libreta en revisión: 2 días hábiles desde que se detectó (con sus feriados). */
	public LocalDate plazo(LibretaEmitida libreta) {
		Map<String, LocalDateTime> primeras = auditoria.primerasDesde(AccionAuditoria.LIBRETA_HUELLA_NO_COINCIDE,
				LocalDateTime.now(reloj).minusDays(400));
		LocalDate desde = primeras.getOrDefault(libreta.getId().toString(), LocalDateTime.now(reloj)).toLocalDate();
		return calendario.sumarHabiles(desde, diasReemision);
	}

	/** El PDF de libretas que SÍ coinciden con su huella (las demás no se entregan). */
	public byte[] pdf(List<Regenerada> regeneradas) {
		List<GeneradorLibretaPdf.Hoja> hojas = regeneradas.stream().filter(Regenerada::coincide)
				.map(r -> new GeneradorLibretaPdf.Hoja(r.contenido(), version(r.libreta()))).toList();
		return generador.generar(hojas, logos.pngVigente().orElse(null));
	}

	static GeneradorLibretaPdf.Version version(LibretaEmitida l) {
		return new GeneradorLibretaPdf.Version(l.codigoVerificacion(), l.getNumeroVersion(),
				l.getRegistradoBd() != null ? l.getRegistradoBd() : l.getCreadoEn(), l.getMotivo(), l.getCambios());
	}

	public int maxPorArchivo() {
		return generador.maxPorArchivo();
	}
}
