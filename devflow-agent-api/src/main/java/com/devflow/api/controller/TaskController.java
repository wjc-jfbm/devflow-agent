package com.devflow.api.controller;

import com.devflow.api.dto.ApprovalRequest;
import com.devflow.api.dto.TaskCreateRequest;
import com.devflow.api.dto.TaskProgressVO;
import com.devflow.api.service.TaskService;
import com.devflow.common.model.PageResult;
import com.devflow.common.model.R;
import com.devflow.infra.persistence.entity.Task;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 任务管理控制器
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Validated
public class TaskController {

    private final TaskService taskService;

    /**
     * 手动创建任务并触发工作流
     */
    @PostMapping
    public R<Task> createTask(@Valid @RequestBody TaskCreateRequest request) {
        return R.ok(taskService.createAndStartTask(request));
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    public R<Task> getTask(@PathVariable Long id) {
        return R.ok(taskService.getTask(id));
    }

    /**
     * 获取任务列表
     */
    @GetMapping
    public R<PageResult<Task>> listTasks(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(taskService.listTasks(projectId, page, size));
    }

    /**
     * 获取任务进度
     */
    @GetMapping("/{id}/progress")
    public R<TaskProgressVO> getTaskProgress(@PathVariable Long id) {
        return R.ok(taskService.getTaskProgress(id));
    }

    /**
     * 审批任务（人工审批节点）
     */
    @PostMapping("/{id}/approve")
    public R<Void> approveTask(@PathVariable Long id, @Valid @RequestBody ApprovalRequest request) {
        taskService.approveTask(id, request.getApprover(), request.getComment(), request.getAction());
        return R.ok();
    }

    /**
     * 重试失败的任务
     */
    @PostMapping("/{id}/retry")
    public R<Task> retryTask(@PathVariable Long id) {
        return R.ok(taskService.retryTask(id));
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return R.ok();
    }
}
