package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.EnumSet;
import java.util.List;

public class FabricaContextoComoUsuario implements WithSecurityContextFactory<ComoUsuario> {

	@Override
	public SecurityContext createSecurityContext(ComoUsuario anotacion) {
		UsuarioAutenticado usuario = UsuariosDePrueba.autenticado(anotacion.colegioId(), anotacion.usuarioId(),
				anotacion.nombreUsuario(), anotacion.nombreCompleto(), anotacion.clavePendiente(),
				EnumSet.copyOf(List.of(anotacion.roles())));
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(UsuariosDePrueba.autenticacion(usuario));
		return contexto;
	}
}
