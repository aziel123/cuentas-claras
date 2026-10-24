package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 11, tanda 1 (F10): al aplicarse la suspensión (o la baja) del colegio del contexto, se cierran las sesiones de
 * la base de TODO su personal ({@code COLEGIO_SUSPENDIDO}) dentro de la operación de identidad en curso
 * ({@code cc_sistema}); las de las familias siguen (modo consulta). Después, quien llama expira sus sesiones HTTP. Y el
 * filtro de sesión corta en cada pedido a quien quedara adentro.
 */
@Component
public class CierreSesionesColegio {

	private final UsuarioRepository usuarios;

	private final SesionesFirmadas sesiones;

	private final SesionesUsuario sesionesHttp;

	public CierreSesionesColegio(UsuarioRepository usuarios, SesionesFirmadas sesiones, SesionesUsuario sesionesHttp) {
		this.usuarios = usuarios;
		this.sesiones = sesiones;
		this.sesionesHttp = sesionesHttp;
	}

	/** Cierra las sesiones de la base del personal del colegio en contexto; devuelve las cuentas afectadas. */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<Long> cerrarPersonal() {
		List<Long> cuentas = new ArrayList<>();
		for (Usuario u : usuarios.personalActivo()) {
			if (u.getApoderadoId() == null) {
				sesiones.cerrarDe(u.getId(), MotivoCierreSesion.COLEGIO_SUSPENDIDO);
				cuentas.add(u.getId());
			}
		}
		return cuentas;
	}

	/** Expira las sesiones HTTP de esas cuentas (después de confirmar la transacción). */
	public void expirarHttp(List<Long> cuentas) {
		cuentas.forEach(sesionesHttp::expirar);
	}
}
