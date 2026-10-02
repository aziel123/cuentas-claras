package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionJpa;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prueba de la capa JPA sobre el H2 en modo MySQL del perfil {@code test}.
 * <ul>
 *   <li>{@code replace = NONE}: sin esto, {@code @DataJpaTest} usa un H2 sin {@code MODE=MySQL}
 *       y las migraciones con {@code DATETIME(6)} fallan.</li>
 *   <li>Importa la auditoría JPA ({@code creadoEn}, {@code creadoPor}) y el reloj.</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ ConfiguracionJpa.class, ConfiguracionTiempo.class })
@ActiveProfiles("test")
public @interface PruebaJpa {
}
