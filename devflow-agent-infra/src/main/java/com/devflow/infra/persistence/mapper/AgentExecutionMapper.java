package com.devflow.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.infra.persistence.entity.AgentExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface AgentExecutionMapper extends BaseMapper<AgentExecution> {

    /**
     * 聚合统计 Agent 执行数据 — 一次 SQL 替代全表拉取 + 内存流式计算
     * 返回 map 包含: totalCount, totalTokens, avgDurationMs
     */
    @Select("SELECT COUNT(*) AS totalCount, " +
            "COALESCE(SUM(tokens_used), 0) AS totalTokens, " +
            "COALESCE(AVG(duration_ms), 0) AS avgDurationMs " +
            "FROM agent_execution")
    Map<String, Object> getAggregatedStats();
}
