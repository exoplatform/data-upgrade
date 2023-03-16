/*
 * Copyright (C) 2023 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */

package org.exoplatform.migration;

import java.util.*;

import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;

public class ProcessesPermissionsMigration extends UpgradeProductPlugin {

  private static final Log      log = ExoLogger.getExoLogger(ProcessesPermissionsMigration.class);

  private final PortalContainer container;

  private final WorkFlowDAO     workFlowDAO;

  private final SpaceService    spaceService;

  private final ProjectService  projectService;

  public ProcessesPermissionsMigration(PortalContainer container,
                                       WorkFlowDAO workFlowDAO,
                                       ProjectService projectService,
                                       SpaceService spaceService,
                                       InitParams initParams) {
    super(initParams);
    this.container = container;
    this.workFlowDAO = workFlowDAO;
    this.projectService = projectService;
    this.spaceService = spaceService;
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext previousUpgradePluginExecution) {

    int executionCount = previousUpgradePluginExecution == null ? 0 : previousUpgradePluginExecution.getExecutionCount();
    return !isExecuteOnlyOnce() || executionCount == 0;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    ExoContainerContext.setCurrentContainer(container);
    log.info("Start upgrade of processes permissions");
    boolean upgraded = false;
    boolean shouldUpgrade = false;
    List<WorkFlowEntity> workflowsToUpdate = workFlowDAO.findAll();
    if (workflowsToUpdate == null || workflowsToUpdate.isEmpty()) {
      log.info("No processes permissions to be upgraded.");
      return;
    }
    List<WorkFlowEntity> updatedWorkflows = new ArrayList();
    for (WorkFlowEntity workflowEntity : workflowsToUpdate) {
      if (workflowEntity.getManager() == null || workflowEntity.getManager().isEmpty()
          || workflowEntity.getParticipator() == null || workflowEntity.getParticipator().isEmpty()) {
        Space space = getProjectParentSpace(workflowEntity.getProjectId());
        if (space != null) {
          List<String> memberships = new LinkedList();
          memberships.add((new MembershipEntry(space.getGroupId(), "manager")).toString());
          memberships.add((new MembershipEntry(space.getGroupId(), "member")).toString());
          Set<String> managers = new HashSet<>(Arrays.asList(memberships.get(0)));
          Set<String> participators = new HashSet<>(Arrays.asList(memberships.get(1)));
          participators.addAll(managers);
          workflowEntity.setManager(managers);
          workflowEntity.setParticipator(participators);
          updatedWorkflows.add(workflowEntity);
          shouldUpgrade = true;
        }
      }
    }
    if (!updatedWorkflows.isEmpty()) {
      workFlowDAO.updateAll(updatedWorkflows);
      upgraded = true;
    }
    if (upgraded) {
      log.info("Processes permissions upgrade proceeded successfully. It took {} ms", (System.currentTimeMillis() - startupTime));
    } else {
      if (!shouldUpgrade) {
        log.info("No processes permissions to be upgraded");
      } else {
        throw new IllegalStateException("Processes permissions upgrade failed due to previous errors");
      }
    }
  }

  private Space getProjectParentSpace(Long projectId) {
    try {
      ProjectDto projectDto = projectService.getProject(projectId);
      boolean isProjectInSpace = projectDto.getManager().stream().anyMatch(manager -> manager.contains("/spaces/"));
      if (isProjectInSpace) {
        String participator = projectDto.getParticipator().iterator().next();
        String groupId = participator.substring(participator.indexOf(":") + 1);
        return spaceService.getSpaceByGroupId(groupId);
      }
    } catch (EntityNotFoundException e) {
      log.error("Project Not found", e);
    }
    return null;
  }

}
