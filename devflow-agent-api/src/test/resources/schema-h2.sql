CREATE TABLE IF NOT EXISTS t_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    repo_url VARCHAR(512) NOT NULL,
    language VARCHAR(32) DEFAULT 'Java',
    framework VARCHAR(64) DEFAULT 'Spring Boot',
    description VARCHAR(1024),
    github_token VARCHAR(256),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    issue_url VARCHAR(512),
    issue_number INT,
    issue_title VARCHAR(256),
    issue_body TEXT,
    status VARCHAR(32) DEFAULT 'PENDING',
    current_phase VARCHAR(32) DEFAULT 'INIT',
    priority INT DEFAULT 5,
    pr_url VARCHAR(512),
    pr_branch VARCHAR(128),
    error_msg TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_task_project_id ON t_task(project_id);
CREATE INDEX IF NOT EXISTS idx_task_status ON t_task(status);

CREATE TABLE IF NOT EXISTS t_agent_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    agent_type VARCHAR(32) NOT NULL,
    input TEXT,
    output TEXT,
    status VARCHAR(32) DEFAULT 'PENDING',
    tokens_used INT DEFAULT 0,
    duration_ms BIGINT DEFAULT 0,
    error_msg TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_agent_task_id ON t_agent_execution(task_id);
CREATE INDEX IF NOT EXISTS idx_agent_type ON t_agent_execution(agent_type);

CREATE TABLE IF NOT EXISTS t_code_change (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    change_type VARCHAR(16) DEFAULT 'MODIFY',
    original_content LONGTEXT,
    new_content LONGTEXT,
    commit_sha VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_code_task_id ON t_code_change(task_id);

CREATE TABLE IF NOT EXISTS t_review_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    agent_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) DEFAULT 'INFO',
    file_path VARCHAR(512),
    line_number INT,
    category VARCHAR(64),
    message TEXT,
    suggestion TEXT,
    is_fixed TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_review_task_id ON t_review_report(task_id);

CREATE TABLE IF NOT EXISTS t_approval (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    phase VARCHAR(32) NOT NULL,
    status VARCHAR(16) DEFAULT 'PENDING',
    approver VARCHAR(64),
    comment TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_approval_task_id ON t_approval(task_id);