package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoOperadores;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Correcciones del sprint 8 (S8-A1, S8-A2 y S8-M5): convierte cada {@link AvisoPlataforma} en mensajes, en la MISMA
 * transacción del cambio (como los avisos de pago). Cada destinatario recibe el aviso en su contacto REGISTRADO en ese
 * momento: el titular (por cada canal que tiene), el correo institucional del colegio, el correo externo de control y toda
 * la Promotoría activa. Si no se pudo crear ninguno, lanza {@link ReglaNegocioException} y el cambio no se guarda: el
 * operador nunca cambia algo de un colegio en silencio. En MySQL, trg_mensaje_nace exige que cada correo externo sea el
 * institucional o el de control de ESE colegio.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AvisosPlataforma {

	private final CreadorMensajes creador;

	private final UsuarioRepository usuarios;

	private final ColegioRepository colegios;

	private final ConfiguracionColegioRepository configuracion;

	public AvisosPlataforma(CreadorMensajes creador, UsuarioRepository usuarios, ColegioRepository colegios,
			ConfiguracionColegioRepository configuracion) {
		this.creador = creador;
		this.usuarios = usuarios;
		this.colegios = colegios;
		this.configuracion = configuracion;
	}

	@EventListener
	public void alAvisar(AvisoPlataforma aviso) {
		if (!Objects.equals(ContextoColegio.actual(), aviso.colegioId())) {
			throw new IllegalStateException("El aviso de la plataforma se crea con el colegio " + aviso.colegioId()
					+ " en contexto");
		}
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(tipoMensaje(aviso.tipo()),
				plantilla(aviso.tipo()), aviso.parametros(), aviso.entidad(), aviso.entidadId());
		String sufijo = aviso.tipo().codigo() + (aviso.sufijo() == null ? "" : ":" + aviso.sufijo());
		List<Mensaje> creados = new ArrayList<>();
		Set<Long> avisados = new TreeSet<>();
		if (aviso.titularId() != null) {
			usuarios.findById(aviso.titularId()).ifPresent(titular -> {
				avisados.add(titular.getId());
				creador.paraUsuarioPorCanal(titular, CanalMensaje.WHATSAPP, contenido, sufijo).ifPresent(creados::add);
				creador.paraUsuarioPorCanal(titular, CanalMensaje.CORREO, contenido, sufijo).ifPresent(creados::add);
			});
		}
		if (aviso.promotoria()) {
			for (Usuario promotoria : usuarios.activosConRol(Rol.PROMOTOR)) {
				if (promotoria.getApoderadoId() == null && avisados.add(promotoria.getId())) {
					creador.paraUsuario(promotoria, contenido, sufijo).ifPresent(creados::add);
				}
			}
		}
		Set<String> correos = new TreeSet<>();
		if (aviso.institucional()) {
			colegios.findById(aviso.colegioId()).map(Colegio::getCorreoInstitucional).map(String::strip)
					.filter(c -> c.contains("@") && correos.add(c.toLowerCase(Locale.ROOT)))
					.flatMap(c -> creador.externo(c, contenido, sufijo + ":I")).ifPresent(creados::add);
		}
		if (aviso.control()) {
			configuracion.findByColegioIdAndClave(aviso.colegioId(), ConfiguracionColegio.HUELLA_CORREO_EXTERNO)
					.map(ConfiguracionColegio::getValor).map(String::strip)
					.filter(c -> c.contains("@") && correos.add(c.toLowerCase(Locale.ROOT)))
					.flatMap(c -> creador.externo(c, contenido, sufijo + ":C")).ifPresent(creados::add);
		}
		if (creados.isEmpty()) {
			throw new ReglaNegocioException("No hay a quién avisar de este cambio (ni el titular, ni el correo institucional, "
					+ "ni el correo de control, ni la Promotoría tienen un contacto registrado). No se guardó nada.");
		}
	}

	/**
	 * Sprint 11, tanda 1 (sección 9): el aviso a cada operador activo de la plataforma con un contacto registrado, en su
	 * colegio (el de plataforma). Va en una transacción aparte de la del colegio (es otro colegio): el testigo que impide
	 * el cambio en silencio es el aviso al colegio (titular, Promotoría y correo de control), que va en la misma; este se
	 * suma a la bitácora de la plataforma. Devuelve cuántos creó.
	 */
	@EventListener
	public void alAvisarOperadores(AvisoOperadores aviso) {
		if (!Objects.equals(ContextoColegio.actual(), aviso.plataformaId())) {
			throw new IllegalStateException("El aviso a los operadores se crea con el colegio de plataforma en contexto");
		}
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(tipoMensaje(aviso.tipo()),
				plantilla(aviso.tipo()), aviso.parametros(), aviso.entidad(), aviso.entidadId());
		String sufijo = "OP:" + aviso.tipo().codigo() + (aviso.sufijo() == null ? "" : ":" + aviso.sufijo());
		int creados = 0;
		for (Usuario operador : usuarios.activosConRol(Rol.OPERADOR_PLATAFORMA)) {
			if (creador.paraUsuario(operador, contenido, sufijo).isPresent()) {
				creados++;
			}
		}
		if (creados == 0) {
			org.slf4j.LoggerFactory.getLogger(AvisosPlataforma.class).warn("Ningún operador activo tiene un contacto "
					+ "registrado: el aviso {} queda solo en la bitácora de la plataforma.", aviso.tipo());
		}
	}

	private static PlantillaMensaje plantilla(AvisoPlataforma.Tipo tipo) {
		return switch (tipo) {
			case ENLACE_TITULAR_REENVIADO -> PlantillaMensaje.ENLACE_TITULAR_REENVIADO;
			case CONTACTO_TITULAR_CORREGIDO -> PlantillaMensaje.CONTACTO_TITULAR_CORREGIDO;
			case FICHA_CORREGIDA -> PlantillaMensaje.FICHA_COLEGIO_CORREGIDA;
			case CORREO_CONTROL_PROPUESTO -> PlantillaMensaje.CORREO_CONTROL_PROPUESTO;
			case CORREO_CONTROL_CAMBIADO -> PlantillaMensaje.CORREO_CONTROL_CAMBIADO;
			case CAMBIO_ESTADO_PROPUESTO -> PlantillaMensaje.CAMBIO_ESTADO_PROPUESTO;
			case CAMBIO_ESTADO_APROBADO -> PlantillaMensaje.CAMBIO_ESTADO_APROBADO;
			case CAMBIO_ESTADO_RESUELTO -> PlantillaMensaje.CAMBIO_ESTADO_RESUELTO;
			case CAMBIO_ESTADO_APLICADO -> PlantillaMensaje.CAMBIO_ESTADO_APLICADO;
			case CAMBIO_ESTADO_EN_ESPERA -> PlantillaMensaje.CAMBIO_ESTADO_EN_ESPERA;
			case SUSPENSION_PROXIMA -> PlantillaMensaje.CAMBIO_ESTADO_PROXIMO;
			case OBJECION_TITULAR -> PlantillaMensaje.OBJECION_TITULAR;
			case OBJECION_RESPONDIDA -> PlantillaMensaje.OBJECION_RESPONDIDA;
			case FACULTAD_TITULAR_USADA -> PlantillaMensaje.FACULTAD_TITULAR_USADA;
			case ACCION_DIRECTA -> PlantillaMensaje.ACCION_DIRECTA;
			case DIRECTA_EN_ESPERA -> PlantillaMensaje.DIRECTA_EN_ESPERA;
			case DIRECTA_FRENADA -> PlantillaMensaje.DIRECTA_FRENADA;
			case CONCILIACION_BLOQUEADA -> PlantillaMensaje.CONCILIACION_BLOQUEADA;
		};
	}

	private static TipoMensaje tipoMensaje(AvisoPlataforma.Tipo tipo) {
		return switch (tipo) {
			case CORREO_CONTROL_PROPUESTO, CORREO_CONTROL_CAMBIADO -> TipoMensaje.CORREO_CONTROL;
			default -> TipoMensaje.AVISO_ALTA;
		};
	}
}
