package pe.edu.virgenmaria.cuentasclaras.operacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
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

	private final Clock reloj;

	public ServicioEstadoTecnico(EstadoTecnico estado, PropiedadesMonitoreo propiedades, Clock reloj) {
		this.estado = estado;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	@PreAuthorize("hasRole('PROMOTOR')")
	public VistaSistema vista() {
		FotoTecnica foto = estado.foto();
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
				case IGUAL -> "Están todas las filas del respaldo anterior.";
				case FALTAN_FILAS -> "Faltan filas que existían en el respaldo anterior (" + r.diferencias()
						+ "). Avisa al responsable técnico y no toques nada.";
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
				foto.poolAgotado(), foto.version());
	}

	/** {@code OK}, {@code ATRASADO} o {@code REVISAR}, sin fechas ni datos (ruta pública para el vigilante externo). */
	public String respaldoParaVigilante() {
		return estado.respaldo().paraVigilante();
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
