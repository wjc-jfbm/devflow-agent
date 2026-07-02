package com.devflow.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.infra.persistence.entity.ReviewReportEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewReportMapper extends BaseMapper<ReviewReportEntity> {
}
