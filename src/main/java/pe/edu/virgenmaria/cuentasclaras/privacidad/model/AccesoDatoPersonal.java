package pe.edu.virgenmaria.cuentasclaras.privacidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;

import java.util.Objects;

/**
 * Quién vio datos personales (sprint 7, tanda 3; Ley 29733, sección 8.2): una persona del personal, desde su sesión, abrió
 * una pantalla con datos personales de una familia o un alumno (o una búsqueda o lista con N filas). Solo inserción:
 * todas sus columnas son {@code updatable = false} y {@code cc_app} tiene solo INSERT (1142 al editar o borrar). Fuera de
 * la cadena HMAC de la bitácora: son cientos de filas al día y no son operaciones financieras.
 */
@Entity
@Table(name = "acceso_dato_personal")
public class AccesoDatoPersonal extends BaseEntity {

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(name = "sesion_id", updatable = false)
	private Long sesionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30, updatable = false)
	private TipoAcceso tipo;

	@Column(name = "familia_id", updatable = false)
	private Long familiaId;

	@Column(name = "alumno_id", updatable = false)
	private Long alumnoId;

	@Column(nullable = false, updatable = false)
	private int cantidad;

	@Column(length = 45, updatable = false)
	private String ip;

	protected AccesoDatoPersonal() {
		// requerido por JPA
	}

	public static AccesoDatoPersonal de(Long usuarioId, Long sesionId, TipoAcceso tipo, Long familiaId, Long alumnoId,
			int cantidad, String ip) {
		AccesoDatoPersonal a = new AccesoDatoPersonal();
		a.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
		a.sesionId = sesionId;
		a.tipo = Objects.requireNonNull(tipo, "tipo");
		if ((tipo == TipoAcceso.FICHA_ALUMNO && alumnoId == null) || (familiaId == null && (tipo == TipoAcceso.FICHA_FAMILIA
				|| tipo == TipoAcceso.APROBACION_CONTACTO || tipo == TipoAcceso.LLAMADA_CONTROL))) {
			throw new IllegalArgumentException("La ficha debe decir de qué familia o alumno es");
		}
		if (cantidad < 0) {
			throw new IllegalArgumentException("cantidad");
		}
		a.familiaId = familiaId;
		a.alumnoId = alumnoId;
		a.cantidad = cantidad;
		a.ip = ip == null || ip.length() <= 45 ? ip : ip.substring(0, 45);
		return a;
	}

	@PreRemove
	void impedirBorrado() {
		throw new UnsupportedOperationException("El registro de accesos a datos personales no se borra");
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Long getSesionId() {
		return sesionId;
	}

	public TipoAcceso getTipo() {
		return tipo;
	}

	public Long getFamiliaId() {
		return familiaId;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public int getCantidad() {
		return cantidad;
	}

	public String getIp() {
		return ip;
	}
}
