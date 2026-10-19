# ── Etapa 1: Build con Maven y Eclipse Temurin JDK 21 ──
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copiar archivos de Maven Wrapper
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Copiar código fuente y compilar JAR sin ejecutar tests (los tests se ejecutan en CI)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ── Etapa 2: Imagen final de ejecución con JRE 21 ligero ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear usuario sin privilegios y asegurar permisos de escritura para uploads
RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/uploads/contratos \
    && chown -R appuser:appgroup /app

USER appuser

# Copiar el JAR generado
COPY --chown=appuser:appgroup --from=builder /app/target/*.jar app.jar

# Exponer el puerto del servidor Spring Boot
EXPOSE 8080

# Comando de inicio
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
