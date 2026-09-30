package com.serjnn.ProductService.externaltask.worker;

import com.serjnn.ProductService.externaltask.client.ExternalServiceClient;
import com.serjnn.ProductService.externaltask.config.ExternalTaskProperties;
import com.serjnn.ProductService.externaltask.dto.ExternalServiceResponse;
import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;
import com.serjnn.ProductService.externaltask.models.ExternalTask;
import com.serjnn.ProductService.externaltask.repo.ExternalTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
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
    public void dispatchPendingTasks() {
        // Recover any stale in-flight tasks from crashes/restarts older than 5 minutes
        taskRepository.recoverStaleInFlightTasks(Duration.ofMinutes(5));

        List<ExternalTask> tasks = taskRepository.claimBatchForProcessing(
                ExternalTaskState.PENDING,
                ExternalTaskState.IN_FLIGHT_POST,
                properties.batchSize()
        );

        if (tasks.isEmpty()) {
            return;
        }
        log.info("PostDispatchWorker claimed {} PENDING tasks for dispatch", tasks.size());

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
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    // 429 Too Many Requests -> transient throttling, backoff and retry
                    int nextRetryCount = task.retryCount() + 1;
                    if (nextRetryCount <= task.maxRetries()) {
                        long backoffSeconds = (long) Math.pow(properties.baseBackoffSeconds(), nextRetryCount);
                        Instant nextRetry = Instant.now().plusSeconds(backoffSeconds);
                        log.warn("Rate limited (429) posting task id={} businessKey={}. Backing off for {}s (retry {}/{})",
                                task.id(), task.businessKey(), backoffSeconds, nextRetryCount, task.maxRetries());
                        taskRepository.markAsPendingForRetry(task.id(), nextRetryCount, nextRetry);
                    } else {
                        log.error("Rate limit (429) retries exhausted for task id={} businessKey={}. Marking FATAL_FAILED.",
                                task.id(), task.businessKey());
                        taskRepository.markAsFatalFailed(task.id(), "Exceeded max retries on rate limiting: " + e.getMessage());
                    }
                } else if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    // 409 Conflict -> duplicate key / already processed on remote server, verify via audit
                    log.warn("Conflict (409) posting task id={} businessKey={}. Moving to FAILED_CHECK_NEEDED for audit.",
                            task.id(), task.businessKey());
                    Instant nextRetry = Instant.now().plusSeconds(properties.auditDelaySeconds());
                    taskRepository.markAsFailedCheckNeeded(task.id(), "Conflict (409): " + e.getMessage(), nextRetry);
                } else {
                    // Other 4xx client errors (e.g. 400 Bad Request, 422 Unprocessable) -> Non-recoverable
                    log.error("Non-recoverable client error ({}) posting task id={} businessKey={}: {}. Marking FATAL_FAILED.",
                            e.getStatusCode(), task.id(), task.businessKey(), e.getMessage());
                    taskRepository.markAsFatalFailed(task.id(), e.getMessage());
                }
            } catch (Exception e) {
                log.error("Unexpected error posting task id={} businessKey={}: {}. Moving to FAILED_CHECK_NEEDED.",
                        task.id(), task.businessKey(), e.getMessage(), e);
                Instant nextRetry = Instant.now().plusSeconds(properties.auditDelaySeconds());
                taskRepository.markAsFailedCheckNeeded(task.id(), e.getMessage(), nextRetry);
            }
        }
    }
}
