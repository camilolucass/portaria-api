# ---------- build ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# as dependencias mudam menos que o codigo: baixa-las antes aproveita o cache
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# os testes exigem Docker (Testcontainers) e ja rodam no CI
RUN mvn -B -DskipTests package

# ---------- runtime ----------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

ENV TZ=America/Sao_Paulo
RUN apk add --no-cache tzdata \
 && addgroup -S portaria && adduser -S portaria -G portaria
USER portaria

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"'

ENTRYPOINT ["java", "-jar", "app.jar"]
