# Étape 1 : interface Angular
FROM node:20-alpine AS web
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npx ng build

# Étape 2 : backend Spring Boot (l'interface est servie par Spring)
FROM maven:3.9-eclipse-temurin-17 AS api
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -q dependency:go-offline
COPY backend/src ./src
COPY --from=web /app/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -q -DskipTests package

# Étape 3 : image finale légère
FROM eclipse-temurin:17-jre
WORKDIR /opt/trustsign
COPY --from=api /app/backend/target/trustsign-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
