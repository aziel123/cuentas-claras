package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.RenovacionMatricula;
import pe.edu.virgenmaria.cuentasclaras.matricula.repository.RenovacionMatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.RenovacionRespondida;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.RenovacionesAbiertas;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioRenovacionFamilia;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mensajes de la matrícula del año siguiente y del portal (sprint 5, tanda 2), en la misma transacción de lo que los
 * origina:
 * <ul>
 *   <li>{@link RenovacionesAbiertas}: invitación al responsable de pago de cada alumno («confirme si continúa»).</li>
 *   <li>{@link RenovacionRespondida} en persona: aviso a TODOS los apoderados activos, por ambos canales («si usted no lo
 *       pidió, avísenos»; G16). La respuesta en el portal no genera mensaje: la dio la misma familia.</li>
 *   <li>{@link #avisoAtendido}: Promotoría o Dirección respondió un «¿Algo no cuadra?».</li>
 * </ul>
 * Al alumno se le nombra solo por su nombre de pila (Ley 29733).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AvisosMatricula {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final CreadorMensajes creador;

	private final RenovacionMatriculaRepository renovaciones;

	private final ApoderadoRepository apoderados;

	public AvisosMatricula(CreadorMensajes creador, RenovacionMatriculaRepository renovaciones,
			ApoderadoRepository apoderados) {
		this.creador = creador;
		this.renovaciones = renovaciones;
		this.apoderados = apoderados;
	}

	@EventListener
	public void alAbrirRenovaciones(RenovacionesAbiertas evento) {
		for (Long id : evento.renovacionIds()) {
			RenovacionMatricula r = renovaciones.findById(id)
					.orElseThrow(() -> new IllegalStateException("La renovación " + id + " no existe"));
			Apoderado responsable = r.getAlumno().getResponsablePago();
			if (!responsable.isActivo()) {
				continue;
			}
			creador.paraApoderado(responsable, new CreadorMensajes.Contenido(TipoMensaje.RENOVACION_MATRICULA,
					PlantillaMensaje.RENOVACION, List.of(ServicioRenovacionFamilia.nombreDePila(r.getAlumno().getNombres()),
							r.getGradoDestino().etiqueta(), Integer.toString(r.getAnioDestino().getAnio()),
							r.getVenceEn().format(FECHA)), "renovacion_matricula", r.getId()), false, null);
		}
	}

	@EventListener
	public void alResponderRenovacion(RenovacionRespondida evento) {
		if (!evento.presencial()) {
			return;
		}
		RenovacionMatricula r = renovaciones.findById(evento.renovacionId())
				.orElseThrow(() -> new IllegalStateException("La renovación " + evento.renovacionId() + " no existe"));
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.RENOVACION_REGISTRADA,
				PlantillaMensaje.RENOVACION_REGISTRADA, List.of(
						ServicioRenovacionFamilia.nombreDePila(r.getAlumno().getNombres()),
						evento.continua() ? "continúa en " + r.getGradoDestino().etiqueta() : "no continuará",
						Integer.toString(r.getAnioDestino().getAnio())),
				"renovacion_matricula", r.getId());
		apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(r.getFamiliaId()).stream().filter(Apoderado::isActivo)
				.forEach(a -> creador.paraApoderado(a, contenido, true, null));
	}

	/** Promotoría o Dirección respondió el aviso: se avisa a quien lo envió (la respuesta se ve en el portal). */
	public void avisoAtendido(Long apoderadoId, Long avisoId, LocalDate enviadoEl) {
		apoderados.findById(apoderadoId).filter(Apoderado::isActivo).ifPresent(a -> creador.paraApoderado(a,
				new CreadorMensajes.Contenido(TipoMensaje.AVISO_ATENDIDO, PlantillaMensaje.AVISO_ATENDIDO,
						List.of(enviadoEl.format(FECHA)), "aviso_familia", avisoId), false, null));
	}
}
