FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/logs /app/jwt
COPY badmshot-*.jar /app/app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
