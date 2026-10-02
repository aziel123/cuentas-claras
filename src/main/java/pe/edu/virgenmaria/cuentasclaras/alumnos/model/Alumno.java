package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Alumno. Su responsable de pago es un apoderado activo de SU familia: lo exige también la base con la FK
 * (responsable_pago_id, familia_id). No se borra: se retira (con fecha y motivo).
 */
@Entity
@Table(name = "alumno")
public class Alumno extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "familia_id", nullable = false)
	private Familia familia;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "responsable_pago_id", nullable = false)
	private Apoderado responsablePago;

	@Embedded
	private DocumentoIdentidad documento;

	@Column(name = "apellido_paterno", nullable = false, length = 60)
	private String apellidoPaterno;

	@Column(name = "apellido_materno", length = 60)
	private String apellidoMaterno;

	@Column(nullable = false, length = 60)
	private String nombres;

	@Column(name = "fecha_nacimiento", nullable = false)
	private LocalDate fechaNacimiento;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoAlumno estado;

	@Column(name = "nombre_busqueda", nullable = false, length = 190)
	private String nombreBusqueda;

	@Column(name = "retirado_en")
	private LocalDate retiradoEn;

	@Column(name = "retirado_por", length = 60)
	private String retiradoPor;

	@Column(name = "motivo_retiro", length = 500)
	private String motivoRetiro;

	protected Alumno() {
		// requerido por JPA
	}

	/** Alumno activo; su familia es la de su responsable de pago. */
	public static Alumno nuevo(DatosAlumno datos, Apoderado responsable) {
		Alumno alumno = new Alumno();
		alumno.asignar(datos);
		alumno.estado = EstadoAlumno.ACTIVO;
		alumno.asignarResponsable(responsable);
		return alumno;
	}

	/** @return los campos que cambiaron (vacía si no cambió nada) */
	public List<String> actualizar(DatosAlumno datos) {
		List<String> cambios = new ArrayList<>();
		if (!documento.equals(datos.documento())) {
			cambios.add("documento");
		}
		if (!Objects.equals(apellidoPaterno, datos.apellidoPaterno())
				|| !Objects.equals(apellidoMaterno, datos.apellidoMaterno())
				|| !Objects.equals(nombres, datos.nombres())) {
			cambios.add("nombre");
		}
		if (!Objects.equals(fechaNacimiento, datos.fechaNacimiento())) {
			cambios.add("fecha de nacimiento");
		}
		if (!cambios.isEmpty()) {
			asignar(datos);
		}
		return cambios;
	}

	/** Cambia el responsable de pago. Si es de otra familia, el alumno pasa a esa familia. */
	public void cambiarResponsable(Apoderado nuevo) {
		Objects.requireNonNull(nuevo, "nuevo");
		if (estado != EstadoAlumno.ACTIVO) {
			throw new ReglaNegocioException(nombreCompleto() + " no está activo: no se le cambia el responsable de pago.");
		}
		if (nuevo.getId() != null && nuevo.getId().equals(responsablePago.getId())) {
			throw new ReglaNegocioException(nuevo.nombreCompleto() + " ya es el responsable de pago.");
		}
		asignarResponsable(nuevo);
	}

	public void retirar(LocalDate fecha, String por, String motivo) {
		if (estado != EstadoAlumno.ACTIVO) {
			throw new ReglaNegocioException(nombreCompleto() + " no está activo.");
		}
		estado = EstadoAlumno.RETIRADO;
		retiradoEn = Objects.requireNonNull(fecha, "fecha");
		retiradoPor = Objects.requireNonNull(por, "por");
		motivoRetiro = Objects.requireNonNull(motivo, "motivo");
	}

	public boolean activo() {
		return estado == EstadoAlumno.ACTIVO;
	}

	public String nombreCompleto() {
		return nombres + " " + apellidoPaterno + (apellidoMaterno == null ? "" : " " + apellidoMaterno);
	}

	private void asignarResponsable(Apoderado responsable) {
		Objects.requireNonNull(responsable, "responsable");
		if (!responsable.isActivo()) {
			throw new ReglaNegocioException(responsable.nombreCompleto() + " está desactivado: elige un apoderado activo.");
		}
		responsablePago = responsable;
		familia = responsable.getFamilia();
	}

	private void asignar(DatosAlumno datos) {
		Objects.requireNonNull(datos, "datos");
		documento = Objects.requireNonNull(datos.documento(), "documento");
		apellidoPaterno = Objects.requireNonNull(datos.apellidoPaterno(), "apellidoPaterno");
		apellidoMaterno = datos.apellidoMaterno();
		nombres = Objects.requireNonNull(datos.nombres(), "nombres");
		fechaNacimiento = Objects.requireNonNull(datos.fechaNacimiento(), "fechaNacimiento");
		nombreBusqueda = Normalizador.paraBusqueda(apellidoPaterno, apellidoMaterno, nombres);
	}

	public Familia getFamilia() {
		return familia;
	}

	public Apoderado getResponsablePago() {
		return responsablePago;
	}

	public DocumentoIdentidad getDocumento() {
		return documento;
	}

	public String getApellidoPaterno() {
		return apellidoPaterno;
	}

	public String getApellidoMaterno() {
		return apellidoMaterno;
	}

	public String getNombres() {
		return nombres;
	}

	public LocalDate getFechaNacimiento() {
		return fechaNacimiento;
	}

	public EstadoAlumno getEstado() {
		return estado;
	}

	public LocalDate getRetiradoEn() {
		return retiradoEn;
	}

	public String getRetiradoPor() {
		return retiradoPor;
	}

	public String getMotivoRetiro() {
		return motivoRetiro;
	}
}
