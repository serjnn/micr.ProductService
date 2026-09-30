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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetReconciliationWorker {

    private final ExternalTaskRepository taskRepository;
    private final ExternalServiceClient externalClient;
    private final ExternalTaskProperties properties;

    @Scheduled(fixedDelayString = "${app.external-task.reconcile-delay-ms:3000}")
    public void reconcileUncertainTasks() {
        List<ExternalTask> tasks = taskRepository.claimBatchForProcessing(
                ExternalTaskState.FAILED_CHECK_NEEDED,
                ExternalTaskState.IN_FLIGHT_AUDIT,
                properties.batchSize()
        );

        if (tasks.isEmpty()) {
            return;
        }
        log.info("GetReconciliationWorker claimed {} FAILED_CHECK_NEEDED tasks for audit", tasks.size());

        for (ExternalTask task : tasks) {
            try {
                Optional<ExternalServiceResponse> responseOpt = externalClient.checkTaskStatus(task.businessKey());

                if (responseOpt.isPresent()) {
                    ExternalServiceResponse response = responseOpt.get();
                    String resourceId = response.externalResourceId();
                    taskRepository.markAsSucceeded(task.id(), resourceId);
                    log.info("Audit SUCCESS: Remote record confirmed for task id={} businessKey={}. Marked SUCCEEDED with remote ID: {}",
                            task.id(), task.businessKey(), resourceId);
                } else {
                    // 404 Not Found: Remote system NEVER received or stored our previous POST.
                    // Safe to reset to PENDING and retry the POST.
                    if (task.retryCount() < task.maxRetries()) {
                        int nextRetryCount = task.retryCount() + 1;
                        long backoffSeconds = (long) Math.pow(properties.baseBackoffSeconds(), nextRetryCount);
                        Instant nextRetryAt = Instant.now().plusSeconds(backoffSeconds);
                        taskRepository.markAsPendingForRetry(task.id(), nextRetryCount, nextRetryAt);
                        log.info("Audit NOT FOUND: Task id={} businessKey={} not found remotely. Reset to PENDING (retry {}/{}, backoff {}s)",
                                task.id(), task.businessKey(), nextRetryCount, task.maxRetries(), backoffSeconds);
                    } else {
                        String errorMessage = String.format("Exceeded max retries (%d) during reconciliation", task.maxRetries());
                        taskRepository.markAsFatalFailed(task.id(), errorMessage);
                        log.error("Audit EXHAUSTED: Task id={} businessKey={} exceeded max retries. Marked FATAL_FAILED.",
                                task.id(), task.businessKey());
                    }
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    // 429 Rate limited during audit -> reschedule audit with backoff
                    log.warn("Rate limited (429) auditing task id={} businessKey={}. Rescheduling audit.",
                            task.id(), task.businessKey());
                    Instant nextRetry = Instant.now().plusSeconds(properties.auditDelaySeconds());
                    taskRepository.markAsFailedCheckNeeded(task.id(), "Audit rate-limited (429): " + e.getMessage(), nextRetry);
                } else {
                    // Permanent 4xx client errors (400, 401, 403, 404 is handled in checkTaskStatus)
                    log.error("Non-recoverable client error ({}) during audit of task id={} businessKey={}: {}. Marking FATAL_FAILED.",
                            e.getStatusCode(), task.id(), task.businessKey(), e.getMessage());
                    taskRepository.markAsFatalFailed(task.id(), "Non-recoverable audit client error: " + e.getMessage());
                }
            } catch (ResourceAccessException | HttpServerErrorException e) {
                // Transient network / 5xx server error on GET audit -> keep in FAILED_CHECK_NEEDED for next tick
                log.warn("Audit attempt failed for task id={} businessKey={}: {}. Retrying audit later.",
                        task.id(), task.businessKey(), e.getMessage());
                Instant nextRetry = Instant.now().plusSeconds(properties.auditDelaySeconds());
                taskRepository.markAsFailedCheckNeeded(task.id(), "Audit failed: " + e.getMessage(), nextRetry);
            } catch (Exception e) {
                log.error("Unexpected error during audit of task id={} businessKey={}: {}. Retrying audit.",
                        task.id(), task.businessKey(), e.getMessage(), e);
                Instant nextRetry = Instant.now().plusSeconds(properties.auditDelaySeconds());
                taskRepository.markAsFailedCheckNeeded(task.id(), "Audit unexpected error: " + e.getMessage(), nextRetry);
            }
        }
    }
}
