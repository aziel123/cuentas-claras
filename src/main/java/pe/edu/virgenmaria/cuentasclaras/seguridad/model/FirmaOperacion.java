package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * La firma de una aprobación (sprint 7, tanda 2; sección 3.4): la clave canónica de la operación
 * ({@code seguridad.service.sesion.ClaveFirma}) y el secreto de la sesión de quien resuelve. En MySQL,
 * trg_firma_operacion_nace comprueba que el SHA-256 del secreto es el de una sesión ABIERTA de esa persona en ese colegio,
 * lo deja en NULL (nunca queda guardado) y pone {@code firmada_bd} con la hora de la base. El trigger de la tabla resuelta
 * exige la firma de quien figura como aprobador, de hace 5 minutos o menos. Una firma por colegio y clave: no se reusa.
 * <p>
 * Solo inserción: todas sus columnas son {@code updatable = false} (cc_app tiene solo INSERT) y {@code firmadaBd} no se
 * inserta (la pone el trigger; en H2 queda en NULL y la aplicación no la usa).
 */
@Entity
@Table(name = "firma_operacion")
public class FirmaOperacion extends BaseEntity {

	/** {@code tabla:partes}, solo letras, dígitos, «:», «_» y «-» (también lo exige un CHECK). */
	public static final String PATRON_CLAVE = "^[a-z_]+:[A-Za-z0-9:_-]+$";

	public static final int MAX_CLAVE = 120;

	@Column(name = "sesion_id", nullable = false, updatable = false)
	private Long sesionId;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(nullable = false, updatable = false, length = MAX_CLAVE)
	private String clave;

	@Column(updatable = false, length = 64)
	private String token;

	@Column(name = "firmada_bd", insertable = false, updatable = false)
	private LocalDateTime firmadaBd;

	protected FirmaOperacion() {
		// requerido por JPA
	}

	public static FirmaOperacion de(Long sesionId, Long usuarioId, String clave, String token) {
		FirmaOperacion f = new FirmaOperacion();
		f.sesionId = Objects.requireNonNull(sesionId, "sesionId");
		f.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
		if (clave == null || clave.length() > MAX_CLAVE || !clave.matches(PATRON_CLAVE)) {
			throw new IllegalArgumentException("Clave de firma no válida: " + clave);
		}
		f.clave = clave;
		if (token == null || !token.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("El secreto de la sesión no es válido");
		}
		f.token = token;
		return f;
	}

	@PreRemove
	void impedirBorrado() {
		throw new UnsupportedOperationException("Las firmas no se borran");
	}

	public Long getSesionId() {
		return sesionId;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getClave() {
		return clave;
	}

	public LocalDateTime getFirmadaBd() {
		return firmadaBd;
	}
}
