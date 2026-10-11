package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.PropuestaConectorRequest;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.AdaptadorConector.ResultadoPrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 8, tanda 2 (sección 3.6, E12 y E13): proponer un conector y probar su conexión. Cambiar un conector es cambiar
 * adónde entra el dinero del colegio, así que nunca lo hace una sola persona:
 * <ol>
 *   <li>Lo PROPONE una persona de Administración o Promotoría, con la firma de su sesión (trg_conector_colegio_nace). Se
 *       cifra el secreto (AES-256-GCM, la clave fuera de la base) y nace su solicitud {@code CAMBIO_CONECTOR}.</li>
 *   <li>Se PRUEBA la conexión con el adaptador del módulo que lo usa ({@link AdaptadorConector}).</li>
 *   <li>Lo APRUEBA otra persona de Promotoría o Dirección en la bandeja, con su firma ({@link ManejadorCambioConector}).</li>
 * </ol>
 * Un tipo tiene a lo más un cambio propuesto a la vez. Ningún secreto vuelve a la pantalla ni va a la bitácora: solo su
 * pista (los 4 últimos caracteres).
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','ADMINISTRACION')")
public class PropuestaConector {

	static final String ENTIDAD = "conector_colegio";

	private final ConectorColegioRepository conectores;

	private final SecretosConectores secretos;

	private final ConectoresColegio vigentes;

	private final RegistroSolicitudes solicitudes;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final ColegioRepository colegios;

	private final List<AdaptadorConector> adaptadores;

	private final Clock reloj;

	private final TransactionTemplate transaccion;

	public PropuestaConector(ConectorColegioRepository conectores, SecretosConectores secretos, ConectoresColegio vigentes,
			RegistroSolicitudes solicitudes, FirmaSesion firma, AuditoriaService auditoria, ColegioRepository colegios,
			List<AdaptadorConector> adaptadores, Clock reloj, PlatformTransactionManager transacciones) {
		this.conectores = conectores;
		this.secretos = secretos;
		this.vigentes = vigentes;
		this.solicitudes = solicitudes;
		this.firma = firma;
		this.auditoria = auditoria;
		this.colegios = colegios;
		this.adaptadores = List.copyOf(adaptadores);
		this.reloj = reloj;
		this.transaccion = new TransactionTemplate(transacciones);
	}

	/** Propone un conector nuevo del tipo: queda PROPUESTO, con su solicitud para que otra persona la apruebe. */
	@Transactional
	public Long proponer(TipoConector tipo, PropuestaConectorRequest pedido) {
		Long colegioId = ContextoColegio.actual();
		Colegio colegio = colegios.findById(colegioId).filter(c -> !c.esPlataforma())
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
		if (conectores.existsByTipoAndEstado(tipo, EstadoConector.PROPUESTO)) {
			throw new ReglaNegocioException("Ya hay un cambio de «" + tipo.etiqueta().toLowerCase() + "» esperando "
					+ "aprobación: que otra persona lo apruebe o lo rechace antes de proponer otro.");
		}
		ConfiguracionConector configuracion = ValidacionConector.configuracion(tipo, pedido, colegio.getRuc());
		Map<String, String> claros = ValidacionConector.secretos(tipo, pedido);
		String clavePublica = UUID.randomUUID().toString();
		byte[] cifrado = claros.isEmpty() ? null : secretos.cifrar(claros, colegioId, tipo.name(), clavePublica);
		String pista = claros.isEmpty() ? null : SecretosConectores.pista(ValidacionConector.principal(claros));
		UsuarioAutenticado quien = actor();
		// La firma de quien propone, antes de la fila (trg_conector_colegio_nace la exige a su nombre).
		firma.firmar(ClaveFirma.conectorPropuesto(clavePublica));
		ConectorColegio conector = conectores.saveAndFlush(ConectorColegio.propuesto(tipo, pedido.proveedor(),
				pedido.modo(), configuracion.aJson(), clavePublica, cifrado, pista,
				cifrado == null ? null : secretos.version(), quien.getUsername()));
		Optional<ConectorColegio> actual = conectores.findFirstByTipoAndEstado(tipo, EstadoConector.VIGENTE);
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("tipo", tipo.name());
		datos.put("proveedor", pedido.proveedor().name());
		datos.put("modo", pedido.modo().name());
		if (pista != null) {
			datos.put("pista", pista);
		}
		actual.ifPresent(a -> datos.put("reemplaza", a.getId().toString()));
		SolicitudCambio solicitud = solicitudes.crear(TipoSolicitud.CAMBIO_CONECTOR, ENTIDAD, conector.getId(),
				resumen(tipo, conector, configuracion), datos, pedido.motivo());
		conector.enlazarSolicitud(solicitud.getId());
		conectores.saveAndFlush(conector);
		auditoria.registrar(AccionAuditoria.CONECTOR_PROPUESTO, ENTIDAD, conector.getId().toString(),
				actual.map(a -> descripcion(a)).orElse("sin conector"), descripcion(conector),
				quien.nombreCompleto() + " propuso cambiar " + tipo.descripcion() + ": " + configuracion.resumen()
						+ (pista == null ? "" : " (secreto que termina en " + pista + ")") + ". Motivo: " + pedido.motivo()
						+ ". Debe aprobarlo otra persona de Promotoría o Dirección" + (tipo.mueveDinero()
								? ", con una Promotoría de por medio." : "."));
		return conector.getId();
	}

