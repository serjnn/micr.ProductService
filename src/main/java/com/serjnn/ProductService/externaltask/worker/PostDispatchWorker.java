package com.serjnn.ProductService.externaltask.worker;

import com.serjnn.ProductService.externaltask.client.ExternalServiceClient;
import com.serjnn.ProductService.externaltask.config.ExternalTaskProperties;
import com.serjnn.ProductService.externaltask.dto.ExternalServiceResponse;
import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;
import com.serjnn.ProductService.externaltask.models.ExternalTask;
import com.serjnn.ProductService.externaltask.repo.ExternalTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostDispatchWorker {

    private final ExternalTaskRepository taskRepository;
    private final ExternalServiceClient externalClient;
    private final ExternalTaskProperties properties;

    @Scheduled(fixedDelayString = "${app.external-task.dispatch-delay-ms:2000}")
    @Transactional
    public void dispatchPendingTasks() {
        List<ExternalTask> tasks = taskRepository.lockBatchForProcessing(ExternalTaskState.PENDING, properties.batchSize());
        if (tasks.isEmpty()) {
            return;
        }
        log.info("PostDispatchWorker locked {} PENDING tasks for dispatch", tasks.size());

        for (ExternalTask task : tasks) {
            try {
                ExternalServiceResponse response = externalClient.postTask(
                        task.businessKey(),
                        task.taskType(),
                        task.payload()
                );
                String resourceId = response != null ? response.externalResourceId() : null;
                taskRepository.markAsSucceeded(task.id(), resourceId);
                log.info("Task id={} businessKey={} successfully dispatched and marked SUCCEEDED. Remote ID: {}",
                        task.id(), task.businessKey(), resourceId);
            } catch (ResourceAccessException | HttpServerErrorException e) {
                // Network timeout, connection refused/reset, 502/503/504 gateway timeout -> Uncertain state
                log.warn("Transient network error / timeout posting task id={} businessKey={}: {}. Moving to FAILED_CHECK_NEEDED.",
                        task.id(), task.businessKey(), e.getMessage());
                Instant nextRetry = Instant.now().plusSeconds(properties.auditDelaySeconds());
                taskRepository.markAsFailedCheckNeeded(task.id(), e.getMessage(), nextRetry);
            } catch (HttpClientErrorException e) {
                // 4xx client errors (e.g. 400 Bad Request, 422 Unprocessable) -> Non-recoverable
                log.error("Non-recoverable client error ({}) posting task id={} businessKey={}: {}. Marking FATAL_FAILED.",
                        e.getStatusCode(), task.id(), task.businessKey(), e.getMessage());
                taskRepository.markAsFatalFailed(task.id(), e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error posting task id={} businessKey={}: {}. Moving to FAILED_CHECK_NEEDED.",
                        task.id(), task.businessKey(), e.getMessage(), e);
                Instant nextRetry = Instant.now().plusSeconds(properties.auditDelaySeconds());
                taskRepository.markAsFailedCheckNeeded(task.id(), e.getMessage(), nextRetry);
            }
        }
    }
}
