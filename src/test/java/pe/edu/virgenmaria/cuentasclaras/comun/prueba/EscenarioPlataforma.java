package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.AltaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.EnumSet;

/**
 * Sprint 8, tanda 1: el operador de la plataforma y los datos de un alta para las pruebas. El operador nace en el colegio
 * PLATAFORMA creado por {@code sistema.plataforma} (como lo hace {@code InicializadorOperadores}; en MySQL lo exige
 * trg_usuario_nace).
 */
public final class EscenarioPlataforma {

	/** RUC válidos (dígito verificador de SUNAT), distintos entre sí. */
	public static final String RUC_A = "20600000013";

	public static final String RUC_B = "20600000021";

	public static final String RUC_C = "20600000030";

	public static final String RUC_D = "20600000048";

	private EscenarioPlataforma() {
	}

	public static long plataformaId(ColegioRepository colegios) {
		return colegios.findFirstByTipo(TipoColegio.PLATAFORMA).orElseThrow().getId();
	}

	/** Un operador activo (o el que ya existe con ese nombre), con la clave de prueba ya elegida. */
	public static Usuario operador(UsuarioRepository usuarios, PasswordEncoder codificador, ColegioRepository colegios,
			String nombre) {
		long plataforma = plataformaId(colegios);
		Usuario existente = ContextoColegio.en(plataforma, () -> usuarios.findByNombreUsuario(nombre).orElse(null));
		if (existente != null) {
			return existente;
		}
		return EjecucionComoSistema.como(ActorSistema.PLATAFORMA, plataforma, () -> UsuariosDePrueba.insertar(usuarios,
				codificador, plataforma, nombre, UsuariosDePrueba.CLAVE, false, EnumSet.of(Rol.OPERADOR_PLATAFORMA)));
	}

	/** Los datos de un alta completa (titular con celular). */
	public static AltaColegioRequest alta(String nombre, String ruc, String titularUsuario, String dni, String celular) {
		return new AltaColegioRequest(nombre, "Asociación " + nombre + " S.A.C.", ruc, "Av. Los Educadores 123, Lima",
				"contacto@" + titularUsuario.replace('.', '-') + ".edu.pe", EnumSet.of(Nivel.PRIMARIA, Nivel.SECUNDARIA),
				"CT-" + titularUsuario, "Titular " + nombre, dni,
				titularUsuario, celular, null);
	}
}
