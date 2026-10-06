package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

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
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLinea;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LineaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LineaRecaudacionRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aprobación de {@code APLICAR_INGRESO} de una línea de recaudación por revisar: vuelve a validar con los datos actuales
 * (la línea sigue por revisar, las cuotas destino siguen por pagar y suman al menos el pago) y publica
 * {@link IngresoRecaudacionAprobado}: el pago lo registra {@code sistema.recaudacion} después del commit (el trigger del
 * pago exige esta solicitud APROBADA). Si el dinero va a una familia distinta de la del código (o el código no era de
 * nadie), exige hablar antes con las familias (A2) y va primero en la bandeja.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorAplicarIngresoRecaudacion implements ManejadorSolicitud {

	private final LineaRecaudacionRepository lineas;

	private final CuotaRepository cuotas;

	private final FamiliaRepository familias;

	private final ApoderadoRepository apoderados;

	private final ApplicationEventPublisher eventos;

	public ManejadorAplicarIngresoRecaudacion(LineaRecaudacionRepository lineas, CuotaRepository cuotas,
			FamiliaRepository familias, ApoderadoRepository apoderados, ApplicationEventPublisher eventos) {
		this.lineas = lineas;
		this.cuotas = cuotas;
		this.familias = familias;
		this.apoderados = apoderados;
		this.eventos = eventos;
	}

	@Override
	public String entidad() {
		return ServicioExcepcionesRecaudacion.ENTIDAD;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.APLICAR_INGRESO;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		LineaRecaudacion linea = lineas.bloquear(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("El pago por banco de la solicitud no existe."));
		if (linea.getEstado() != EstadoLinea.EXCEPCION) {
			throw new ReglaNegocioException("El pago por banco ya no está por revisar: recházala.");
		}
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		Long destino = Long.valueOf(datos.get(ServicioExcepcionesRecaudacion.DATO_FAMILIA));
		List<Cuota> elegidas = cuotas.bloquear(ServicioExcepcionesRecaudacion.cuotasDe(datos));
		BigDecimal debe = Dinero.CERO;
		for (Cuota cuota : elegidas) {
			if (!Objects.equals(cuota.getAlumno().getFamilia().getId(), destino) || !cuota.admiteCobro()
					|| cuota.anulacionPendiente()) {
				throw new ReglaNegocioException("La cuota «" + cuota.getDescripcion() + "» ya no está por pagar: recházala y "
						+ "pide otra aplicación.");
			}
			debe = debe.add(cuota.saldo());
		}
		if (elegidas.isEmpty() || debe.compareTo(linea.getMonto()) < 0) {
			throw new ReglaNegocioException("Las cuotas ya no suman el pago (" + Dinero.formatear(linea.getMonto())
					+ "): recházala y pide otra aplicación o la devolución.");
		}
		eventos.publishEvent(new IngresoRecaudacionAprobado(linea.getId(), solicitud.getId(), linea.getColegioId()));
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		LineaRecaudacion linea = lineas.findById(solicitud.getEntidadId()).orElse(null);
		if (linea == null) {
			return List.of();
		}
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		List<String> texto = new ArrayList<>();
		texto.add("Pago por banco: " + Dinero.formatear(linea.getMonto()) + " del " + linea.getFechaPago() + " con el código "
				+ CodigoPago.legible(linea.getCodigo()) + (linea.getAlumno() == null ? " (no corresponde a ningún alumno)"
						: " (" + linea.getAlumno().nombreCompleto() + ")") + ", operación " + linea.getNumeroOperacion());
		if (linea.getMotivoExcepcion() != null) {
			texto.add("Por qué quedó por revisar: " + linea.getMotivoExcepcion().descripcion());
		}
		texto.add("Se aplicaría a: " + String.join("; ", ServicioExcepcionesRecaudacion.cuotasDe(datos).stream()
				.map(id -> cuotas.findById(id).map(c -> c.getDescripcion() + " de " + c.getAlumno().nombreCompleto()
						+ " (debe " + Dinero.formatear(c.saldo()) + ")").orElse("cuota " + id)).toList()));
		familiasPorLlamar(solicitud).forEach((id, nombre) -> {
			String contactos = contactos(id);
			if (contactos != null) {
				texto.add("Antes de aprobar, llama a " + nombre + ": " + contactos);
			}
		});
		return texto;
	}

	/** La familia del código (si la hay) y la de destino, cuando el dinero cambia de familia. */
	private Map<Long, String> familiasPorLlamar(SolicitudCambio solicitud) {
		Map<Long, String> porLlamar = new LinkedHashMap<>();
		LineaRecaudacion linea = lineas.findById(solicitud.getEntidadId()).orElse(null);
		if (linea == null) {
			return porLlamar;
		}
		Long destino = Long.valueOf(DatosSolicitud.leer(solicitud.getDatos())
				.get(ServicioExcepcionesRecaudacion.DATO_FAMILIA));
		Familia origen = linea.getAlumno() == null ? null : linea.getAlumno().getFamilia();
		if (origen == null || !destino.equals(origen.getId())) {
			if (origen != null) {
				porLlamar.put(origen.getId(), origen.getNombre());
			}
			porLlamar.put(destino, familias.findById(destino).map(Familia::getNombre).orElse("la familia de destino"));
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
		return "Habló con: " + String.join(" y ", partes) + ".";
	}

	/** Una aplicación que cambia el dinero de familia va primero; las demás, junto a las anulaciones. */
	@Override
	public int prioridad(SolicitudCambio solicitud) {
		return familiasPorLlamar(solicitud).isEmpty() ? 1 : 0;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return familiasPorLlamar(solicitud).isEmpty() ? null
				: "El pago por banco se aplicaría a una familia distinta de la del código: confirma con las familias antes "
						+ "de aprobar.";
	}

	private String contactos(Long familiaId) {
		List<String> lista = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familiaId).stream()
				.filter(a -> a.getTelefonoWhatsapp() != null)
				.map(a -> a.nombreCompleto() + ": " + Telefono.formatear(a.getTelefonoWhatsapp())).toList();
		return lista.isEmpty() ? null : String.join(" · ", lista);
	}
}
