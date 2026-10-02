package pe.edu.virgenmaria.cuentasclaras.arquitectura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
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
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.ConsultaAuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

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

	/** Los servicios de gestión exigen rol también por método, no solo por URL. */
	@ArchTest
	static final ArchRule serviciosSensiblesExigenRol = classes()
			.that().areTopLevelClasses().and().belongToAnyOf(ServicioUsuarios.class, ConsultaAuditoriaService.class)
			.should().beAnnotatedWith(PreAuthorize.class)
			.because("la matriz de URL no basta: el servicio también se protege (@PreAuthorize)");

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
