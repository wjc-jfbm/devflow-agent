package com.devflow.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devflow.infra.persistence.entity.Task;
import com.devflow.infra.persistence.mapper.TaskMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TaskRepository extends ServiceImpl<TaskMapper, Task> {

    public List<Task> findByProjectId(Long projectId) {
        return list(new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId));
    }

    public List<Task> findByStatus(String status) {
        return list(new LambdaQueryWrapper<Task>().eq(Task::getStatus, status));
    }
}
