# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom files
COPY pom.xml ./
COPY backend/pom.xml backend/
COPY gateway/pom.xml gateway/

# Download dependencies
RUN mvn dependency:go-offline -B -pl gateway -am

# Copy source code
COPY gateway/src gateway/src

# Package gateway jar
RUN mvn clean package -DskipTests -pl gateway -am

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/gateway/target/gateway-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
