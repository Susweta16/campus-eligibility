# Multi-stage build: compile with a JDK, run with a smaller JRE image.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir -p out && javac -d out src/eligibility/*.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY web ./web
COPY data ./data

# Cloud hosts set $PORT; Server.java reads it automatically.
ENV PORT=8080
EXPOSE 8080

CMD ["java", "-cp", "out", "eligibility.Server"]
