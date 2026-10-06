package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aprobación de {@code APLICAR_INGRESO}: vuelve a validar con los datos actuales (la orden sigue por revisar, las cuotas
 * destino siguen por pagar y suman al menos el ingreso) y publica {@link IngresoAprobado}: el pago lo registra el sistema
 * después del commit (el trigger del pago exige esta solicitud APROBADA). Una aplicación a OTRA familia exige hablar
 * antes con ambas familias (A2) y va primero en la bandeja.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorAplicarIngreso implements ManejadorSolicitud {

	private final OrdenPagoRepository ordenes;

	private final CuotaRepository cuotas;

	private final FamiliaRepository familias;

	private final ApoderadoRepository apoderados;

	private final ApplicationEventPublisher eventos;

	public ManejadorAplicarIngreso(OrdenPagoRepository ordenes, CuotaRepository cuotas, FamiliaRepository familias,
			ApoderadoRepository apoderados, ApplicationEventPublisher eventos) {
		this.ordenes = ordenes;
		this.cuotas = cuotas;
		this.familias = familias;
		this.apoderados = apoderados;
		this.eventos = eventos;
	}

	@Override
	public String entidad() {
		return ServicioIngresosPorRevisar.ENTIDAD;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.APLICAR_INGRESO;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		OrdenPago orden = ordenes.bloquear(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("El pago en línea de la solicitud no existe."));
		if (orden.getEstado() != EstadoOrden.POR_REVISAR) {
			throw new ReglaNegocioException("El pago en línea ya no está por revisar: recházala.");
		}
		orden.exigirSinContracargo();
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		Long destino = Long.valueOf(datos.get(ServicioIngresosPorRevisar.DATO_FAMILIA));
		List<Cuota> elegidas = cuotas.bloquear(ServicioIngresosPorRevisar.cuotasDe(datos));
		BigDecimal debe = Dinero.CERO;
		for (Cuota cuota : elegidas) {
			if (!Objects.equals(cuota.getAlumno().getFamilia().getId(), destino) || !cuota.admiteCobro()) {
				throw new ReglaNegocioException("La cuota «" + cuota.getDescripcion() + "» ya no está por pagar: recházala y "
						+ "pide otra aplicación.");
			}
			debe = debe.add(cuota.saldo());
		}
		if (elegidas.isEmpty() || debe.compareTo(orden.getMontoConfirmado()) < 0) {
			throw new ReglaNegocioException("Las cuotas ya no suman el ingreso (" + Dinero.formatear(orden.getMontoConfirmado())
					+ "): recházala y pide otra aplicación o la devolución.");
		}
		eventos.publishEvent(new IngresoAprobado(orden.getId(), solicitud.getId(), orden.getColegioId()));
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		OrdenPago orden = ordenes.findById(solicitud.getEntidadId()).orElse(null);
		if (orden == null) {
			return List.of();
		}
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		List<String> lineas = new ArrayList<>();
		lineas.add("Ingreso por revisar: " + Dinero.formatear(orden.getMontoConfirmado()) + " de "
				+ orden.getFamilia().getNombre() + " por " + (orden.getMedioConfirmado() == null ? "pago en línea"
						: orden.getMedioConfirmado().etiqueta()) + " (operación " + orden.getOperacion() + ")");
		if (orden.getMotivoRevision() != null) {
			lineas.add("Por qué quedó por revisar: " + orden.getMotivoRevision().descripcion());
		}
		lineas.add("Se aplicaría a: " + String.join("; ", ServicioIngresosPorRevisar.cuotasDe(datos).stream()
				.map(id -> cuotas.findById(id).map(c -> c.getDescripcion() + " de " + c.getAlumno().nombreCompleto()
						+ " (debe " + Dinero.formatear(c.saldo()) + ")").orElse("cuota " + id)).toList()));
		String contactos = contactos(orden.getFamilia().getId());
		if (contactos != null) {
			lineas.add("Contacto del apoderado que pagó: " + contactos);
		}
		Long destino = Long.valueOf(datos.get(ServicioIngresosPorRevisar.DATO_FAMILIA));
		if (!destino.equals(orden.getFamilia().getId())) {
			String otros = contactos(destino);
			if (otros != null) {
				lineas.add("Antes de aprobar, llama también a la otra familia: " + otros);
			}
		}
		return lineas;
	}

	private Map<Long, String> familiasPorLlamar(SolicitudCambio solicitud) {
		Map<Long, String> porLlamar = new LinkedHashMap<>();
		OrdenPago orden = ordenes.findById(solicitud.getEntidadId()).orElse(null);
		if (orden == null) {
			return porLlamar;
		}
		Long destino = Long.valueOf(DatosSolicitud.leer(solicitud.getDatos()).get(ServicioIngresosPorRevisar.DATO_FAMILIA));
		if (!destino.equals(orden.getFamilia().getId())) {
			porLlamar.put(orden.getFamilia().getId(), orden.getFamilia().getNombre());
			porLlamar.put(destino, familias.findById(destino).map(Familia::getNombre).orElse("familia de destino"));
		}
		return porLlamar;
	}

	@Override
	public List<String> llamadas(SolicitudCambio solicitud) {
		return List.copyOf(familiasPorLlamar(solicitud).values());
	}

	@Override
	public String confirmarLlamadas(SolicitudCambio solicitud, List<String> telefonos) {
		List<String> partes = new ArrayList<>();
		int i = 0;
		for (Map.Entry<Long, String> familia : familiasPorLlamar(solicitud).entrySet()) {
			String numero = Telefono.normalizar(telefonos.get(i++));
			Apoderado llamado = numero == null ? null
					: apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familia.getKey()).stream()
							.filter(a -> numero.equals(a.getTelefonoWhatsapp())).findFirst().orElse(null);
			if (llamado == null) {
				throw new ReglaNegocioException("El número que escribiste para " + familia.getValue() + " no es el celular "
						+ "registrado de ninguno de sus apoderados. Llama a un número registrado.");
			}
			partes.add(llamado.nombreCompleto() + " (" + Enmascarar.telefono(numero) + ", " + familia.getValue() + ")");
		}
		return "Habló con ambas familias: " + String.join(" y ", partes) + ".";
	}

	/** Una aplicación a OTRA familia va primero (mueve dinero entre familias); las demás, junto a las anulaciones. */
	@Override
	public int prioridad(SolicitudCambio solicitud) {
		return familiasPorLlamar(solicitud).isEmpty() ? 1 : 0;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return familiasPorLlamar(solicitud).isEmpty() ? null
				: "El dinero pasa a OTRA familia: confirma con ambas familias antes de aprobar.";
	}

	private String contactos(Long familiaId) {
		List<String> lista = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familiaId).stream()
				.filter(a -> a.getTelefonoWhatsapp() != null)
				.map(a -> a.nombreCompleto() + ": " + Telefono.formatear(a.getTelefonoWhatsapp())).toList();
		return lista.isEmpty() ? null : String.join(" · ", lista);
	}
}
