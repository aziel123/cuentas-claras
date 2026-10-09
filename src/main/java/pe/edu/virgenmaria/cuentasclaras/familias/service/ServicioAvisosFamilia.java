package pe.edu.virgenmaria.cuentasclaras.familias.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.SesionApoderado;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AnulacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.DescuentoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.AvisosMatricula;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoRequest;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoVista;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.OpcionesAviso;
import pe.edu.virgenmaria.cuentasclaras.familias.model.AvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.model.EstadoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.repository.AvisoFamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ControlParticipantes;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * «¿Algo no cuadra?» (sprint 5, tanda 2; decisión 52). El apoderado lo envía desde el portal (5 por día y por familia,
 * G24) y le llega SOLO a Promotoría y Dirección como alerta: Caja y Administración no lo ven ni lo cierran, porque pueden
 * ser parte del problema (G1). Al atenderlo, la familia recibe un mensaje y ve la respuesta en el portal.
 */
@Service
@Transactional
public class ServicioAvisosFamilia {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final AvisoFamiliaRepository avisos;

	private final SesionApoderado sesion;

	private final PagoRepository pagos;

	private final CuotaRepository cuotas;

	private final FamiliaRepository familias;

	private final ApoderadoRepository apoderados;

	private final AuditoriaService auditoria;

	private final AvisosMatricula mensajes;

	private final Clock reloj;

	private final int maximoPorDia;

	private final AnulacionPagoRepository anulaciones;

	private final DescuentoRepository descuentos;

	private final ControlParticipantes participantes;

	/** Sprint 7, tanda 2: la firma de la sesión de quien resuelve (sección 3.4). */
	private final FirmaSesion firmaSesion;

	public ServicioAvisosFamilia(AvisoFamiliaRepository avisos, SesionApoderado sesion, PagoRepository pagos,
			CuotaRepository cuotas, FamiliaRepository familias, ApoderadoRepository apoderados, AuditoriaService auditoria,
			AvisosMatricula mensajes, Clock reloj,
			@Value("${cuentasclaras.familias.avisos-por-dia:5}") int maximoPorDia, AnulacionPagoRepository anulaciones,
			DescuentoRepository descuentos, ControlParticipantes participantes, FirmaSesion firmaSesion) {
		this.firmaSesion = firmaSesion;
		this.anulaciones = anulaciones;
		this.descuentos = descuentos;
		this.participantes = participantes;
		this.avisos = avisos;
		this.sesion = sesion;
		this.pagos = pagos;
		this.cuotas = cuotas;
		this.familias = familias;
		this.apoderados = apoderados;
		this.auditoria = auditoria;
		this.mensajes = mensajes;
		this.reloj = reloj;
		this.maximoPorDia = maximoPorDia;
	}

	@PreAuthorize("hasRole('APODERADO')")
	public Long enviar(AvisoRequest pedido) {
		Apoderado apoderado = sesion.apoderado();
		Long familiaId = apoderado.getFamilia().getId();
		LocalDateTime ahora = LocalDateTime.now(reloj);
		if (avisos.countByFamiliaIdAndCreadoEnGreaterThanEqual(familiaId, ahora.toLocalDate().atStartOfDay())
				>= maximoPorDia) {
			throw new ReglaNegocioException("Ya enviaste " + maximoPorDia + " avisos hoy. Si es urgente, llama a "
					+ "Promotoría.");
		}
		if (pedido.tipo() == null) {
			throw new ReglaNegocioException("Elige qué no cuadra.");
		}
		Pago pago = pedido.pagoId() == null ? null : pagos.findById(pedido.pagoId())
				.filter(p -> Objects.equals(p.getFamilia().getId(), familiaId))
				.orElseThrow(() -> new ReglaNegocioException("Elige uno de tus pagos."));
		Cuota cuota = pedido.cuotaId() == null ? null : cuotas.findById(pedido.cuotaId())
				.filter(c -> Objects.equals(c.getAlumno().getFamilia().getId(), familiaId))
				.orElseThrow(() -> new ReglaNegocioException("Elige una de tus cuotas."));
		AvisoFamilia aviso = avisos.saveAndFlush(AvisoFamilia.nuevo(familiaId, apoderado.getId(), pedido.tipo(),
				pago == null ? null : pago.getId(), cuota == null ? null : cuota.getId(), pedido.texto()));
		// Ley 29733: la bitácora guarda el tipo y la referencia, no el texto de la familia.
		auditoria.registrar(AccionAuditoria.AVISO_FAMILIA_RECIBIDO, "aviso_familia", aviso.getId().toString(), null,
				pedido.tipo().etiqueta(), "Familia " + apoderado.getFamilia().getNombre() + referencia(pago, cuota)
						+ ". Lo ven solo Promotoría y Dirección.");
		return aviso.getId();
	}

