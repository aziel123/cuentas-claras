package pe.edu.virgenmaria.cuentasclaras.operacion.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.log.ContadorErrores;
import pe.edu.virgenmaria.cuentasclaras.operacion.log.ContadorErrores.ErrorAgrupado;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoProceso;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoTecnico;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.FotoTecnica;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Alertas técnicas (sprint 7, sección 10.4): cada 5 minutos revisa el estado técnico y manda un CORREO DIRECTO por SMTP
 * al operador ({@code CC_OPERADOR_CORREO}). No pasa por la tabla {@code mensaje}: si la base se cae, la alerta igual
 * sale. No es un actor de sistema: no escribe en la base. Una misma alerta (tipo y huella) sale como máximo una vez por
 * hora; a las 07:00 manda un resumen técnico. Sin datos personales: tipo, proceso, huella, id de petición y conteos.
 */
@Component
public class AlertasTecnicas {

	private static final Logger LOG = LoggerFactory.getLogger(AlertasTecnicas.class);

	/** Gravedad de una alerta técnica. */
	public enum Gravedad {
		CRITICA, ATENCION
	}

	/** Una alerta técnica: gravedad, tipo, huella (qué proceso, qué error) y el texto para el operador. */
	public record AlertaTecnica(Gravedad gravedad, String tipo, String huella, String texto) {

		String clave() {
			return tipo + "|" + huella;
		}
	}

	private final EstadoTecnico estado;

	private final ContadorErrores errores;

	private final PropiedadesMonitoreo propiedades;

	private final ObjectProvider<JavaMailSender> correo;

	private final String remitente;

	private final Clock reloj;

	private final Map<String, Instant> enviadas = new HashMap<>();

	private final Set<String> huellasConocidas = new HashSet<>();

	public AlertasTecnicas(EstadoTecnico estado, ContadorErrores errores, PropiedadesMonitoreo propiedades,
			ObjectProvider<JavaMailSender> correo, @Value("${cuentasclaras.mensajeria.correo.remitente:}") String remitente,
			Clock reloj) {
		this.estado = estado;
		this.errores = errores;
		this.propiedades = propiedades;
		this.correo = correo;
		this.remitente = remitente == null ? "" : remitente.strip();
		this.reloj = reloj;
	}

	/** Cada 5 minutos: detecta y avisa lo nuevo (o lo que sigue mal, una vez por hora). */
	@Scheduled(cron = "${cuentasclaras.monitoreo.cada:0 */5 * * * *}", zone = ConfiguracionTiempo.ZONA)
	public synchronized void revisar() {
		List<AlertaTecnica> nuevas = porEnviar(detectar(estado.foto()));
		if (nuevas.isEmpty()) {
			return;
		}
		nuevas.forEach(a -> LOG.warn("Alerta técnica {} · {} · {}: {}", a.gravedad(), a.tipo(), a.huella(), a.texto()));
		long criticas = nuevas.stream().filter(a -> a.gravedad() == Gravedad.CRITICA).count();
		boolean salio = enviar("[Cuentas Claras] " + nuevas.size() + " alerta(s) técnica(s)"
				+ (criticas > 0 ? ", " + criticas + " CRÍTICA(S)" : ""),
				nuevas.stream().map(a -> a.gravedad() + " · " + a.tipo() + " · " + a.huella() + "\n  " + a.texto())
						.collect(Collectors.joining("\n\n")));
		// Correcciones del sprint 7 (QA-S7-7): se dan por enviadas SOLO si el correo salió (o si las alertas por correo
		// están apagadas: no hay nada que reintentar). Si el SMTP falló, la revisión siguiente (5 minutos) las reintenta.
		if (salio) {
			marcarEnviadas(nuevas);
		}
	}

