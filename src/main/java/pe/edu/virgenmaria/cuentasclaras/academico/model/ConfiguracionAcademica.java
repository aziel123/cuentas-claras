package pe.edu.virgenmaria.cuentasclaras.academico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Sprint 9, tanda 2 (sección 3.6, decisión 148; A6): las horas y plazos académicos de UN colegio (la clave es el colegio,
 * así que no extiende {@code BaseEntity}: siempre se lee por {@code ContextoColegio.actual()}). Sin fila, valen los
 * valores por defecto ({@link #porDefecto}). La cambia SOLO Dirección con su firma
 * ({@code configuracion_academica:{colegio}:CAMBIADA:{version}}; trg_configuracion_academica_nace y _cambio) y queda
 * resaltada en la bitácora con el antes y el después. Los rangos los fija la base (CHECK): el aviso no se corre a la noche
 * para que las familias no se enteren (E15). Nunca se borra.
 */
@Entity
@Table(name = "configuracion_academica")
public class ConfiguracionAcademica {

	public static final LocalTime ALERTA = LocalTime.of(9, 30);

	public static final LocalTime AVISO = LocalTime.of(10, 30);

	public static final LocalTime SEGUNDO_AVISO = LocalTime.of(16, 0);

	public static final LocalTime RESUMEN_COMUNICADOS = LocalTime.of(18, 0);

	@Id
	@Column(name = "colegio_id", nullable = false, updatable = false)
	private Long colegioId;

	@Column(name = "hora_alerta", nullable = false)
	private LocalTime horaAlerta;

	@Column(name = "hora_aviso", nullable = false)
	private LocalTime horaAviso;

	@Column(name = "hora_segundo_aviso", nullable = false)
	private LocalTime horaSegundoAviso;

	@Column(name = "dias_justificacion", nullable = false)
	private Integer diasJustificacion;

	@Column(name = "dias_registro_tardio", nullable = false)
	private Integer diasRegistroTardio;

	@Column(name = "hora_resumen_comunicados", nullable = false)
	private LocalTime horaResumenComunicados;

	@Column(name = "tope_resumenes_mes", nullable = false)
	private Integer topeResumenesMes;

	@Column(name = "actualizado_por", nullable = false, length = 60)
	private String actualizadoPor;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private LocalDateTime creadoEn;

	@Column(name = "creado_por", nullable = false, updatable = false, length = 60)
	private String creadoPor;

	@Column(name = "actualizado_en", nullable = false)
	private LocalDateTime actualizadoEn;

	@Version
	@Column(nullable = false)
	private Long version;

	protected ConfiguracionAcademica() {
		// requerido por JPA
	}

	/** Los valores por defecto (los mismos de V33), sin guardar: un colegio sin fila los usa. */
	public static ConfiguracionAcademica porDefecto(Long colegioId) {
		ConfiguracionAcademica c = new ConfiguracionAcademica();
		c.colegioId = Objects.requireNonNull(colegioId, "colegioId");
		c.horaAlerta = ALERTA;
		c.horaAviso = AVISO;
		c.horaSegundoAviso = SEGUNDO_AVISO;
		c.diasJustificacion = 5;
		c.diasRegistroTardio = 5;
		c.horaResumenComunicados = RESUMEN_COMUNICADOS;
		c.topeResumenesMes = 4;
		return c;
	}

	/** La primera vez que Dirección la cambia: la fila nace con lo que eligió. */
	public static ConfiguracionAcademica nueva(Long colegioId, Valores valores, String director, LocalDateTime ahora) {
		ConfiguracionAcademica c = porDefecto(colegioId);
		c.creadoPor = Objects.requireNonNull(director, "director");
		c.creadoEn = Objects.requireNonNull(ahora, "ahora");
		c.aplicar(valores, director, ahora);
		return c;
	}

	/** Dirección cambia las horas o plazos (los rangos se validan aquí y en la base). */
	public void cambiar(Valores valores, String director, LocalDateTime ahora) {
		if (creadoPor == null) {
			throw new IllegalStateException("Los valores por defecto no se cambian: se crea la fila con nueva()");
		}
		aplicar(valores, director, ahora);
	}

	private void aplicar(Valores v, String director, LocalDateTime ahora) {
		Objects.requireNonNull(v, "valores");
		v.validar();
		horaAlerta = v.horaAlerta();
		horaAviso = v.horaAviso();
		horaSegundoAviso = v.horaSegundoAviso();
		diasJustificacion = v.diasJustificacion();
		diasRegistroTardio = v.diasRegistroTardio();
		horaResumenComunicados = v.horaResumenComunicados();
		topeResumenesMes = v.topeResumenesMes();
		actualizadoPor = Objects.requireNonNull(director, "director");
		actualizadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** La versión que tendrá la fila al guardarla (0 al crearla): va en la clave de la firma. */
	public long versionAlGuardar() {
		return creadoEn == null || version == null ? 0 : version + 1;
	}

	public boolean guardada() {
		return creadoPor != null && version != null;
	}

	public Valores valores() {
		return new Valores(horaAlerta, horaAviso, horaSegundoAviso, diasJustificacion, diasRegistroTardio,
				horaResumenComunicados, topeResumenesMes);
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La configuración académica no se borra: se cambia.");
	}

	/** Los valores que elige Dirección, con los rangos de la base. */
	public record Valores(LocalTime horaAlerta, LocalTime horaAviso, LocalTime horaSegundoAviso, int diasJustificacion,
			int diasRegistroTardio, LocalTime horaResumenComunicados, int topeResumenesMes) {

		public void validar() {
			if (horaAlerta == null || horaAviso == null || horaSegundoAviso == null || horaResumenComunicados == null) {
				throw new ReglaNegocioException("Completa todas las horas.");
			}
			if (fuera(horaAlerta, 7, 0, 12, 0)) {
				throw new ReglaNegocioException("La alerta de secciones sin asistencia va entre las 07:00 y las 12:00.");
			}
			if (fuera(horaAviso, 9, 0, 13, 0) || !horaAviso.isAfter(horaAlerta)) {
				throw new ReglaNegocioException("El aviso a las familias va entre las 09:00 y las 13:00, después de la "
						+ "alerta: no se puede correr a la tarde ni a la noche.");
			}
			if (fuera(horaSegundoAviso, 13, 0, 18, 0)) {
				throw new ReglaNegocioException("El segundo aviso (y el cierre de las correcciones del día) va entre las "
						+ "13:00 y las 18:00.");
			}
			if (fuera(horaResumenComunicados, 12, 0, 19, 30)) {
				throw new ReglaNegocioException("El resumen de comunicados va entre las 12:00 y las 19:30.");
			}
			if (diasJustificacion < 1 || diasJustificacion > 10 || diasRegistroTardio < 1 || diasRegistroTardio > 10) {
				throw new ReglaNegocioException("Los plazos van de 1 a 10 días hábiles.");
			}
			if (topeResumenesMes < 0 || topeResumenesMes > 20) {
				throw new ReglaNegocioException("El tope de resúmenes por WhatsApp va de 0 a 20 al mes.");
			}
		}

		private static boolean fuera(LocalTime hora, int h1, int m1, int h2, int m2) {
			return hora.isBefore(LocalTime.of(h1, m1)) || hora.isAfter(LocalTime.of(h2, m2));
		}
	}

	public Long getColegioId() {
		return colegioId;
	}

	public LocalTime getHoraAlerta() {
		return horaAlerta;
	}

	public LocalTime getHoraAviso() {
		return horaAviso;
	}

	public LocalTime getHoraSegundoAviso() {
		return horaSegundoAviso;
	}

	public int getDiasJustificacion() {
		return diasJustificacion;
	}

	public int getDiasRegistroTardio() {
		return diasRegistroTardio;
	}

	public LocalTime getHoraResumenComunicados() {
		return horaResumenComunicados;
	}

	public int getTopeResumenesMes() {
		return topeResumenesMes;
	}

	public String getActualizadoPor() {
		return actualizadoPor;
	}

	public LocalDateTime getActualizadoEn() {
		return actualizadoEn;
	}

	public Long getVersion() {
		return version;
	}
}
