FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/ProductService-0.0.1-SNAPSHOT.jar /app/product-service.jar
EXPOSE 7022
ENTRYPOINT ["java", "-jar", "/app/product-service.jar"]
