package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.InasistenciasDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AvisoAsistenciaCorte;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConfiguracionAcademica;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MarcaAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.OrigenMarca;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AvisoAsistenciaCorteRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.MarcaAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 9, tanda 2 (secciones 3.6 y 9; A1 y A2): lo que lee el proceso de avisos de asistencia
 * ({@code comunicacion.proceso.AvisosAsistencia}, como {@code sistema.asistencia}) por {@link InasistenciasDelDia}. Solo
 * el primer nombre del alumno, su familia, el estado y la hora: nunca apellidos, sección, documentos ni nada de cobranza.
 * La marca de agua del colegio ({@link AvisoAsistenciaCorte}) solo la escribe este actor (con {@code cc_app}: 1142).
 */
@Service
@PreAuthorize("hasRole('SISTEMA_ASISTENCIA')")
@Transactional(propagation = Propagation.MANDATORY)
public class AvisosDeAsistencia implements InasistenciasDelDia {

	private final MarcaAsistenciaRepository marcas;

	private final AvisoAsistenciaCorteRepository cortes;

	private final AlumnoRepository alumnos;

	private final UsuarioRepository usuarios;

	private final ConfiguracionesAcademicas configuraciones;

	public AvisosDeAsistencia(MarcaAsistenciaRepository marcas, AvisoAsistenciaCorteRepository cortes,
			AlumnoRepository alumnos, UsuarioRepository usuarios, ConfiguracionesAcademicas configuraciones) {
		this.marcas = marcas;
		this.cortes = cortes;
		this.alumnos = alumnos;
		this.usuarios = usuarios;
		this.configuraciones = configuraciones;
	}

	@Override
	public Horas horas() {
		ConfiguracionAcademica c = configuraciones.vigente();
		return new Horas(c.getHoraAviso(), c.getHoraSegundoAviso());
	}

	@Override
	public Corte corte() {
		return cortes.findById(ContextoColegio.actual()).map(c -> new Corte(c.getUltimaMarcaId(), c.ultimoEnvio()))
				.orElse(new Corte(0, null));
	}

	@Override
	public List<Inasistencia> delDia(LocalDate fecha) {
		Map<Long, Optional<Alumno>> deAlumno = new HashMap<>();
		List<Inasistencia> lista = new ArrayList<>();
		for (MarcaAsistencia m : marcas.vigentesDelDia(fecha)) {
			if (!m.getEstado().seAvisa()) {
				continue;
			}
			Optional<Alumno> alumno = deAlumno.computeIfAbsent(m.getAlumnoId(), alumnos::findById);
			alumno.ifPresent(a -> lista.add(new Inasistencia(m.getId(), a.getFamilia().getId(), a.getId(),
					NombreCorto.primerNombre(a.getNombres()), m.getFecha(), m.getEstado().enElAviso(), m.getHoraLlegada())));
		}
		return lista;
	}

	@Override
	public List<Cambio> despuesDe(long ultimaMarcaId) {
		Map<String, List<MarcaAsistencia>> cadenas = new LinkedHashMap<>();
		for (MarcaAsistencia m : marcas.findByIdGreaterThanOrderByIdAsc(ultimaMarcaId)) {
			cadenas.computeIfAbsent(m.getRegistroId() + ":" + m.getAlumnoId(), k -> new ArrayList<>()).add(m);
		}
		Map<Long, Optional<Alumno>> deAlumno = new HashMap<>();
		List<Cambio> cambios = new ArrayList<>();
		for (List<MarcaAsistencia> cadena : cadenas.values()) {
			MarcaAsistencia primera = cadena.getFirst();
			MarcaAsistencia vigente = cadena.getLast();
			boolean registroNuevo = primera.getOrigen() == OrigenMarca.REGISTRO;
			String antes = registroNuevo ? "sin registro" : marcas.findById(primera.getReemplazaId())
					.map(a -> a.getEstado().enElAviso()).orElse("sin registro");
			String ahora = vigente.getEstado().enElAviso();
			if (!registroNuevo && antes.equals(ahora)) {
				continue;
			}
			Optional<Alumno> alumno = deAlumno.computeIfAbsent(vigente.getAlumnoId(), alumnos::findById);
			alumno.ifPresent(a -> cambios.add(new Cambio(vigente.getId(), a.getFamilia().getId(), a.getId(),
					NombreCorto.primerNombre(a.getNombres()), vigente.getFecha(), antes, ahora,
					vigente.getEstado().seAvisa(), registroNuevo, quien(vigente), vigente.getHoraLlegada())));
		}
		return cambios;
	}

	@Override
	public long ultimaMarcaId() {
		Long ultimo = marcas.ultimoId();
		return ultimo == null ? 0 : ultimo;
	}

	@Override
	public void marcarAvisado(long hastaMarcaId, LocalDateTime enviadoEn) {
		Long colegio = ContextoColegio.actual();
		AvisoAsistenciaCorte corte = cortes.bloquear(colegio).orElseGet(() -> AvisoAsistenciaCorte.nuevo(colegio,
				enviadoEn));
		corte.registrarEnvio(hastaMarcaId, enviadoEn);
		cortes.saveAndFlush(corte);
	}

	/** «Prof. Ana T.» si lo registró el personal; «su familia» si fue una justificación desde el portal. */
	private String quien(MarcaAsistencia m) {
		Optional<Usuario> cuenta = usuarios.findByNombreUsuario(m.getCreadoPor());
		if (cuenta.isEmpty()) {
			return "el colegio";
		}
		if (cuenta.get().getApoderadoId() != null) {
			return "su familia";
		}
		return NombreCorto.docente(cuenta.get().getNombreCompleto());
	}
}
