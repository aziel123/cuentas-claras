package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.Sha256;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoSolicitado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigosEnviados;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 11, tanda 1c (decisión 216, A1c-1, A1c-5 y A1c-7): el mensaje {@code CODIGO_PROMOTORIA} o {@code CODIGO_CONTACTO}
 * al WhatsApp REGISTRADO de la cuenta, en la misma transacción del pedido, con la SHA-256 del código y la huella de la
 * acción. Sus parámetros no llevan el código: el primero es un marcador que el envío reemplaza ({@code DespachoMensajes}).
 * Y el puerto {@link CodigosEnviados}.
 */
@Component
public class CodigosMensajes implements CodigosEnviados {

	private final CreadorMensajes creador;

	private final UsuarioRepository usuarios;

	private final MensajeRepository mensajes;

	private final DerivadorSecreto derivador;

	public CodigosMensajes(CreadorMensajes creador, UsuarioRepository usuarios, MensajeRepository mensajes,
			DerivadorSecreto derivador) {
		this.creador = creador;
		this.usuarios = usuarios;
		this.mensajes = mensajes;
		this.derivador = derivador;
	}

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void alPedirCodigo(CodigoSolicitado pedido) {
		Usuario cuenta = usuarios.findById(pedido.usuarioId())
				.orElseThrow(() -> new IllegalStateException("La cuenta del pedido no existe"));
		TipoMensaje tipo = pedido.contacto() ? TipoMensaje.CODIGO_CONTACTO : TipoMensaje.CODIGO_PROMOTORIA;
		PlantillaMensaje plantilla = pedido.contacto() ? PlantillaMensaje.CODIGO_CONTACTO : PlantillaMensaje.CODIGO_PROMOTORIA;
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(tipo, plantilla,
				List.of(CodigosFacultadMensajes.MARCADOR_CODIGO, pedido.accion(), pedido.venceA() == null ? "" : pedido.venceA()),
				"usuario", cuenta.getId());
		long colegio = ContextoColegio.actual();
		Mensaje mensaje = creador.codigoPorWhatsapp(cuenta, contenido, pedido.claveBase(),
				clave -> Sha256.hex(derivador.codigoPromotoria(colegio, clave)), pedido.huella())
				.orElseThrow(() -> new ReglaNegocioException("Tu cuenta no tiene un celular con WhatsApp registrado: el código "
						+ "no tiene a dónde llegar."));
		pedido.registrar(mensaje.getId(), mensaje.getCreadoEn(), "WhatsApp " + Enmascarar.telefono(mensaje.getDestino()));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<CodigoEnviado> buscar(Long mensajeId) {
		return mensajeId == null ? Optional.empty() : mensajes.findById(mensajeId)
				.filter(m -> m.getTipo() == TipoMensaje.CODIGO_PROMOTORIA || m.getTipo() == TipoMensaje.CODIGO_CONTACTO)
				.map(m -> new CodigoEnviado(m.getId(), m.getUsuarioId(), m.getTipo() == TipoMensaje.CODIGO_CONTACTO,
						m.getCodigoSha256(), m.getHuellaAccion(), m.getCreadoEn(), m.getClave()));
	}
}
