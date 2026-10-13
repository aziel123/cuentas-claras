package pe.edu.virgenmaria.cuentasclaras.operacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.config.PropiedadesPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoTecnico;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.FotoTecnica;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Estado técnico para Promotoría ({@code /panel/sistema}) y para el vigilante externo ({@code /salud/respaldo}).
 */
@Service
public class ServicioEstadoTecnico {

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final EstadoTecnico estado;

	private final PropiedadesMonitoreo propiedades;

	private final PropiedadesPlataforma plataforma;

	private final Clock reloj;

	public ServicioEstadoTecnico(EstadoTecnico estado, PropiedadesMonitoreo propiedades, PropiedadesPlataforma plataforma,
			Clock reloj) {
		this.estado = estado;
		this.propiedades = propiedades;
		this.plataforma = plataforma;
		this.reloj = reloj;
	}

	/**
	 * Sprint 8, tanda 3 (sección 3.8, decisiones 129 y 130; E24): quién puede abrir el respaldo y custodia la clave de la
	 * bitácora. En una instalación COMPARTIDA, la plataforma (2 custodios del proveedor): ningún colegio recibe la clave de
	 * un respaldo con datos de otros. En una DEDICADA, la Promotoría y el responsable técnico, como en el sprint 7.
	 */
	public String custodios() {
		return plataforma.compartida()
				? "Custodios del respaldo: la plataforma (el responsable técnico y un segundo custodio del proveedor). "
						+ "Tu colegio comprueba su bitácora con la huella diaria y con la verificación trimestral."
				: "Custodios del respaldo: Promotoría y el responsable técnico (cada uno con su clave).";
	}

	@PreAuthorize("hasRole('PROMOTOR')")
	public VistaSistema vista() {
		// Sprint 8, tanda 3 (V30): «Faltan filas» de SU colegio.
		Long colegio = ContextoColegio.actual();
		FotoTecnica foto = estado.foto(colegio != null && colegio > 0 ? colegio : null);
		EstadoRespaldo r = foto.respaldo();
		String respaldo;
		String detalle;
		if (!r.existe()) {
			respaldo = "Todavía no hay respaldos registrados";
			detalle = propiedades.respaldoExigido() ? "El respaldo diario debe correr a las 02:30. Avisa al responsable técnico."
					: "En este entorno no se exigen respaldos.";
		}
		else {
			respaldo = "Último respaldo: " + cuando(r.fin()) + (r.alDia() ? ", verificado en el destino" : ", ATRASADO");
			detalle = switch (r.comparacion()) {
				case PRIMERO -> "Primer respaldo de este destino.";
				case IGUAL -> r.diferencias() != null && r.diferencias().startsWith(EstadoTecnico.RESUELTA)
						? r.diferencias() + "."
						: "Están todas las filas del respaldo anterior.";
				case FALTAN_FILAS -> "Faltan filas que existían en un respaldo anterior (" + r.diferencias()
						+ (r.porResolverArchivo() == null ? "" : "; respaldo " + r.porResolverArchivo())
						+ "). Avisa al responsable técnico y no toques nada. La alerta sigue hasta que la resuelvas con "
						+ "motivo, después de revisar qué pasó.";
			};
		}
		return new VistaSistema(respaldo, detalle, r.alDia(), r.faltanFilas(), propiedades.respaldoExigido(),
				foto.alertasEncendidas(), foto.tareasActivas(),
				foto.procesos().stream().map(p -> new VistaSistema.Proceso(p.nombre(), p.programacion(),
						p.ultimoExito() == null ? "Sin latido desde el arranque" : cuando(p.ultimoExito()), p.atrasado(),
						p.critico())).toList(),
				foto.errores().stream().map(e -> new VistaSistema.ErrorDeHoy(e.huella(), e.hoy(), cuando(e.ultimo()),
						e.ultimoIdPeticion() == null ? "—" : e.ultimoIdPeticion())).toList(),
				foto.bitacoraAlDia(), foto.discoLibre() == null ? "No se pudo leer" : foto.discoLibre() + " % libre",
				foto.discoLibre() != null && foto.discoLibre() < propiedades.discoMinimoPorcentaje(), foto.baseResponde(),
				foto.poolAgotado(), foto.version(), custodios());
	}

	/** {@code OK}, {@code ATRASADO} o {@code REVISAR}, sin fechas ni datos (ruta pública para el vigilante externo). */
	public String respaldoParaVigilante() {
		return estado.paraVigilante();
	}

	private String cuando(Instant momento) {
		return cuando(LocalDateTime.ofInstant(momento, ConfiguracionTiempo.ZONA_LIMA));
	}

	private String cuando(LocalDateTime momento) {
		LocalDate hoy = LocalDate.now(reloj.withZone(ConfiguracionTiempo.ZONA_LIMA));
		if (momento.toLocalDate().equals(hoy)) {
			return "hoy " + HORA.format(momento);
		}
		if (momento.toLocalDate().equals(hoy.minusDays(1))) {
			return "ayer " + HORA.format(momento);
		}
		return FECHA_HORA.format(momento);
	}
}
