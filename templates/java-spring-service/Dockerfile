FROM eclipse-temurin:17-jre
RUN useradd -u 10001 app
USER 10001
COPY target/*.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
