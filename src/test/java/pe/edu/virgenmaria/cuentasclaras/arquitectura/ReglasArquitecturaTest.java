package pe.edu.virgenmaria.cuentasclaras.arquitectura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.CuentasClarasApplication;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EslabonCadena;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import jakarta.persistence.MappedSuperclass;
import org.springframework.security.access.prepost.PreAuthorize;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EslabonCadenaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Reglas de arquitectura que protegen las garantías del sprint 1 (multi-colegio y auditoría).
 * Analiza solo el código de producción.
 * <p>
 * Cada regla nueva del diseño o de la skill {@code crear-modulo-spring} debería tener aquí su guardián.
 */
@AnalyzeClasses(packagesOf = CuentasClarasApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ReglasArquitecturaTest {

	private static final String BASE = "pe.edu.virgenmaria.cuentasclaras";

	/** Únicas clases que pueden ver todos los colegios (se crean en la tanda 2). */
	private static final Set<String> AUTORIZADAS_COMO_SISTEMA = Set.of(
			BASE + ".seguridad.service.ServicioDetallesUsuario",
			BASE + ".seguridad.inicial.InicializadorPromotor",
			BASE + ".seguridad.inicial.DatosDemoDev");

	/** Única clase que puede usar JDBC directo: comprueba los permisos de MySQL (paso 9). */
	private static final String VERIFICADOR_PERMISOS = BASE + ".auditoria.service.VerificadorPermisosBaseDatos";

	/** Hibernate no filtra por colegio las consultas nativas: están prohibidas. */
	@ArchTest
	static void repositoriosNoUsanConsultasNativas(JavaClasses clases) {
		noMethods()
				.should().beAnnotatedWith(consultaNativa())
				.orShould().beAnnotatedWith(NativeQuery.class)
				.because("las consultas nativas se saltan el filtro por colegio de @TenantId")
				.check(clases);
		noClasses()
				.should().beAnnotatedWith(jakarta.persistence.NamedNativeQuery.class)
				.orShould().beAnnotatedWith(jakarta.persistence.NamedNativeQueries.class)
				.orShould().beAnnotatedWith(org.hibernate.annotations.NamedNativeQuery.class)
				.orShould().callMethodWhere(llamadaA("createNative", "SQL nativo de EntityManager o Session"))
				.because("las consultas nativas se saltan el filtro por colegio de @TenantId")
				.check(clases);
	}

	@ArchTest
	static final ArchRule soloVerificadorPermisosUsaJdbcTemplate = noClasses()
			.that().doNotHaveFullyQualifiedName(VERIFICADOR_PERMISOS)
			.should().dependOnClassesThat().belongToAnyOf(JdbcTemplate.class, JdbcOperations.class,
					NamedParameterJdbcTemplate.class, NamedParameterJdbcOperations.class, JdbcClient.class)
			.because("JDBC directo no pasa por el filtro por colegio ni por la auditoría");

	@ArchTest
	static final ArchRule entidadesDeNegocioExtiendenBaseEntity = classes()
			.that().areAnnotatedWith(Entity.class)
			.and().doNotBelongToAnyOf(Colegio.class, EventoAuditoria.class, EslabonCadena.class,
					pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionBd.class)
			.should().beAssignableTo(BaseEntity.class)
			.because("BaseEntity aporta colegioId (@TenantId), autoría y versión");

	@ArchTest
	static void repositorioDeAuditoriaNoExtiendeCrudRepositoryNiTieneDeleteOUpdate(JavaClasses clases) {
		classes()
				.that().resideInAPackage("..auditoria.repository..")
				.should().notBeAssignableTo(CrudRepository.class)
				.because("la bitácora es de solo inserción")
				.check(clases);
		noMethods()
				.that().areDeclaredInClassesThat().resideInAPackage("..auditoria.repository..")
				.should().haveNameMatching("(?i)(delete|remove|update|saveAll).*")
				.orShould().beAnnotatedWith(Modifying.class)
				.because("la bitácora es de solo inserción")
				.check(clases);
	}

	@ArchTest
	static void nadieLlamaMetodosDeBorradoDeRepositorios(JavaClasses clases) {
		noClasses()
				.should().callMethodWhere(borradoDeRepositorio())
				.orShould().callMethod(EntityManager.class, "remove", Object.class)
				.because("nada se borra físicamente: los usuarios se desactivan y lo financiero se anula")
				.check(clases);
		noMethods()
				.should().beAnnotatedWith(consultaQueEmpiezaCon("delete"))
				.because("nada se borra físicamente: los usuarios se desactivan y lo financiero se anula")
				.check(clases);
	}

	@ArchTest
	static final ArchRule comoSistemaSoloSeUsaEnLasClasesAutorizadas = noClasses()
			.that(noEsAutorizadaComoSistema())
			.should().callMethod(ContextoColegio.class, "comoSistema", Supplier.class)
			.because("comoSistema ve los datos de todos los colegios");

	@ArchTest
	static final ArchRule controladoresNoDependenDeRepositorios = noClasses()
			.that().areMetaAnnotatedWith(Controller.class)
			.or().areMetaAnnotatedWith(org.springframework.web.bind.annotation.ControllerAdvice.class)
			.should().dependOnClassesThat().areAssignableTo(Repository.class)
			.because("los controladores no tienen lógica: delegan en servicios");

	/** Las fechas salen del {@code Clock} de la aplicación (hora de Lima y ajustable en pruebas). */
	@ArchTest
	static final ArchRule nadieUsaLaHoraDelSistemaSinReloj = noClasses()
			.should().callMethod(LocalDateTime.class, "now")
			.orShould().callMethod(LocalDate.class, "now")
			.orShould().callMethod(Instant.class, "now")
			.orShould().callMethod(ZonedDateTime.class, "now")
			.orShould().callMethod(Clock.class, "systemDefaultZone")
			.because("la hora se toma del Clock de ConfiguracionTiempo (America/Lima), así las pruebas la controlan");

	/** Los controladores trabajan con DTOs: una entidad JPA nunca llega a la vista ni a la API. */
	@ArchTest
	static final ArchRule controladoresNoExponenEntidades = noClasses()
			.that().areMetaAnnotatedWith(Controller.class)
			.or().areMetaAnnotatedWith(org.springframework.web.bind.annotation.ControllerAdvice.class)
			.should().dependOnClassesThat().areAnnotatedWith(Entity.class)
			.because("las entidades JPA no se exponen: se usan DTOs");

	/** El dinero va en BigDecimal: ninguna entidad tiene campos double o float. */
	@ArchTest
	static final ArchRule entidadesSinDoubleNiFloat = noFields()
			.that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.or().areDeclaredInClassesThat().areAnnotatedWith(MappedSuperclass.class)
			.should().haveRawType(double.class)
			.orShould().haveRawType(Double.class)
			.orShould().haveRawType(float.class)
			.orShould().haveRawType(Float.class)
			.because("el dinero se guarda en BigDecimal con escala 2");

	/** Solo el paquete de auditoría escribe en la bitácora y su cadena: todos los demás usan AuditoriaService. */
	@ArchTest
	static final ArchRule soloAuditoriaServiceEscribeEnLaBitacora = noClasses()
			.that().resideOutsideOfPackage("..auditoria.service..")
			.and().resideOutsideOfPackage("..auditoria.repository..")
			.should().dependOnClassesThat().belongToAnyOf(EventoAuditoriaRepository.class, EslabonCadenaRepository.class)
			.because("cada evento debe pasar por el sellado HMAC y la secuencia de AuditoriaService");

	/**
	 * Los servicios sensibles exigen rol también por método, no solo por URL, y con la expresión EXACTA:
	 * ni {@code permitAll} ni roles de más.
	 */
	private static final String LECTURA_ESCOLAR = "hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')";

	private static final String ESCRITURA_ESCOLAR = "hasAnyRole('DIRECTOR','ADMINISTRACION')";

	private static final String SOLO_ADMINISTRACION = "hasRole('ADMINISTRACION')";

	private static final String APROBACION = "hasAnyRole('PROMOTOR','DIRECTOR')";

	private static final Map<String, String> EXPRESIONES_EXIGIDAS = Map.ofEntries(
			Map.entry(BASE + ".seguridad.service.ServicioUsuarios", "hasAnyRole('PROMOTOR','DIRECTOR')"),
			Map.entry(BASE + ".auditoria.service.ConsultaAuditoriaService", "hasAnyRole('PROMOTOR','DIRECTOR')"),
			Map.entry(BASE + ".auditoria.service.ConsultaAuditoriaService#paraRevisar", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".auditoria.service.VerificadorIntegridadAuditoria#verificar", "hasRole('PROMOTOR')"),
			// Sprint 2: Promotoría consulta; Dirección y Administración registran y corrigen.
			Map.entry(BASE + ".colegio.service.ServicioEstructura", LECTURA_ESCOLAR),
			Map.entry(BASE + ".colegio.service.ServicioEstructura#crearAnio", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".colegio.service.ServicioEstructura#crearSeccion", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".colegio.service.ServicioEstructura#desactivarSeccion", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioAlumnos", LECTURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioAlumnos#prepararRegistro", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioAlumnos#registrar", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioAlumnos#datosParaEditar", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioAlumnos#actualizar", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioAlumnos#cambiarResponsablePago", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioAlumnos#retirar", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioFamilias", LECTURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioFamilias#obtenerApoderado", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioFamilias#agregarApoderado", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioFamilias#actualizarApoderado", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioFamilias#desactivarApoderado", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioFamilias#renombrar", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioFamilias#solicitarDatosFacturacion", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.service.ServicioMatriculas", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.importacion.ServicioImportacionAlumnos", ESCRITURA_ESCOLAR),
			// Sprint 2, tanda 3: Administración propone y arma; Promotoría o Dirección aprueban y confirman.
			Map.entry(BASE + ".cobranza.service.ServicioPlanesPension", LECTURA_ESCOLAR),
			Map.entry(BASE + ".cobranza.service.ServicioPlanesPension#propuestaPorDefecto", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioPlanesPension#datosParaEditar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioPlanesPension#crearBorrador", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioPlanesPension#editarBorrador", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioPlanesPension#nuevaVersion", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioPlanesPension#descartar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioPlanesPension#aprobar", APROBACION),
			Map.entry(BASE + ".cobranza.service.ServicioPlanesPension#enviar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioPlanesPension#devolver", APROBACION),
			Map.entry(BASE + ".cobranza.service.AlertasCobranza", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".cobranza.service.ServicioCronograma", LECTURA_ESCOLAR),
			Map.entry(BASE + ".cobranza.service.GeneradorCronograma#generarPendientes", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".cobranza.service.ServicioSaldoInicial", LECTURA_ESCOLAR),
			Map.entry(BASE + ".cobranza.service.ServicioSaldoInicial#crearLote", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioSaldoInicial#agregarLinea", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioSaldoInicial#quitarLinea", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioSaldoInicial#enviar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioSaldoInicial#descartar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioSaldoInicial#confirmar", APROBACION),
			Map.entry(BASE + ".cobranza.service.ServicioSaldoInicial#devolver", APROBACION),
			Map.entry(BASE + ".cobranza.service.ServicioAnulacionCuotas", LECTURA_ESCOLAR),
			// Correcciones del sprint 2: la bandeja de solicitudes la resuelven Promotoría o Dirección.
			Map.entry(BASE + ".aprobaciones.service.BandejaAprobaciones", APROBACION),
			// Sprint 3 (caja): solo Caja cobra. Promotoría, Dirección y Administración no cobran.
			Map.entry(BASE + ".caja.service.ServicioCobro", "hasRole('CAJA')"),
			// Sprint 3, tanda 2: la cajera (sus pagos) o Administración PIDEN anular; descuentos los pide Administración.
			Map.entry(BASE + ".caja.service.ServicioAnulacionPagos", "hasAnyRole('CAJA','ADMINISTRACION')"),
			Map.entry(BASE + ".caja.service.ServicioEstadoCuenta", LECTURA_ESCOLAR),
			Map.entry(BASE + ".cobranza.service.ServicioDescuentos", LECTURA_ESCOLAR),
			Map.entry(BASE + ".cobranza.service.ServicioDescuentos#prepararSolicitud", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioDescuentos#revisar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".cobranza.service.ServicioDescuentos#solicitar", SOLO_ADMINISTRACION),
			// Sprint 3, tanda 3: la cajera cierra; Promotoría y Dirección miran las cajas; Administración verifica.
			Map.entry(BASE + ".caja.service.ServicioCierreCaja", "hasRole('CAJA')"),
			Map.entry(BASE + ".caja.service.ConsultaCajas", "hasAnyRole('PROMOTOR','DIRECTOR')"),
			Map.entry(BASE + ".caja.service.AlertasCaja", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".caja.service.IndicadoresCaja", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".caja.service.ServicioVerificacionBancaria", LECTURA_ESCOLAR),
			Map.entry(BASE + ".caja.service.ServicioVerificacionBancaria#verificarPago", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".caja.service.ServicioVerificacionBancaria#verificarDeposito", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".caja.service.ServicioVerificacionBancaria#registrarReembolso", SOLO_ADMINISTRACION),
			// Sprint 4, tanda 1: el apoderado paga lo suyo; el sistema (y solo él) registra el pago en línea;
			// Administración pide aplicar o devolver un ingreso por revisar y reemite comprobantes rechazados.
			Map.entry(BASE + ".pasarela.service.ServicioPagoEnLinea", "hasRole('APODERADO')"),
			Map.entry(BASE + ".pasarela.simulada.SimuladorPagos", "hasRole('APODERADO')"),
			Map.entry(BASE + ".caja.service.ComprobantesDeFamilia", "hasRole('APODERADO')"),
			Map.entry(BASE + ".caja.service.RegistroPagosAutomaticos", "hasAnyRole('SISTEMA_PASARELA','SISTEMA_RECAUDACION')"),
			Map.entry(BASE + ".caja.service.ServicioAnulacionPagos#solicitarPorContracargo", "hasRole('SISTEMA_PASARELA')"),
			Map.entry(BASE + ".pasarela.service.ConsultaPagosEnLinea", LECTURA_ESCOLAR),
			Map.entry(BASE + ".pasarela.service.ServicioIngresosPorRevisar", LECTURA_ESCOLAR),
			Map.entry(BASE + ".pasarela.service.ServicioIngresosPorRevisar#solicitarAplicacion", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".pasarela.service.ServicioIngresosPorRevisar#solicitarDevolucion", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".pasarela.service.DevolucionesPasarela", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".pasarela.service.AlertasPagosEnLinea", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".comprobantes.service.ConsultaComprobantes", LECTURA_ESCOLAR),
			Map.entry(BASE + ".comprobantes.service.ConsultaComprobantes#adelantarReintento", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".comprobantes.service.AlertasComprobantes", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".caja.service.ServicioReemision", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".alumnos.service.ServicioAccesoApoderados", "hasAnyRole('PROMOTOR','ADMINISTRACION')"),
			Map.entry(BASE + ".alumnos.service.ServicioAccesoApoderados#cuentaDe", LECTURA_ESCOLAR),
			// Sprint 4, tanda 2: Administración sube y registra el archivo del banco; Promotoría o Dirección lo confirman a
			// ciegas (nunca quien lo subió); el pago lo registra solo el sistema.
			Map.entry(BASE + ".recaudacion.service.ServicioRecaudacion", LECTURA_ESCOLAR),
			Map.entry(BASE + ".recaudacion.service.ServicioRecaudacion#previsualizar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".recaudacion.service.ServicioRecaudacion#registrar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".recaudacion.service.ServicioRecaudacion#descartar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".recaudacion.service.ServicioRecaudacion#exportarBaseDeudas", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".recaudacion.service.ServicioRecaudacion#paraConfirmar", APROBACION),
			Map.entry(BASE + ".recaudacion.service.ServicioRecaudacion#confirmar", APROBACION),
			Map.entry(BASE + ".recaudacion.service.ConsultaRecaudacion", LECTURA_ESCOLAR),
			Map.entry(BASE + ".recaudacion.service.ServicioExcepcionesRecaudacion", LECTURA_ESCOLAR),
			Map.entry(BASE + ".recaudacion.service.ServicioExcepcionesRecaudacion#solicitarAplicacion",
					SOLO_ADMINISTRACION),
			Map.entry(BASE + ".recaudacion.service.ServicioExcepcionesRecaudacion#solicitarDevolucion",
					SOLO_ADMINISTRACION),
			Map.entry(BASE + ".recaudacion.service.ServicioExcepcionesRecaudacion#registrarDevolucion",
					SOLO_ADMINISTRACION),
			Map.entry(BASE + ".recaudacion.service.AlertasRecaudacion", "hasRole('PROMOTOR')"),
			// Sprint 4, tanda 3: Administración sube el extracto y revisa las diferencias; Promotoría o Dirección lo confirman
			// a ciegas (nunca quien lo subió); solo Promotoría registra las cuentas; lo automático, solo el sistema.
			Map.entry(BASE + ".conciliacion.service.ServicioExtractos", LECTURA_ESCOLAR),
			Map.entry(BASE + ".conciliacion.service.ServicioExtractos#previsualizar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".conciliacion.service.ServicioExtractos#registrar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".conciliacion.service.ServicioExtractos#descartar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".conciliacion.service.ServicioExtractos#paraConfirmar", APROBACION),
			Map.entry(BASE + ".conciliacion.service.ServicioExtractos#confirmar", APROBACION),
			Map.entry(BASE + ".conciliacion.service.ServicioPartidas", LECTURA_ESCOLAR),
			Map.entry(BASE + ".conciliacion.service.ServicioPartidas#confirmarSugerida", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".conciliacion.service.ServicioPartidas#descartar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".conciliacion.service.ServicioPartidas#emparejarManual", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".conciliacion.service.ServicioPartidas#explicar",
					"hasAnyRole('ADMINISTRACION','PROMOTOR','DIRECTOR')"),
			Map.entry(BASE + ".conciliacion.service.ServicioCuentasBancarias", LECTURA_ESCOLAR),
			Map.entry(BASE + ".conciliacion.service.ServicioCuentasBancarias#registrar", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".conciliacion.service.ServicioCuentasBancarias#desactivar", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".conciliacion.service.ResumenConciliacion", LECTURA_ESCOLAR),
			Map.entry(BASE + ".conciliacion.service.AlertasConciliacion", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".conciliacion.service.IndicadoresConciliacion", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".caja.service.RegistroVerificacionAutomatica", "hasRole('SISTEMA_CONCILIACION')"),
			Map.entry(BASE + ".pasarela.service.RegistroLiquidaciones", "hasRole('SISTEMA_PASARELA')"),
			// Correcciones del sprint 4: el contracargo lo registra solo el sistema; la devolución en línea la pide
			// Administración; el acceso del apoderado lo restablece solo Promotoría, que también ve sus alertas.
			Map.entry(BASE + ".pasarela.service.ContracargosPasarela", "hasRole('SISTEMA_PASARELA')"),
			Map.entry(BASE + ".caja.service.ServicioVerificacionBancaria#reembolsarEnLinea", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".alumnos.service.ServicioAccesoApoderados#restablecerAcceso", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".alumnos.service.ServicioAccesoApoderados#conCuentaActiva", LECTURA_ESCOLAR),
			Map.entry(BASE + ".seguridad.service.AlertasActivacion", "hasRole('PROMOTOR')"),
			// Sprint 5, tanda 1: el enlace lo genera solo sistema.mensajeria; la huella, solo sistema.auditoria; la
			// bandeja de envíos la ven Promotoría, Dirección y Administración (solo esta adelanta un reintento); el
			// historial, el apoderado de SU familia.
			Map.entry(BASE + ".seguridad.service.EnlacesActivacion#generarParaMensaje", "hasRole('SISTEMA_MENSAJERIA')"),
			Map.entry(BASE + ".auditoria.service.HuellasDiarias", "hasRole('SISTEMA_AUDITORIA')"),
			Map.entry(BASE + ".auditoria.service.VerificadorIntegridadAuditoria#verificarComoSistema",
					"hasRole('SISTEMA_AUDITORIA')"),
			Map.entry(BASE + ".auditoria.service.AlertasHuella", "hasRole('PROMOTOR')"),
			Map.entry(BASE + ".comunicacion.service.ConsultaMensajes", LECTURA_ESCOLAR),
			Map.entry(BASE + ".comunicacion.service.ConsultaMensajes#reintentar", SOLO_ADMINISTRACION),
			Map.entry(BASE + ".comunicacion.service.ConsultaMensajes#historialDeMiFamilia", "hasRole('APODERADO')"),
			Map.entry(BASE + ".comunicacion.service.AlertasComunicacion", "hasRole('PROMOTOR')"));

	/**
	 * S4-M2 y sprint 5: EnlacesActivacion lo usan solo los servicios protegidos que dan o restablecen el acceso (para
	 * anular los enlaces anteriores) y el proceso de envío (que genera el enlace nuevo).
	 */
	@ArchTest
	static final ArchRule enlacesActivacionSoloDesdeServiciosProtegidos = noClasses()
			.that().resideOutsideOfPackages(BASE + ".alumnos.service..", BASE + ".seguridad.service..")
			.and().doNotHaveFullyQualifiedName(BASE + ".comunicacion.proceso.DespachoMensajes")
			.should().dependOnClassesThat().haveFullyQualifiedName(BASE + ".seguridad.service.EnlacesActivacion")
			.because("EnlacesActivacion anula sin exigir rol: el permiso lo exige quien da o restablece el acceso");

	/** Sprint 5 (G7): el token del enlace se genera SOLO en el envío, dentro del proceso de sistema.mensajeria. */
	@ArchTest
	static final ArchRule enlacesSoloDesdeDespachoMensajes = noClasses()
			.that().doNotHaveFullyQualifiedName(BASE + ".comunicacion.proceso.DespachoMensajes")
			.should().callMethodWhere(new DescribedPredicate<>("EnlacesActivacion.generarParaMensaje") {
				@Override
				public boolean test(JavaMethodCall llamada) {
					return llamada.getTargetOwner().getName().equals(BASE + ".seguridad.service.EnlacesActivacion")
							&& llamada.getName().equals("generarParaMensaje");
				}
			})
			.because("quien da el acceso nunca ve el enlace: lo genera el proceso de envío al enviarlo");

	/** Sprint 5: caja, cobranza, alumnos, seguridad y auditoría publican eventos; la mensajería los escucha. */
	@ArchTest
	static final ArchRule cajaCobranzaAlumnosSeguridadNoDependenDeComunicacion = noClasses()
			.that().resideInAnyPackage(BASE + ".caja..", BASE + ".cobranza..", BASE + ".comprobantes..",
					BASE + ".alumnos..", BASE + ".seguridad..", BASE + ".auditoria..", BASE + ".comun..",
					BASE + ".pasarela..", BASE + ".recaudacion..", BASE + ".conciliacion..")
			.should().dependOnClassesThat().resideInAPackage(BASE + ".comunicacion..")
			.because("los módulos financieros publican PagoRegistrado, PagoAnulado o DescuentoAprobado y no conocen la "
					+ "mensajería");

	/** Sprint 5 (G11): la mensajería simulada solo existe en dev, test y piloto (nunca en prod). */
	@ArchTest
	static final ArchRule simuladosDeComunicacionSoloEnDevTestPiloto = classes()
			.that(new DescribedPredicate<JavaClass>("son clases de comunicacion que terminan en Simulado") {
				@Override
				public boolean test(JavaClass clase) {
					return clase.getPackageName().startsWith(BASE + ".comunicacion")
							&& clase.getSimpleName().endsWith("Simulado");
				}
			})
			.should(new ArchCondition<>("estar anotadas con @Profile({\"dev\", \"test\", \"piloto\"})") {
				@Override
				public void check(JavaClass clase, ConditionEvents eventos) {
					boolean ok = clase.tryGetAnnotationOfType(org.springframework.context.annotation.Profile.class)
							.map(p -> Set.of(p.value()).equals(Set.of("dev", "test", "piloto"))).orElse(false);
					if (!ok) {
						eventos.add(SimpleConditionEvent.violated(clase, clase.getName() + " no está limitada a dev, test y "
								+ "piloto"));
					}
				}
			})
			.because("la mensajería simulada no avisa a nadie: en producción los padres dejarían de ser auditores");

	/** Sprint 5: los mensajes no se borran ni se editan por consulta (solo cambian por sus métodos). */
	@ArchTest
	static final ArchRule repositoriosDeComunicacionSinModifyingNiBorrados = noMethods()
			.that().areDeclaredInClassesThat().resideInAnyPackage(BASE + ".comunicacion.repository..")
			.should().beAnnotatedWith(Modifying.class)
			.orShould().beAnnotatedWith(consultaQueEmpiezaCon("update"))
			.orShould().beAnnotatedWith(consultaQueEmpiezaCon("delete"))
			.orShould().haveNameMatching("(?i)(delete|remove|update).*")
			.because("el mensaje es el registro del aviso a la familia: no se borra ni se reescribe")
			.allowEmptyShould(true);

	@ArchTest
	static void serviciosSensiblesExigenRol(JavaClasses clases) {
		List<String> problemas = new ArrayList<>();
		EXPRESIONES_EXIGIDAS.forEach((objetivo, esperada) -> {
			String[] partes = objetivo.split("#");
			JavaClass clase = clases.get(partes[0]);
			if (partes.length == 1) {
				revisar(problemas, objetivo, clase.tryGetAnnotationOfType(PreAuthorize.class).map(PreAuthorize::value)
						.orElse(null), esperada);
			}
			else {
				List<JavaMethod> metodos = clase.getMethods().stream().filter(m -> m.getName().equals(partes[1])).toList();
				if (metodos.isEmpty()) {
					problemas.add(objetivo + ": no existe");
				}
				metodos.forEach(m -> revisar(problemas, objetivo + m.getRawParameterTypes(),
						m.tryGetAnnotationOfType(PreAuthorize.class).map(PreAuthorize::value).orElse(null), esperada));
			}
		});
		// Ningún @PreAuthorize del código deja pasar a todos.
		clases.forEach(c -> c.getMethods().forEach(m -> m.tryGetAnnotationOfType(PreAuthorize.class)
				.filter(a -> a.value().contains("permitAll"))
				.ifPresent(a -> problemas.add(m.getFullName() + ": permitAll"))));
		assertThat(problemas).as("expresiones de @PreAuthorize").isEmpty();
	}

	private static void revisar(List<String> problemas, String donde, String encontrada, String esperada) {
		if (!esperada.equals(encontrada == null ? null : encontrada.replace(" ", ""))) {
			problemas.add(donde + ": esperaba @PreAuthorize(\"" + esperada + "\") y tiene " + encontrada);
		}
	}

	/** Sprint 2: las dependencias entre módulos van en un solo sentido, colegio ← alumnos ← cobranza. */
	@ArchTest
	static final ArchRule colegioNoDependeDeAlumnosNiCobranza = noClasses()
			.that().resideInAPackage(BASE + ".colegio..")
			.should().dependOnClassesThat().resideInAnyPackage(BASE + ".alumnos..", BASE + ".cobranza..")
			.because("colegio es la base: alumnos y cobranza dependen de él, nunca al revés (usa un puerto, "
					+ "como ConteoMatriculas)");

	@ArchTest
	static final ArchRule alumnosNoDependeDeCobranza = noClasses()
			.that().resideInAPackage(BASE + ".alumnos..")
			.should().dependOnClassesThat().resideInAPackage(BASE + ".cobranza..")
			.because("cobranza escucha MatriculaRegistrada e implementa ConsultaCuotasMatricula; alumnos no la conoce");

	/** Aprobaciones es genérico: los módulos dueños del dato implementan ManejadorSolicitud, nunca al revés. */
	@ArchTest
	static final ArchRule aprobacionesNoDependeDeAlumnosNiCobranza = noClasses()
			.that().resideInAPackage(BASE + ".aprobaciones..")
			.should().dependOnClassesThat().resideInAnyPackage(BASE + ".alumnos..", BASE + ".cobranza..")
			.because("cada módulo aplica su cambio con un ManejadorSolicitud; aprobaciones no conoce los datos");

	/** RegistroSolicitudes crea solicitudes sin exigir rol: solo lo usan servicios que ya exigieron el suyo. */
	@ArchTest
	static final ArchRule registroSolicitudesSoloDesdeServicios = noClasses()
			.that().resideOutsideOfPackages(BASE + ".aprobaciones.service..", BASE + ".alumnos.service..",
					BASE + ".cobranza.service..", BASE + ".caja.service..", BASE + ".pasarela.service..",
					BASE + ".recaudacion.service..", BASE + ".conciliacion.service..")
			.should().dependOnClassesThat().haveFullyQualifiedName(BASE + ".aprobaciones.service.RegistroSolicitudes")
			.because("la solicitud la crea el servicio protegido que valida el cambio pedido");

	/** Un manejador de solicitud corre dentro de la aprobación: exige una transacción abierta. */
	@ArchTest
	static final ArchRule manejadoresExigenTransaccionAbierta = classes()
			.that().implement(BASE + ".aprobaciones.service.ManejadorSolicitud")
			.should(new ArchCondition<>("estar anotada con @Transactional(propagation = MANDATORY)") {
				@Override
				public void check(JavaClass clase, ConditionEvents eventos) {
					boolean ok = clase.tryGetAnnotationOfType(Transactional.class)
							.map(t -> t.propagation() == Propagation.MANDATORY).orElse(false);
					if (!ok) {
						eventos.add(SimpleConditionEvent.violated(clase, clase.getName() + " no exige transacción"));
					}
				}
			})
			.because("el cambio se aplica en la misma transacción que la aprobación y su auditoría");

	/** RegistroAlumnos no tiene @PreAuthorize: solo lo usan los servicios protegidos de alumnos y la demo de dev. */
	@ArchTest
	static final ArchRule registroAlumnosSoloDesdeServiciosDeAlumnos = noClasses()
			.that().resideOutsideOfPackages(BASE + ".alumnos.service..", BASE + ".alumnos.importacion..",
					BASE + ".alumnos.inicial..")
			.should().dependOnClassesThat().haveFullyQualifiedName(BASE + ".alumnos.service.RegistroAlumnos")
			.because("RegistroAlumnos escribe sin exigir rol: el permiso lo exige el servicio que lo llama");

	/** Apache POI solo detrás del lector endurecido: nadie más abre un Excel por su cuenta. */
	@ArchTest
	static final ArchRule soloComunExcelUsaApachePoi = noClasses()
			.that().resideOutsideOfPackage(BASE + ".comun.excel..")
			.should().dependOnClassesThat().resideInAnyPackage("org.apache.poi..", "org.apache.xmlbeans..",
					"org.openxmlformats..")
			.because("el Excel se lee solo con LectorXlsxSeguro (streaming, sin fórmulas, contra zip bombs y XXE)");

	/** La importación no tiene un camino propio de escritura: pasa por RegistroAlumnos (mismas reglas y auditoría). */
	@ArchTest
	static final ArchRule importacionNoUsaRepositoriosParaEscribir = noClasses()
			.that().resideInAPackage(BASE + ".alumnos.importacion..")
			.should().callMethodWhere(new DescribedPredicate<>("save* de un repositorio que no sea el de importaciones") {
				@Override
				public boolean test(JavaMethodCall llamada) {
					return llamada.getTargetOwner().isAssignableTo(Repository.class)
							&& llamada.getName().startsWith("save")
							&& !llamada.getTargetOwner().getSimpleName().equals("ImportacionAlumnosRepository");
				}
			})
			.because("alumnos, apoderados y matrículas se guardan con RegistroAlumnos");

	/** Cobranza (sprint 2, tanda 3): las cuotas, los planes y los lotes no se borran ni se cambian con SQL masivo. */
	@ArchTest
	static final ArchRule repositoriosDeCobranzaSinModifyingNiBorrados = noMethods()
			.that().areDeclaredInClassesThat().resideInAPackage(BASE + ".cobranza.repository..")
			.should().beAnnotatedWith(Modifying.class)
			.orShould().beAnnotatedWith(consultaQueEmpiezaCon("update"))
			.orShould().haveNameMatching("(?i)(delete|remove|update).*")
			.because("una cuota solo cambia por sus métodos (y en MySQL el UPDATE está limitado por columna)");

	@ArchTest
	static final ArchRule repositoriosDeCobranzaNoHeredanBorrados = classes()
			.that().resideInAPackage(BASE + ".cobranza.repository..")
			.should().notBeAssignableTo(CrudRepository.class)
			.because("CrudRepository trae delete*: los repositorios financieros declaran solo lo que usan");

	/** En ningún módulo hay JPQL «update ... Cuota»: el monto y la fecha no se tocan ni en bloque. */
	@ArchTest
	static final ArchRule nadieHaceUpdateJpqlSobreCuota = noMethods()
			.should().beAnnotatedWith(new DescribedPredicate<JavaAnnotation<?>>("@Query(\"update Cuota ...\")") {
				@Override
				public boolean test(JavaAnnotation<?> anotacion) {
					return anotacion.getRawType().isEquivalentTo(Query.class) && anotacion.get("value")
							.map(v -> v.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").contains("update cuota"))
							.orElse(false);
				}
			})
			.because("las cuotas son inmutables salvo su estado de pago y anulación");

	@ArchTest
	static final ArchRule entidadesDeCobranzaSinSettersPublicos = noMethods()
			.that().areDeclaredInClassesThat().resideInAPackage(BASE + ".cobranza.model..")
			.and().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.and().arePublic()
			.should().haveNameMatching("set[A-Z].*")
			.because("cada cambio de una cuota, un plan o un lote es un método con su regla");

	/** Cobranza solo se conoce desde alumnos por el puerto ConsultaCuotasMatricula, que implementa cobranza. */
	@ArchTest
	static final ArchRule puertoDeCuotasLoImplementaCobranza = classes()
			.that().implement(BASE + ".alumnos.service.ConsultaCuotasMatricula")
			.should().resideInAPackage(BASE + ".cobranza..")
			.because("alumnos define el puerto y cobranza lo implementa");

	@ArchTest
	static final ArchRule seguridadYAuditoriaNoDependenDeCobranza = noClasses()
			.that().resideInAnyPackage(BASE + ".seguridad..", BASE + ".auditoria..", BASE + ".comun..")
			.should().dependOnClassesThat().resideInAPackage(BASE + ".cobranza..")
			.because("cobranza usa la base común, nunca al revés");

	@ArchTest
	static final ArchRule cobranzaNoDependeDeAcademico = noClasses()
			.that().resideInAPackage("..cobranza..")
			.should().dependOnClassesThat().resideInAPackage("..academico..")
			.allowEmptyShould(true);

	// ------------------------------------------------------------------ Sprint 3 · caja y comprobantes

	/** Comprobantes es un servicio genérico: solo depende de comun (no conoce pagos, cuotas ni alumnos). */
	@ArchTest
	static final ArchRule comprobantesNoDependeDeCajaNiCobranza = noClasses()
			.that().resideInAPackage(BASE + ".comprobantes..")
			.should().dependOnClassesThat().resideInAnyPackage(BASE + ".caja..", BASE + ".cobranza..",
					BASE + ".alumnos..", BASE + ".aprobaciones..", BASE + ".auditoria..")
			.because("comprobantes emite y numera; quien cobra (caja) le pasa los datos ya resueltos");

	@ArchTest
	static final ArchRule cobranzaNoDependeDeCaja = noClasses()
			.that().resideInAPackage(BASE + ".cobranza..")
			.should().dependOnClassesThat().resideInAnyPackage(BASE + ".caja..", BASE + ".comprobantes..")
			.because("caja usa las cuotas de cobranza; cobranza no conoce los pagos");

	@ArchTest
	static final ArchRule aprobacionesNoDependeDeCaja = noClasses()
			.that().resideInAPackage(BASE + ".aprobaciones..")
			.should().dependOnClassesThat().resideInAnyPackage(BASE + ".caja..", BASE + ".comprobantes..")
			.because("cada módulo aplica su cambio con un ManejadorSolicitud; aprobaciones no conoce los pagos");

	@ArchTest
	static final ArchRule baseNoDependeDeCaja = noClasses()
			.that().resideInAnyPackage(BASE + ".seguridad..", BASE + ".auditoria..", BASE + ".comun..", BASE + ".colegio..",
					BASE + ".alumnos..")
			.should().dependOnClassesThat().resideInAnyPackage(BASE + ".caja..", BASE + ".comprobantes..")
			.because("caja y comprobantes usan la base común, nunca al revés");

	/** Lo pagado de una cuota solo cambia desde el libro de pagos (después de insertar la aplicación). */
	@ArchTest
	static final ArchRule soloCajaServiceReflejaPagosEnCuotas = noClasses()
			.that().resideOutsideOfPackage(BASE + ".caja.service..")
			.should().callMethod(BASE + ".cobranza.model.Cuota", "reflejarPagos", java.math.BigDecimal.class.getName())
			.because("monto_pagado es la suma de aplicacion_pago: solo LibroPagos lo refleja");

	/** Lo descontado de una cuota solo cambia al aprobarse un descuento (tanda 2). */
	@ArchTest
	static final ArchRule soloManejadorDescuentoReflejaDescuentos = noClasses()
			.that().doNotHaveFullyQualifiedName(BASE + ".cobranza.service.ManejadorDescuento")
			.should().callMethod(BASE + ".cobranza.model.Cuota", "reflejarDescuentos", java.math.BigDecimal.class.getName())
			.because("monto_descuento es la suma de ajuste_cuota: solo el manejador del descuento aprobado lo refleja");

	/** LibroPagos y ServicioComprobantes escriben sin exigir rol: solo los usan los servicios de caja (que lo exigen). */
	@ArchTest
	static final ArchRule libroPagosYServicioComprobantesSoloDesdeCajaService = noClasses()
			.that().resideOutsideOfPackages(BASE + ".caja.service..", BASE + ".comprobantes.service..")
			.should().dependOnClassesThat().haveFullyQualifiedName(BASE + ".caja.service.LibroPagos")
			.orShould().dependOnClassesThat().haveFullyQualifiedName(BASE + ".comprobantes.service.ServicioComprobantes")
			.orShould().dependOnClassesThat().haveFullyQualifiedName(BASE + ".comprobantes.service.AperturaSerie")
			.orShould().dependOnClassesThat().haveFullyQualifiedName(BASE + ".caja.service.AperturaCaja")
			.because("registran pagos, cajas y comprobantes sin @PreAuthorize: el rol lo exige quien los llama");

	@ArchTest
	static final ArchRule libroPagosYServicioComprobantesExigenTransaccion = classes()
			.that().haveFullyQualifiedName(BASE + ".caja.service.LibroPagos")
			.or().haveFullyQualifiedName(BASE + ".comprobantes.service.ServicioComprobantes")
			.should(new ArchCondition<>("estar anotada con @Transactional(propagation = MANDATORY)") {
				@Override
				public void check(JavaClass clase, ConditionEvents eventos) {
					boolean ok = clase.tryGetAnnotationOfType(Transactional.class)
							.map(t -> t.propagation() == Propagation.MANDATORY).orElse(false);
					if (!ok) {
						eventos.add(SimpleConditionEvent.violated(clase, clase.getName() + " no exige transacción"));
					}
				}
			})
			.because("el pago, su comprobante y su número se guardan en la misma transacción (sin huecos)");

	/** Anular, revertir y reemplazar un pago solo ocurre al aplicar una anulación aprobada (LibroPagos y su manejador). */
	@ArchTest
	static final ArchRule soloLaAnulacionAprobadaAnulaRevierteYReemplaza = noClasses()
			.that().doNotHaveFullyQualifiedName(BASE + ".caja.service.LibroPagos")
			.and().doNotHaveFullyQualifiedName(BASE + ".caja.service.ManejadorAnulacionPago")
			.should().callMethod(BASE + ".caja.model.AplicacionPago", "revertir", BASE + ".caja.model.AplicacionPago")
			.orShould().callMethod(BASE + ".caja.model.Pago", "anular")
			.orShould().callMethodWhere(new DescribedPredicate<>("Pago.reemplazo") {
				@Override
				public boolean test(JavaMethodCall llamada) {
					return llamada.getTargetOwner().getName().equals(BASE + ".caja.model.Pago")
							&& llamada.getName().equals("reemplazo");
				}
			})
			.because("anular, revertir y reemplazar un pago solo ocurre dentro de la anulación aprobada")
			.allowEmptyShould(true);

	/** El conteo y el cierre solo los mueve el cierre ciego; la reapertura, solo la aprobada; la revisión, la bandeja. */
	@ArchTest
	static final ArchRule soloElCierreCiegoCuentaYCierraLaCaja = noClasses()
			.that().doNotHaveFullyQualifiedName(BASE + ".caja.service.ServicioCierreCaja")
			.should().callMethod(BASE + ".caja.model.CajaDiaria", "registrarConteo", "java.math.BigDecimal",
					"java.math.BigDecimal")
			.orShould().callMethod(BASE + ".caja.model.CajaDiaria", "cerrar", BASE + ".caja.model.CierreCaja")
			.because("la caja se cuenta a ciegas y se cierra solo desde ServicioCierreCaja");

	@ArchTest
	static final ArchRule soloLaReaperturaAprobadaReabreLaCaja = noClasses()
			.that().doNotHaveFullyQualifiedName(BASE + ".caja.service.ManejadorReaperturaCaja")
			.should().callMethod(BASE + ".caja.model.CajaDiaria", "reabrir")
			.because("una caja cerrada se reabre solo con una reapertura aprobada por otra persona");

	@ArchTest
	static final ArchRule soloLaBandejaRevisaUnCierre = noClasses()
			.that().doNotHaveFullyQualifiedName(BASE + ".caja.service.ManejadorCierreCaja")
			.should().callMethod(BASE + ".caja.model.CierreCaja", "aprobar", "java.lang.String",
					"java.time.LocalDateTime", "java.lang.String")
			.orShould().callMethod(BASE + ".caja.model.CierreCaja", "observar", "java.lang.String",
					"java.time.LocalDateTime", "java.lang.String")
			.because("un cierre lo aprueba u observa otra persona desde la bandeja");

	@ArchTest
	static final ArchRule repositoriosDeCajaYComprobantesSinModifyingNiBorrados = noMethods()
			.that().areDeclaredInClassesThat().resideInAnyPackage(BASE + ".caja.repository..",
					BASE + ".comprobantes.repository..")
			.should().beAnnotatedWith(Modifying.class)
			.orShould().beAnnotatedWith(consultaQueEmpiezaCon("update"))
			.orShould().beAnnotatedWith(consultaQueEmpiezaCon("delete"))
			.orShould().haveNameMatching("(?i)(delete|remove|update).*")
			.because("el libro de pagos y los comprobantes son de solo inserción (salvo la anulación y el envío)");

	@ArchTest
	static final ArchRule repositoriosDeCajaYComprobantesNoHeredanBorrados = classes()
			.that().resideInAnyPackage(BASE + ".caja.repository..", BASE + ".comprobantes.repository..")
			.should().notBeAssignableTo(CrudRepository.class)
			.because("CrudRepository trae delete*: los repositorios financieros declaran solo lo que usan");

	@ArchTest
	static final ArchRule entidadesDeCajaYComprobantesSinSettersPublicos = noMethods()
			.that().areDeclaredInClassesThat().resideInAnyPackage(BASE + ".caja.model..", BASE + ".comprobantes.model..")
			.and().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.and().arePublic()
			.should().haveNameMatching("set[A-Z].*")
			.because("un pago, un comprobante o una caja cambian solo por sus métodos con regla");

	// Sprint 4, tanda 1: los módulos nuevos dependen de caja y cobranza (por sus puertos), nunca al revés.

	@ArchTest
	static final ArchRule cajaYCobranzaNoDependenDeLosModulosNuevos = noClasses()
			.that().resideInAnyPackage(BASE + ".caja..", BASE + ".cobranza..", BASE + ".comprobantes..",
					BASE + ".alumnos..", BASE + ".seguridad..", BASE + ".auditoria..", BASE + ".comun..")
			.should().dependOnClassesThat().resideInAnyPackage(BASE + ".pasarela..", BASE + ".recaudacion..",
					BASE + ".conciliacion..")
			.because("pasarela y recaudacion usan los puertos de caja (RegistroPagosAutomaticos, PagosEnCurso) y de "
					+ "cobranza (CuotasEnPagoEnLinea); ellos no las conocen");

	/** Sprint 4, tanda 2: pagos en línea y recaudación bancaria son canales independientes (sección 3, decisión 1). */
	@ArchTest
	static final ArchRule pasarelaYRecaudacionNoDependenEntreSi = noClasses()
			.that().resideInAPackage(BASE + ".recaudacion..")
			.should().dependOnClassesThat().resideInAPackage(BASE + ".pasarela..")
			.orShould().dependOnClassesThat().resideInAPackage(BASE + ".conciliacion..")
			.because("la recaudación depende de caja, cobranza, alumnos, aprobaciones, auditoria y comun");

	@ArchTest
	static final ArchRule pasarelaNoDependeDeRecaudacion = noClasses()
			.that().resideInAPackage(BASE + ".pasarela..")
			.should().dependOnClassesThat().resideInAPackage(BASE + ".recaudacion..")
			.because("cada canal tiene sus propios manejadores de APLICAR_INGRESO y DEVOLVER_INGRESO (por entidad)");

	/** Los lotes, sus líneas y los archivos originales del banco no se borran ni se editan por consulta. */
	@ArchTest
	static final ArchRule repositoriosDeRecaudacionSinModifyingNiBorrados = noMethods()
			.that().areDeclaredInClassesThat().resideInAnyPackage(BASE + ".recaudacion.repository..",
					BASE + ".comun.archivo..")
			.should().beAnnotatedWith(Modifying.class)
			.orShould().beAnnotatedWith(consultaQueEmpiezaCon("update"))
			.orShould().beAnnotatedWith(consultaQueEmpiezaCon("delete"))
			.orShould().haveNameMatching("(?i)(delete|remove|update).*")
			.because("la recaudación y el archivo original del banco son evidencia: no se borran ni se editan");

	@ArchTest
	static final ArchRule repositoriosDeRecaudacionNoHeredanBorrados = classes()
			.that().resideInAnyPackage(BASE + ".recaudacion.repository..", BASE + ".comun.archivo..")
			.and().areInterfaces()
			.should().notBeAssignableTo(CrudRepository.class)
			.because("CrudRepository trae delete*: los repositorios financieros declaran solo lo que usan");

	@ArchTest
	static final ArchRule entidadesDeRecaudacionSinSettersPublicos = noMethods()
			.that().areDeclaredInClassesThat().resideInAnyPackage(BASE + ".recaudacion.model..", BASE + ".comun.archivo..")
			.and().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.and().arePublic()
			.should().haveNameMatching("set[A-Z].*")
			.because("un lote o una línea cambian solo por sus métodos con regla");

	/** El pago de recaudación solo nace de RegistroPagosAutomaticos, desde los procesos de recaudación. */
	@ArchTest
	static final ArchRule soloElProcesoDeRecaudacionRegistraPagosDeBanco = noClasses()
			.that().resideInAPackage(BASE + ".recaudacion..")
			.and().resideOutsideOfPackage(BASE + ".recaudacion.proceso..")
			.should().dependOnClassesThat().haveFullyQualifiedName(BASE + ".caja.service.RegistroPagosAutomaticos")
			.because("los pagos por banco los registra sistema.recaudacion, nunca un servicio que llama una persona");

	/** Actuar como sistema (sin persona detrás) solo desde los procesos: nunca desde un controlador ni un servicio web. */
	@ArchTest
	static final ArchRule ejecucionComoSistemaSoloEnProcesos = noClasses()
			.that().resideOutsideOfPackages(BASE + "..proceso..", BASE + ".comun.sistema..")
			.should().dependOnClassesThat().haveFullyQualifiedName(BASE + ".comun.sistema.EjecucionComoSistema")
			.because("un actor de sistema salta los permisos de las personas: solo lo usan las tareas y los procesos");

	/** La pasarela simulada solo existe en dev, test y piloto (nunca en prod). */
	@ArchTest
	static final ArchRule pasarelaSimuladaSoloEnDevTestPiloto = classes()
			.that(new DescribedPredicate<JavaClass>("son beans de la pasarela simulada") {
				@Override
				public boolean test(JavaClass clase) {
					return (clase.getPackageName().startsWith(BASE + ".pasarela.simulada")
							&& clase.isMetaAnnotatedWith(org.springframework.stereotype.Component.class))
							|| clase.getName().equals(BASE + ".pasarela.web.SimuladorPasarelaController");
				}
			})
			.should(new ArchCondition<>("estar anotadas con @Profile({\"dev\", \"test\", \"piloto\"})") {
				@Override
				public void check(JavaClass clase, ConditionEvents eventos) {
					boolean ok = clase.tryGetAnnotationOfType(org.springframework.context.annotation.Profile.class)
							.map(p -> Set.of(p.value()).equals(Set.of("dev", "test", "piloto"))).orElse(false);
					if (!ok) {
						eventos.add(SimpleConditionEvent.violated(clase, clase.getName() + " no está limitada a dev, test y "
								+ "piloto"));
					}
				}
			})
			.because("la pasarela simulada marca pagos sin dinero real")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule repositoriosDePasarelaSinModifyingNiBorrados = noMethods()
			.that().areDeclaredInClassesThat().resideInAnyPackage(BASE + ".pasarela.repository..")
			.should().beAnnotatedWith(Modifying.class)
			.orShould().beAnnotatedWith(consultaQueEmpiezaCon("update"))
			.orShould().beAnnotatedWith(consultaQueEmpiezaCon("delete"))
			.orShould().haveNameMatching("(?i)(delete|remove|update).*")
			.because("las órdenes, sus cuotas y los avisos de la pasarela no se borran ni se editan por consulta");

	@ArchTest
	static final ArchRule repositoriosDePasarelaNoHeredanBorrados = classes()
			.that().resideInAnyPackage(BASE + ".pasarela.repository..")
			.should().notBeAssignableTo(CrudRepository.class)
			.because("CrudRepository trae delete*: los repositorios financieros declaran solo lo que usan");

	@ArchTest
	static final ArchRule entidadesDePasarelaSinSettersPublicos = noMethods()
			.that().areDeclaredInClassesThat().resideInAnyPackage(BASE + ".pasarela.model..")
			.and().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.and().arePublic()
			.should().haveNameMatching("set[A-Z].*")
			.because("una orden cambia solo por sus métodos con regla");

	// Sprint 4, tanda 3: la conciliación lee caja, pasarela y recaudación; ninguno de ellos la conoce (decisión 1).

	@ArchTest
	static final ArchRule pasarelaNoDependeDeConciliacion = noClasses()
			.that().resideInAPackage(BASE + ".pasarela..")
			.should().dependOnClassesThat().resideInAPackage(BASE + ".conciliacion..")
			.because("la conciliación depende de caja, pasarela, recaudacion, auditoria y comun; nunca al revés");

	/** El extracto, sus movimientos y sus partidas no se borran ni se editan por consulta. */
	@ArchTest
	static final ArchRule repositoriosDeConciliacionSinModifyingNiBorrados = noMethods()
			.that().areDeclaredInClassesThat().resideInAnyPackage(BASE + ".conciliacion.repository..")
			.should().beAnnotatedWith(Modifying.class)
			.orShould().beAnnotatedWith(consultaQueEmpiezaCon("update"))
			.orShould().beAnnotatedWith(consultaQueEmpiezaCon("delete"))
			.orShould().haveNameMatching("(?i)(delete|remove|update).*")
			.because("el extracto del banco es evidencia: no se borra ni se edita; una partida solo se confirma o descarta");

	@ArchTest
	static final ArchRule repositoriosDeConciliacionNoHeredanBorrados = classes()
			.that().resideInAnyPackage(BASE + ".conciliacion.repository..")
			.should().notBeAssignableTo(CrudRepository.class)
			.because("CrudRepository trae delete*: los repositorios financieros declaran solo lo que usan");

	@ArchTest
	static final ArchRule entidadesDeConciliacionSinSettersPublicos = noMethods()
			.that().areDeclaredInClassesThat().resideInAnyPackage(BASE + ".conciliacion.model..")
			.and().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.and().arePublic()
			.should().haveNameMatching("set[A-Z].*")
			.because("un extracto o una partida cambian solo por sus métodos con regla");

	/** La verificación AUTOMÁTICA la deja solo la conciliación automática (sistema.conciliacion), desde su proceso. */
	@ArchTest
	static final ArchRule soloLaConciliacionVerificaAutomaticamente = noClasses()
			.that().resideOutsideOfPackages(BASE + ".conciliacion.proceso..", BASE + ".conciliacion.service..",
					BASE + ".caja.service..")
			.should().dependOnClassesThat().haveFullyQualifiedName(BASE + ".caja.service.RegistroVerificacionAutomatica")
			.because("la verificación automática sale solo de una partida confirmada sobre un extracto confirmado");

	/** Las partidas PROPUESTA (el emparejamiento) solo las arma la conciliación. */
	@ArchTest
	static final ArchRule emparejadorSoloDesdeConciliacion = noClasses()
			.that().resideOutsideOfPackage(BASE + ".conciliacion..")
			.should().dependOnClassesThat().haveFullyQualifiedName(BASE + ".conciliacion.service.Emparejador")
			.because("el emparejador escribe sin @PreAuthorize: el rol lo exige quien lo llama");

	/** Las liquidaciones de la pasarela solo las registra el proceso de importación (sistema.pasarela). */
	@ArchTest
	static final ArchRule soloElImportadorRegistraLiquidaciones = noClasses()
			.that().resideOutsideOfPackages(BASE + ".pasarela.proceso..", BASE + ".pasarela.service..")
			.should().dependOnClassesThat().haveFullyQualifiedName(BASE + ".pasarela.service.RegistroLiquidaciones")
			.because("una liquidación la registra sistema.pasarela con lo que respondió la API de la pasarela");

	private static DescribedPredicate<JavaAnnotation<?>> consultaNativa() {
		return new DescribedPredicate<>("@Query(nativeQuery = true)") {
			@Override
			public boolean test(JavaAnnotation<?> anotacion) {
				return anotacion.getRawType().isEquivalentTo(Query.class)
						&& Boolean.TRUE.equals(anotacion.get("nativeQuery").orElse(false));
			}
		};
	}

	private static DescribedPredicate<JavaAnnotation<?>> consultaQueEmpiezaCon(String verbo) {
		return new DescribedPredicate<>("@Query(\"" + verbo + " ...\")") {
			@Override
			public boolean test(JavaAnnotation<?> anotacion) {
				return anotacion.getRawType().isEquivalentTo(Query.class)
						&& anotacion.get("value").map(Object::toString)
							.map(v -> v.strip().toLowerCase(Locale.ROOT).startsWith(verbo))
							.orElse(false);
			}
		};
	}

	private static DescribedPredicate<JavaMethodCall> llamadaA(String prefijo, String descripcion) {
		return new DescribedPredicate<>(descripcion) {
			@Override
			public boolean test(JavaMethodCall llamada) {
				return llamada.getName().startsWith(prefijo);
			}
		};
	}

	private static DescribedPredicate<JavaMethodCall> borradoDeRepositorio() {
		return new DescribedPredicate<>("un método delete* de un repositorio de Spring Data") {
			@Override
			public boolean test(JavaMethodCall llamada) {
				return llamada.getTargetOwner().isAssignableTo(Repository.class)
						&& llamada.getName().startsWith("delete");
			}
		};
	}

	private static DescribedPredicate<JavaClass> noEsAutorizadaComoSistema() {
		return new DescribedPredicate<>("no son las clases autorizadas a usar comoSistema") {
			@Override
			public boolean test(JavaClass clase) {
				return AUTORIZADAS_COMO_SISTEMA.stream()
						.noneMatch(nombre -> clase.getName().equals(nombre) || clase.getName().startsWith(nombre + "$"));
			}
		};
	}
}
