package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.proceso.EmisionLibretas;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.EmisorLibretas;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.FotoLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.IntegridadLibretas;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.LibretasColegio;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.LibretasFamilia;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.ReemisionLibretas;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.VerificacionLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.PruebaNotas;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.AvisosLibretas;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Sprint 10, tanda 3: el escenario de las libretas en H2, sobre el de las notas ({@link PruebaNotas}: 5.° A de Primaria con
 * Mateo y Valeria, de la familia de Rosa, y Lucas, de la de Pedro). Los contactos están confirmados y la Promotoría y la
 * Dirección de los escenarios existen en la base (la reemisión comprueba sus roles). La base y sus triggers, en
 * {@code AuditoriaSprint10LibretasMySqlTest}.
 */
public abstract class PruebaLibretas extends PruebaNotas {

	@Autowired
	protected EmisionLibretas emision;

	@Autowired
	protected AvisosLibretas avisos;

	@Autowired
	protected LibretasFamilia portal;

	@Autowired
	protected LibretasColegio libretasColegio;

	@Autowired
	protected VerificacionLibreta verificacion;

	@Autowired
	protected ReemisionLibretas reemision;

	@Autowired
	protected FotoLibreta foto;

	@Autowired
	protected IntegridadLibretas integridad;

	@Autowired
	protected MockMvc mvc;

	protected Long rosa;

	protected Long pedro;

	protected Long familiaRosa;

	protected Long familiaPedro;

	@BeforeEach
	protected void prepararLibretas() {
		EscenarioEscolar.contactosConfirmados(jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, EscenarioCobranza.PROMOTORIA.getUsername(),
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
		rosa = jdbc.queryForObject("SELECT id FROM apoderado WHERE numero_documento = ?", Long.class,
				EscenarioEscolar.DNI_ROSA);
		pedro = jdbc.queryForObject("SELECT id FROM apoderado WHERE numero_documento = '41234567'", Long.class);
		familiaRosa = jdbc.queryForObject("SELECT familia_id FROM apoderado WHERE id = ?", Long.class, rosa);
		familiaPedro = jdbc.queryForObject("SELECT familia_id FROM apoderado WHERE id = ?", Long.class, pedro);
		SecurityContextHolder.clearContext();
	}

	/** Ana pone A en Matemática, Dirección cierra el I bimestre y sistema.libretas emite. */
	protected EmisorLibretas.Resultado cerrarYEmitir() {
		anaPoneA();
		cerrarPeriodo();
		return emitir();
	}

	protected EmisorLibretas.Resultado emitir() {
		SecurityContextHolder.clearContext();
		return emision.enColegio(1L);
	}

	/** sistema.libretas crea los avisos a las 10:00 del día del reloj. */
	protected int avisar() {
		SecurityContextHolder.clearContext();
		return avisos.enColegio(1L, LocalDateTime.of(reloj.instant().atZone(java.time.ZoneId.of("America/Lima"))
				.toLocalDate(), LocalTime.of(10, 0)));
	}

	protected UsuarioAutenticado rosaEnLinea() {
		return new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
				EnumSet.of(Rol.APODERADO), rosa);
	}

	protected UsuarioAutenticado pedroEnLinea() {
		return new UsuarioAutenticado(41L, 1L, "pedro.familia", "Pedro Flores", null, true, false, false,
				EnumSet.of(Rol.APODERADO), pedro);
	}

	/** La libreta vigente de una matrícula en el periodo de las notas. */
	protected Long libreta(Long matricula) {
		return jdbc.queryForObject("SELECT id FROM libreta_emitida WHERE matricula_id = ? AND periodo_id = ? ORDER BY "
				+ "numero_version DESC LIMIT 1", Long.class, matricula, periodo);
	}

	/** Pide la corrección como Ana y la aprueba la Promotoría (no la pidió ni registró la nota). */
	protected void corregir(Long matricula, Long competencia, String nivel, String conclusion) {
		// Al día siguiente del cierre (como en la vida real: la corrección llega después de lo informado al cierre).
		hoyEs(java.time.LocalDate.of(2026, 5, 5));
		Long nota = vigente(matricula, competencia);
		como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedir(nota, nivel, conclusion, MOTIVO_CORRECCION));
		try {
			EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "calificacion", nota);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	protected static String texto(byte[] pdf) {
		try (PDDocument documento = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(documento);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	protected static int paginas(byte[] pdf) {
		try (PDDocument documento = Loader.loadPDF(pdf)) {
			return documento.getNumberOfPages();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	protected List<String> eventos(String accion) {
		return jdbc.queryForList("SELECT detalle FROM evento_auditoria WHERE accion = ? ORDER BY secuencia", String.class,
				accion);
	}
}
