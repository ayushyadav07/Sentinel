FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl ca-certificates \
    && curl -sSfL https://raw.githubusercontent.com/anchore/grype/main/install.sh \
       | sh -s -- -b /usr/local/bin \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/scanner-mcp.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