	@PreAuthorize("hasRole('APODERADO')")
	@Transactional(readOnly = true)
	public List<AvisoVista> misAvisos() {
		Apoderado apoderado = sesion.apoderado();
		return avisos.findByFamiliaIdOrderByIdDesc(apoderado.getFamilia().getId()).stream()
				.map(a -> vista(a, apoderado.getFamilia().getNombre(), null)).toList();
	}

	@PreAuthorize("hasRole('APODERADO')")
	@Transactional(readOnly = true)
	public OpcionesAviso opciones() {
		Long familiaId = sesion.familiaId();
		List<OpcionesAviso.Opcion> suyos = pagos.findByFamiliaIdOrderByIdDesc(familiaId).stream().limit(30)
				.map(p -> new OpcionesAviso.Opcion(p.getId(), p.getComprobante().numeroCompleto() + " · "
						+ p.getFecha().format(FECHA) + " · S/ " + p.getTotal().toPlainString()))
				.toList();
		List<OpcionesAviso.Opcion> suyas = cuotas.porPagarDeFamilia(familiaId).stream()
				.map(c -> new OpcionesAviso.Opcion(c.getId(), c.getDescripcion() + " · " + c.getAlumno().getNombres()))
				.toList();
		return new OpcionesAviso(suyos, suyas);
	}

	/** Los abiertos primero (los críticos antes), luego los últimos atendidos. Solo Promotoría y Dirección. */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	@Transactional(readOnly = true)
	public List<AvisoVista> bandeja() {
		List<AvisoFamilia> abiertos = new ArrayList<>(avisos.findByEstadoOrderByIdAsc(EstadoAvisoFamilia.ABIERTO));
		abiertos.sort(Comparator.comparing((AvisoFamilia a) -> !a.getTipo().critico()).thenComparing(AvisoFamilia::getId));
		List<AvisoVista> filas = new ArrayList<>();
		abiertos.forEach(a -> filas.add(vistaPersonal(a)));
		avisos.findTop50ByEstadoOrderByIdDesc(EstadoAvisoFamilia.ATENDIDO).forEach(a -> filas.add(vistaPersonal(a)));
		return filas;
	}

	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	@Transactional(noRollbackFor = AutoatencionAvisoException.class)
	public void atender(Long id, String respuesta) {
		AvisoFamilia aviso = avisos.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Aviso no encontrado"));
		String quien = SecurityContextHolder.getContext().getAuthentication().getName();
		// S5-M2: quien registró el pago, o pidió o aprobó la anulación o el descuento del que se queja la familia, no
		// cierra su queja (ni quien preparó su cuenta).
		Set<String> involucrados = participantes.ampliar(involucrados(aviso));
		if (involucrados.contains(quien)) {
			auditoria.registrar(AccionAuditoria.AUTOAPROBACION_RECHAZADA, "aviso_familia", id.toString(), null,
					aviso.getTipo().etiqueta(), "Intentó atender el aviso de una familia sobre un pago, una anulación o un "
							+ "descuento en el que participó. Se rechazó.");
			throw new AutoatencionAvisoException("No puedes atender este aviso: tú registraste el pago, o pediste o "
					+ "aprobaste la anulación o el descuento del que se queja la familia. Debe atenderlo otra persona "
					+ "(Promotoría).");
		}
		// Primero la regla (un aviso atendido no se vuelve a atender) y después la firma: la firma se inserta antes que el
		// UPDATE del aviso, que espera al saveAndFlush.
		aviso.atender(respuesta, quien, LocalDateTime.now(reloj));
		firmaSesion.firmar(ClaveFirma.avisoFamilia(aviso.getId()));
		avisos.saveAndFlush(aviso);
		auditoria.registrar(AccionAuditoria.AVISO_FAMILIA_ATENDIDO, "aviso_familia", id.toString(), "ABIERTO",
				"ATENDIDO", "Aviso «" + aviso.getTipo().etiqueta() + "» de la familia " + nombreFamilia(aviso.getFamiliaId())
						+ ": se respondió a la familia.");
		mensajes.avisoAtendido(aviso.getApoderadoId(), aviso.getId(), aviso.getCreadoEn().toLocalDate());
	}

