package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Sprint 11, tanda 1 (sección 3.12, AP-1 y AP-2): cada uso de la facultad del titular del contrato (la cuenta de la primera
 * Promotoría del colegio): dar o quitar PROMOTOR o DIRECTOR a OTRA cuenta del personal, sin segunda aprobación, con la
 * firma de su sesión ({@code facultad_titular:{colegio}:{numero}}) y el código que le llegó por WhatsApp
 * ({@code mensaje_id}, el CODIGO_FACULTAD, una vez). Numerado por colegio sin huecos. Se escribe en la MISMA transacción
 * que el rol, por la ruta de identidad ({@code cc_sistema}); en MySQL trg_uso_facultad_titular_nace lo valida y pone su hora,
 * y trg_usuario_rol_alta y _baja exigen esta fila de hace 5 minutos o menos. De solo inserción: nada se edita ni se borra.
 */
@Entity
@Immutable
@Table(name = "uso_facultad_titular")
public class UsoFacultadTitular extends BaseEntity {

	/** Dar o quitar el rol. */
	public enum Accion {
		DAR, QUITAR
	}

	@Column(nullable = false, updatable = false)
	private int numero;

	@Column(name = "titular_id", nullable = false, updatable = false)
	private Long titularId;

	@Column(nullable = false, updatable = false, length = 60)
	private String titular;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 30)
	private Rol rol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 6)
	private Accion accion;

	@Column(name = "cuenta_nueva", nullable = false, updatable = false)
	private boolean cuentaNueva;

	@Column(name = "mensaje_id", nullable = false, updatable = false)
	private Long mensajeId;

	protected UsoFacultadTitular() {
		// requerido por JPA
	}

	public static UsoFacultadTitular de(int numero, Usuario titular, Long usuarioId, Rol rol, Accion accion,
			boolean cuentaNueva, Long mensajeId) {
		Objects.requireNonNull(titular, "titular");
		if (rol != Rol.PROMOTOR && rol != Rol.DIRECTOR) {
			throw new ReglaNegocioException("La facultad del titular es solo para Promotoría y Dirección.");
		}
		if (Objects.equals(titular.getId(), usuarioId)) {
			throw new ReglaNegocioException("La facultad del titular no se usa sobre la propia cuenta.");
		}
		if (accion == Accion.QUITAR && cuentaNueva) {
			throw new IllegalArgumentException("Una cuenta nueva no pierde un rol");
		}
		UsoFacultadTitular u = new UsoFacultadTitular();
		u.numero = numero;
		u.titularId = titular.getId();
		u.titular = titular.getNombreUsuario();
		u.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
		u.rol = rol;
		u.accion = Objects.requireNonNull(accion, "accion");
		u.cuentaNueva = cuentaNueva;
		u.mensajeId = Objects.requireNonNull(mensajeId, "mensajeId");
		return u;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Un uso de la facultad del titular no se borra.");
	}

	public int getNumero() {
		return numero;
	}

	public Long getTitularId() {
		return titularId;
	}

	public String getTitular() {
		return titular;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Rol getRol() {
		return rol;
	}

	public Accion getAccion() {
		return accion;
	}

	public boolean isCuentaNueva() {
		return cuentaNueva;
	}

	public Long getMensajeId() {
		return mensajeId;
	}
}
