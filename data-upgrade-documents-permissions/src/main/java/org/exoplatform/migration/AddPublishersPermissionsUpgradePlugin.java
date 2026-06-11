/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.migration;

import io.meeds.social.space.constant.SpaceMembershipStatus;
import jakarta.persistence.TypedQuery;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.services.jcr.access.PermissionType;
import io.meeds.common.ContainerTransactional;

import jakarta.persistence.EntityManager;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.util.*;

public class AddPublishersPermissionsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log       LOG                            = ExoLogger.getExoLogger(AddPublishersPermissionsUpgradePlugin.class);

  private static final String    DEFAULT_GROUPS_HOME_PATH       = "/Groups";

  public static final String     GROUPS_PATH_ALIAS              = "groupsPath";

  public static final String     DOCUMENTS_NODE                 = "Documents";

  private static String          groupsPath                     = null;

  private int                    migratedRedactionalSpacesCount = 0;

  private static final String    PLUGIN_NAME                    = "PublishersPermissionsUpgradePlugin";

  private static final String    PLUGIN_EXECUTED_KEY            = "permissionsUpgradeExecuted";

  private boolean                upgradeFailed                  = false;

  private SpaceService           spaceService;

  private SessionProviderService sessionProviderService;

  private RepositoryService      repositoryService;

  private EntityManagerService   entityManagerService;

  private NodeHierarchyCreator   nodeHierarchyCreator;

  private SettingService         settingService;

  public AddPublishersPermissionsUpgradePlugin(InitParams initParams,
                                               SpaceService spaceService,
                                               SessionProviderService sessionProviderService,
                                               RepositoryService repositoryService,
                                               EntityManagerService entityManagerService,
                                               NodeHierarchyCreator nodeHierarchyCreator,
                                               SettingService settingService) {
    super(initParams);
    this.spaceService = spaceService;
    this.sessionProviderService = sessionProviderService;
    this.repositoryService = repositoryService;
    this.entityManagerService = entityManagerService;
    this.nodeHierarchyCreator = nodeHierarchyCreator;
    this.settingService = settingService;
  }
  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    LOG.info("Start Upgrade of redactional spaces documents");
    SessionProvider sessionProvider = null;
    int notMigratedRedactionalSpacesCount = 0;
    int processedRedactionalSpacesCount = 0;
    int totalRedactionalSpacesCount = 0;
    try {
      ManageableRepository repository = repositoryService.getCurrentRepository();
      sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      Session session = sessionProvider.getSession("collaboration", repository);

      List<Long> spaceIds = getRedactionalSpaces();
      if (spaceIds.isEmpty()) {
        return;
      }
      totalRedactionalSpacesCount = spaceIds.size();
      LOG.info("Total number of redactional spaces to be migrated: {}", totalRedactionalSpacesCount);
      for (List<Long> spaceIdsChunk : ListUtils.partition(spaceIds, 10)) {
        int notMigratedRedactionalSpacesCountByTransaction = manageDocumentsPermissions(spaceIdsChunk, session);
        int processedRedactionalSpacesCountByTransaction = spaceIdsChunk.size();
        processedRedactionalSpacesCount += processedRedactionalSpacesCountByTransaction;
        migratedRedactionalSpacesCount += processedRedactionalSpacesCountByTransaction - notMigratedRedactionalSpacesCountByTransaction;
        notMigratedRedactionalSpacesCount += notMigratedRedactionalSpacesCountByTransaction;
        LOG.info("Redactional spaces documents migration progress: processed={}/{} succeeded={} error={}",
                processedRedactionalSpacesCount,
                totalRedactionalSpacesCount,
                migratedRedactionalSpacesCount,
                notMigratedRedactionalSpacesCount);
      }
    } catch (Exception e) {
      LOG.error("An error occurred when upgrading redactional spaces documents:", e);
      this.upgradeFailed = true;
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
    if (totalRedactionalSpacesCount == migratedRedactionalSpacesCount) {
      LOG.info("End redactional spaces documents migration successful migration: total={} succeeded={} error={}. It tooks {} ms.",
              totalRedactionalSpacesCount,
              migratedRedactionalSpacesCount,
              notMigratedRedactionalSpacesCount,
              (System.currentTimeMillis() - startupTime));
    } else {
      LOG.warn("End redactional spaces documents migration with some errors: total={} succeeded={} error={}. It tooks {} ms."
              + " The not migrated redactional spaces documents will be processed again next startup.",
              totalRedactionalSpacesCount,
              migratedRedactionalSpacesCount,
              notMigratedRedactionalSpacesCount,
              (System.currentTimeMillis() - startupTime));
      this.upgradeFailed = true;
      throw new IllegalStateException("Some redactional spaces documents wasn't executed successfully. It will be re-attempted next startup");
    }
  }

  @Override
  public void afterUpgrade() {
    if (!upgradeFailed) {
      settingService.set(Context.GLOBAL.id(PLUGIN_NAME),
              Scope.APPLICATION.id(PLUGIN_NAME),
              PLUGIN_EXECUTED_KEY,
              SettingValue.create(true));
    }
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext upgradePluginExecutionContext) {
    SettingValue<?> settingValue = settingService.get(Context.GLOBAL.id(PLUGIN_NAME),
            Scope.APPLICATION.id(PLUGIN_NAME),
            PLUGIN_EXECUTED_KEY);
    boolean shouldUpgrade = super.shouldProceedToUpgrade(newVersion, previousGroupVersion, upgradePluginExecutionContext);
    if (!shouldUpgrade && settingValue == null) {
      settingService.set(Context.GLOBAL.id(PLUGIN_NAME),
              Scope.APPLICATION.id(PLUGIN_NAME),
              PLUGIN_EXECUTED_KEY,
              SettingValue.create(true));
    }
    return shouldUpgrade;
  }

  @ContainerTransactional
  public List<Long> getRedactionalSpaces() {
    EntityManager entityManager = entityManagerService.getEntityManager();
    String selectQuery = "SELECT DISTINCT sm.space.id FROM SocSpaceMember sm "
                         + "WHERE sm.status = :status";
    TypedQuery<Long> query = entityManager.createQuery(selectQuery, Long.class);
    query.setParameter("status", SpaceMembershipStatus.REDACTOR);
    return query.getResultList();
  }

  private int manageDocumentsPermissions(List<Long> spaceIds, Session session) {
    int notMigratedRedactionalSpacesCountByTransaction = 0;
    for (Long spaceId : spaceIds) {
      try {
        Space space = spaceService.getSpaceById(spaceId.toString());
        LOG.info("Migrating redactional space documents with id '{}' and name '{}'", spaceId, space.getPrettyName());
        Node spaceRootNode = getGroupNode(nodeHierarchyCreator, session, space.getGroupId());
        if (spaceRootNode != null) {
          applyPublisherPermissions(spaceRootNode, space);
          session.save();
        }
        LOG.info("Success migrating redactional space documents with id '{}' and name '{}'", spaceId, space.getPrettyName());
      } catch (Exception e) {
        notMigratedRedactionalSpacesCountByTransaction++;
        LOG.warn("Error migrating redactional space documents with id '{}'. Continue to migrate other items", spaceId, e);
      }
    }
    return notMigratedRedactionalSpacesCountByTransaction;
  }

  private void applyPublisherPermissions(Node parentNode, Space space) throws RepositoryException {
    NodeIterator children = parentNode.getNodes();
    while (children.hasNext()) {
      Node child = children.nextNode();
      LOG.info("Updating publisher permissions for node '{}'", child.getPath());
      ExtendedNode extNode = (ExtendedNode) child;
      if (extNode.isNodeType(NodetypeConstant.EXO_PRIVILEGEABLE)) {
        List<AccessControlEntry> permissions = extNode.getACL().getPermissionEntries();
        boolean hasManager = permissions.stream()
                .anyMatch(p -> p.getIdentity().equals("manager:" + space.getGroupId()));
        boolean hasRedactor = permissions.stream()
                .anyMatch(p -> p.getIdentity().equals("redactor:" + space.getGroupId()));
        boolean hasPublisher = permissions.stream()
                .anyMatch(p -> p.getIdentity().equals("publisher:" + space.getGroupId()));
        if (hasManager && hasRedactor && !hasPublisher) {
          extNode.setPermission("publisher:" + space.getGroupId(), PermissionType.ALL);
        }
      }
      child.save();
      applyPublisherPermissions(child, space);
    }
  }

  private static Node getGroupNode(NodeHierarchyCreator nodeHierarchyCreator,
                                  Session session,
                                  String groupId) throws RepositoryException {
    String groupsHomePath = getGroupsPath(nodeHierarchyCreator);
    String groupPath = groupsHomePath + groupId + "/" + DOCUMENTS_NODE; // NOSONAR
    if (session.itemExists(groupPath)) {
      return (Node) session.getItem(groupPath);
    }
    return null;
  }

  private static String getGroupsPath(NodeHierarchyCreator nodeHierarchyCreator) {
    if (groupsPath != null) {
      return groupsPath;
    }
    groupsPath = nodeHierarchyCreator.getJcrPath(GROUPS_PATH_ALIAS);
    if (StringUtils.isBlank(groupsPath)) {
      groupsPath = DEFAULT_GROUPS_HOME_PATH;
    }
    return groupsPath;
  }
}

