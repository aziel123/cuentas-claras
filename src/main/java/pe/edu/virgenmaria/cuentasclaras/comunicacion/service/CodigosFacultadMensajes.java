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
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CorreoControlRegistrado;

import java.util.List;

/**
 * Sprint 11, tanda 1 (AP-2): el puerto {@link CorreoControlRegistrado} (sin correo de control no hay facultad ni
 * directa en producción) y el marcador del código. Tanda 1c: el mensaje del código lo crea {@link CodigosMensajes}
 * ({@code CODIGO_PROMOTORIA}, atado a la acción); {@code CODIGO_FACULTAD} queda solo en los mensajes ya enviados.
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

	@Override
	@Transactional(readOnly = true)
	public boolean esElCorreoDeControl(String correo) {
		Long colegio = ContextoColegio.actual();
		return colegio != null && correo != null && configuracion.findByColegioIdAndClave(colegio,
				ConfiguracionColegio.HUELLA_CORREO_EXTERNO).map(ConfiguracionColegio::getValor)
				.filter(v -> pe.edu.virgenmaria.cuentasclaras.comun.texto.ContactoNormal.iguales(v, correo)).isPresent();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean registrado() {
		Long colegio = ContextoColegio.actual();
		return colegio != null && configuracion.findByColegioIdAndClave(colegio, ConfiguracionColegio.HUELLA_CORREO_EXTERNO)
				.map(ConfiguracionColegio::getValor).filter(v -> v != null && v.contains("@")).isPresent();
	}
}
