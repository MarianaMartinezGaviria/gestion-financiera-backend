FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw clean package -DskipTests && JAR_FILE=$(find target -maxdepth 1 -name "*.jar" ! -name "*.original" | head -n 1) && cp "$JAR_FILE" app.jar

FROM eclipse-temurin:21-jre AS run

WORKDIR /app

COPY --from=build /app/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]