	/** A las 07:00: el resumen técnico del día (aunque no haya alertas). */
	@Scheduled(cron = "${cuentasclaras.monitoreo.resumen-tecnico:0 0 7 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void resumenDiario() {
		FotoTecnica foto = estado.foto();
		List<AlertaTecnica> abiertas = detectar(foto);
		StringBuilder texto = new StringBuilder();
		texto.append("Respaldo: ").append(foto.respaldo().existe()
				? foto.respaldo().fin() + " (" + foto.respaldo().comparacion() + ", " + (foto.respaldo().alDia() ? "al día" : "ATRASADO") + ")"
				: "ninguno registrado").append('\n');
		texto.append("Procesos atrasados: ").append(foto.atrasados().size()).append(" de ").append(foto.procesos().size())
				.append('\n');
		texto.append("Errores de hoy: ").append(foto.errores().stream().mapToLong(ErrorAgrupado::hoy).sum())
				.append(" en ").append(foto.errores().size()).append(" huella(s)\n");
		texto.append("Bitácora al día: ").append(foto.bitacoraAlDia() ? "sí" : "NO").append('\n');
		texto.append("Disco libre: ").append(foto.discoLibre() == null ? "?" : foto.discoLibre() + " %").append('\n');
		texto.append("Versión: ").append(foto.version()).append("\n\n");
		texto.append(abiertas.isEmpty() ? "Sin alertas abiertas."
				: "Alertas abiertas:\n" + abiertas.stream().map(a -> "- " + a.gravedad() + " · " + a.tipo() + " · " + a.huella())
						.collect(Collectors.joining("\n")));
		enviar("[Cuentas Claras] Resumen técnico" + (abiertas.isEmpty() ? "" : ": " + abiertas.size() + " alerta(s) abierta(s)"),
				texto.toString());
	}

	/** Todas las alertas que corresponden a ese estado (sin filtrar por las ya enviadas). */
	public List<AlertaTecnica> detectar(FotoTecnica foto) {
		List<AlertaTecnica> alertas = new ArrayList<>();
		if (!foto.baseResponde()) {
			alertas.add(new AlertaTecnica(Gravedad.CRITICA, "BASE", "conexion", "La base de datos no responde."));
		}
		if (foto.poolAgotado()) {
			alertas.add(new AlertaTecnica(Gravedad.CRITICA, "POOL", "conexiones",
					"El pool de conexiones está agotado desde hace más de 1 minuto: hay peticiones esperando."));
		}
		for (EstadoProceso proceso : foto.atrasados()) {
			alertas.add(new AlertaTecnica(proceso.critico() ? Gravedad.CRITICA : Gravedad.ATENCION, "PROCESO_ATRASADO",
					proceso.clave(), "«" + proceso.nombre() + "» no terminó bien en su ventana (" + proceso.programacion()
							+ "; último éxito: " + (proceso.ultimoExito() == null ? "ninguno desde el arranque"
									: enLima(proceso.ultimoExito()) + ", hora de Lima") + ")."));
		}
		if (foto.baseResponde() && foto.respaldo().faltanFilas()) {
			String archivo = foto.respaldo().porResolverArchivo() != null ? foto.respaldo().porResolverArchivo()
					: foto.respaldo().archivo();
			alertas.add(new AlertaTecnica(Gravedad.CRITICA, "FALTAN_FILAS", archivo,
					"El respaldo " + archivo + " encontró filas faltantes o un ancla distinta: "
							+ foto.respaldo().diferencias() + ". Sigue abierta hasta que Promotoría la resuelva con motivo "
							+ "en /panel/sistema. Preserva la evidencia (incidente-auditoria.md)."));
		}
		if (foto.baseResponde() && propiedades.respaldoExigido() && !foto.respaldo().alDia()) {
			alertas.add(new AlertaTecnica(Gravedad.CRITICA, "SIN_RESPALDO", "respaldo",
					"No hay un respaldo de las últimas " + propiedades.respaldoMaxHoras() + " horas"
							+ (foto.respaldo().existe() ? " (el último terminó " + foto.respaldo().fin() + ")." : ".")));
		}
		if (foto.baseResponde() && !foto.bitacoraAlDia()) {
			alertas.add(new AlertaTecnica(Gravedad.CRITICA, "BITACORA", "eslabon",
					"El eslabón de la cadena no apunta al último evento de la bitácora: revisa incidente-auditoria.md."));
		}
		for (ErrorAgrupado error : foto.errores()) {
			long recientes = errores.enVentana(error.huella(), propiedades.erroresVentana());
			if (recientes >= propiedades.erroresUmbral()) {
				alertas.add(new AlertaTecnica(Gravedad.ATENCION, "ERRORES", error.huella(), recientes + " errores en "
						+ propiedades.erroresVentana().toMinutes() + " minutos (último id de petición: "
						+ error.ultimoIdPeticion() + ")."));
			}
			else if (!huellasConocidas.contains(error.huella())) {
				alertas.add(new AlertaTecnica(Gravedad.ATENCION, "ERROR_NUEVO", error.huella(),
						"Un error que no se había visto (último id de petición: " + error.ultimoIdPeticion() + ")."));
			}
		}
		if (foto.discoLibre() != null && foto.discoLibre() < propiedades.discoMinimoPorcentaje()) {
			alertas.add(new AlertaTecnica(Gravedad.ATENCION, "DISCO", "disco", "Queda " + foto.discoLibre()
					+ " % de disco libre (mínimo " + propiedades.discoMinimoPorcentaje() + " %)."));
		}
		return alertas;
	}

