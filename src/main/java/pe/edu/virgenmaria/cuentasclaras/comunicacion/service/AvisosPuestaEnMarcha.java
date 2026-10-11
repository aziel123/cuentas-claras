package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioEnProduccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.ConectorEnVigencia;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 8, tanda 2 (secciones 3.4 y 3.6): avisos de la puesta en marcha, creados en la MISMA transacción de lo que los
 * origina (como los avisos de pago). Van a TODA la Promotoría activa (cuentas del personal) y al correo externo de control
 * del colegio ({@code configuracion_colegio}, que registra el operador). Si no hay a ninguna Promotoría a quién avisar, la
 * excepción revierte el cambio: un conector no cambia en silencio.
 * <ul>
 *   <li>{@link ConectorEnVigencia}: cambió adónde entra el dinero o quién emite las boletas (sin secretos: la terminación
 *       de la cuenta o de la llave).</li>
 *   <li>{@link ColegioEnProduccion}: el colegio empezó a cobrar.</li>
 * </ul>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AvisosPuestaEnMarcha {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final CreadorMensajes creador;

	private final UsuarioRepository usuarios;

	private final ConfiguracionColegioRepository configuracion;

	public AvisosPuestaEnMarcha(CreadorMensajes creador, UsuarioRepository usuarios,
			ConfiguracionColegioRepository configuracion) {
		this.creador = creador;
		this.usuarios = usuarios;
		this.configuracion = configuracion;
	}

	@EventListener
	public void alConectorEnVigencia(ConectorEnVigencia evento) {
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.CONECTOR_VIGENTE,
				PlantillaMensaje.CONECTOR_VIGENTE, List.of(evento.tipo().descripcion(), evento.proveedor(), evento.detalle(),
						evento.propuestoPor(), evento.aprobadoPor()), "conector_colegio", evento.conectorId());
		avisar(evento.colegioId(), contenido, "C" + evento.conectorId(), "el cambio del conector");
	}

	@EventListener
	public void alColegioEnProduccion(ColegioEnProduccion evento) {
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.COLEGIO_EN_PRODUCCION,
				PlantillaMensaje.COLEGIO_EN_PRODUCCION, List.of(evento.momento().format(FECHA), evento.promotoria()),
				"colegio", evento.colegioId());
		avisar(evento.colegioId(), contenido, "P" + evento.colegioId(), "el paso a producción");
	}

	private void avisar(Long colegioId, CreadorMensajes.Contenido contenido, String sufijo, String que) {
		List<Mensaje> creados = new ArrayList<>();
		for (Usuario promotoria : usuarios.activosConRol(Rol.PROMOTOR)) {
			if (promotoria.getApoderadoId() == null) {
				creador.paraUsuario(promotoria, contenido, sufijo).ifPresent(creados::add);
			}
		}
		if (creados.isEmpty()) {
			throw new ReglaNegocioException("No hay a quién avisar " + que + ": ninguna persona de Promotoría tiene celular "
					+ "ni correo. No se guardó nada.");
		}
		Optional<String> externo = configuracion.findByColegioIdAndClave(colegioId, ConfiguracionColegio.HUELLA_CORREO_EXTERNO)
				.map(ConfiguracionColegio::getValor).filter(c -> c.contains("@")).map(String::strip);
		externo.ifPresent(correo -> creador.externo(correo, contenido));
	}
}
