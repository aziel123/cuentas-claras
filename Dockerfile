# Imagen de la aplicación Cuentas Claras (front y back: Spring Boot + Thymeleaf).
# Sprint 8, tanda 3 (decisión 134): la construye el CI en cada merge a main y se despliega por su digest. Las imágenes base
# van fijadas por digest (una etiqueta se puede mover); Dependabot las actualiza cada semana. Solo se copian .mvn, mvnw,
# pom.xml y src: ningún archivo de configuración ni clave entra en la imagen (gitleaks lo revisa en el CI).
# Etapa 1: compila el jar con Maven Wrapper y Java 21.
FROM eclipse-temurin:21-jdk@sha256:23cc5734ff46e776f51ce66eb9ee08444fd47caabec0a95b7eb6e94be2d3696d AS compilacion
WORKDIR /fuente
COPY .mvn .mvn
COPY mvnw pom.xml ./
# En Windows, Git puede dejar mvnw con saltos de línea CRLF y Linux no lo ejecuta ("./mvnw: not found").
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/cuentas-claras-*.jar /fuente/app.jar

# Etapa 2: solo el runtime de Java y el jar, con un usuario sin privilegios.
FROM eclipse-temurin:21-jre@sha256:227118764cccd3c3294617061aaa93a08309c63a395eefa83e6b35032992ea99
RUN useradd --system --uid 10001 cuentasclaras
WORKDIR /app
COPY --from=compilacion /fuente/app.jar /app/app.jar
USER cuentasclaras
EXPOSE 8080
# Sin argumentos arranca la aplicación; con "migrar" aplica las migraciones Flyway y termina.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
