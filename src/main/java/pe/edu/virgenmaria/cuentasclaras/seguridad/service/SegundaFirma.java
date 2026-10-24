package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.Collection;

/**
 * Sprint 11, tanda 1c (secciones 3.16 y 3.16b, decisión 215): UNA política para la segunda firma, en lugar de reglas
 * repartidas. La Promotoría (el dueño) no pide aprobación a personas de menor jerarquía: aplica directo lo suyo y aprueba
 * lo de las cuentas que creó (A5 deja de bloquearla). Para todos los demás roles, A5 sigue igual
 * ({@link ControlParticipantes}: quien creó la cuenta del autor o le restableció la clave en 30 días participa). Lo que
 * piden los demás roles sigue con dos personas: la Promotoría puede ser la segunda. No exige rol: lo usan servicios que
 * ya lo exigieron. Las excepciones (el contacto propio y el de una cuenta directiva, A1c-1) las decide cada caso.
 */
@Component
public class SegundaFirma {

	private final UsuarioRepository usuarios;

	private final ColegioRepository colegios;

	private final ControlParticipantes participantes;

	private final pe.edu.virgenmaria.cuentasclaras.seguridad.repository.AntiguedadRolRepository antiguedad;

	private final java.time.Clock reloj;

	/** Sprint 11, tanda 1c (A1c-2, decisión 222): la antigüedad en el rol para frenar y aprobar al instante. */
	public static final java.time.Duration ANTIGUEDAD = java.time.Duration.ofDays(30);

	public SegundaFirma(UsuarioRepository usuarios, ColegioRepository colegios, ControlParticipantes participantes,
			pe.edu.virgenmaria.cuentasclaras.seguridad.repository.AntiguedadRolRepository antiguedad, java.time.Clock reloj) {
		this.usuarios = usuarios;
		this.colegios = colegios;
		this.participantes = participantes;
		this.antiguedad = antiguedad;
		this.reloj = reloj;
	}

	/**
	 * A1c-2: si esa persona es una Promotoría activa con 30 días o más en el rol (la misma regla que
	 * {@code cc_promotoria_antigua} en MySQL, con {@code usuario_rol.desde}).
	 */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public boolean promotoriaAntigua(String nombreUsuario) {
		return nombreUsuario != null && usuarios.findByNombreUsuario(nombreUsuario)
				.filter(u -> u.isActivo() && u.getApoderadoId() == null && u.getRoles().contains(Rol.PROMOTOR))
				.flatMap(u -> antiguedad.findByUsuarioIdAndRol(u.getId(), Rol.PROMOTOR))
				.map(r -> r.getDesde() != null && !r.getDesde().isAfter(java.time.LocalDateTime.now(reloj).minus(ANTIGUEDAD)))
				.orElse(false);
	}

	/** Cuántas Promotorías activas tiene el colegio (A1c-9: con una sola, lo que devuelve dinero avisa a los operadores). */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public long promotoriasActivas() {
		return usuarios.contarActivosConRol(Rol.PROMOTOR);
	}

	/** Si esa persona aplica directo: una Promotoría activa del personal, en un colegio que opera. */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public boolean aplicaDirecto(String nombreUsuario) {
		Long colegio = ContextoColegio.actual();
		if (nombreUsuario == null || colegio == null
				|| !colegios.findById(colegio).map(c -> !c.esPlataforma() && c.getEstado().opera()).orElse(false)) {
			return false;
		}
		return usuarios.findByNombreUsuario(nombreUsuario)
				.filter(u -> u.isActivo() && u.getApoderadoId() == null && u.getRoles().contains(Rol.PROMOTOR)).isPresent();
	}

	/**
	 * Si {@code quien} puede aprobar lo que hicieron {@code autores}: una Promotoría, siempre (aunque sea la autora: es una
	 * directa); cualquier otra persona, solo si no participó ni preparó la cuenta de un autor (A5).
	 */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public boolean puedeResolver(String quien, Collection<String> autores) {
		return aplicaDirecto(quien) || !participantes.ampliar(autores).contains(quien);
	}

	/**
	 * Quiénes no pueden aprobar o confirmar lo que hicieron {@code autores}, para quien está en sesión: si es Promotoría,
	 * nadie (aplica directo, también lo suyo: plan, saldo inicial, lote, extracto, cierre mensual); si no, los autores y
	 * quienes prepararon sus cuentas (A5).
	 */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public java.util.Set<String> participantesPara(Collection<String> autores) {
		return aplicaDirecto(actual()) ? java.util.Set.of() : participantes.ampliar(autores);
	}

	/**
	 * Quiénes quedan fuera por ser PARTE de lo que se revisa (quien cobró, quien subió el extracto del cargo, quien aprobó
	 * el descuento del que se queja la familia), para quien está en sesión: si es Promotoría, solo los autores mismos (A5
	 * no la bloquea por las cuentas que preparó); si no, también quienes prepararon sus cuentas.
	 */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public java.util.Set<String> excluidosPara(Collection<String> autores) {
		if (aplicaDirecto(actual())) {
			java.util.Set<String> directos = new java.util.LinkedHashSet<>(autores);
			directos.remove(null);
			return directos;
		}
		return participantes.ampliar(autores);
	}

	private static String actual() {
		org.springframework.security.core.Authentication a = org.springframework.security.core.context.SecurityContextHolder
				.getContext().getAuthentication();
		return a == null ? null : a.getName();
	}

	/** Los participantes según A5 (para los mensajes y las bandejas de quien no es Promotoría). */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public java.util.Set<String> participantes(Collection<String> autores) {
		return participantes.ampliar(autores);
	}
}
