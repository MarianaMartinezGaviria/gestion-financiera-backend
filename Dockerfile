FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gestion-financiera-backend/pom.xml ./pom.xml
COPY gestion-financiera-backend/mvnw ./mvnw
COPY gestion-financiera-backend/.mvn ./.mvn
RUN chmod +x mvnw
COPY gestion-financiera-backend/src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/gestion-financiera-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
