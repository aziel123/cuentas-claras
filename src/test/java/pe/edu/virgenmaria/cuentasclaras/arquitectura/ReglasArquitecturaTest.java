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
import com.tngtech.archunit.lang.ArchRule;
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
			.and().doNotBelongToAnyOf(Colegio.class, EventoAuditoria.class, EslabonCadena.class)
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
			Map.entry(BASE + ".alumnos.service.ServicioMatriculas", ESCRITURA_ESCOLAR),
			Map.entry(BASE + ".alumnos.importacion.ServicioImportacionAlumnos", ESCRITURA_ESCOLAR));

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

	@ArchTest
	static final ArchRule cobranzaNoDependeDeAcademico = noClasses()
			.that().resideInAPackage("..cobranza..")
			.should().dependOnClassesThat().resideInAPackage("..academico..")
			.allowEmptyShould(true);

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
