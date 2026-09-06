# Kafka Configuration & Error Handling Guide

This document provides a comprehensive technical breakdown of how Apache Kafka is configured in **ProductService**, with special focus on error handling, backoff calculations, dead-letter recovery, poison pill resilience, producer throughput, and observability.

---

## 1. High-Level Architecture

ProductService plays both **Consumer** and **Producer** roles in the event-driven ecosystem:

```mermaid
flowchart LR
    subgraph External["External Services / Discount Service"]
        DS[Discount Service]
    end

    subgraph KafkaBroker["Apache Kafka Broker"]
        T_IN["discountChangesTopic<br/>(Discount Changes)"]
        T_OUT["discountNotifTopic<br/>(3 Partitions)"]
        T_DLT["discountChangesTopic.DLT<br/>(Dead Letter Topic)"]
    end

    subgraph ProductServiceApp["Product Service"]
        KC[KafkaConsumerService<br/>Single-Record Consumer]
        EH["DefaultErrorHandler<br/>ExponentialBackOff (1s -> 2s -> 4s)"]
        DLR["DeadLetterPublishingRecoverer<br/>(Routes to .DLT)"]
        IP[IncomingDiscountsProcessor]
        SN[SubscribersNotifier]
        KP[KafkaSender<br/>KafkaTemplate]
    end

    DS -->|Publish changes| T_IN
    T_IN -->|Poll record| KC
    KC -.->|On Error (Exhausted)| EH
    EH -->|Publish failed record| DLR
    DLR -->|Route| T_DLT
    KC -->|Process item| IP
    IP -->|Discount Increased| SN
    SN -->|Key: productId (Idempotent)| KP
    KP -->|acks=all, snappy| T_OUT
```

---

## 2. Kafka Error Handler (`CommonErrorHandler`) & Dead Letter Recovery

### Configuration Location
- **File**: [`KafkaConsumerConfiguration.java`](src/main/java/com/serjnn/ProductService/kafka/consumer/KafkaConsumerConfiguration.java)
- **Bean Definition**:
  ```java
  @Bean
  public CommonErrorHandler kafkaErrorHandler(@Qualifier("dltKafkaTemplate") KafkaTemplate<Object, Object> dltKafkaTemplate) {
      ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
      backOff.setMaxElapsedTime(10000L);
      DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate);
      return new DefaultErrorHandler(recoverer, backOff);
  }
  ```

### How `DefaultErrorHandler` and `DeadLetterPublishingRecoverer` Work
1. **Poison Pill Interception**: The consumer uses `ErrorHandlingDeserializer` wrapping `StringDeserializer` and `JsonDeserializer`. Malformed JSON or incompatible schemas are safely trapped and forwarded to the error handler rather than crashing the consumer thread.
2. **Exponential Backoff Schedule**:
   - **Initial Interval (`initialInterval`)**: `1000 ms` ($1\text{ s}$)
   - **Multiplier (`multiplier`)**: `2.0`
   - **Maximum Elapsed Time (`maxElapsedTime`)**: `10000 ms` ($10\text{ s}$)

#### Detailed Backoff Math & Retry Progression:

| Attempt | Status | Delay Before Attempt | Cumulative Elapsed Time | Next Delay Calculated | Within 10s Limit? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1 (Initial)** | Failed | $0\text{ s}$ | $0\text{ s}$ | $1000\text{ ms}$ ($1\text{ s}$) | Yes |
| **2 (Retry 1)** | Failed | $1000\text{ ms}$ ($1\text{ s}$) | $1000\text{ ms}$ ($1\text{ s}$) | $1000 \times 2.0 = 2000\text{ ms}$ ($2\text{ s}$) | Yes ($1\text{ s} + 2\text{ s} \le 10\text{ s}$) |
| **3 (Retry 2)** | Failed | $2000\text{ ms}$ ($2\text{ s}$) | $3000\text{ ms}$ ($3\text{ s}$) | $2000 \times 2.0 = 4000\text{ ms}$ ($4\text{ s}$) | Yes ($3\text{ s} + 4\text{ s} \le 10\text{ s}$) |
| **4 (Retry 3)** | Failed | $4000\text{ ms}$ ($4\text{ s}$) | $7000\text{ ms}$ ($7\text{ s}$) | $4000 \times 2.0 = 8000\text{ ms}$ ($8\text{ s}$) | **No** ($7\text{ s} + 8\text{ s} = 15\text{ s} > 10\text{ s}$) |
| **Exhaustion / Recovery** | Recovered | — | $7000\text{ ms}$ | `DeadLetterPublishingRecoverer` | Routes to `.DLT` topic |

