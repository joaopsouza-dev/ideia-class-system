FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B
FROM eclipse-temurin:21-jre

WORKDIR /app

# Usuário sem privilégios; /app/storage é o ponto de montagem do volume de uploads.
# O chown na imagem faz o volume nomeado nascer com dono "app" na primeira montagem.
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app/storage \
    && chown -R app:app /app

COPY --from=build --chown=app:app /app/target/*.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
