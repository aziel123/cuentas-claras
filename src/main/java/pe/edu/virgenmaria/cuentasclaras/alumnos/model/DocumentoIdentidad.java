package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;

import java.util.Objects;

/**
 * Documento de identidad ya validado ({@link ReglasDatosPersonales#documento}). Es único por colegio y tipo (base).
 * Se puede corregir (con motivo y auditoría), por eso sus columnas se actualizan.
 */
@Embeddable
public record DocumentoIdentidad(
		@Enumerated(EnumType.STRING) @Column(name = "tipo_documento", nullable = false, length = 20) TipoDocumento tipo,
		@Column(name = "numero_documento", nullable = false, length = 12) String numero)
		implements java.io.Serializable {

	public DocumentoIdentidad {
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(numero, "numero");
	}

	/** «DNI 78451236». Solo para pantallas del personal autorizado, nunca para la bitácora ni los logs. */
	public String texto() {
		return tipo.abreviatura() + " " + numero;
	}

	/** «DNI ****1236»: para la bitácora. */
	public String enmascarado() {
		return Enmascarar.documento(tipo.abreviatura(), numero);
	}

	@Override
	public String toString() {
		// Nunca el número completo en logs o mensajes de excepción.
		return enmascarado();
	}
}
