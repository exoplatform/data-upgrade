/*
 * Copyright (C) 2003-2024 eXo Platform SAS
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

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;

/**
 * plugin will be executed in order to update recordings existing in users drive by adding delete permission
 */
public class RecordingsPermissionsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log log = ExoLogger.getLogger(RecordingsPermissionsUpgradePlugin.class.getName());

  private OrganizationService organizationService;

  private RepositoryService repositoryService;

  private SessionProviderService sessionProviderService;

  public RecordingsPermissionsUpgradePlugin(InitParams initParams,
                                            OrganizationService organizationService,
                                            RepositoryService repositoryService,
                                            SessionProviderService sessionProviderService) {
    super(initParams);
    this.organizationService = organizationService;
    this.repositoryService = repositoryService;
    this.sessionProviderService = sessionProviderService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    int updatedRecordingsCount = 0;
    long totalRecordingsCount = 0;
    int totalBadPermissionRecordingsCount = 0;
    log.info("Start upgrade : Updating recordings permissions");

    SessionProvider sessionProvider = null;
    RequestLifeCycle.begin(PortalContainer.getInstance());
    try {
      sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      Session session = sessionProvider.getSession(
              repositoryService.getCurrentRepository()
                      .getConfiguration()
                      .getDefaultWorkspaceName(),
              repositoryService.getCurrentRepository());
      Node users = (Node) session.getItem("/Users");
      String statement =
              "SELECT * FROM nt:base WHERE jcr:path LIKE '%/Private/recordings/%' AND (jcr:primaryType='exo:symlink' OR jcr:primaryType='nt:file')";
      Query jcrQuery = users.getSession().getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
      QueryResult queryResult = jcrQuery.execute();
      NodeIterator nodeIterator = queryResult.getNodes();
      totalRecordingsCount = nodeIterator.getSize();
      log.info("Total number of recordings: {}", totalRecordingsCount);
      while (nodeIterator.hasNext()) {
        Node node = nodeIterator.nextNode();
        String nodePath = node.getPath();
        String[] pathParts = nodePath.substring(0, nodePath.indexOf("/Private")).split("/");
        String userName = pathParts[pathParts.length - 1];
        User user = organizationService.getUserHandler().findUserByName(userName);
        if (user != null) {
          boolean haveDeletePermission = ((ExtendedNode) node).getACL()
                  .getPermissionEntries()
                  .stream()
                  .anyMatch(accessControlEntry -> accessControlEntry.getIdentity()
                          .equals(userName)
                          && accessControlEntry.getPermission().equals("remove"));
          if (!haveDeletePermission) {
            totalBadPermissionRecordingsCount += 1;
            try {
              if (node.canAddMixin("exo:privilegeable")) {
                node.addMixin("exo:privilegeable");
              }
              ((ExtendedNode) node).setPermission(userName,
                      new String[]{PermissionType.READ, PermissionType.ADD_NODE,
                              PermissionType.SET_PROPERTY, PermissionType.REMOVE});
              node.save();
              updatedRecordingsCount += 1;
              log.info("{} Recording permissions" +
                              " updated.",
                      updatedRecordingsCount);
            } catch (Exception e) {
              if (log.isErrorEnabled()) {
                log.error("An unexpected error occurs when updating recording {} permissions:", node.getPath(), e);
              }
            }
          }
        }
      }
      log.info("End updating permissions of {}/{} recordings with bad permissions,  total number of checked records = {}. It took {} ms",
              updatedRecordingsCount,
              totalBadPermissionRecordingsCount,
              totalRecordingsCount,
              (System.currentTimeMillis() - startupTime));
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("An unexpected error occurs when updating recordings permissions:", e);
      }
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
      RequestLifeCycle.end();
    }
  }

}
