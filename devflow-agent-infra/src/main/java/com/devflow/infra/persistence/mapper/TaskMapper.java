package com.devflow.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.infra.persistence.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 按状态分组统计任务数量 — 一次查询替代多次 COUNT(*)
     * 返回每行的 key: status, value: cnt
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM task WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countByStatus();
}
