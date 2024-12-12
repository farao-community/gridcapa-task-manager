FROM eclipse-temurin:21-jre-alpine AS BUILDER
ARG JAR_FILE=gridcapa-task-manager-app/target/*.jar
COPY ${JAR_FILE} app.jar
RUN mkdir -p /tmp/app  \
    && java -Djarmode=tools  \
    -jar /app.jar extract --layers --launcher \
    --destination /tmp/app

FROM eclipse-temurin:21-jre-alpine
COPY --from=BUILDER /tmp/app/dependencies/ ./
COPY --from=BUILDER /tmp/app/spring-boot-loader/ ./
COPY --from=BUILDER /tmp/app/application/ ./
COPY --from=BUILDER /tmp/app/snapshot-dependencies/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]