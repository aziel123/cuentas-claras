# Imagen de la aplicación Cuentas Claras (front y back: Spring Boot + Thymeleaf).
# Sprint 8, tanda 3 (decisión 134): la construye el CI en cada merge a main y se despliega por su digest. Las imágenes base
# van fijadas por digest (una etiqueta se puede mover); Dependabot las actualiza cada semana. Solo se copian .mvn, mvnw,
# pom.xml y src: ningún archivo de configuración ni clave entra en la imagen (gitleaks lo revisa en el CI).
# Etapa 1: compila el jar con Maven Wrapper y Java 21.
FROM eclipse-temurin:21-jdk@sha256:3e3c176ffed168beb42c607be9bc1639b466cf00261a0fb04425562c9d0c5c2b AS compilacion
WORKDIR /fuente
COPY .mvn .mvn
COPY mvnw pom.xml ./
# En Windows, Git puede dejar mvnw con saltos de línea CRLF y Linux no lo ejecuta ("./mvnw: not found").
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/cuentas-claras-*.jar /fuente/app.jar

# Etapa 2: solo el runtime de Java y el jar, con un usuario sin privilegios.
FROM eclipse-temurin:21-jre@sha256:cff19e6215689161eb6162c11b86b0c60ddf802164f2eaf48d570f8fb79a36c5
RUN useradd --system --uid 10001 cuentasclaras
WORKDIR /app
COPY --from=compilacion /fuente/app.jar /app/app.jar
USER cuentasclaras
EXPOSE 8080
# Sin argumentos arranca la aplicación; con "migrar" aplica las migraciones Flyway y termina.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
