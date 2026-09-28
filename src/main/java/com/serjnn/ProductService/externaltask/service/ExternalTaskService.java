package com.serjnn.ProductService.externaltask.service;

import com.serjnn.ProductService.externaltask.config.ExternalTaskProperties;
import com.serjnn.ProductService.externaltask.dto.CreateExternalTaskRequest;
import com.serjnn.ProductService.externaltask.dto.ExternalTaskResponse;
import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;
import com.serjnn.ProductService.externaltask.exception.ExternalTaskNotFoundException;
import com.serjnn.ProductService.externaltask.models.ExternalTask;
import com.serjnn.ProductService.externaltask.repo.ExternalTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalTaskService {

    private final ExternalTaskRepository taskRepository;
    private final ExternalTaskProperties properties;

    @Transactional
    public ExternalTaskResponse submitTask(CreateExternalTaskRequest request) {
        UUID businessKey = UUID.randomUUID();
        int maxRetries = request.maxRetries() != null ? request.maxRetries() : properties.defaultMaxRetries();
        log.info("Submitting new external task: businessKey={}, type={}", businessKey, request.taskType());

        ExternalTask task = ExternalTask.newPendingTask(businessKey, request.taskType(), request.payload(), maxRetries);
        Long id = taskRepository.save(task);

        return ExternalTaskResponse.from(taskRepository.findById(id).orElseThrow(() -> new ExternalTaskNotFoundException(id)));
    }

    @Transactional
    public ExternalTaskResponse submitTask(UUID businessKey, String taskType, String payload, Integer maxRetries) {
        int retries = maxRetries != null ? maxRetries : properties.defaultMaxRetries();
        log.info("Submitting new external task with explicit key: businessKey={}, type={}", businessKey, taskType);

        ExternalTask task = ExternalTask.newPendingTask(businessKey, taskType, payload, retries);
        Long id = taskRepository.save(task);

        return ExternalTaskResponse.from(taskRepository.findById(id).orElseThrow(() -> new ExternalTaskNotFoundException(id)));
    }

    public ExternalTaskResponse getTaskByBusinessKey(UUID businessKey) {
        return taskRepository.findByBusinessKey(businessKey)
                .map(ExternalTaskResponse::from)
                .orElseThrow(() -> new ExternalTaskNotFoundException(businessKey));
    }

    public ExternalTaskResponse getTaskById(Long id) {
        return taskRepository.findById(id)
                .map(ExternalTaskResponse::from)
                .orElseThrow(() -> new ExternalTaskNotFoundException(id));
    }

    public Slice<ExternalTaskResponse> getTasks(ExternalTaskState state, Pageable pageable) {
        if (state != null) {
            return taskRepository.findAllByState(state, pageable).map(ExternalTaskResponse::from);
        }
        return taskRepository.findAll(pageable).map(ExternalTaskResponse::from);
    }
}
