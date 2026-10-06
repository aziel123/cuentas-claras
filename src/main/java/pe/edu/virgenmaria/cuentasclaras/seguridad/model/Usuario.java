package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Usuario del sistema. Nunca se borra: se desactiva. El nombre de usuario es único en toda
 * la plataforma (el login no pide colegio), se guarda en minúsculas y no cambia.
 */
@Entity
@Table(name = "usuario")
public class Usuario extends BaseEntity {

	/** Nombre de usuario válido, ya normalizado: minúsculas, números, punto, guion y guion bajo; de 3 a 60. */
	public static final Pattern PATRON_NOMBRE_USUARIO = Pattern.compile("^[a-z0-9._-]{3,60}$");

	@Column(name = "nombre_usuario", nullable = false, updatable = false, length = 60)
	private String nombreUsuario;

	@Column(name = "nombre_completo", nullable = false, length = 150)
	private String nombreCompleto;

	@Column(length = 150)
	private String correo;

	@Column(name = "clave_hash", nullable = false, length = 100)
	private String claveHash;

	@Column(nullable = false)
	private boolean activo = true;

	@Column(name = "debe_cambiar_clave", nullable = false)
	private boolean debeCambiarClave = true;

	@Column(name = "intentos_fallidos", nullable = false)
	private int intentosFallidos;

	@Column(name = "bloqueado_hasta")
	private LocalDateTime bloqueadoHasta;

	@Column(name = "ultimo_ingreso_en")
	private LocalDateTime ultimoIngresoEn;

	@Column(name = "clave_cambiada_en")
	private LocalDateTime claveCambiadaEn;

	@Column(name = "desactivado_en")
	private LocalDateTime desactivadoEn;

	@Column(name = "desactivado_por", length = 60)
	private String desactivadoPor;

	/** Hasta cuándo sirve la clave temporal; {@code null}: no vence (o la clave no es temporal). */
	@Column(name = "clave_temporal_hasta")
	private LocalDateTime claveTemporalHasta;

	@Column(name = "clave_restablecida_por", length = 60)
	private String claveRestablecidaPor;

	@Column(name = "clave_restablecida_en")
	private LocalDateTime claveRestablecidaEn;

