package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.api.EstadoDeAvisos;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.Estadisticas;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.Comunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoDestinatario;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ConfirmacionLectura;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoDestinatarioRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ConfirmacionLecturaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 3 (sección 3.10, decisión 163, B16): destinatarias, avisadas, entregadas, leídas y confirmadas de un
 * comunicado. La lista de pendientes lleva el NOMBRE DEL ALUMNO (nunca el apoderado ni su contacto) y solo la ve quien
 * tiene derecho: Dirección, Promotoría o el docente con su asignación VIGENTE en la sección (B16). No hay botón «escribir a
 * quien no confirmó»: el recordatorio de las 48 horas lo hace el sistema. «Sin portal»: familias sin una cuenta activa.
 */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class EstadisticasComunicado {

	private static final int MAX_PENDIENTES = 300;

	private final ComunicadoDestinatarioRepository destinatarios;

	private final ConfirmacionLecturaRepository confirmaciones;

	private final ApoderadoRepository apoderados;

	private final UsuarioRepository usuarios;

	private final AlcanceDeFamilias alcances;

	private final ObjectProvider<EstadoDeAvisos> avisos;

	public EstadisticasComunicado(ComunicadoDestinatarioRepository destinatarios,
			ConfirmacionLecturaRepository confirmaciones, ApoderadoRepository apoderados, UsuarioRepository usuarios,
			AlcanceDeFamilias alcances, ObjectProvider<EstadoDeAvisos> avisos) {
		this.destinatarios = destinatarios;
		this.confirmaciones = confirmaciones;
		this.apoderados = apoderados;
		this.usuarios = usuarios;
		this.alcances = alcances;
		this.avisos = avisos;
	}

	public Estadisticas de(Comunicado c, boolean pendientesVisibles) {
		List<ComunicadoDestinatario> lista = destinatarios.findByComunicadoIdOrderByIdAsc(c.getId());
		Set<Long> confirmadas = confirmaciones.findByComunicadoId(c.getId()).stream()
				.map(ConfirmacionLectura::getFamiliaId).collect(Collectors.toSet());
		List<Long> mensajes = lista.stream().map(ComunicadoDestinatario::getAvisadoMensajeId).filter(Objects::nonNull)
				.toList();
		EstadoDeAvisos estado = avisos.getIfAvailable();
		Map<Long, EstadoDeAvisos.Entrega> entregas = estado == null || mensajes.isEmpty() ? Map.of() : estado.de(mensajes);
		int avisadas = mensajes.size();
		int entregadas = 0;
		int leidas = 0;
		for (ComunicadoDestinatario d : lista) {
			EstadoDeAvisos.Entrega e = d.getAvisadoMensajeId() == null ? null : entregas.get(d.getAvisadoMensajeId());
			if (e != null && e.entregado()) {
				entregadas++;
			}
			if (d.getAbiertoEn() != null || (e != null && e.leido())) {
				leidas++;
			}
		}
		Set<Long> familias = lista.stream().map(ComunicadoDestinatario::getFamiliaId).collect(Collectors.toSet());
		int sinPortal = familias.size() - familiasConPortal(familias).size();
		List<String> pendientes = new ArrayList<>();
		if (pendientesVisibles && c.isRequiereConfirmacion()) {
			Set<Long> faltan = new HashSet<>(familias);
			faltan.removeAll(confirmadas);
			for (Matricula m : alcances.matriculas(c.getAnioEscolarId(), AlcanceDeFamilias.Alcance.de(c))) {
				if (faltan.contains(m.getAlumno().getFamilia().getId()) && pendientes.size() < MAX_PENDIENTES) {
					pendientes.add(m.getAlumno().getNombres() + " " + m.getAlumno().getApellidoPaterno() + " ("
							+ m.getSeccion().etiqueta() + ")");
				}
			}
		}
		return new Estadisticas(lista.size(), avisadas, entregadas, leidas, confirmadas.size(), sinPortal, pendientes,
				pendientesVisibles);
	}

	/** Las familias que tienen al menos una cuenta activa del portal. */
	private Set<Long> familiasConPortal(Set<Long> familias) {
		if (familias.isEmpty()) {
			return Set.of();
		}
		Map<Long, Long> apoderadoAFamilia = apoderados.findByFamiliaIdInAndActivoTrueOrderByIdAsc(familias).stream()
				.collect(Collectors.toMap(Apoderado::getId, a -> a.getFamilia().getId(), (a, b) -> a));
		Set<Long> conPortal = new HashSet<>();
		for (Usuario u : usuarios.findByApoderadoIdIn(apoderadoAFamilia.keySet())) {
			if (u.isActivo() && apoderadoAFamilia.containsKey(u.getApoderadoId())) {
				conPortal.add(apoderadoAFamilia.get(u.getApoderadoId()));
			}
		}
		return conPortal;
	}
}
