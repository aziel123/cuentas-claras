package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificacionDeContenido;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.Comunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoAdjunto;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoDestinatario;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EstadoComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoAdjuntoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoDestinatarioRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 3 (E19 y B11).
 * <ul>
 *   <li><b>E19:</b> «Verificar integridad» de la bitácora recalcula la huella de cada comunicado (con el SHA-256 de su PDF
 *       RECALCULADO desde los bytes guardados) y exige el evento de la bitácora encadenada con esa huella. Si el DBA cambia
 *       un título, un texto o el PDF, la huella ya no coincide con la de ningún evento y se detecta.</li>
 *   <li><b>B11:</b> al enviarse, compara los destinatarios congelados con las familias de las matrículas ACTIVAS del alcance
 *       (o, en una fe de erratas, con los del original). Si no coinciden, un evento resaltado para Promotoría.</li>
 * </ul>
 */
@Component
public class IntegridadComunicados implements VerificacionDeContenido {

	private static final int MAX_REVISADOS = 5000;

	private static final Set<AccionAuditoria> ENVIOS = Set.of(AccionAuditoria.COMUNICADO_ENVIADO,
			AccionAuditoria.FE_DE_ERRATAS_ENVIADA, AccionAuditoria.COMUNICADO_POR_APROBAR);

	private final ComunicadoRepository comunicados;

	private final ComunicadoAdjuntoRepository adjuntos;

	private final ComunicadoDestinatarioRepository destinatarios;

	private final AlcanceDeFamilias alcances;

	private final AuditoriaService auditoria;

	public IntegridadComunicados(ComunicadoRepository comunicados, ComunicadoAdjuntoRepository adjuntos,
			ComunicadoDestinatarioRepository destinatarios, AlcanceDeFamilias alcances, AuditoriaService auditoria) {
		this.comunicados = comunicados;
		this.adjuntos = adjuntos;
		this.destinatarios = destinatarios;
		this.alcances = alcances;
		this.auditoria = auditoria;
	}

	/** E19: los comunicados cuyo contenido ya no coincide con la huella que quedó en la bitácora. */
	@Override
	@Transactional(readOnly = true)
	public List<String> problemas() {
		List<String> problemas = new ArrayList<>();
		for (Comunicado c : comunicados.findAllByOrderByIdDesc(Limit.of(MAX_REVISADOS))) {
			String sha = null;
			if (c.getAdjuntoId() != null) {
				ComunicadoAdjunto adjunto = adjuntos.findById(c.getAdjuntoId()).orElse(null);
				if (adjunto == null) {
					problemas.add("Al comunicado " + c.getId() + " le falta su PDF.");
					continue;
				}
				sha = HuellaComunicado.sha256(adjunto.getContenido());
				if (!sha.equals(adjunto.getSha256())) {
					problemas.add("El PDF del comunicado " + c.getId() + " cambió después de enviarse.");
					continue;
				}
			}
			String huella = HuellaComunicado.de(c, sha);
			boolean conEvento = !huella.equals(c.getHuellaContenido()) ? false
					: ENVIOS.stream().anyMatch(a -> auditoria.existe(a, EnvioComunicados.ENTIDAD, c.getId().toString(),
							huella));
			if (!conEvento) {
				problemas.add("El comunicado " + c.getId() + " no coincide con la huella con la que se envió (título, "
						+ "texto, alcance o PDF cambiados fuera de la aplicación).");
			}
		}
		return problemas;
	}

	/** B11: los destinatarios congelados son exactamente las familias del alcance (o las del original). */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean revisarDestinatarios(Comunicado c) {
		if (c.getEstado() != EstadoComunicado.ENVIADO) {
			return true;
		}
		Set<Long> congelados = destinatarios.findByComunicadoIdOrderByIdAsc(c.getId()).stream()
				.map(ComunicadoDestinatario::getFamiliaId).collect(Collectors.toSet());
		Set<Long> esperados;
		if (c.esFeDeErratas()) {
			esperados = destinatarios.findByComunicadoIdOrderByIdAsc(c.getCorrigeId()).stream()
					.map(ComunicadoDestinatario::getFamiliaId).collect(Collectors.toSet());
		}
		else {
			Map<Long, Integer> familias = alcances.familias(c.getAnioEscolarId(), AlcanceDeFamilias.Alcance.de(c));
			esperados = familias.keySet();
		}
		boolean coinciden = congelados.equals(esperados) && c.getDestinatarios() != null
				&& c.getDestinatarios() == congelados.size();
		if (!coinciden) {
			auditoria.registrar(AccionAuditoria.COMUNICADO_DESTINATARIOS_DESCUADRADOS, EnvioComunicados.ENTIDAD,
					c.getId().toString(), String.valueOf(esperados.size()), String.valueOf(congelados.size()),
					"Revisar: el comunicado " + c.getId() + " quedó con " + congelados.size() + " familia(s) y su alcance "
							+ "tiene " + esperados.size() + ".");
		}
		return coinciden;
	}
}
