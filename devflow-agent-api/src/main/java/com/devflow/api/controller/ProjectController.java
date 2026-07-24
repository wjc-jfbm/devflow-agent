package com.devflow.api.controller;

import com.devflow.api.dto.ProjectCreateRequest;
import com.devflow.common.model.PageResult;
import com.devflow.common.model.R;
import com.devflow.infra.persistence.entity.Project;
import com.devflow.api.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 项目管理控制器
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Validated
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public R<Project> createProject(@Valid @RequestBody ProjectCreateRequest request) {
        Project project = new Project();
        project.setName(request.getName());
        project.setRepoUrl(request.getRepoUrl());
        project.setLanguage(request.getLanguage());
        project.setFramework(request.getFramework());
        project.setDescription(request.getDescription());
        project.setGithubToken(request.getGithubToken());
        return R.ok(projectService.createProject(project));
    }

    @GetMapping("/{id}")
    public R<Project> getProject(@PathVariable Long id) {
        return R.ok(projectService.getProject(id));
    }

    @GetMapping
    public R<PageResult<Project>> listProjects(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(projectService.listProjects(page, size));
    }

    @PutMapping("/{id}")
    public R<Project> updateProject(@PathVariable Long id, @Valid @RequestBody ProjectCreateRequest request) {
        Project project = projectService.getProject(id);
        if (project == null) {
            return R.fail("Project not found");
        }
        project.setName(request.getName());
        project.setRepoUrl(request.getRepoUrl());
        project.setLanguage(request.getLanguage());
        project.setFramework(request.getFramework());
        project.setDescription(request.getDescription());
        project.setGithubToken(request.getGithubToken());
        return R.ok(projectService.updateProject(project));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return R.ok();
    }
}
