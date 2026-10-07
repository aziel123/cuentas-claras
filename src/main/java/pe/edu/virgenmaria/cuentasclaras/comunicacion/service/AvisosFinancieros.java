package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AplicacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.OrigenPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAplicacion;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.service.PagoAnulado;
import pe.edu.virgenmaria.cuentasclaras.caja.service.PagoRegistrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.DescuentoAprobado;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Avisos financieros a la familia (sprint 5, control 4 de la skill: el padre es el auditor). Los oyentes son SÍNCRONOS:
 * corren dentro de la transacción del pago, la anulación o el descuento y crean sus mensajes antes del commit. Si un
 * mensaje no se puede crear, la excepción revierte la operación: no puede existir un pago vigente sin su aviso.
 * <ul>
 *   <li>Pago: al responsable de pago de cada alumno pagado (uno por apoderado), con monto, conceptos, comprobante, fecha
 *       y hora, medio y quién lo registró (G2).</li>
 *   <li>Anulación y descuento: a TODOS los apoderados activos de la familia, por WhatsApp y correo (G3 y G4).</li>
 * </ul>
 * Ley 29733: solo el nombre de pila del alumno; nunca el DNI ni el nombre completo.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AvisosFinancieros {

	static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final CreadorMensajes creador;

	private final PagoRepository pagos;

	private final AplicacionPagoRepository aplicaciones;

	private final ApoderadoRepository apoderados;

	private final AlumnoRepository alumnos;

	private final CuotaRepository cuotas;

	private final UsuarioRepository usuarios;

	public AvisosFinancieros(CreadorMensajes creador, PagoRepository pagos, AplicacionPagoRepository aplicaciones,
			ApoderadoRepository apoderados, AlumnoRepository alumnos, CuotaRepository cuotas, UsuarioRepository usuarios) {
		this.creador = creador;
		this.pagos = pagos;
		this.aplicaciones = aplicaciones;
		this.apoderados = apoderados;
		this.alumnos = alumnos;
		this.cuotas = cuotas;
		this.usuarios = usuarios;
	}

	@EventListener
	public void alPagoRegistrado(PagoRegistrado evento) {
		Pago pago = pagos.findById(evento.pagoId())
				.orElseThrow(() -> new IllegalStateException("El pago " + evento.pagoId() + " no existe"));
		List<AplicacionPago> lineas = aplicaciones.dePagos(List.of(pago.getId())).stream()
				.filter(a -> a.getTipo() == TipoAplicacion.APLICACION).toList();
		Map<Long, Apoderado> responsables = new LinkedHashMap<>();
		lineas.forEach(a -> {
			Apoderado responsable = a.getCuota().getAlumno().getResponsablePago();
			responsables.putIfAbsent(responsable.getId(), responsable);
		});
		List<String> parametros = List.of(Dinero.formatear(pago.getTotal()), conceptos(lineas.stream()
				.map(AplicacionPago::getCuota).toList()), pago.getComprobante().numeroCompleto(),
				pago.getCreadoEn().format(FECHA_HORA), pago.getMedio().etiqueta(), registradoPor(pago));
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.PAGO_REGISTRADO,
				PlantillaMensaje.PAGO_REGISTRADO, parametros, "pago", pago.getId());
		responsables.values().stream().filter(Apoderado::isActivo)
				.forEach(a -> creador.paraApoderado(a, contenido, false, null));
	}

	@EventListener
	public void alPagoAnulado(PagoAnulado evento) {
		Pago pago = pagos.findById(evento.pagoId())
				.orElseThrow(() -> new IllegalStateException("El pago " + evento.pagoId() + " no existe"));
		List<String> parametros = List.of(pago.getComprobante().numeroCompleto(), Dinero.formatear(pago.getTotal()),
				pago.getMedio().etiqueta(), recortar(evento.motivo(), 200), rolDe(evento.aprobadoPor()));
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.PAGO_ANULADO,
				PlantillaMensaje.PAGO_ANULADO, parametros, "pago", pago.getId());
		apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(pago.getFamilia().getId()).stream()
				.filter(Apoderado::isActivo).forEach(a -> creador.paraApoderado(a, contenido, true, null));
	}

	@EventListener
	public void alDescuentoAprobado(DescuentoAprobado evento) {
		Alumno alumno = alumnos.findById(evento.alumnoId())
				.orElseThrow(() -> new IllegalStateException("El alumno del descuento no existe"));
		// Ya bloqueadas en esta transacción por el manejador del descuento: no espera.
		List<Cuota> afectadas = cuotas.bloquear(evento.cuotaIds());
		List<String> parametros = List.of(Dinero.formatear(evento.total()), conceptos(afectadas),
				rolDe(evento.aprobadoPor()));
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.DESCUENTO_APROBADO,
				PlantillaMensaje.DESCUENTO_APROBADO, parametros, "descuento", evento.descuentoId());
		apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(alumno.getFamilia().getId()).stream()
				.filter(Apoderado::isActivo).forEach(a -> creador.paraApoderado(a, contenido, true, null));
	}

	/** «Pensión de marzo de Ana; Pensión de marzo de Luis» (hasta 300 caracteres). */
	static String conceptos(List<Cuota> lista) {
		Set<String> textos = new LinkedHashSet<>();
		lista.forEach(c -> textos.add(c.getDescripcion() + " de " + nombreDePila(c.getAlumno())));
		return recortar(String.join("; ", textos), 300);
	}

	static String nombreDePila(Alumno alumno) {
		String nombres = alumno.getNombres() == null ? "" : alumno.getNombres().strip();
		return nombres.isEmpty() ? "su hijo(a)" : nombres.split("\\s+")[0];
	}

	/** Quién lo registró: el nombre corto de la cajera, «pago en línea» o «banco» (decisión 43). */
	private String registradoPor(Pago pago) {
		if (pago.getOrigen() == OrigenPago.PASARELA) {
			return "pago en línea";
		}
		if (pago.getOrigen() == OrigenPago.RECAUDACION) {
			return "banco";
		}
		return usuarios.findByNombreUsuario(pago.getCajero()).map(Usuario::getNombreCompleto)
				.map(AvisosFinancieros::nombreCorto).orElse("caja del colegio");
	}

	/** «Lucía Ramos Vega» → «Lucía R.». */
	static String nombreCorto(String nombreCompleto) {
		String[] partes = nombreCompleto.strip().split("\\s+");
		return partes.length == 1 ? partes[0] : partes[0] + " " + partes[1].charAt(0) + ".";
	}

	/** El rol de quien aprobó («Dirección»), nunca su usuario. */
	private String rolDe(String nombreUsuario) {
		return usuarios.findByNombreUsuario(nombreUsuario)
				.map(u -> u.getRoles().stream().filter(r -> r != Rol.APODERADO).sorted().map(Rol::etiqueta)
						.collect(Collectors.joining(" y ")))
				.filter(s -> !s.isBlank()).orElse("el colegio");
	}

	private static String recortar(String texto, int maximo) {
		String limpio = texto == null ? "" : texto.strip();
		return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo - 1) + "…";
	}
}
