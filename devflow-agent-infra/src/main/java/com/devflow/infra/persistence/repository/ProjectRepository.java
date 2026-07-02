package com.devflow.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devflow.infra.persistence.entity.Project;
import com.devflow.infra.persistence.mapper.ProjectMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProjectRepository extends ServiceImpl<ProjectMapper, Project> {

    /**
     * 根据仓库URL查找项目（模糊匹配，兼容 .git 后缀差异）
     */
    public Project findByRepoUrl(String repoUrl) {
        // 移除 .git 后缀和尾部斜杠做标准化匹配
        String normalizedUrl = repoUrl.replace(".git", "").replaceAll("/$", "");
        List<Project> projects = list(new LambdaQueryWrapper<Project>()
                .likeRight(Project::getRepoUrl, normalizedUrl.replace("https://github.com/", ""))
                .or()
                .likeRight(Project::getRepoUrl, normalizedUrl.replace("http://github.com/", "")));
        // 二次精确匹配（因为模糊查询可能有多条）
        for (Project p : projects) {
            String projectUrl = p.getRepoUrl().replace(".git", "").replaceAll("/$", "").toLowerCase();
            if (projectUrl.equals(normalizedUrl.toLowerCase())) {
                return p;
            }
        }
        return null;
    }
}
