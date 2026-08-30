# Product Service

The **Product Service** is a microservice responsible for managing the product catalog, categorizing products, and handling client subscriptions for discount notifications. It integrates with Redis for caching discounts and uses Kafka for asynchronous communication with other services.

## Core Responsibilities

- **Product Management:** CRUD operations for products (Add, Get, List).
- **Categorization:** Filtering products by predefined categories.
- **Discount Tracking:** Monitoring and caching product discounts.
- **Subscription Management:** Allowing clients to subscribe to specific products.
- **Notifications:** Processing incoming discount changes and notifying subscribed clients via Kafka events.

## Tech Stack

- **Java 17+** with **Spring Boot 3.x**
- **PostgreSQL:** Relational database for persistent storage (Products, Subscribers).
- **Redis:** Distributed cache for storing active product discounts.
- **Apache Kafka:** Event streaming platform for incoming discount updates and outgoing notifications.
- **Eureka:** Service discovery registration.
- **Flyway:** Database schema versioning and migrations.
- **SpringDoc OpenAPI (Swagger):** API documentation and testing interface.

---

## API Documentation

The service exposes a RESTful API. When the service is running, you can access the interactive Swagger UI at:
`http://localhost:7022/swagger-ui.html`

### Product Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/products` | Retrieve all products. |
| `GET` | `/api/v1/products/{id}` | Get product details by ID (includes current discount info). |
| `POST` | `/api/v1/products/by-ids` | Bulk retrieve products by a list of IDs. |
| `GET` | `/api/v1/products/category/{cat}` | Filter products by category (`ELECTRONICS`, `FOOD`, `CLOTH`, `TOYS`). |
| `POST` | `/api/v1/products` | Add a new product to the catalog. |

### Subscription Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/products/{productId}/subscribe/{clientId}` | Subscribe a specific client to a product's discount updates. |

---

## External Interactions & Messaging

### Kafka Topics

#### 1. `discountChangesTopic` (Consumer)
The service listens to this topic for updates on product discounts.
- **Payload:** `DiscountChangesDto`
- **Action:** When a message is received, the service updates its Redis cache and triggers the notification process for active subscribers.

#### 2. `discountNotifTopic` (Producer)
The service publishes messages to this topic when a product's discount changes and there are active subscribers for that product.
- **Payload:** `DiscountNotification` (contains `productId`, `clientId`, and the `newDiscount` value).

### Redis Caching
Discounts are cached in Redis to provide fast access during product retrieval. The `DiscountCacheManager` handles lookups and updates to ensure the most current pricing information is served.

### Service Discovery (Eureka)
The service registers itself as `product` in the Eureka Discovery Server for internal microservice communication.

---

## Getting Started

### Environment Variables
The service requires the following environment variables (or corresponding values in `application.yaml`):

- `KAFKA_BOOTSTRAP_SERVERS`: Kafka broker address (e.g., `localhost:9092`).
- `REDIS_HOST` & `REDIS_PORT`: Redis server configuration.
- `EUREKA_SERVER_URL`: URL for the Eureka registry.
- `spring.datasource.url/username/password`: PostgreSQL connection details.

### Database Migrations
Flyway automatically applies migrations from `src/main/resources/db/migration` on startup.

### Running the Application
```bash
./mvnw spring-boot:run
```
The service will start on port `7022`.
