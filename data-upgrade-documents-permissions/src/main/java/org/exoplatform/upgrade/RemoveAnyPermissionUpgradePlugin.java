/**
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
package org.exoplatform.upgrade;

import java.util.Collections;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.exoplatform.commons.upgrade.UpgradePluginException;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.security.IdentityConstants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoveAnyPermissionUpgradePlugin extends UpgradeProductPlugin {

  private static final long   ANONYMOUS_SESSION_TIMEOUT = 3600000l;

  private static final int    LIMIT_PER_SESSION         = 100;

  private static final String ANY_SPACES_JCR_SQL_QUERY  =
                                                       "SELECT * FROM exo:privilegeable WHERE jcr:path LIKE '/Groups/spaces/%'";

  private RepositoryService   repositoryService;

  public RemoveAnyPermissionUpgradePlugin(InitParams initParams,
                                          RepositoryService repositoryService) {
    super(initParams);
    this.repositoryService = repositoryService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) throws UpgradePluginException {
    upgradePathType(ANY_SPACES_JCR_SQL_QUERY, "spaces");
  }

  private void upgradePathType(String jcrSqlQuery, String pathType) {
    log.info("START:: {} anonymously accessible documents", pathType);
    UpgradeReport upgradeReport = new UpgradeReport();
    try {
      boolean hasMore = true;
      do {
        hasMore = upgradeChunk(jcrSqlQuery, LIMIT_PER_SESSION, upgradeReport);
      } while (hasMore);
    } catch (Exception e) {
      log.error("{} anonymously accessible documents migration interrupted, current status: {}", pathType, upgradeReport, e);
    }
    if (upgradeReport.errorCount > 0
        || canAnonymouslyAccess(jcrSqlQuery, pathType)) {
      throw new UpgradePluginException("Some %s documents wasn't upgraded. It will be re-attempted next startup. Report: %s".formatted(pathType,
                                                                                                                                       upgradeReport));
    } else {
      log.info("END:: {} anonymously accessible documents migration: {}", pathType, upgradeReport);
    }
  }

  private boolean upgradeChunk(String sqlQuery, int limit, UpgradeReport upgradeReport) throws RepositoryException {
    ManageableRepository repository = repositoryService.getCurrentRepository();
    Session systemSession = getSystemSession(repository);
    Session anonymousUserSession = getAnonymousSession(repository);
    int count = 0;
    try {
      QueryManager queryManager = anonymousUserSession.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(sqlQuery, Query.SQL);
      NodeIterator nodesIterator = query.execute().getNodes();
      while (nodesIterator.hasNext() && count < limit) {
        upgradeNextAnonymousNode(nodesIterator,
                                 systemSession,
                                 upgradeReport);
        count++;
      }
      return count == limit;
    } finally {
      upgradeReport.incrementTotalCount(count);
      log.info("PROGRESS:: anonymously accessible documents migration: {}", upgradeReport);
      anonymousUserSession.logout();
      systemSession.logout();
    }
  }

  private void upgradeNextAnonymousNode(NodeIterator anonymousNodesIterator,
                                        Session systemSession,
                                        UpgradeReport upgradeReport) {
    String path = null;
    try {
      path = anonymousNodesIterator.nextNode().getPath();
      ExtendedNode node = (ExtendedNode) systemSession.getItem(path);
      if (hasAnyPermissions(node)) {
        node.removePermission(IdentityConstants.ANY);
        node.save();
      }
      upgradeReport.incrementProcessedCount();
    } catch (Exception e) {
      upgradeReport.incrementErrorCount();
      log.warn("Error while deleting 'any' permission from Node with path '{}'. Continue the upgrade", path, e);
    }
  }

  private boolean canAnonymouslyAccess(String sqlQuery, String pathType) {
    Session anonymousUserSession = null;
    try {
      ManageableRepository repository = repositoryService.getCurrentRepository();
      anonymousUserSession = getAnonymousSession(repository);
      QueryManager queryManager = anonymousUserSession.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(sqlQuery, Query.SQL);
      NodeIterator nodesIterator = query.execute().getNodes();
      return nodesIterator.hasNext();
    } catch (Exception e) {
      log.warn("{} anonymously accessible documents migration check error. Consider it as not fully upgraded", pathType, e);
      return true;
    } finally {
      if (anonymousUserSession != null) {
        anonymousUserSession.logout();
      }
    }
  }

  private Session getAnonymousSession(ManageableRepository repository) throws RepositoryException {
    ExtendedSession dynamicSession = (ExtendedSession) repository.getDynamicSession(
                                                                                    repository.getConfiguration()
                                                                                              .getDefaultWorkspaceName(),
                                                                                    Collections.emptyList());
    dynamicSession.setTimeout(ANONYMOUS_SESSION_TIMEOUT);
    return dynamicSession;
  }

  private Session getSystemSession(ManageableRepository repository) throws RepositoryException {
    return repository.getSystemSession(repository.getConfiguration().getDefaultWorkspaceName());
  }

  private boolean hasAnyPermissions(ExtendedNode node) throws RepositoryException {
    return node.getACL()
               .getPermissionEntries()
               .stream()
               .map(AccessControlEntry::getIdentity)
               .anyMatch(IdentityConstants.ANY::equals);
  }

  public static class UpgradeReport {

    private int  totalCount     = 0;

    private int  processedCount = 0;

    private int  errorCount     = 0;

    private long startupTime    = System.currentTimeMillis();

    public void incrementTotalCount(int count) {
      this.totalCount += count;
    }

    public void incrementProcessedCount() {
      this.processedCount++;
    }

    public void incrementErrorCount() {
      this.errorCount++;
    }

    @Override
    public String toString() {
      return "Total: %s, Processed: %s, Errors: %s within %s ms".formatted(totalCount,
                                                                           processedCount,
                                                                           errorCount,
                                                                           System.currentTimeMillis() - startupTime);
    }
  }

}