> **Summary**: The listener executes **1 initial attempt + 3 retries = 4 total execution attempts** over approximately **7 seconds**. If still failing, `DeadLetterPublishingRecoverer` automatically publishes the message to `<topic>.DLT` for inspection/remediation.

---

## 3. Consumer Configuration & Single-Record Processing

### Configuration Location
- **Files**:
  - [`KafkaConsumerConfiguration.java`](src/main/java/com/serjnn/ProductService/kafka/consumer/KafkaConsumerConfiguration.java)
  - [`KafkaConsumerService.java`](src/main/java/com/serjnn/ProductService/kafka/consumer/KafkaConsumerService.java)
  - [`application.yaml`](src/main/resources/application.yaml)

### Consumer Settings & Explanations:

| Property | Default / Configured Value | Environment Variable | Purpose |
| :--- | :--- | :--- | :--- |
| `bootstrap-servers` | `localhost:9092` | `KAFKA_BOOTSTRAP_SERVERS` | Kafka cluster address |
| `group-id` | `first_product_group` | `KAFKA_CONSUMER_GROUP_ID` | Consumer group identifier |
| `auto-offset-reset` | `earliest` | `KAFKA_CONSUMER_AUTO_OFFSET_RESET` | Read from beginning if no committed offset exists |
| `enable-auto-commit` | `false` | `KAFKA_CONSUMER_ENABLE_AUTO_COMMIT` | Disables background auto-commit to let Spring container manage commits |
| `auto-commit-interval`| `100` ms | `KAFKA_CONSUMER_AUTO_COMMIT_INTERVAL` | Inactive because auto commit is disabled |
| `max-poll-records` | `50` | `KAFKA_CONSUMER_MAX_POLL_RECORDS` | Maximum records returned per `poll()` loop |
| `trusted-packages` | `*` | `KAFKA_CONSUMER_TRUSTED_PACKAGES` | JSON deserialization security whitelist |
| `value-default-type` | `com.serjnn.ProductService.dtos.DiscountChangesDto` | `KAFKA_CONSUMER_VALUE_DEFAULT_TYPE` | Target DTO class for deserialization |

### Deserialization & Listener Semantics:
- **Single-Record Processing**: `factory.setBatchListener(false)` and `setAckMode(ContainerProperties.AckMode.RECORD)` ensure per-record isolation, preventing partial-batch failure and duplicate downstream notifications.
- **Error-Handling Deserializers**:
  ```java
  new DefaultKafkaConsumerFactory<>(
      props,
      new ErrorHandlingDeserializer<>(new StringDeserializer()),
      new ErrorHandlingDeserializer<>(jsonDeserializer)
  );
  ```
- **Type Header Decoupling**:
  ```java
  props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
  ```
- **Metrics Attachment**: `factory.addListener(new MicrometerConsumerListener<>(meterRegistry))` tracks fetch latencies and consumer metrics.

---

## 4. Producer Configuration & Notification Delivery

### Configuration Location
- **Files**:
  - [`KafkaProducerConfiguration.java`](src/main/java/com/serjnn/ProductService/kafka/producer/KafkaProducerConfiguration.java)
  - [`KafkaSender.java`](src/main/java/com/serjnn/ProductService/kafka/producer/KafkaSender.java)
  - [`application.yaml`](src/main/resources/application.yaml)

### Producer Settings & Tuning:

| Property | Value | Purpose |
| :--- | :--- | :--- |
| `acks` | `all` (`-1`) | Highest durability: leader waits for all in-sync replicas before acknowledging |
| `enable.idempotence` | `true` | Exactly-once semantics per producer session, preventing duplicate messages on network retry |
| `compression.type` | `snappy` | Compresses payload blocks by 60–80%, reducing network I/O and broker disk usage |
| `linger.ms` | `10` | Delays send by up to 10ms to allow record batching under high notification load |
| `batch.size` | `32768` (32 KB) | Batches records up to 32KB per partition buffer |
| `key.serializer` | `StringSerializer` | Serializes `productId` as `String` key |
| `value.serializer`| `JsonSerializer` | Serializes `DiscountNotification` to JSON |

