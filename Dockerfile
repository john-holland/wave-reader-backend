ARG JAVA_VERSION=17

FROM gradle:8.5-jdk${JAVA_VERSION} as BUILD

WORKDIR /app
COPY . .
RUN gradle --no-daemon shadowJar

FROM eclipse-temurin:${JAVA_VERSION}-jre

WORKDIR /app
COPY --from=BUILD /app/build/libs/*-all.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
