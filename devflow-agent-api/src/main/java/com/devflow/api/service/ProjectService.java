package com.devflow.api.service;

import com.devflow.common.exception.BusinessException;
import com.devflow.infra.persistence.entity.Project;
import com.devflow.infra.persistence.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    public Project createProject(Project project) {
        if (project == null) {
            throw new BusinessException("Project cannot be null");
        }
        if (project.getRepoUrl() == null || project.getRepoUrl().isBlank()) {
            throw new BusinessException(400, "Repository URL is required");
        }
        log.info("Creating project: {}", project.getName());
        projectRepository.save(project);
        return project;
    }

    public Project getProject(Long id) {
        log.debug("Getting project: id={}", id);
        Project project = projectRepository.getById(id);
        if (project == null) {
            log.warn("Project not found: id={}", id);
            throw new BusinessException(404, "Project not found: " + id);
        }
        return project;
    }

    public List<Project> listProjects() {
        log.debug("Listing all projects");
        return projectRepository.list();
    }

    public Project updateProject(Project project) {
        if (project == null || project.getId() == null) {
            throw new BusinessException(400, "Project ID is required");
        }
        Project existing = projectRepository.getById(project.getId());
        if (existing == null) {
            throw new BusinessException(404, "Project not found: " + project.getId());
        }
        log.info("Updating project: id={}, name={}", project.getId(), project.getName());
        projectRepository.updateById(project);
        return project;
    }

    public void deleteProject(Long id) {
        Project project = projectRepository.getById(id);
        if (project == null) {
            throw new BusinessException(404, "Project not found: " + id);
        }
        log.info("Deleting project: id={}, name={}", id, project.getName());
        projectRepository.removeById(id);
    }
}