### Topic Definition:
- **Topic Bean**: `NewTopic discountNotifTopic()`
- **Topic Name**: `${app.kafka.topic.discount-notifications:discountNotifTopic}`
- **Partitions**: `${app.kafka.topic.discount-notifications-partitions:3}`

### Partitioning & Async Handling in `KafkaSender`:
- **Message Key**: `String.valueOf(discountNotification.productId())`
  - Keying by `productId` guarantees that all notifications for a specific product land on the exact same partition, preserving per-product sequence.
- **Asynchronous Execution**:
  ```java
  kafkaTemplate.send(topicName, messageKey, discountNotification)
      .whenComplete((result, ex) -> {
          if (ex != null) {
              log.error("Failed to send discount notification to topic {}: {}", topicName, discountNotification, ex);
          } else {
              log.debug("Successfully sent discount notification to topic {}: {}", topicName, discountNotification);
          }
      });
  ```

---

## 5. Metrics, Tracing & Observability

ProductService is configured with comprehensive Kafka observability:

1. **Spring Kafka Micrometer Observation**:
   - `spring.kafka.listener.observation-enabled: true`
   - `spring.kafka.template.observation-enabled: true`
2. **Micrometer Listeners**:
   - `MicrometerConsumerListener` attached to consumer factory.
   - `MicrometerProducerListener` attached to producer factory.
3. **Latency Histograms** enabled in `application.yaml`:
   - `kafka.consumer.fetch.manager.fetch.latency: true`
   - `kafka.producer.request.latency: true`
4. **Distributed Tracing & Correlation**:
   - Tracing probability: `1.0` (100% sampling).
   - Trace IDs and Span IDs injected into logging pattern:
     ```yaml
     logging.pattern.level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
     ```
   - Zipkin Endpoint: `${ZIPKIN_URL:http://localhost:9411/api/v2/spans}`

---

## 6. Complete Environment Variables Reference

| Environment Variable | Property Path | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `KAFKA_BOOTSTRAP_SERVERS` | `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker list |
| `KAFKA_CONSUMER_GROUP_ID` | `spring.kafka.consumer.group-id` | `first_product_group` | Consumer group ID |
| `KAFKA_CONSUMER_AUTO_OFFSET_RESET` | `spring.kafka.consumer.auto-offset-reset` | `earliest` | Offset reset strategy |
| `KAFKA_CONSUMER_ENABLE_AUTO_COMMIT` | `spring.kafka.consumer.enable-auto-commit` | `false` | Enable/disable auto commit |
| `KAFKA_CONSUMER_AUTO_COMMIT_INTERVAL`| `spring.kafka.consumer.auto-commit-interval`| `100` | Auto-commit interval in ms |
| `KAFKA_CONSUMER_MAX_POLL_RECORDS` | `spring.kafka.consumer.max-poll-records` | `50` | Max records per poll batch |
| `KAFKA_CONSUMER_VALUE_DEFAULT_TYPE` | `spring.kafka.consumer.value-default-type` | `com.serjnn.ProductService.dtos.DiscountChangesDto` | Fallback deserializer DTO type |
| `KAFKA_CONSUMER_TRUSTED_PACKAGES` | `spring.kafka.consumer.trusted-packages` | `*` | Allowed packages for JSON deserialization |
| `KAFKA_PRODUCER_ACKS` | `spring.kafka.producer.acks` | `all` | Required broker acknowledgements |
| `KAFKA_TOPIC_DISCOUNT_CHANGES` | `app.kafka.topic.discount-changes` | `discountChangesTopic` | Inbound topic for discount changes |
| `KAFKA_TOPIC_DISCOUNT_NOTIFICATIONS` | `app.kafka.topic.discount-notifications` | `discountNotifTopic` | Outbound topic for discount notifications |
| `KAFKA_TOPIC_DISCOUNT_NOTIFICATIONS_PARTITIONS` | `app.kafka.topic.discount-notifications-partitions` | `3` | Partitions count for outbound topic |