	/** S5-M2: quienes registraron, pidieron o aprobaron lo que la familia no reconoce. */
	Set<String> involucrados(AvisoFamilia aviso) {
		Set<String> quienes = new LinkedHashSet<>();
		if (aviso.getPagoId() != null) {
			pagos.findById(aviso.getPagoId()).ifPresent(p -> {
				quienes.add(p.getCajero());
				quienes.add(p.getCreadoPor());
			});
			anulaciones.findByPagoId(aviso.getPagoId()).ifPresent(a -> {
				quienes.add(a.getSolicitadoPor());
				quienes.add(a.getAprobadoPor());
			});
		}
		if (aviso.getCuotaId() != null) {
			cuotas.findById(aviso.getCuotaId()).ifPresent(c -> descuentos.findByAlumnoIdOrderByIdDesc(c.getAlumno().getId())
					.stream().filter(d -> d.cuotaIds().contains(aviso.getCuotaId())).forEach(d -> {
						quienes.add(d.getCreadoPor());
						quienes.add(d.getResueltoPor());
					}));
		}
		quienes.remove(null);
		return quienes;
	}

	private AvisoVista vistaPersonal(AvisoFamilia a) {
		String apoderado = apoderados.findById(a.getApoderadoId()).map(Apoderado::nombreCompleto).orElse("—");
		return vista(a, nombreFamilia(a.getFamiliaId()), apoderado);
	}

	private AvisoVista vista(AvisoFamilia a, String familia, String apoderado) {
		Pago pago = a.getPagoId() == null ? null : pagos.findById(a.getPagoId()).orElse(null);
		Cuota cuota = a.getCuotaId() == null ? null : cuotas.findById(a.getCuotaId()).orElse(null);
		String ref = referencia(pago, cuota);
		return new AvisoVista(a.getId(), a.getCreadoEn(), familia, apoderado, a.getTipo().etiqueta(), a.getTipo().critico(),
				ref.isEmpty() ? null : ref.substring(2), a.getTexto(), a.getEstado() == EstadoAvisoFamilia.ABIERTO,
				a.getRespuesta(), a.getAtendidoEn());
	}

	private String nombreFamilia(Long familiaId) {
		return familias.findById(familiaId).map(f -> f.getNombre()).orElse("—");
	}

	private static String referencia(Pago pago, Cuota cuota) {
		StringBuilder texto = new StringBuilder();
		if (pago != null) {
			texto.append(". Pago ").append(pago.getComprobante().numeroCompleto()).append(" del ")
					.append(pago.getFecha().format(FECHA));
		}
		if (cuota != null) {
			texto.append(". Cuota ").append(cuota.getDescripcion());
		}
		return texto.toString();
	}
}
