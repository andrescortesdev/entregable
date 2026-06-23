# ── Etapa 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copiar wrapper y pom primero (aprovecha caché de capas)
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Descargar dependencias (cacheado si pom.xml no cambia)
RUN ./mvnw dependency:go-offline -q

# Copiar fuentes y compilar
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ── Etapa 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Usuario no-root por seguridad
RUN addgroup -S poli && adduser -S poli -G poli
USER poli

# Copiar JAR desde la etapa de build
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
