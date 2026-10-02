package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.PrincipalConColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Usuario en sesión. Es el principal de Spring Security y le dice a {@code ContextoColegio}
 * el colegio de cada petición.
 * <p>
 * Con clave temporal solo tiene la autoridad {@link #CLAVE_PENDIENTE}, que únicamente permite
 * cambiar la clave. {@code equals}/{@code hashCode} por id: así el registro de sesiones reconoce
 * dos ingresos del mismo usuario.
 */
public final class UsuarioAutenticado implements UserDetails, CredentialsContainer, PrincipalConColegio, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public static final String CLAVE_PENDIENTE = "CLAVE_PENDIENTE";

	private final Long id;

	private final Long colegioId;

	private final String nombreUsuario;

	private final String nombreCompleto;

	private String claveHash;

	private final boolean activo;

	private final boolean bloqueado;

	private final boolean debeCambiarClave;

	private final Set<Rol> roles;

	public UsuarioAutenticado(Long id, Long colegioId, String nombreUsuario, String nombreCompleto, String claveHash,
			boolean activo, boolean bloqueado, boolean debeCambiarClave, Set<Rol> roles) {
		this.id = Objects.requireNonNull(id, "id");
		this.colegioId = Objects.requireNonNull(colegioId, "colegioId");
		this.nombreUsuario = nombreUsuario;
		this.nombreCompleto = nombreCompleto;
		this.claveHash = claveHash;
		this.activo = activo;
		this.bloqueado = bloqueado;
		this.debeCambiarClave = debeCambiarClave;
		this.roles = roles.isEmpty() ? EnumSet.noneOf(Rol.class) : EnumSet.copyOf(roles);
	}

	public static UsuarioAutenticado de(Usuario usuario, LocalDateTime ahora) {
		return new UsuarioAutenticado(usuario.getId(), usuario.getColegioId(), usuario.getNombreUsuario(),
				usuario.getNombreCompleto(), usuario.getClaveHash(), usuario.isActivo(), usuario.estaBloqueado(ahora),
				usuario.isDebeCambiarClave(), usuario.getRoles());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (debeCambiarClave) {
			return List.of(new SimpleGrantedAuthority(CLAVE_PENDIENTE));
		}
		return roles.stream().map(r -> new SimpleGrantedAuthority(r.autoridad())).toList();
	}

	/** Rol que decide la página de inicio: PROMOTOR, DIRECTOR, ADMINISTRACION, CAJA, DOCENTE, APODERADO. */
	public Rol rolPrincipal() {
		return roles.stream().min(Enum::compareTo)
				.orElseThrow(() -> new IllegalStateException("El usuario no tiene roles"));
	}

	/** Roles separados por coma, para la auditoría. */
	public String rolesComoTexto() {
		return roles.stream().map(Rol::name).collect(Collectors.joining(","));
	}

	@Override
	public String rolesParaAuditoria() {
		return rolesComoTexto();
	}

	@Override
	public Long colegioId() {
		return colegioId;
	}

	@Override
	public Long usuarioId() {
		return id;
	}

	public String nombreCompleto() {
		return nombreCompleto;
	}

	public Set<Rol> roles() {
		return Collections.unmodifiableSet(roles);
	}

	public boolean debeCambiarClave() {
		return debeCambiarClave;
	}

	@Override
	public String getPassword() {
		return claveHash;
	}

	@Override
	public String getUsername() {
		return nombreUsuario;
	}

	@Override
	public boolean isAccountNonLocked() {
		return !bloqueado;
	}

	@Override
	public boolean isEnabled() {
		return activo;
	}

	@Override
	public void eraseCredentials() {
		claveHash = null;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof UsuarioAutenticado u && id.equals(u.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		// Sin el hash de la clave: este texto puede terminar en un log.
		return "UsuarioAutenticado[id=" + id + ", nombreUsuario=" + nombreUsuario + ", colegioId=" + colegioId + "]";
	}
}
