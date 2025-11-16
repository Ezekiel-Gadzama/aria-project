# Multi-stage build for ARIA application
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
# Avoid brittle go-offline; resolve during package

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Build React frontend
FROM node:18-alpine AS frontend-build

WORKDIR /app/frontend

# Copy frontend package files
COPY frontend/package*.json ./

# Install dependencies
RUN npm install

# Copy frontend source
COPY frontend/ ./

# Build React app
RUN npm run build

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install Python, pip, and venv for Telethon scripts
RUN apt-get update && \
    apt-get install -y python3 python3-pip python3-venv && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copy built JAR from build stage
COPY --from=build /app/target/aria-core-1.0-SNAPSHOT.jar app.jar

# Copy built React frontend
COPY --from=frontend-build /app/frontend/build ./static

# Copy Python scripts
COPY scripts ./scripts

# Create a virtual environment and install Python dependencies for Telethon
RUN python3 -m venv /opt/aria-venv && \
    /opt/aria-venv/bin/pip install --no-cache-dir -r /app/scripts/telethon/requirements.txt

# Ensure the virtualenv python is first on PATH
ENV PATH="/opt/aria-venv/bin:${PATH}"

# Create directories
RUN mkdir -p media/telegram_media

# Expose port for Spring Boot API
EXPOSE 8080

# Set environment variables
ENV JAVA_OPTS="-Xmx2g -Xms512m"

# Run Spring Boot application (will serve both API and static React files)
ENTRYPOINT ["java", "-jar", "app.jar"]
