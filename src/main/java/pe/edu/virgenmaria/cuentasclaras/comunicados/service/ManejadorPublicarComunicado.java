package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.Comunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoAdjunto;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EstadoComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoAdjuntoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 9, tanda 3 (decisión 157, B3): aprueba en la bandeja un comunicado con PDF de un docente o con un PDF que
 * requiere revisión (poco texto o imágenes). Lo aprueba el OTRO órgano: el de un docente, Dirección o Promotoría; el de
 * Dirección, Promotoría; el de Promotoría, Dirección; nunca quien lo escribió (lo exige también trg_comunicado_estado con
 * la solicitud firmada). Quien aprueba ve el PDF antes (la tarjeta enlaza a la descarga, que solo se abre para quien lo
 * puede aprobar: B8). Al aprobarse, el comunicado sale: se congelan sus destinatarios.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorPublicarComunicado implements ManejadorSolicitud {

	private final ComunicadoRepository comunicados;

	private final ComunicadoAdjuntoRepository adjuntos;

	private final EnvioComunicados envio;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ManejadorPublicarComunicado(ComunicadoRepository comunicados, ComunicadoAdjuntoRepository adjuntos,
			EnvioComunicados envio, AuditoriaService auditoria, Clock reloj) {
		this.comunicados = comunicados;
		this.adjuntos = adjuntos;
		this.envio = envio;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.PUBLICAR_COMUNICADO;
	}

	@Override
	public String entidad() {
		return EnvioComunicados.ENTIDAD;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Comunicado c = delaSolicitud(solicitud);
		UsuarioAutenticado persona = SesionDelEmisor.persona();
		if (persona.roles().stream().noneMatch(c.getEmisorRol()::loApruebaQuienTiene)) {
			throw new ReglaNegocioException("Este comunicado lo aprueba " + EnvioComunicados.quienAprueba(c.getEmisorRol())
					+ " (el otro órgano de quien lo escribió).");
		}
		c.aprobar(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		comunicados.saveAndFlush(c);
		envio.congelarDestinatarios(c);
		envio.registrarEnvio(c, java.util.Optional.ofNullable(c.getAdjuntoId()).flatMap(adjuntos::findById)
				.map(ComunicadoAdjunto::getSha256).orElse(null));
	}

	@Override
	public void alRechazar(SolicitudCambio solicitud) {
		Comunicado c = delaSolicitud(solicitud);
		c.rechazar();
		comunicados.saveAndFlush(c);
		auditoria.registrar(AccionAuditoria.COMUNICADO_RECHAZADO, EnvioComunicados.ENTIDAD, c.getId().toString(),
				"POR_APROBAR", "RECHAZADO", "Se rechazó el comunicado " + c.getId() + " de " + c.getEmisorEtiqueta()
						+ " (solicitud " + solicitud.getId() + "). No salió a las familias.");
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		List<String> lineas = new ArrayList<>();
		comunicados.findById(solicitud.getEntidadId()).ifPresent(c -> {
			lineas.add("De: " + c.getEmisorEtiqueta());
			lineas.add("Título: " + c.getTitulo());
			java.util.Optional.ofNullable(c.getAdjuntoId()).flatMap(adjuntos::findById).ifPresent(a -> lineas.add("PDF: " + a.getPaginas() + " página(s)"
					+ (a.isRequiereRevision() ? ", con poco texto o imágenes" : "") + ". Ábrelo en /comunicados/"
					+ c.getId() + "/adjunto antes de aprobar."));
		});
		return lineas;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "Abre el PDF y léelo completo antes de aprobar: si pide pagos, muestra un código QR, una cuenta o un "
				+ "celular, o habla de deudas, recházalo. Al aprobar, sale a todas las familias del alcance.";
	}

	private Comunicado delaSolicitud(SolicitudCambio solicitud) {
		Comunicado c = comunicados.findBySolicitudId(solicitud.getId())
				.filter(x -> x.getId().equals(solicitud.getEntidadId()))
				.orElseThrow(() -> new ReglaNegocioException("La solicitud no corresponde a un comunicado: recházala."));
		if (c.getEstado() != EstadoComunicado.POR_APROBAR) {
			throw new ReglaNegocioException("Ese comunicado ya no está por aprobar.");
		}
		return c;
	}
}
