package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.Consentimientos;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Canal;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConsentimientoAviso;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Finalidad;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.ConsentimientoAvisoRepository;

/**
 * Sprint 9, tanda 2 (decisión 152): el consentimiento vigente (la última fila) para los avisos de asistencia. Sin fila,
 * WhatsApp vale «no» (el opt-in de Meta es explícito) y el correo vale «sí» (salvo oposición). Lo leen la mensajería
 * ({@code sistema.asistencia} al crear el aviso y {@code sistema.mensajeria} al crear su respaldo por correo).
 */
@Service
@PreAuthorize("hasAnyRole('SISTEMA_ASISTENCIA','SISTEMA_COMUNICADOS','SISTEMA_LIBRETAS','SISTEMA_MENSAJERIA','APODERADO','DIRECTOR','ADMINISTRACION')")
public class ConsentimientosVigentes implements Consentimientos {

	private final ConsentimientoAvisoRepository consentimientos;

	public ConsentimientosVigentes(ConsentimientoAvisoRepository consentimientos) {
		this.consentimientos = consentimientos;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean recibeAsistencia(long apoderadoId, boolean porWhatsapp) {
		Canal canal = porWhatsapp ? Canal.WHATSAPP : Canal.CORREO;
		return consentimientos.findFirstByApoderadoIdAndFinalidadAndCanalOrderByIdDesc(apoderadoId, Finalidad.ASISTENCIA,
				canal).map(ConsentimientoAviso::isOtorgado).orElse(canal.salvoOposicion());
	}

	@Override
	@Transactional(readOnly = true)
	public boolean recibeComunicados(long apoderadoId, boolean porWhatsapp) {
		Canal canal = porWhatsapp ? Canal.WHATSAPP : Canal.CORREO;
		return consentimientos.findFirstByApoderadoIdAndFinalidadAndCanalOrderByIdDesc(apoderadoId, Finalidad.COMUNICADOS,
				canal).map(ConsentimientoAviso::isOtorgado).orElse(canal.salvoOposicion());
	}

	@Override
	@Transactional(readOnly = true)
	public boolean recibeLibretas(long apoderadoId, boolean porWhatsapp) {
		Canal canal = porWhatsapp ? Canal.WHATSAPP : Canal.CORREO;
		return consentimientos.findFirstByApoderadoIdAndFinalidadAndCanalOrderByIdDesc(apoderadoId, Finalidad.LIBRETAS,
				canal).map(ConsentimientoAviso::isOtorgado).orElse(canal.salvoOposicion());
	}

	@Override
	@Transactional(readOnly = true)
	public java.util.Optional<Long> contactoPrincipal(long familiaId) {
		java.util.Set<Long> vistos = new java.util.HashSet<>();
		for (ConsentimientoAviso c : consentimientos.findByFamiliaIdOrderByIdDesc(familiaId)) {
			if (c.getFinalidad() != Finalidad.COMUNICADOS || c.getCanal() != Canal.WHATSAPP || !vistos.add(c.getApoderadoId())) {
				continue;
			}
			if (c.isOtorgado() && c.isContactoPrincipal()) {
				return java.util.Optional.of(c.getApoderadoId());
			}
		}
		return java.util.Optional.empty();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean esContactoPrincipal(long apoderadoId) {
		return consentimientos.findFirstByApoderadoIdAndFinalidadAndCanalOrderByIdDesc(apoderadoId, Finalidad.COMUNICADOS,
				Canal.WHATSAPP).map(c -> c.isOtorgado() && c.isContactoPrincipal()).orElse(false);
	}
}
