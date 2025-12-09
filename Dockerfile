FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Install bash for mvnw script
RUN apk add --no-cache bash

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw package -DskipTests -Dmaven.test.skip=true -B

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -D appuser
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
