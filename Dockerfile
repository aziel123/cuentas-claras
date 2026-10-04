# Imagen de la aplicación Cuentas Claras (front y back: Spring Boot + Thymeleaf).
# Etapa 1: compila el jar con Maven Wrapper y Java 21.
FROM eclipse-temurin:21-jdk AS compilacion
WORKDIR /fuente
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/cuentas-claras-*.jar /fuente/app.jar

# Etapa 2: solo el runtime de Java y el jar, con un usuario sin privilegios.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 cuentasclaras
WORKDIR /app
COPY --from=compilacion /fuente/app.jar /app/app.jar
USER cuentasclaras
EXPOSE 8080
# Sin argumentos arranca la aplicación; con "migrar" aplica las migraciones Flyway y termina.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
