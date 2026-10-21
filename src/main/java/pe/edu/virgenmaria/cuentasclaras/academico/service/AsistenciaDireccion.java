package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsistenciaDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.CambioDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConfiguracionAcademica;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MarcaAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.OrigenMarca;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.MarcaAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 9, tanda 2 (E5, E13, A13): lo que hace Dirección con la asistencia, sin registrarla ni corregirla (decisión 144):
 * <ul>
 *   <li>{@link #cambiosDelDia}: la lista de los cambios de hoy (correcciones, justificaciones, reversiones), resaltando los
 *       que fueron después del aviso a las familias;</li>
 *   <li>{@link #revertir}: revierte una justificación de la familia (por ejemplo, falsa), con motivo y la firma de su
 *       sesión; la marca vuelve a FALTA y la familia recibe la corrección. Un director que también enseña en la sección no
 *       revierte allí (A13).</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('DIRECTOR')")
public class AsistenciaDireccion {

	private final MarcaAsistenciaRepository marcas;

	private final AlumnoRepository alumnos;

	private final SeccionRepository secciones;

	private final UsuarioRepository usuarios;

	private final ConfiguracionesAcademicas configuraciones;

	private final DocentesDeSeccion docentes;

	private final SesionDeQuienEscribe sesiones;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public AsistenciaDireccion(MarcaAsistenciaRepository marcas, AlumnoRepository alumnos, SeccionRepository secciones,
			UsuarioRepository usuarios, ConfiguracionesAcademicas configuraciones, DocentesDeSeccion docentes,
			SesionDeQuienEscribe sesiones, FirmaSesion firma, AuditoriaService auditoria, Clock reloj) {
		this.marcas = marcas;
		this.alumnos = alumnos;
		this.secciones = secciones;
		this.usuarios = usuarios;
		this.configuraciones = configuraciones;
		this.docentes = docentes;
		this.sesiones = sesiones;
		this.firma = firma;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** Los cambios de la asistencia de una fecha (por defecto hoy), del más reciente al más antiguo. */
	@Transactional(readOnly = true)
	public List<CambioDelDia> cambiosDelDia(LocalDate fecha) {
		LocalDate dia = fecha == null ? LocalDate.now(reloj) : fecha;
		ConfiguracionAcademica configuracion = configuraciones.vigente();
		Map<Long, Optional<Alumno>> deAlumno = new HashMap<>();
		Map<Long, Optional<Seccion>> deSeccion = new HashMap<>();
		List<CambioDelDia> cambios = new ArrayList<>();
		for (MarcaAsistencia m : marcas.cambiosDelDia(dia)) {
			MarcaAsistencia anterior = marcas.findById(m.getReemplazaId()).orElse(null);
			String alumno = deAlumno.computeIfAbsent(m.getAlumnoId(), alumnos::findById)
					.map(a -> a.getNombres() + " " + a.getApellidoPaterno()).orElse("—");
			String seccion = deSeccion.computeIfAbsent(m.getSeccionId(), secciones::findById).map(Seccion::etiqueta)
					.orElse("—");
			boolean despues = !m.getCreadoEn().toLocalDate().equals(dia)
					|| !m.getCreadoEn().toLocalTime().isBefore(configuracion.getHoraAviso());
			boolean revertible = m.getOrigen() == OrigenMarca.JUSTIFICACION && !marcas.existsByReemplazaId(m.getId());
			cambios.add(new CambioDelDia(m.getId(), seccion, alumno, anterior == null ? "—" : anterior.getEstado().etiqueta(),
					m.getEstado().etiqueta(), m.getOrigen().etiqueta(), quien(m), m.getCreadoEn(), despues,
					m.getOrigen() == OrigenMarca.JUSTIFICACION ? motivoFamilia(m) : m.getMotivo(), revertible));
		}
		return cambios;
	}

	/**
	 * Rediseño (tablero de Dirección): la asistencia de hoy en números (marcas vigentes por estado y secciones con
	 * registro), sin nombres. Solo lectura.
	 */
	@Transactional(readOnly = true)
	public AsistenciaDelDia resumenDelDia() {
		LocalDate hoy = LocalDate.now(reloj);
		long[] porEstado = new long[EstadoAsistencia.values().length];
		java.util.Set<Long> conRegistro = new java.util.HashSet<>();
		for (MarcaAsistencia m : marcas.vigentesDelDia(hoy)) {
			porEstado[m.getEstado().ordinal()]++;
			conRegistro.add(m.getSeccionId());
		}
		return new AsistenciaDelDia(hoy, porEstado[EstadoAsistencia.PRESENTE.ordinal()],
				porEstado[EstadoAsistencia.TARDE.ordinal()], porEstado[EstadoAsistencia.FALTA.ordinal()],
				porEstado[EstadoAsistencia.FALTA_JUSTIFICADA.ordinal()], conRegistro.size());
	}

	/** Revierte una justificación de la familia, con motivo y la firma de la sesión (E13). */
	@Transactional
	public void revertir(long marcaId, String motivo) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		MarcaAsistencia vigente = marcas.findById(marcaId).orElseThrow(ServicioAsistencia::marcaNoEncontrada);
		if (marcas.existsByReemplazaId(vigente.getId())) {
			throw new ReglaNegocioException("Esa marca ya cambió: abre la lista de nuevo.");
		}
		if (vigente.getOrigen() != OrigenMarca.JUSTIFICACION) {
			throw new ReglaNegocioException("Solo se revierte una falta que justificó la familia.");
		}
		if (docentes.es(persona.getUsername(), vigente.getSeccionId())) {
			throw new ReglaNegocioException("Enseñas en esta sección: la justificación la revierte otra persona de "
					+ "Dirección.");
		}
		MarcaAsistencia nueva = MarcaAsistencia.reversion(vigente, motivo, sesiones.de(persona));
		firma.firmar(ClaveFirma.marcaRevertida(vigente.getId()));
		marcas.saveAndFlush(nueva);
		auditoria.registrar(AccionAuditoria.JUSTIFICACION_REVERTIDA, ServicioAsistencia.ENTIDAD_MARCA,
				nueva.getId().toString(), vigente.getEstado().name(), nueva.getEstado().name(), persona.nombreCompleto()
						+ " revirtió la justificación de la marca " + vigente.getId() + " del " + vigente.getFecha()
						+ ". Motivo: " + nueva.getMotivo() + ". La familia recibe la corrección.");
	}

	private String quien(MarcaAsistencia m) {
		return usuarios.findByNombreUsuario(m.getCreadoPor()).map(Usuario::getNombreCompleto).orElse(m.getCreadoPor());
	}

	private static String motivoFamilia(MarcaAsistencia m) {
		return m.getMotivoJustificacion() == null ? null : m.getMotivoJustificacion().etiqueta()
				+ (m.getNotaJustificacion() == null ? "" : ": " + m.getNotaJustificacion());
	}
}
