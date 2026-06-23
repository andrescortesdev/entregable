# ── Etapa 1: Build ───────────────────────────────────────────────────────────
# Alpine está bien para compilar (no ejecuta código nativo de Firebase aquí)
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -q

COPY src ./src
RUN ./mvnw package -DskipTests -q

# ── Etapa 2: Runtime ─────────────────────────────────────────────────────────
# IMPORTANTE: usar imagen Debian (jammy), NO Alpine.
# Firebase Admin SDK usa gRPC con binarios nativos (tcnative) compilados contra
# glibc. Alpine usa musl libc, lo que causa SIGSEGV en el arranque.
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Usuario no-root por seguridad
RUN groupadd -r poli && useradd -r -g poli poli
USER poli

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