	/** Las que no salieron en la última hora (no las marca: lo hace {@link #marcarEnviadas} si el correo salió). */
	synchronized List<AlertaTecnica> porEnviar(List<AlertaTecnica> alertas) {
		Instant ahora = reloj.instant();
		List<AlertaTecnica> nuevas = new ArrayList<>();
		for (AlertaTecnica alerta : alertas) {
			Instant ultima = enviadas.get(alerta.clave());
			if (ultima == null || !ahora.isBefore(ultima.plus(propiedades.repetirCada()))) {
				nuevas.add(alerta);
			}
		}
		return nuevas;
	}

	/** Las alertas que salieron: no se repiten en la próxima hora; sus huellas de error quedan conocidas. */
	synchronized void marcarEnviadas(List<AlertaTecnica> alertas) {
		Instant ahora = reloj.instant();
		for (AlertaTecnica alerta : alertas) {
			enviadas.put(alerta.clave(), ahora);
			if (alerta.tipo().startsWith("ERROR")) {
				huellasConocidas.add(alerta.huella());
			}
		}
	}

	/** @return si el correo salió o si las alertas por correo están apagadas (no hay nada que reintentar) */
	private boolean enviar(String asunto, String texto) {
		JavaMailSender servidor = correo.getIfAvailable();
		if (propiedades.operadorCorreo().isBlank() || remitente.isBlank() || servidor == null) {
			LOG.warn("Alertas técnicas apagadas (falta CC_OPERADOR_CORREO, el remitente o el servidor de correo): {}",
					asunto);
			return true;
		}
		SimpleMailMessage mensaje = new SimpleMailMessage();
		mensaje.setFrom(remitente);
		mensaje.setTo(propiedades.operadorCorreo());
		mensaje.setSubject(asunto);
		mensaje.setText(texto + "\n\nDetalle en /panel/sistema. Este correo no lleva datos personales.");
		try {
			servidor.send(mensaje);
			return true;
		}
		catch (MailException e) {
			LOG.error("No se pudo enviar la alerta técnica por correo (se reintenta en la revisión siguiente): {}",
					e.getClass().getSimpleName());
			return false;
		}
	}

	private static final DateTimeFormatter LIMA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
			.withZone(ConfiguracionTiempo.ZONA_LIMA);

	/** Correcciones del sprint 7 (QA-S7-7): las horas de las alertas, en hora de Lima como todo el sistema. */
	private static String enLima(Instant instante) {
		return LIMA.format(instante);
	}
}
