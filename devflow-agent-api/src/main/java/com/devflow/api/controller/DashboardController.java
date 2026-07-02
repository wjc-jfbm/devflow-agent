package com.devflow.api.controller;

import com.devflow.api.dto.DashboardStatsVO;
import com.devflow.api.service.DashboardService;
import com.devflow.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘控制器
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取统计数据
     */
    @GetMapping("/stats")
    public R<DashboardStatsVO> getStats() {
        return R.ok(dashboardService.getStats());
    }
}
