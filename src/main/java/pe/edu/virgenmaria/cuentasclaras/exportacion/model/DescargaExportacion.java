package pe.edu.virgenmaria.cuentasclaras.exportacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Sprint 11, tanda 2 (AP2-13): cada descarga del ZIP de una exportación (hasta 3), con la IP truncada como la bitácora.
 * Solo inserción: trg_descarga_exportacion_nace la valida y pone su hora.
 */
@Entity
@Immutable
@Table(name = "descarga_exportacion")
public class DescargaExportacion extends BaseEntity {

	@Column(name = "exportacion_id", nullable = false, updatable = false)
	private Long exportacionId;

	@Column(nullable = false, updatable = false)
	private int numero;

	@Column(updatable = false, length = 45)
	private String ip;

	protected DescargaExportacion() {
		// requerido por JPA
	}

	public static DescargaExportacion de(Long exportacionId, int numero, String ip) {
		DescargaExportacion d = new DescargaExportacion();
		d.exportacionId = Objects.requireNonNull(exportacionId, "exportacionId");
		d.numero = numero;
		d.ip = ip;
		return d;
	}

	public Long getExportacionId() {
		return exportacionId;
	}

	public int getNumero() {
		return numero;
	}

	public String getIp() {
		return ip;
	}
}
