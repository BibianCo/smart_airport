#  Imagen base: Eclipse Temurin JDK 21 sobre Alpine Linux (ligero)
#  Maven 3.9 incluido en la imagen maven:3.9-eclipse-temurin-21
FROM maven:3.9-eclipse-temurin-21-alpine AS build

# Metadata del autor
LABEL maintainer="UPTC Sistemas Operativos 2026"
LABEL description="AeroSim — Simulación de Aeropuerto Inteligente con Spring Boot"

# Directorio de trabajo dentro del contenedor de build
WORKDIR /app

# ── Copiar el pom.xml primero (aprovecha la caché de Docker) ──────
# Docker cachea esta capa. Si el pom.xml no cambia entre builds,
# no vuelve a descargar todas las dependencias de Maven.
COPY pom.xml .

# Descargar todas las dependencias de Maven (sin compilar).
# Si el pom.xml no cambia, esta capa se sirve desde caché → builds rápidos.
RUN mvn dependency:go-offline -B --quiet

# ── Copiar el código fuente ────────────────────────────────────────
COPY src ./src

# ── Compilar y empaquetar (saltando tests para la imagen) ─────────
# -DskipTests : los tests se corren en CI, no al construir la imagen
# -B           : modo batch (sin interactividad, ideal para Docker)
# --quiet      : reduce el ruido de salida
RUN mvn package -DskipTests -B --quiet

# ──────────────────────────────────────────────────────────────────
#  ETAPA 2 — RUNTIME
#  Solo JRE 21 (sin Maven, sin código fuente, sin herramientas de build)
#  Imagen ~200MB vs ~600MB si usáramos la imagen de build completa
# ──────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# ── Usuario no-root (buena práctica de seguridad) ─────────────────
# Crear grupo y usuario "aerosim" para no ejecutar como root
RUN addgroup -S airport && adduser -S airport -G airport

# ── Copiar SOLO el JAR desde la etapa de build ────────────────────
# El nombre airport-0.0.1-SNAPSHOT.jar viene de <artifactId>-<version> en el pom.xml
COPY --from=build /app/target/airport-0.0.1-SNAPSHOT.jar airport.jar

# Cambiar la propiedad del archivo al usuario airport
RUN chown airport:airport airport.jar

# Ejecutar como usuario no-root
USER airport

# ── Puerto expuesto ───────────────────────────────────────────────
# Debe coincidir con server.port en application.properties
EXPOSE 8080

# ── Variables de entorno configurables al hacer docker run ────────
# Permiten sobreescribir los valores de application.properties
# sin necesidad de reconstruir la imagen.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV airport.num-runways=3
ENV airport.num-gates=5
ENV airport.tiempo-min-runway-ms=2000
ENV airport.tiempo-max-runway-ms=5000
ENV SERVER_PORT=8080

# ── Health check ──────────────────────────────────────────────────
# Docker comprueba cada 30s que la app esté respondiendo.
# Si falla 3 veces seguidas, el contenedor se marca como "unhealthy".
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/state || exit 1

# ── Comando de arranque ───────────────────────────────────────────
# Solo pasamos JAVA_OPTS y el JAR.
# Spring Boot leerá las demás variables directamente del entorno.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar airport.jar"]