/*
 * Copyright (C) 2003-2023 eXo Platform SAS
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
package org.exoplatform.jcr.upgrade;

import javax.jcr.Item;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import java.util.ArrayList;
import java.util.List;

/**
 * plugin will be executed in order to move folders under spaces drives
 * from an original path to a destination path as provided in the configuration
 */
public class MoveNodesUpgradePlugin extends UpgradeProductPlugin {

  private static final Log       log                     = ExoLogger.getLogger(MoveNodesUpgradePlugin.class.getName());

  private static final String    ORIGIN_PATH      = "origin-folder-path";

  private static final String    DESTINATION_PATH = "destination-folder-path";
  private static final String    FOLDERS_TO_REMOVE = "folders-to-remove";

  private static final int       SPACES_PAGE_SIZE  = 2;

  private final SpaceService     spaceService;

  private RepositoryService      repositoryService;

  private SessionProviderService sessionProviderService;

  private String                 originPath;

  private String                 destinationPath;

  private List<String>           foldersToRemove   = new ArrayList<>();


  public MoveNodesUpgradePlugin(InitParams initParams,
                                SpaceService spaceService,
                                RepositoryService repositoryService,
                                SessionProviderService sessionProviderService) {
    super(initParams);
    if(initParams.getValueParam(ORIGIN_PATH) != null && StringUtils.isNotBlank(initParams.getValueParam(ORIGIN_PATH).getValue())) {
      this.originPath = initParams.getValueParam(ORIGIN_PATH).getValue();
    }
    if(initParams.getValueParam(ORIGIN_PATH) != null && StringUtils.isNotBlank(initParams.getValueParam(ORIGIN_PATH).getValue())) {
      this.destinationPath = initParams.getValueParam(DESTINATION_PATH).getValue();
    }
    if(initParams.getValuesParam(FOLDERS_TO_REMOVE) != null && !initParams.getValuesParam(FOLDERS_TO_REMOVE).getValues().isEmpty()) {
      this.foldersToRemove = initParams.getValuesParam(FOLDERS_TO_REMOVE).getValues();
    }

    this.spaceService = spaceService;
    this.repositoryService = repositoryService;
    this.sessionProviderService = sessionProviderService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    if(StringUtils.isBlank(originPath) || StringUtils.isBlank(destinationPath)) {
      log.warn("Invalid parameter was provided for {}, this upgrade plugin will be ignored", StringUtils.isBlank(originPath) ? "'Origin path'":"'Destination path'");
      return;
    }
    long startupTime = System.currentTimeMillis();
    int movedFoldersCount = 0;
    log.info("Start upgrade : Moving of folder from {} to {}", originPath, destinationPath);

    SessionProvider sessionProvider = null;
    RequestLifeCycle.begin(PortalContainer.getInstance());
    try {
      sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      Session session = sessionProvider.getSession(
              repositoryService.getCurrentRepository()
                      .getConfiguration()
                      .getDefaultWorkspaceName(),
              repositoryService.getCurrentRepository());
      ListAccess<Space> spaces = spaceService.getAllSpacesWithListAccess();
      int index = 0;
      while(index <= spaces.getSize()) {
        Space[] spaceArray = spaces.load(index, SPACES_PAGE_SIZE);
        for (Space space : spaceArray) {
          String originFolderPath = "/Groups" + space.getGroupId() + originPath;
          String destinationFolderPath = "/Groups" + space.getGroupId() + destinationPath;
          try {
            Item originFolderNode = session.getItem(originFolderPath);
            if (originFolderNode != null) {
              session.move(originFolderPath, destinationFolderPath);
              movedFoldersCount++;
            }
          } catch(RepositoryException e) {
            if (log.isDebugEnabled()) {
              log.warn("Folder {} to move was not found, ignoring it", originFolderPath, e);
            } else {
              log.warn("Folder {} to move was not found, ignoring it", originFolderPath);
            }
          }
          // remove unnecessary folders if defined in init params
          if(!foldersToRemove.isEmpty()) {
            for(String folderToRemove : foldersToRemove) {
              folderToRemove = "/Groups" + space.getGroupId() + folderToRemove;
              try {
                Item folderToRemoveNode = session.getItem(folderToRemove);
                if (folderToRemoveNode != null) {
                  folderToRemoveNode.remove();
                }
              } catch (RepositoryException re) {
                if(log.isDebugEnabled()) {
                  log.warn("Folder {} to delete was not found, ignoring it", folderToRemove, re);
                } else {
                  log.warn("Folder {} to delete was not found, ignoring it", folderToRemove);
                }
              }
            }
          }
        }

        session.save();
        index = index + SPACES_PAGE_SIZE;
      }

      log.info("End Moving of '{}' folders. It took {} ms",
              movedFoldersCount,
              (System.currentTimeMillis() - startupTime));
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("An unexpected error occurs when moving folders:", e);
      }
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
      RequestLifeCycle.end();
    }
  }

}
