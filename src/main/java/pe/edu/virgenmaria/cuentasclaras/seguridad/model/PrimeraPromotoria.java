package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Sprint 8, tanda 1 (H7, sección 3.3): la primera Promotoría de la historia de un colegio, creada UNA sola vez por un
 * operador de la plataforma con la firma de su sesión ({@code primera_promotoria:{colegio}}, 5 minutos). Esta fila (UNIQUE
 * por colegio) es lo que trg_usuario_rol_alta exige para dar el primer PROMOTOR; la escribe solo {@code cc_sistema}
 * (GRANT), en la misma transacción que la cuenta del titular. {@code operadorId} y {@code plataformaId} nombran la cuenta
 * del operador en el colegio de plataforma (FK compuesta). De solo inserción.
 */
@Entity
@Table(name = "primera_promotoria")
public class PrimeraPromotoria extends BaseEntity {

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(name = "operador_id", updatable = false)
	private Long operadorId;

	@Column(name = "plataforma_id", updatable = false)
	private Long plataformaId;

	protected PrimeraPromotoria() {
		// requerido por JPA
	}

	public static PrimeraPromotoria de(Long usuarioId, Long operadorId, Long plataformaId) {
		PrimeraPromotoria p = new PrimeraPromotoria();
		p.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
		p.operadorId = Objects.requireNonNull(operadorId, "operadorId");
		p.plataformaId = Objects.requireNonNull(plataformaId, "plataformaId");
		return p;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La marca de la primera Promotoría no se borra.");
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Long getOperadorId() {
		return operadorId;
	}

	public Long getPlataformaId() {
		return plataformaId;
	}
}