	/**
	 * Sprint 4: la cuenta en línea del apoderado, enlazada a SU registro (y por él, a su familia). No cambia
	 * ({@code updatable = false}); solo las cuentas APODERADO la tienen.
	 */
	@Column(name = "apoderado_id", updatable = false)
	private Long apoderadoId;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "usuario_rol", joinColumns = @JoinColumn(name = "usuario_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "rol", nullable = false, length = 20)
	private Set<Rol> roles = EnumSet.noneOf(Rol.class);

	protected Usuario() {
		// requerido por JPA
	}

	/**
	 * Crea un usuario con clave temporal: debe cambiarla en su primer ingreso.
	 * El colegio lo asigna Hibernate al guardar, según el contexto.
	 */
	public static Usuario nuevo(String nombreUsuario, String nombreCompleto, String correo, String claveHash,
			Set<Rol> roles) {
		return crear(nombreUsuario, nombreCompleto, correo, claveHash, roles, null);
	}

	/**
	 * Cuenta en línea de un apoderado (sprint 4): solo el rol APODERADO, enlazada a su registro. La crea Promotoría o
	 * Administración desde su ficha.
	 */
	public static Usuario deApoderado(String nombreUsuario, String nombreCompleto, String correo, String claveHash,
			Long apoderadoId) {
		return crear(nombreUsuario, nombreCompleto, correo, claveHash, EnumSet.of(Rol.APODERADO),
				Objects.requireNonNull(apoderadoId, "apoderadoId"));
	}

	private static Usuario crear(String nombreUsuario, String nombreCompleto, String correo, String claveHash,
			Set<Rol> roles, Long apoderadoId) {
		ReglasSegregacion.validarCuenta(roles, apoderadoId);
		String normalizado = normalizarNombreUsuario(nombreUsuario);
		if (ActorSistema.esReservado(normalizado)) {
			throw new ReglaNegocioException("Los nombres de usuario que empiezan con «" + ActorSistema.PREFIJO_RESERVADO
					+ "» están reservados para los procesos automáticos del sistema.");
		}
		Usuario usuario = new Usuario();
		usuario.apoderadoId = apoderadoId;
		usuario.nombreUsuario = normalizado;
		usuario.nombreCompleto = requerido(nombreCompleto, "El nombre completo es obligatorio.");
		usuario.correo = correo == null || correo.isBlank() ? null : correo.trim();
		usuario.claveHash = requerido(claveHash, "La clave es obligatoria.");
		usuario.roles = EnumSet.copyOf(roles);
		usuario.debeCambiarClave = true;
		return usuario;
	}

	/** {@code true} si el nombre (ya normalizado) cumple {@link #PATRON_NOMBRE_USUARIO}. */
	public static boolean esNombreUsuarioValido(String normalizado) {
		return normalizado != null && PATRON_NOMBRE_USUARIO.matcher(normalizado).matches();
	}

	/** Minúsculas y sin espacios alrededor: así se guarda y así se busca. */
	public static String normalizarNombreUsuario(String nombreUsuario) {
		return requerido(nombreUsuario, "El nombre de usuario es obligatorio.").toLowerCase(Locale.ROOT);
	}

	/**
	 * Suma un intento fallido. Al llegar al máximo bloquea la cuenta durante {@code bloqueo}
	 * y reinicia el contador.
	 *
	 * @return {@code true} si este intento bloqueó la cuenta
	 */
	public boolean registrarIngresoFallido(int maximo, Duration bloqueo, LocalDateTime ahora) {
		intentosFallidos++;
		if (intentosFallidos >= maximo) {
			bloqueadoHasta = ahora.plus(bloqueo);
			intentosFallidos = 0;
			return true;
		}
		return false;
	}

	public void registrarIngresoExitoso(LocalDateTime ahora) {
		intentosFallidos = 0;
		bloqueadoHasta = null;
		ultimoIngresoEn = ahora;
	}

	public boolean estaBloqueado(LocalDateTime ahora) {
		return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
	}

	public void desbloquear() {
		intentosFallidos = 0;
		bloqueadoHasta = null;
	}

	/**
	 * @param temporal {@code true} si la clave la generó el sistema y el usuario debe cambiarla
	 */
	public void cambiarClave(String nuevoHash, LocalDateTime ahora, boolean temporal) {
		claveHash = requerido(nuevoHash, "La clave es obligatoria.");
		claveCambiadaEn = ahora;
		debeCambiarClave = temporal;
		if (!temporal) {
			claveTemporalHasta = null;
		}
	}

	/** La clave temporal actual deja de servir en {@code hasta} (el titular debe pedir otra). */
	public void vencerClaveTemporalEn(LocalDateTime hasta) {
		if (!debeCambiarClave) {
			throw new IllegalStateException("Solo vence una clave temporal");
		}
		claveTemporalHasta = hasta;
	}

	/**
	 * Otra persona le genera una clave temporal nueva: desbloquea la cuenta y deja rastro de quién y cuándo,
	 * para avisar al titular.
	 */
	public void restablecerClave(String hashTemporal, LocalDateTime ahora, LocalDateTime vence, String por) {
		cambiarClave(hashTemporal, ahora, true);
		claveTemporalHasta = vence;
		claveRestablecidaPor = requerido(por, "Falta quién restablece la clave.");
		claveRestablecidaEn = ahora;
		desbloquear();
	}

	/** {@code true} si debe cambiar su clave temporal y esta ya venció. */
	public boolean claveTemporalVencida(LocalDateTime ahora) {
		return debeCambiarClave && claveTemporalHasta != null && !ahora.isBefore(claveTemporalHasta);
	}

	public void cambiarRoles(Set<Rol> nuevosRoles) {
		ReglasSegregacion.validarCuenta(nuevosRoles, apoderadoId);
		roles.clear();
		roles.addAll(nuevosRoles);
	}

	public void desactivar(String por, LocalDateTime ahora) {
		if (!activo) {
			throw new ReglaNegocioException("El usuario ya está desactivado.");
		}
		activo = false;
		desactivadoEn = ahora;
		desactivadoPor = requerido(por, "Falta quién desactiva al usuario.");
	}

	public void reactivar() {
		if (activo) {
			throw new ReglaNegocioException("El usuario ya está activo.");
		}
		activo = true;
		desactivadoEn = null;
		desactivadoPor = null;
	}

	private static String requerido(String valor, String mensaje) {
		if (valor == null || valor.isBlank()) {
			throw new ReglaNegocioException(mensaje);
		}
		return valor.trim();
	}

	public String getNombreUsuario() {
		return nombreUsuario;
	}

	public String getNombreCompleto() {
		return nombreCompleto;
	}

	public String getCorreo() {
		return correo;
	}

	public String getClaveHash() {
		return claveHash;
	}

	public boolean isActivo() {
		return activo;
	}

	public boolean isDebeCambiarClave() {
		return debeCambiarClave;
	}

	public int getIntentosFallidos() {
		return intentosFallidos;
	}

	public LocalDateTime getBloqueadoHasta() {
		return bloqueadoHasta;
	}

	public LocalDateTime getUltimoIngresoEn() {
		return ultimoIngresoEn;
	}

	public LocalDateTime getClaveCambiadaEn() {
		return claveCambiadaEn;
	}

	public LocalDateTime getDesactivadoEn() {
		return desactivadoEn;
	}

	public String getDesactivadoPor() {
		return desactivadoPor;
	}

	public LocalDateTime getClaveTemporalHasta() {
		return claveTemporalHasta;
	}

	public String getClaveRestablecidaPor() {
		return claveRestablecidaPor;
	}

	public LocalDateTime getClaveRestablecidaEn() {
		return claveRestablecidaEn;
	}

	public Long getApoderadoId() {
		return apoderadoId;
	}

	public Set<Rol> getRoles() {
		return Collections.unmodifiableSet(roles);
	}

	@Override
	public String toString() {
		// Sin el hash de la clave: este texto puede terminar en un log.
		return "Usuario[id=" + getId() + ", nombreUsuario=" + nombreUsuario + ", colegioId=" + getColegioId() + "]";
	}

	@Override
	public boolean equals(Object otro) {
		if (this == otro) {
			return true;
		}
		return otro instanceof Usuario u && getId() != null && Objects.equals(getId(), u.getId());
	}

	@Override
	public int hashCode() {
		return Usuario.class.hashCode();
	}
}
