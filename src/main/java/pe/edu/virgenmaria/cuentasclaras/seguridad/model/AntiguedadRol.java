package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 11, tanda 1c (A1c-2, decisión 222): desde cuándo una cuenta tiene un rol ({@code usuario_rol.desde}, que pone la
 * base al darlo). Solo se lee (la escriben trg_usuario_rol_alta y la colección de roles de {@link Usuario}): la
 * antigüedad de 30 días que exige frenar una espera y aprobar al instante. Sin colegio propio: se consulta por la cuenta,
 * que ya se leyó con el filtro del colegio.
 */
@Entity
@Immutable
@Table(name = "usuario_rol")
@IdClass(AntiguedadRol.Clave.class)
public class AntiguedadRol {

	/** La clave de la fila: la cuenta y el rol. */
	public static class Clave implements Serializable {

		private Long usuarioId;

		private Rol rol;

		public Clave() {
			// requerido por JPA
		}

		@Override
		public boolean equals(Object otro) {
			return otro instanceof Clave c && Objects.equals(usuarioId, c.usuarioId) && rol == c.rol;
		}

		@Override
		public int hashCode() {
			return Objects.hash(usuarioId, rol);
		}
	}

	@Id
	@Column(name = "usuario_id")
	private Long usuarioId;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "rol", length = 20)
	private Rol rol;

	@Column(name = "desde")
	private LocalDateTime desde;

	protected AntiguedadRol() {
		// requerido por JPA
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Rol getRol() {
		return rol;
	}

	public LocalDateTime getDesde() {
		return desde;
	}
}
