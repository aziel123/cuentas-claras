package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;

/**
 * Sprint 8, tanda 2 (MySQL real): un colegio NUEVO dado de alta por el operador, con su equipo por el camino legítimo
 * (la Promotoría titular activa su cuenta, crea una Administración y una Caja, y da la primera Dirección). Los conectores
 * se prueban en él: así un conector de prueba nunca cambia la cuenta de depósito, el OSE o la pasarela del colegio 1, que
 * usan las demás pruebas de MySQL. El colegio queda en preparación (los conectores se registran antes de cobrar).
 */
final class ColegioConEquipoMySql {

	record Equipo(long colegioId, Usuario promotora, Usuario directora, Usuario administracion) {
	}

	private ColegioConEquipoMySql() {
	}

	static Equipo crear(String sufijo, String ruc, String celular, AltaColegio alta, ServicioUsuarios servicioUsuarios,
			UsuarioRepository usuarios, PasswordEncoder codificador, ColegioRepository colegios) {
		Usuario operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.s8");
		UsuariosDePrueba.iniciarSesion(operador);
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio conectores " + sufijo, ruc, "titular.c." + sufijo,
				"5" + String.format("%07d", Math.floorMod(sufijo.hashCode(), 10_000_000)), celular), null, null);
		if (!creado.completo()) {
			throw new IllegalStateException(creado.pendiente());
		}
		alta.registrarCorreoControl(creado.colegioId(), "contador.c." + sufijo + "@estudio.pe");
		SecurityContextHolder.clearContext();
		long colegio = creado.colegioId();
		String titular = "titular.c." + sufijo;
		Usuario promotora = RutaConexion.identidad(() -> ContextoColegio.en(colegio, () -> {
			Usuario cuenta = usuarios.findByNombreUsuario(titular).orElseThrow();
			cuenta.cambiarClave(codificador.encode(UsuariosDePrueba.CLAVE), LocalDateTime.now(Clock.system(
					ConfiguracionTiempo.ZONA_LIMA)), false);
			return usuarios.saveAndFlush(cuenta);
		}));
		Usuario administracion = UsuariosDePrueba.guardar(usuarios, codificador, colegio, "adm.c." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.ADMINISTRACION);
		UsuariosDePrueba.guardar(usuarios, codificador, colegio, "caja.c." + sufijo, UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		Usuario docente = UsuariosDePrueba.guardar(usuarios, codificador, colegio, "dir.c." + sufijo, UsuariosDePrueba.CLAVE,
				false, Rol.DOCENTE);
		UsuariosDePrueba.iniciarSesion(promotora);
		servicioUsuarios.cambiarRoles(docente.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DIRECTOR),
				"Primera Dirección del colegio de conectores"));
		SecurityContextHolder.clearContext();
		Usuario directora = ContextoColegio.en(colegio, () -> usuarios.findById(docente.getId()).orElseThrow());
		return new Equipo(colegio, promotora, directora, administracion);
	}
}
