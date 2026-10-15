# ── Etapa 1: Build con Maven y Eclipse Temurin JDK 21 ──
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copiar archivos de Maven Wrapper y dependencias primero para optimizar caché
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Copiar código fuente y compilar JAR sin ejecutar tests (los tests se ejecutan en CI)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ── Etapa 2: Imagen final de ejecución con JRE 21 ligero ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear usuario sin privilegios para mayor seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copiar el JAR generado
COPY --from=builder /app/target/*.jar app.jar

# Exponer el puerto del servidor Spring Boot
EXPOSE 8080

# Comando de inicio
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
