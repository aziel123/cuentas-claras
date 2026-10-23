package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comunicados.api.ComunicadosPorAvisar;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.Comunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoDestinatario;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EstadoComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoDestinatarioRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ConfirmacionLecturaRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 3 (sección 9): lo que lee {@code comunicacion} para avisar los comunicados (sin su texto; el título solo
 * de los urgentes, que escribe Dirección o Promotoría y pasó por el filtro) y donde anota qué mensaje avisó y cuál
 * recordó. Solo {@code sistema.comunicados}. Uno retirado o por aprobar no se avisa.
 */
@Service
@PreAuthorize("hasRole('SISTEMA_COMUNICADOS')")
public class AvisosDeComunicados implements ComunicadosPorAvisar {

	private final ComunicadoRepository comunicados;

	private final ComunicadoDestinatarioRepository destinatarios;

	private final ConfirmacionLecturaRepository confirmaciones;

	public AvisosDeComunicados(ComunicadoRepository comunicados, ComunicadoDestinatarioRepository destinatarios,
			ConfirmacionLecturaRepository confirmaciones) {
		this.comunicados = comunicados;
		this.destinatarios = destinatarios;
		this.confirmaciones = confirmaciones;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Pendiente> sinAvisar(LocalDateTime desde) {
		List<ComunicadoDestinatario> sin = destinatarios.findByAvisadoMensajeIdIsNullAndCreadoEnGreaterThanEqualOrderByIdAsc(
				desde);
		Map<Long, Comunicado> porId = porId(sin);
		List<Pendiente> pendientes = new ArrayList<>();
		for (ComunicadoDestinatario d : sin) {
			Comunicado c = porId.get(d.getComunicadoId());
			if (c != null && c.getEstado() == EstadoComunicado.ENVIADO) {
				pendientes.add(new Pendiente(d.getId(), c.getId(), d.getFamiliaId(), c.getEmisorEtiqueta(), c.isUrgente(),
						c.isUrgente() ? c.getTitulo() : null, c.getEnviadoEn()));
			}
		}
		return pendientes;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PorRecordar> porRecordar(LocalDateTime antesDe) {
		List<ComunicadoDestinatario> avisados = destinatarios
				.findByAvisadoMensajeIdIsNotNullAndRecordadoMensajeIdIsNullAndCreadoEnLessThanOrderByIdAsc(antesDe);
		Map<Long, Comunicado> porId = porId(avisados);
		List<PorRecordar> resultado = new ArrayList<>();
		for (ComunicadoDestinatario d : avisados) {
			Comunicado c = porId.get(d.getComunicadoId());
			if (c != null && c.getEstado() == EstadoComunicado.ENVIADO && c.isRequiereConfirmacion()
					&& !confirmaciones.existsByComunicadoIdAndFamiliaId(c.getId(), d.getFamiliaId())) {
				resultado.add(new PorRecordar(d.getId(), c.getId(), d.getFamiliaId(), c.getEmisorEtiqueta()));
			}
		}
		return resultado;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void avisado(Long destinatarioId, Long mensajeId) {
		destinatarios.findById(destinatarioId).ifPresent(d -> {
			if (d.getAvisadoMensajeId() == null) {
				d.avisadoPor(mensajeId);
				destinatarios.saveAndFlush(d);
			}
		});
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordado(Long destinatarioId, Long mensajeId) {
		destinatarios.findById(destinatarioId).ifPresent(d -> {
			if (d.getRecordadoMensajeId() == null) {
				d.recordadoPor(mensajeId);
				destinatarios.saveAndFlush(d);
			}
		});
	}

	private Map<Long, Comunicado> porId(List<ComunicadoDestinatario> lista) {
		if (lista.isEmpty()) {
			return Map.of();
		}
		return comunicados.findByIdIn(lista.stream().map(ComunicadoDestinatario::getComunicadoId).distinct().toList())
				.stream().collect(Collectors.toMap(Comunicado::getId, Function.identity()));
	}
}
