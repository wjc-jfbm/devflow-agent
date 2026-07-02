package com.devflow.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devflow.infra.persistence.entity.AgentExecution;
import com.devflow.infra.persistence.mapper.AgentExecutionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentExecutionRepository extends ServiceImpl<AgentExecutionMapper, AgentExecution> {

    public List<AgentExecution> findByTaskId(Long taskId) {
        return list(new LambdaQueryWrapper<AgentExecution>()
                .eq(AgentExecution::getTaskId, taskId)
                .orderByAsc(AgentExecution::getCreatedAt));
    }
}
