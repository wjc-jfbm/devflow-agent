-- DevFlow Agent 数据库初始化脚本
CREATE DATABASE IF NOT EXISTS devflow_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE devflow_agent;

-- 项目表
CREATE TABLE IF NOT EXISTS t_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(128) NOT NULL COMMENT '项目名称',
    repo_url VARCHAR(512) NOT NULL COMMENT '仓库地址',
    language VARCHAR(32) DEFAULT 'Java' COMMENT '编程语言',
    framework VARCHAR(64) DEFAULT 'Spring Boot' COMMENT '框架',
    description VARCHAR(1024) COMMENT '项目描述',
    github_token VARCHAR(256) COMMENT 'GitHub Token(加密)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- 任务表（一次 Issue→PR 的完整流程）
CREATE TABLE IF NOT EXISTS t_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    issue_url VARCHAR(512) COMMENT 'Issue URL',
    issue_number INT COMMENT 'Issue 编号',
    issue_title VARCHAR(256) COMMENT 'Issue 标题',
    issue_body TEXT COMMENT 'Issue 内容',
    status VARCHAR(32) DEFAULT 'PENDING' COMMENT '任务状态: PENDING/RUNNING/PAUSED/COMPLETED/FAILED',
    current_phase VARCHAR(32) DEFAULT 'INIT' COMMENT '当前阶段: INIT/REQUIREMENTS/ARCHITECT/CODING/TESTING/REVIEW/PR_CREATION',
    priority INT DEFAULT 5 COMMENT '优先级(1-10)',
    pr_url VARCHAR(512) COMMENT 'PR URL',
    pr_branch VARCHAR(128) COMMENT 'PR 分支名',
    error_msg TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_project_id (project_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- Agent 执行记录
CREATE TABLE IF NOT EXISTS t_agent_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    agent_type VARCHAR(32) NOT NULL COMMENT 'Agent类型: REQUIREMENTS/ARCHITECT/CODER/TESTER/REVIEWER',
    input TEXT COMMENT '输入内容',
    output TEXT COMMENT '输出内容',
    status VARCHAR(32) DEFAULT 'PENDING' COMMENT '执行状态: PENDING/RUNNING/COMPLETED/FAILED',
    tokens_used INT DEFAULT 0 COMMENT '消耗Token数',
    duration_ms BIGINT DEFAULT 0 COMMENT '执行耗时(毫秒)',
    error_msg TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_agent_type (agent_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent执行记录';

-- 代码变更记录
CREATE TABLE IF NOT EXISTS t_code_change (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    file_path VARCHAR(512) NOT NULL COMMENT '文件路径',
    change_type VARCHAR(16) DEFAULT 'MODIFY' COMMENT '变更类型: ADD/MODIFY/DELETE',
    original_content LONGTEXT COMMENT '原始内容',
    new_content LONGTEXT COMMENT '新内容',
    commit_sha VARCHAR(64) COMMENT 'Commit SHA',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码变更记录';

-- 审查报告
CREATE TABLE IF NOT EXISTS t_review_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    agent_type VARCHAR(32) NOT NULL COMMENT '审查类型: SECURITY/PERFORMANCE/CONVENTION',
    severity VARCHAR(16) DEFAULT 'INFO' COMMENT '严重级别: CRITICAL/WARNING/INFO',
    file_path VARCHAR(512) COMMENT '文件路径',
    line_number INT COMMENT '行号',
    category VARCHAR(64) COMMENT '问题分类',
    message TEXT COMMENT '问题描述',
    suggestion TEXT COMMENT '修复建议',
    is_fixed TINYINT DEFAULT 0 COMMENT '是否已修复',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查报告';

-- 人工审批记录
CREATE TABLE IF NOT EXISTS t_approval (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    phase VARCHAR(32) NOT NULL COMMENT '审批阶段: ARCHITECT/REVIEW',
    status VARCHAR(16) DEFAULT 'PENDING' COMMENT '审批状态: PENDING/APPROVED/REJECTED',
    approver VARCHAR(64) COMMENT '审批人',
    comment TEXT COMMENT '审批意见',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工审批记录';
