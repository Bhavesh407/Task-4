# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:21-jdk-jammy

# Set the working directory in the container
WORKDIR /app

# Copy the Maven wrapper and pom file
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# --- THIS IS THE FIX ---
# Add execute permissions to the Maven wrapper
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline

# Copy the project source
COPY src ./src

# Build the application
RUN ./mvnw package -DskipTests

# Expose the port the app runs on
EXPOSE 8081

# Specify the command to run on container start
ENTRYPOINT ["java","-jar","/app/target/taskapi-0.0.1-SNAPSHOT.jar"]