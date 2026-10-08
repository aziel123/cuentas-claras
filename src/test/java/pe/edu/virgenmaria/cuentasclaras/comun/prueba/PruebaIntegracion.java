package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aplicación completa (seguridad, JPA, Flyway y Thymeleaf sobre H2) con MockMvc y el perfil {@code test}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public @interface PruebaIntegracion {
}
