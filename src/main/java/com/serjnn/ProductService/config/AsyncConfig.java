package com.serjnn.ProductService.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@RequiredArgsConstructor
public class AsyncConfig {

    private final NotifierProperties notifierProperties;

    @Bean(name = "notifierTaskExecutor")
    public Executor notifierTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(notifierProperties.getCorePoolSize());
        executor.setMaxPoolSize(notifierProperties.getMaxPoolSize());
        executor.setQueueCapacity(notifierProperties.getQueueCapacity());
        executor.setThreadNamePrefix("notifier-async-");
        executor.initialize();
        return executor;
    }
}
