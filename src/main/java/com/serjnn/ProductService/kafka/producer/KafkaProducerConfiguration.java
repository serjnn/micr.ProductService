package com.serjnn.ProductService.kafka.producer;

import com.serjnn.ProductService.config.AppKafkaProperties;
import com.serjnn.ProductService.config.KafkaConfigProperties;
import com.serjnn.ProductService.dtos.DiscountNotification;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerProducerListener;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class KafkaProducerConfiguration {

    private final KafkaConfigProperties kafkaConfigProperties;
    private final AppKafkaProperties appKafkaProperties;
    private final MeterRegistry meterRegistry;

    @Bean
    public NewTopic discountNotifTopic() {
        return TopicBuilder.name(appKafkaProperties.getTopic().getDiscountNotifications())
                .partitions(appKafkaProperties.getTopic().getDiscountNotificationsPartitions())
                .build();
    }

    @Bean
    public ProducerFactory<String, DiscountNotification> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigProperties.getBootstrapServers());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, kafkaConfigProperties.getProducer().getAcks());
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 32 * 1024);

        DefaultKafkaProducerFactory<String, DiscountNotification> factory = new DefaultKafkaProducerFactory<>(configProps);
        factory.addListener(new MicrometerProducerListener<>(meterRegistry));
        return factory;
    }

    @Bean
    public KafkaTemplate<String, DiscountNotification> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
