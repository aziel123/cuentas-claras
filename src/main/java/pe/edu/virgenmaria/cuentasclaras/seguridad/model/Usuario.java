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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Usuario del sistema. Nunca se borra: se desactiva. El nombre de usuario es único en toda
 * la plataforma (el login no pide colegio), se guarda en minúsculas y no cambia.
 */
@Entity
@Table(name = "usuario")
public class Usuario extends BaseEntity {

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
		ReglasSegregacion.validar(roles);
		Usuario usuario = new Usuario();
		usuario.nombreUsuario = normalizarNombreUsuario(nombreUsuario);
		usuario.nombreCompleto = requerido(nombreCompleto, "El nombre completo es obligatorio.");
		usuario.correo = correo == null || correo.isBlank() ? null : correo.trim();
		usuario.claveHash = requerido(claveHash, "La clave es obligatoria.");
		usuario.roles = EnumSet.copyOf(roles);
		usuario.debeCambiarClave = true;
		return usuario;
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
	}

	public void cambiarRoles(Set<Rol> nuevosRoles) {
		ReglasSegregacion.validar(nuevosRoles);
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