	/**
	 * «Probar conexión» de un conector PROPUESTO, con el adaptador del módulo que lo usa. La llamada al proveedor va fuera
	 * de la transacción (no deja una fila bloqueada mientras espera); el resultado se guarda y se audita.
	 */
	public ResultadoPrueba probar(Long id) {
		ConectorVigente candidato = transaccion.execute(t -> {
			ConectorColegio conector = conectores.findById(id)
					.orElseThrow(() -> new RecursoNoEncontradoException("Conector no encontrado"));
			if (conector.getEstado() != EstadoConector.PROPUESTO) {
				throw new ReglaNegocioException("Solo se prueba un conector propuesto (por aprobar).");
			}
			return vigentes.descifradoParaPrueba(conector);
		});
		ResultadoPrueba resultado;
		Optional<AdaptadorConector> adaptador = adaptadores.stream()
				.filter(a -> a.tipo() == candidato.tipo() && a.admite(candidato.proveedor())).findFirst();
		if (adaptador.isEmpty()) {
			resultado = ResultadoPrueba.fallida(candidato.proveedor().etiqueta() + " todavía no tiene su adaptador en el "
					+ "sistema: no se puede probar ni poner en vigencia (se construye con el contrato del colegio).");
		}
		else if (candidato.tipo().conSecreto() && candidato.secretos().isEmpty()
				&& candidato.proveedor() != pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector.SIMULADO) {
			resultado = ResultadoPrueba.fallida("El secreto guardado no se puede leer con la clave de esta instalación: "
					+ "propón el conector de nuevo.");
		}
		else {
			try {
				resultado = adaptador.get().probar(candidato);
			}
			catch (RuntimeException e) {
				resultado = ResultadoPrueba.fallida("La prueba no se pudo completar (" + e.getClass().getSimpleName() + ")");
			}
		}
		ResultadoPrueba guardado = resultado;
		transaccion.executeWithoutResult(t -> {
			ConectorColegio conector = conectores.bloquear(id)
					.orElseThrow(() -> new RecursoNoEncontradoException("Conector no encontrado"));
			conector.registrarPrueba(guardado.ok(), guardado.detalle(), LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
			conectores.saveAndFlush(conector);
			auditoria.registrar(AccionAuditoria.CONECTOR_PROBADO, ENTIDAD, id.toString(), null,
					guardado.ok() ? "CORRECTA" : "FALLIDA", "Probó la conexión de " + conector.getTipo().etiqueta()
							+ " (" + conector.getProveedor().etiqueta() + "): " + conector.getPruebaDetalle());
		});
		return resultado;
	}

	static String resumen(TipoConector tipo, ConectorColegio conector, ConfiguracionConector configuracion) {
		return "Cambiar " + tipo.etiqueta().toLowerCase() + " a " + conector.getProveedor().etiqueta() + " ("
				+ conector.getModo().etiqueta().toLowerCase() + "): " + configuracion.resumen()
				+ (conector.getSecretoPista() == null ? "" : "; secreto que termina en " + conector.getSecretoPista());
	}

	static String descripcion(ConectorColegio conector) {
		return conector.getTipo().name() + " " + conector.getProveedor().name() + " " + conector.getModo().name()
				+ (conector.getSecretoPista() == null ? "" : " …" + conector.getSecretoPista());
	}

	private static UsuarioAutenticado actor() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere una persona en sesión");
	}
}
