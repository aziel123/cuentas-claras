package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoFacultadPedido;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CorreoControlRegistrado;

import java.util.List;

/**
 * Sprint 11, tanda 1 (AP-2): el mensaje {@code CODIGO_FACULTAD} al contacto registrado del titular (WhatsApp si tiene
 * celular; si no, correo), en la misma transacción del pedido. Sus parámetros no llevan el código: el segundo es un
 * marcador que el envío reemplaza por el código derivado ({@code DespachoMensajes}). Y el puerto
 * {@link CorreoControlRegistrado} (sin correo de control no hay facultad).
 */
@Component
public class CodigosFacultadMensajes implements CorreoControlRegistrado {

	/** Lo que queda guardado en lugar del código (lo reemplaza el envío). */
	public static final String MARCADOR_CODIGO = "(llega en este mensaje)";

	private final CreadorMensajes creador;

	private final UsuarioRepository usuarios;

	private final ConfiguracionColegioRepository configuracion;

	public CodigosFacultadMensajes(CreadorMensajes creador, UsuarioRepository usuarios,
			ConfiguracionColegioRepository configuracion) {
		this.creador = creador;
		this.usuarios = usuarios;
		this.configuracion = configuracion;
	}

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void alPedirCodigo(CodigoFacultadPedido pedido) {
		Usuario titular = usuarios.findById(pedido.titularId())
				.orElseThrow(() -> new IllegalStateException("El titular del pedido no existe"));
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.CODIGO_FACULTAD,
				PlantillaMensaje.CODIGO_FACULTAD, List.of(MARCADOR_CODIGO, pedido.venceA()), "usuario", titular.getId());
		Mensaje mensaje = creador.paraUsuarioConClave(titular, contenido, pedido.claveBase())
				.orElseThrow(() -> new ReglaNegocioException("Tu cuenta no tiene celular ni correo registrados: el código de "
						+ "la facultad no tiene a dónde llegar."));
		String destino = mensaje.getCanal() == CanalMensaje.WHATSAPP ? "WhatsApp " + Enmascarar.telefono(mensaje.getDestino())
				: "correo " + Enmascarar.correo(mensaje.getDestino());
		pedido.registrar(mensaje.getId(), mensaje.getClave(), mensaje.getCreadoEn(), destino);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean registrado() {
		Long colegio = ContextoColegio.actual();
		return colegio != null && configuracion.findByColegioIdAndClave(colegio, ConfiguracionColegio.HUELLA_CORREO_EXTERNO)
				.map(ConfiguracionColegio::getValor).filter(v -> v != null && v.contains("@")).isPresent();
	}
}
