FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY target/*-1.0.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8086

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8086/actuator/health || exit 1

ENTRYPOINT ["java", "-Xms64m", "-Xmx256m", "-jar", "/app/app.jar"]
