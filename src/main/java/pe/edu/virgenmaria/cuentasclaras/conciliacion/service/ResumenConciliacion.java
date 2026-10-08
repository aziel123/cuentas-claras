package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.service.NombresUsuarios;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.config.PropiedadesConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.CuentaVista;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.DetalleExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaDiferencias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaExtractos;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CategoriaExplicacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ExtractoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.MovimientoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ReglaPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.ExtractoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.MovimientoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.PartidaConciliacionRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.ObjetoAbierto;
import pe.edu.virgenmaria.cuentasclaras.caja.model.NumeroOperacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lectura de la conciliación para las pantallas (sprint 4, tanda 3): la de diferencias (la principal), la de extractos
 * y el detalle de un extracto. Promotoría, Dirección y Administración ven; cada acción la exige su servicio.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ResumenConciliacion {

	private final ExtractoBancarioRepository extractos;

	private final MovimientoBancarioRepository movimientos;

	private final PartidaConciliacionRepository partidas;

	private final ObjetosConciliables objetos;

	private final DiferenciasConciliacion diferencias;

	private final ServicioCuentasBancarias cuentas;

	private final NombresUsuarios nombres;

	private final PropiedadesConciliacion propiedades;

	private final Clock reloj;

	private final CalendarioHabil calendario;

	public ResumenConciliacion(ExtractoBancarioRepository extractos, MovimientoBancarioRepository movimientos,
			PartidaConciliacionRepository partidas, ObjetosConciliables objetos, DiferenciasConciliacion diferencias,
			ServicioCuentasBancarias cuentas, NombresUsuarios nombres, PropiedadesConciliacion propiedades, Clock reloj, CalendarioHabil calendario) {
		this.calendario = calendario;
		this.extractos = extractos;
		this.movimientos = movimientos;
		this.partidas = partidas;
		this.objetos = objetos;
		this.diferencias = diferencias;
		this.cuentas = cuentas;
		this.nombres = nombres;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	public VistaDiferencias diferencias() {
		LocalDate hoy = LocalDate.now(reloj);
		Optional<DiferenciasConciliacion.Cobertura> cobertura = diferencias.cobertura();
		List<VistaDiferencias.CuentaPorConfirmar> porConfirmar = cuentas.lista().stream()
				.filter(c -> c.activa() && c.porConfirmar() > 0)
				.map(c -> new VistaDiferencias.CuentaPorConfirmar(c.id(), c.banco() + " · " + c.numero() + " · " + c.alias(),
						c.porConfirmar(), c.cargadoHasta()))
				.toList();
		// S4-A1: quien confirma a ciegas no ve movimientos de extractos por confirmar (ni sus montos ni sus parejas).
		boolean confirma = puedeConfirmar();
		// Sugeridas, con lo emparejado.
		List<PartidaConciliacion> sugeridas = diferencias.sugeridas().stream()
				.filter(p -> !confirma || p.getMovimiento().getExtracto().getEstado() == EstadoExtracto.CONFIRMADO)
				.toList();
		Map<String, ObjetoAbierto> objetosDeSugeridas = sugeridas.isEmpty() ? Map.of()
				: objetosAlrededor(sugeridas.stream().map(p -> p.getMovimiento().getFecha()).toList());
		List<VistaDiferencias.Sugerida> vistasSugeridas = sugeridas.stream().map(p -> {
			MovimientoBancario m = p.getMovimiento();
			ObjetoAbierto o = objetosDeSugeridas.get(p.getObjetoVigente());
			String operacionObjeto = o == null ? null : o.operacion();
			List<String> avisos = new ArrayList<>();
			boolean rojo = false;
			if (m.getNumeroOperacion() != null && operacionObjeto != null && !m.getNumeroOperacion().equals(operacionObjeto)) {
				rojo = NumeroOperacion.parecidos(m.getNumeroOperacion(), operacionObjeto);
				avisos.add(rojo ? ReglasEmparejamiento.NUMERO_PARECIDO : ReglasEmparejamiento.OPERACION_DISTINTA);
			}
			if (p.getDiferencia().signum() != 0) {
				avisos.add("Diferencia de monto: " + p.getDiferencia().toPlainString());
			}
			return new VistaDiferencias.Sugerida(p.getId(), m.getFecha(), m.getMonto(), m.getDescripcion(),
					m.getNumeroOperacion(), o == null ? p.getObjetoTipo().etiqueta() + " " + p.objetoId() : o.detalle(),
					o == null ? null : o.fecha(), p.getMontoObjeto(), operacionObjeto, avisos, rojo,
					m.getExtracto().getEstado() == EstadoExtracto.CONFIRMADO);
		}).toList();
		// Movimientos sin pareja, con lo que se podría emparejar a mano.
		List<MovimientoBancario> sinPareja = diferencias.sinPareja(hoy).stream()
				.filter(m -> !confirma || m.getExtracto().getEstado() == EstadoExtracto.CONFIRMADO).toList();
		List<ObjetoAbierto> abiertos = sinPareja.isEmpty() ? List.of() : objetos.abiertos(
				sinPareja.getFirst().getFecha().minusDays(ServicioPartidas.DIAS_POSIBLES),
				sinPareja.stream().map(MovimientoBancario::getFecha).max(Comparator.naturalOrder()).orElseThrow()
						.plusDays(ServicioPartidas.DIAS_POSIBLES));
		List<VistaDiferencias.SinPareja> vistasSinPareja = sinPareja.stream()
				.filter(m -> m.getTipo() == TipoMovimiento.ABONO)
				.map(m -> new VistaDiferencias.SinPareja(m.getId(), m.getFecha(), m.getTipo().etiqueta(), true, m.getMonto(),
						m.getDescripcion(), m.getNumeroOperacion(), diferencias.abonoCritico(m, hoy),
						m.getExtracto().getEstado() == EstadoExtracto.CONFIRMADO,
						abiertos.stream().filter(o -> o.tipo().movimiento() == m.getTipo()
								&& ReglasEmparejamiento.montoAdmitido(m.getMonto(), o, propiedades.toleranciaMontoLiquidacion())
								&& Math.abs(o.fecha().toEpochDay() - m.getFecha().toEpochDay()) <= ServicioPartidas.DIAS_POSIBLES)
								.sorted(Comparator.comparing((ObjetoAbierto o) -> Math.abs(o.fecha().toEpochDay()
										- m.getFecha().toEpochDay())))
								.limit(15)
								.map(o -> new VistaDiferencias.Opcion(o.tipo().name() + ":" + o.id(), o.detalle() + " · S/ "
										+ o.monto().toPlainString() + " · " + pe.edu.virgenmaria.cuentasclaras.comun.fecha
												.Calendario.formatear(o.fecha())))
								.toList()))
				.toList();
		// S4-A2: los cargos sin pareja ya no se ignoran: los explica alguien que no subió el extracto.
		List<VistaDiferencias.SinPareja> cargos = sinPareja.stream()
				.filter(m -> m.getTipo() == TipoMovimiento.CARGO)
				.map(m -> new VistaDiferencias.SinPareja(m.getId(), m.getFecha(), m.getTipo().etiqueta(), false,
						m.getMonto(), m.getDescripcion(), m.getNumeroOperacion(),
						ReglasEmparejamiento.diasHabilesEntre(calendario, m.getFecha(), hoy) > 2,
						m.getExtracto().getEstado() == EstadoExtracto.CONFIRMADO, List.of()))
				.toList();
		List<VistaDiferencias.Faltante> faltantes = diferencias.faltantes(hoy).stream()
				.map(f -> new VistaDiferencias.Faltante(f.objeto().tipo().etiqueta(), f.objeto().detalle(),
						f.objeto().fecha(), f.objeto().monto(), f.objeto().operacion()))
				.toList();
		return new VistaDiferencias(hoy, cobertura.map(DiferenciasConciliacion.Cobertura::hasta).orElse(null), resumen(),
				porConfirmar, vistasSugeridas, vistasSinPareja, cargos, faltantes, tieneRol("ROLE_ADMINISTRACION"),
				tieneRol("ROLE_PROMOTOR") || tieneRol("ROLE_DIRECTOR"), opciones(TipoMovimiento.ABONO),
				opciones(TipoMovimiento.CARGO));
	}

	public VistaExtractos extractos() {
		List<CuentaVista> lista = cuentas.lista();
		List<VistaExtractos.Fila> filas = extractos.findTop50ByOrderByIdDesc().stream()
				.map(e -> new VistaExtractos.Fila(e.getId(), e.getCuenta().getBanco().etiqueta() + " · " + e.getCuenta()
						.getNumero(), e.getSecuencia(), e.getDesde(), e.getHasta(), e.getMovimientos(), e.getEstado()
								.etiqueta(), e.getEstado().variante(),
						e.getEstado() == EstadoExtracto.CONFIRMADO ? e.getSaldoFinal() : null, nombres.de(e.getCreadoPor()),
						e.getCreadoEn(), e.getConfirmadoPor() == null ? null : nombres.de(e.getConfirmadoPor())))
				.toList();
		return new VistaExtractos(lista, filas, tieneRol("ROLE_ADMINISTRACION"),
				tieneRol("ROLE_PROMOTOR") || tieneRol("ROLE_DIRECTOR"), tieneRol("ROLE_PROMOTOR"));
	}

	public DetalleExtracto extracto(Long id) {
		ExtractoBancario e = extractos.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Extracto no encontrado"));
		boolean confirmado = e.getEstado() == EstadoExtracto.CONFIRMADO;
		boolean ocultos = montosOcultos(e);
		List<MovimientoBancario> lista = movimientos.findByExtractoIdOrderByNumeroAsc(id);
		Map<Long, PartidaConciliacion> parejas = lista.isEmpty() ? Map.of()
				: partidas.findByMovimientoIdInOrderByIdAsc(lista.stream().map(MovimientoBancario::getId).toList()).stream()
						.filter(PartidaConciliacion::vigente)
						.collect(Collectors.toMap(p -> p.getMovimiento().getId(), Function.identity(), (a, b) -> b));
		List<DetalleExtracto.Movimiento> filas = lista.stream().map(m -> {
			PartidaConciliacion p = parejas.get(m.getId());
			String pareja;
			String variante;
			if (ocultos) {
				pareja = "Se ve al confirmar";
				variante = "neutro";
			}
			else if (p == null) {
				pareja = "Sin pareja";
				variante = m.getTipo() == TipoMovimiento.ABONO ? "peligro" : "neutro";
			}
			else if (p.getRegla() == ReglaPartida.EXPLICADA) {
				pareja = "Explicado: " + p.getCategoria().etiqueta();
				variante = "info";
			}
			else {
				pareja = p.getRegla().etiqueta() + (p.getEstado() == EstadoPartida.CONFIRMADA ? " · confirmada"
						: " · por confirmar") + " · " + p.getObjetoTipo().etiqueta();
				variante = p.getEstado() == EstadoPartida.CONFIRMADA ? "exito" : "alerta";
			}
			return new DetalleExtracto.Movimiento(m.getNumero(), m.getFecha(), ocultos ? null : m.getTipo().etiqueta(),
					ocultos ? null : m.getMonto(), confirmado ? m.getSaldo() : null, m.getDescripcion(),
					m.getNumeroOperacion(), pareja, variante);
		}).toList();
		String usuario = usuarioActual();
		return new DetalleExtracto(e.getId(), e.getCuenta().getId(), e.getCuenta().descripcion(), e.getSecuencia(),
				e.getDesde(), e.getHasta(), e.getEstado().etiqueta(), e.getEstado().variante(), confirmado,
				confirmado ? e.getSaldoInicial() : null, confirmado ? e.getTotalAbonos() : null,
				confirmado ? e.getTotalCargos() : null, confirmado ? e.getSaldoFinal() : null, e.getFormato(),
				e.getArchivoSha256(), nombres.de(e.getCreadoPor()), e.getCreadoEn(),
				e.getConfirmadoPor() == null ? null : nombres.de(e.getConfirmadoPor()), e.getConfirmadoEn(),
				e.getMotivoRechazo(), e.getEstado() == EstadoExtracto.CARGADO && e.getCreadoPor().equals(usuario)
						&& !extractos.existsByAnteriorIdAndSecuenciaVigenteIsNotNull(e.getId()),
				ocultos, filas);
	}

	/**
	 * S4-A1: quien puede confirmar extractos (Promotoría o Dirección) no ve los montos ni los tipos de un extracto que no
	 * está CONFIRMADO si él, u otro de la misma cuenta con días superpuestos (por ejemplo, uno descartado o rechazado con
	 * los mismos días), está por confirmar.
	 */
	private boolean montosOcultos(ExtractoBancario e) {
		if (e.getEstado() == EstadoExtracto.CONFIRMADO || !puedeConfirmar()) {
			return false;
		}
		return e.getEstado() == EstadoExtracto.CARGADO
				|| extractos.existsByCuentaIdAndEstadoAndDesdeLessThanEqualAndHastaGreaterThanEqual(e.getCuenta().getId(),
						EstadoExtracto.CARGADO, e.getHasta(), e.getDesde());
	}

	/** Promotoría o Dirección: confirman extractos y recaudaciones a ciegas. */
	private static boolean puedeConfirmar() {
		return tieneRol("ROLE_PROMOTOR") || tieneRol("ROLE_DIRECTOR");
	}

	/** El resumen del último extracto vigente: cuántos movimientos, cuántos con pareja, sugeridos y sin pareja. */
	private VistaDiferencias.Resumen resumen() {
		Optional<ExtractoBancario> ultimo = extractos.findTop50ByOrderByIdDesc().stream()
				.filter(e -> e.getEstado().vigente()).findFirst();
		if (ultimo.isEmpty()) {
			return null;
		}
		ExtractoBancario e = ultimo.get();
		List<MovimientoBancario> lista = movimientos.findByExtractoIdOrderByNumeroAsc(e.getId());
		List<PartidaConciliacion> vigentes = lista.isEmpty() ? List.of()
				: partidas.findByMovimientoIdInOrderByIdAsc(lista.stream().map(MovimientoBancario::getId).toList()).stream()
						.filter(PartidaConciliacion::vigente).toList();
		int emparejados = (int) vigentes.stream().filter(p -> p.getEstado() == EstadoPartida.CONFIRMADA
				|| p.getRegla() == ReglaPartida.EXACTA).count();
		int sugeridos = (int) vigentes.stream().filter(p -> p.getEstado() == EstadoPartida.PROPUESTA
				&& p.getRegla() != ReglaPartida.EXACTA).count();
		return new VistaDiferencias.Resumen(e.getDesde(), e.getHasta(), e.getEstado().etiqueta(), lista.size(), emparejados,
				sugeridos, lista.size() - vigentes.size());
	}

	private Map<String, ObjetoAbierto> objetosAlrededor(List<LocalDate> fechas) {
		LocalDate desde = fechas.stream().min(Comparator.naturalOrder()).orElseThrow().minusDays(Emparejador.MARGEN_DIAS);
		LocalDate hasta = fechas.stream().max(Comparator.naturalOrder()).orElseThrow().plusDays(Emparejador.MARGEN_DIAS);
		return objetos.entre(desde, hasta).stream()
				.collect(Collectors.toMap(ObjetoAbierto::clave, Function.identity(), (a, b) -> a));
	}

	private static List<VistaDiferencias.Opcion> opciones(TipoMovimiento tipo) {
		return ServicioPartidas.categoriasPara(tipo).stream().sorted(Comparator.comparing(CategoriaExplicacion::ordinal))
				.map(c -> new VistaDiferencias.Opcion(c.name(), c.etiqueta())).toList();
	}

	private static boolean tieneRol(String rol) {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion != null && autenticacion.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.anyMatch(rol::equals);
	}

	private static String usuarioActual() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? "" : autenticacion.getName();
	}
}
