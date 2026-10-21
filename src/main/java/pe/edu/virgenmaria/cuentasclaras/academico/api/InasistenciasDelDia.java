package pe.edu.virgenmaria.cuentasclaras.academico.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Sprint 9, tanda 2 (secciones 3.6 y 9, A1 y A2): lo que necesita {@code comunicacion.proceso.AvisosAsistencia} para
 * avisar a las familias, y nada más: por familia, el PRIMER nombre del alumno, el estado (también la falta justificada,
 * A1), la hora de llegada y quién registró una corrección. Nunca apellidos, sección, documentos ni nada de cobranza (el
 * proceso ni siquiera puede saber quién debe: ArchUnit). Lo usa solo {@code sistema.asistencia}, que también sube la
 * marca de agua de los avisos del colegio (A2).
 */
public interface InasistenciasDelDia {

	/** Las horas del colegio (las de su configuración o, sin ella, las de por defecto). */
	Horas horas();

	/** La marca de agua del colegio: hasta qué marca se avisó y cuándo fue el último envío (vacío al empezar). */
	Corte corte();

	/** Las marcas VIGENTES de la fecha que se avisan (falta, tarde y falta justificada), por familia. */
	List<Inasistencia> delDia(LocalDate fecha);

	/**
	 * Lo que cambió después de la marca de agua (de cualquier día), por alumno y registro: el estado antes del primer
	 * cambio posterior y el vigente. {@code registroNuevo}: el registro mismo es posterior (no había nada que corregir).
	 */
	List<Cambio> despuesDe(long ultimaMarcaId);

	/** El id más alto de las marcas del colegio. */
	long ultimaMarcaId();

	/** Sube la marca de agua (nunca la baja) y anota el momento del envío. */
	void marcarAvisado(long hastaMarcaId, LocalDateTime enviadoEn);

	/** Las horas que fija Dirección: el primer aviso y el segundo (que también cierra las correcciones del día). */
	record Horas(LocalTime aviso, LocalTime segundoAviso) {
	}

	/** La marca de agua: la última marca avisada y el momento del último envío ({@code null} si nunca hubo). */
	record Corte(long ultimaMarcaId, LocalDateTime ultimoEnvio) {
	}

	/** Una inasistencia vigente de un alumno en un día (lo que va en el aviso del día). */
	record Inasistencia(long marcaId, long familiaId, long alumnoId, String primerNombre, LocalDate fecha, String estado,
			LocalTime horaLlegada) {
	}

	/** Un cambio posterior a la marca de agua: antes y ahora, y quién lo registró («Prof. Ana T.»). */
	record Cambio(long marcaId, long familiaId, long alumnoId, String primerNombre, LocalDate fecha, String antes,
			String ahora, boolean ahoraSeAvisa, boolean registroNuevo, String registradoPor, LocalTime horaLlegada) {
	}
}
