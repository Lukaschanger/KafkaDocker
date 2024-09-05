# Use an official OpenJDK runtime as a parent image
FROM openjdk:8-jdk-alpine

# Set the working directory in the container
WORKDIR /usr/src/app

# Copy the Maven build files and the source code
COPY pom.xml .
COPY src/ ./src/

# Install Maven and build the application
RUN apk add --no-cache maven
RUN mvn clean package

# Specify the entry point to run your app
ENTRYPOINT ["java", "-cp", "target/kafka-streams-app-1.0-SNAPSHOT.jar", "kafkastreamsapp"]
