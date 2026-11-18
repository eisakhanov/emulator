# =============================================================================
# Многоступенчатая сборка device-emulator:
#   build   — Maven + JDK 21
#   runtime — только JRE 21 и жирный jar
#
# Примечание: нужны базовые образы с docker.io (maven:3.9-eclipse-temurin-21,
# eclipse-temurin:21-jre-jammy) — при работе из корпоративной сети настройте
# зеркало реестра (registry mirror) или предзагрузите образы.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Сначала только pom.xml — слой с зависимостями кешируется
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Затем исходники — пересборка только при изменении кода
COPY src ./src
RUN mvn -B -q package -DskipTests

# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Не запускать JVM от root
RUN groupadd --system emulator && useradd --system --gid emulator emulator
COPY --from=build /app/target/device-emulator-*.jar app.jar
USER emulator

# REST + SNMP-порты шести устройств (UDP)
EXPOSE 8085 1161 1162 1163 1164 1165 1166/udp

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]