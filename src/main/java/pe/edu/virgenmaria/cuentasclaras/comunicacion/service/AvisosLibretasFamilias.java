package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.Consentimientos;
import pe.edu.virgenmaria.cuentasclaras.academico.api.LibretasPorAvisar;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 3 (secciones 3.7 y 3.8; decisión 180, E30): los avisos de libretas a las familias, que crea SOLO
 * {@code sistema.libretas} (trg_mensaje_nace). UN aviso por familia y periodo, con los hermanos juntos:
 * {@code LIBRETA_DISPONIBLE} si las vigentes son la versión 1 y {@code LIBRETA_CORREGIDA} si alguna es mayor (también en el
 * atraso de la tanda 2: la 1 y la 2 en la misma pasada dan UN solo mensaje). A cada apoderado activo: por WhatsApp con su
 * «sí» a la finalidad LIBRETAS; si no, por correo salvo oposición. Nunca notas, conclusiones ni montos: solo el periodo y
 * los primeros nombres (si un nombre calza con la regla de cobranza, «su hijo o hija»). La clave idempotente lleva la
 * libreta más nueva: una versión nueva es un aviso nuevo y una pasada repetida no duplica.
 */
@Service
@PreAuthorize("hasRole('SISTEMA_LIBRETAS')")
@Transactional(propagation = Propagation.MANDATORY)
public class AvisosLibretasFamilias {

	private static final Logger LOG = LoggerFactory.getLogger(AvisosLibretasFamilias.class);

	/** Las libretas de los últimos días que se consideran por avisar (lo más viejo ya se avisó). */
	static final int DIAS_ATRAS = 30;

	private final LibretasPorAvisar libretas;

	private final Consentimientos consentimientos;

	private final ApoderadoRepository apoderados;

	private final MensajeRepository mensajes;

	private final CreadorMensajes creador;

	private final AuditoriaService auditoria;

	public AvisosLibretasFamilias(LibretasPorAvisar libretas, Consentimientos consentimientos,
			ApoderadoRepository apoderados, MensajeRepository mensajes, CreadorMensajes creador,
			AuditoriaService auditoria) {
		this.libretas = libretas;
		this.consentimientos = consentimientos;
		this.apoderados = apoderados;
		this.mensajes = mensajes;
		this.creador = creador;
		this.auditoria = auditoria;
	}

	/** @return cuántas familias recibieron aviso en esta pasada */
	public int pasada(LocalDateTime ahora) {
		List<LibretasPorAvisar.PorAvisar> pendientes = libretas.emitidasDesde(ahora.minusDays(DIAS_ATRAS));
		if (pendientes.isEmpty()) {
			return 0;
		}
		Map<Long, List<Apoderado>> porFamilia = apoderados.findByFamiliaIdInAndActivoTrueOrderByIdAsc(pendientes.stream()
				.map(LibretasPorAvisar.PorAvisar::familiaId).distinct().toList()).stream()
				.collect(Collectors.groupingBy(a -> a.getFamilia().getId(), LinkedHashMap::new, Collectors.toList()));
		int avisadas = 0;
		for (LibretasPorAvisar.PorAvisar p : pendientes) {
			TipoMensaje tipo = p.corregida() ? TipoMensaje.LIBRETA_CORREGIDA : TipoMensaje.LIBRETA_DISPONIBLE;
			String sufijo = "P" + p.periodoId() + "-L" + p.ultimaLibretaId();
			if (mensajes.existsByTipoAndFamiliaIdAndClaveEndingWith(tipo, p.familiaId(), ":" + sufijo)) {
				continue;
			}
			List<String> parametros = List.of(p.periodo(), nombres(p.nombres()));
			if (ContenidoSinCobranza.hablaDeCobranza(String.join("\n", parametros))) {
				parametros = List.of(p.periodo(), p.nombres().size() > 1 ? "sus hijos" : "su hijo o hija");
			}
			if (ContenidoSinCobranza.hablaDeCobranza(String.join("\n", parametros))) {
				LOG.warn("El aviso de libreta de la familia {} calzó con la regla de cobranza: no se avisó.", p.familiaId());
				continue;
			}
			CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(tipo,
					p.corregida() ? PlantillaMensaje.LIBRETA_CORREGIDA : PlantillaMensaje.LIBRETA, parametros, "familia",
					p.familiaId());
			Optional<Mensaje> primero = Optional.empty();
			for (Apoderado a : porFamilia.getOrDefault(p.familiaId(), List.of())) {
				Optional<Mensaje> creado = creador.paraApoderadoConConsentimiento(a, contenido, sufijo,
						consentimientos.recibeLibretas(a.getId(), true), consentimientos.recibeLibretas(a.getId(), false));
				if (primero.isEmpty()) {
					primero = creado;
				}
			}
			if (primero.isPresent()) {
				avisadas++;
			}
		}
		if (avisadas > 0) {
			auditoria.registrar(AccionAuditoria.AVISOS_LIBRETAS_ENVIADOS, "familia", null, null, avisadas + " familia(s)",
					"Avisos de libretas: " + avisadas + " familia(s) (la libreta está en el portal; sin notas).");
		}
		return avisadas;
	}

	/** «Mateo», «Mateo y Valeria», «Mateo, Lucía y Valeria». */
	static String nombres(List<String> lista) {
		if (lista.isEmpty()) {
			return "su hijo o hija";
		}
		if (lista.size() == 1) {
			return lista.get(0);
		}
		List<String> copia = new ArrayList<>(lista);
		String ultimo = copia.remove(copia.size() - 1);
		return String.join(", ", copia) + " y " + ultimo;
	}
}
