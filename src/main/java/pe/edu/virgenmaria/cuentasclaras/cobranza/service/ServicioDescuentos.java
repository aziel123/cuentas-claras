package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DescuentoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DescuentoVista;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.RevisionDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.SolicitudDescuentoVista;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.CalculadoraDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Descuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoVisibleCuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.DescuentoRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Descuentos y becas (decisión 8): Administración los pide para cuotas PENDIENTES o PARCIALES de un alumno y otra
 * persona de Promotoría o Dirección los aprueba en la bandeja ({@link ManejadorDescuento}), que vuelve a calcularlos.
 * Lo que se dejará de cobrar lo calcula el sistema ({@link CalculadoraDescuento}), nunca quien lo pide.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioDescuentos {

	private final DescuentoRepository descuentos;

	private final CuotaRepository cuotas;

	private final AlumnoRepository alumnos;

	private final MatriculaRepository matriculas;

	private final RegistroSolicitudes solicitudes;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ServicioDescuentos(DescuentoRepository descuentos, CuotaRepository cuotas, AlumnoRepository alumnos,
			MatriculaRepository matriculas, RegistroSolicitudes solicitudes, AuditoriaService auditoria, Clock reloj) {
		this.descuentos = descuentos;
		this.cuotas = cuotas;
		this.alumnos = alumnos;
		this.matriculas = matriculas;
		this.solicitudes = solicitudes;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	public List<DescuentoVista> lista() {
		return descuentos.findAllByOrderByIdDesc(PageRequest.of(0, 50)).stream()
				.map(d -> new DescuentoVista(d.getId(), d.getAlumno().getId(), d.getAlumno().nombreCompleto(),
						d.getTipo().etiqueta(), d.valorTexto(), d.cuotaIds().size(), d.getTotalEstimado(),
						d.getEstado().etiqueta(), d.getEstado().variante(), d.getCreadoPor(), d.getCreadoEn(),
						d.getResueltoPor()))
				.toList();
	}

	/** El alumno (por su DNI u otro documento) con sus cuotas por pagar y sus hermanos matriculados. */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public SolicitudDescuentoVista prepararSolicitud(String documento) {
		String numero = Normalizador.sinEspacios(documento);
		if (numero == null) {
			throw new ReglaNegocioException("Escribe el DNI del alumno.");
		}
		String buscado = numero.toUpperCase(java.util.Locale.ROOT);
		Alumno alumno = java.util.Arrays.stream(TipoDocumento.values())
				.map(t -> alumnos.findByDocumentoTipoAndDocumentoNumero(t, buscado)).flatMap(java.util.Optional::stream)
				.findFirst().orElseThrow(() -> new ReglaNegocioException("No hay un alumno con ese documento."));
		LocalDate hoy = LocalDate.now(reloj);
		List<SolicitudDescuentoVista.CuotaElegible> elegibles = cuotas.findByAlumnoIdOrderByFechaVencimientoAscIdAsc(
				alumno.getId()).stream().filter(Cuota::admiteCobro).map(c -> {
					EstadoVisibleCuota estado = c.estadoAl(hoy);
					return new SolicitudDescuentoVista.CuotaElegible(c.getId(), c.getDescripcion(), c.getFechaVencimiento(),
							c.getMonto(), c.getMontoDescuento(), c.getMontoPagado(), c.saldo(), estado.etiqueta(),
							estado.variante());
				}).toList();
		return new SolicitudDescuentoVista(alumno.getId(), alumno.nombreCompleto(), alumno.getDocumento().texto(),
				alumno.getFamilia().getNombre(), hermanosMatriculados(alumno, anioEnCurso(alumno)), elegibles);
	}

	/** El antes y el después de cada cuota, sin guardar nada. */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public RevisionDescuento revisar(DescuentoRequest pedido) {
		return calcular(pedido).revision();
	}

	/** Deja el descuento SOLICITADO y la solicitud en la bandeja de Promotoría y Dirección. */
	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public Long solicitar(DescuentoRequest pedido) {
		Calculo calculo = calcular(pedido);
		Descuento descuento = descuentos.saveAndFlush(Descuento.solicitar(calculo.alumno(), calculo.anio(),
				pedido.tipo(), pedido.modalidad(), pedido.valor(), calculo.cuotaIds(), calculo.revision().total(),
				pedido.motivo(), pedido.sustento()));
		String resumen = descuento.getTipo().etiqueta() + " de " + descuento.valorTexto() + " para "
				+ calculo.alumno().nombreCompleto() + " en " + calculo.cuotaIds().size() + " cuota(s): se dejará de cobrar "
				+ Dinero.formatear(descuento.getTotalEstimado());
		auditoria.registrar(AccionAuditoria.DESCUENTO_SOLICITADO, "descuento", descuento.getId().toString(), null,
				EstadoDescuento.SOLICITADO.name() + " · " + Dinero.formatear(descuento.getTotalEstimado()),
				resumen + ". Cuotas: " + String.join("; ", calculo.revision().lineas().stream()
						.map(l -> l.descripcion() + " " + Dinero.formatear(l.saldoAntes()) + " → "
								+ Dinero.formatear(l.saldoDespues())).toList())
						+ ". Sustento: " + descuento.getSustento() + ". Motivo: " + descuento.getMotivo());
		solicitudes.crear(TipoSolicitud.DESCUENTO, "descuento", descuento.getId(), resumen,
				Map.of("descuentoId", descuento.getId().toString()), descuento.getMotivo());
		return descuento.getId();
	}

	private record Calculo(Alumno alumno, AnioEscolar anio, List<Long> cuotaIds, RevisionDescuento revision) {
	}

	private Calculo calcular(DescuentoRequest pedido) {
		Objects.requireNonNull(pedido, "pedido");
		Alumno alumno = alumnos.findById(pedido.alumnoId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Alumno no encontrado"));
		List<Long> ids = pedido.cuotaIds().stream().filter(Objects::nonNull).distinct().sorted().toList();
		if (ids.isEmpty()) {
			throw new ReglaNegocioException("Elige al menos una cuota.");
		}
		List<RevisionDescuento.Linea> lineas = new ArrayList<>();
		AnioEscolar anio = null;
		BigDecimal total = Dinero.CERO;
		for (Long id : ids) {
			Cuota cuota = cuotas.findById(id).filter(c -> c.getAlumno().getId().equals(alumno.getId()))
					.orElseThrow(() -> new ReglaNegocioException("Alguna cuota elegida no es de " + alumno.nombreCompleto()
							+ "."));
			if (!cuota.admiteCobro()) {
				throw new ReglaNegocioException("La cuota «" + cuota.getDescripcion() + "» no está pendiente (está "
						+ cuota.getEstado().name().toLowerCase(java.util.Locale.ROOT)
						+ " o tiene una anulación por aprobar): solo se descuentan cuotas pendientes o parciales.");
			}
			if (descuentos.existsByEstadoAndCuotasContaining(EstadoDescuento.SOLICITADO, "," + id + ",")) {
				throw new ReglaNegocioException("La cuota «" + cuota.getDescripcion()
						+ "» ya tiene un descuento esperando aprobación.");
			}
			if (anio == null) {
				anio = cuota.getAnioEscolar();
			}
			else if (!anio.getId().equals(cuota.getAnioEscolar().getId())) {
				throw new ReglaNegocioException("Un descuento es de un solo año escolar: elige cuotas del mismo año.");
			}
			BigDecimal ajuste = ajusteDe(cuota, pedido);
			lineas.add(new RevisionDescuento.Linea(cuota.getDescripcion(), cuota.getMonto(), cuota.saldo(), ajuste,
					cuota.saldo().subtract(ajuste), cuota.getMontoPagado().signum() == 0
							&& cuota.getMontoDescuento().add(ajuste).compareTo(cuota.getMonto()) == 0));
			total = total.add(ajuste);
		}
		if (pedido.tipo() == TipoDescuento.HERMANOS && hermanosMatriculados(alumno, anio).size() < 2) {
			throw new ReglaNegocioException("El descuento por hermanos exige dos o más hermanos con matrícula activa en "
					+ anio.getAnio() + ".");
		}
		RevisionDescuento revision = new RevisionDescuento(pedido, alumno.nombreCompleto(), pedido.tipo().etiqueta(),
				pedido.modalidad() == pe.edu.virgenmaria.cuentasclaras.cobranza.model.ModalidadDescuento.PORCENTAJE
						? Dinero.normalizar(pedido.valor()).stripTrailingZeros().toPlainString() + " %"
						: Dinero.formatear(pedido.valor()) + " por cuota", lineas, total);
		return new Calculo(alumno, anio, ids, revision);
	}

	/** El ajuste sobre el monto original; no puede ser mayor que lo que aún se debe de la cuota. */
	static BigDecimal ajusteDe(Cuota cuota, DescuentoRequest pedido) {
		BigDecimal ajuste = CalculadoraDescuento.ajuste(cuota.getMonto(), pedido.modalidad(), pedido.valor());
		if (ajuste.compareTo(cuota.saldo()) > 0) {
			throw new ReglaNegocioException("El descuento de «" + cuota.getDescripcion() + "» (" + Dinero.formatear(ajuste)
					+ ") es mayor que lo que falta pagar (" + Dinero.formatear(cuota.saldo()) + ").");
		}
		return ajuste;
	}

	/** Los hijos de la familia (él incluido) con matrícula ACTIVA en ese año. */
	private List<String> hermanosMatriculados(Alumno alumno, AnioEscolar anio) {
		if (anio == null) {
			return List.of();
		}
		List<Alumno> familia = alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(alumno.getFamilia().getId());
		return matriculas.delAnioParaAlumnos(anio.getId(), familia.stream().map(Alumno::getId).toList()).stream()
				.filter(Matricula::activa).map(m -> m.getAlumno().nombreCompleto()).distinct().toList();
	}

	private AnioEscolar anioEnCurso(Alumno alumno) {
		return matriculas.findByAlumnoIdOrderByAnioEscolarAnioDesc(alumno.getId()).stream().filter(Matricula::activa)
				.findFirst().map(Matricula::getAnioEscolar).orElse(null);
	}
}
