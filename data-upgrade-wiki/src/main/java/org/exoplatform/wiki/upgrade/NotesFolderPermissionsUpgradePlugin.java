package org.exoplatform.wiki.upgrade;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class NotesFolderPermissionsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log       LOG = ExoLogger.getLogger(NotesFolderPermissionsUpgradePlugin.class);

  private final RepositoryService      repositoryService;

  private final SessionProviderService sessionProviderService;

  private int                    notesCount;

  public NotesFolderPermissionsUpgradePlugin(InitParams initParams,
                                             RepositoryService repositoryService,
                                             SessionProviderService sessionProviderService) {
    super(initParams);
    this.repositoryService = repositoryService;
    this.sessionProviderService = sessionProviderService;
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext previousUpgradePluginExecution) {
    int executionCount = previousUpgradePluginExecution == null ? 0 : previousUpgradePluginExecution.getExecutionCount();
    return !isExecuteOnlyOnce() || executionCount == 0;
  }

  @Override
  public void processUpgrade(String s, String s1) {
    long startupTime = System.currentTimeMillis();
    LOG.info("Start upgrade of space notes node permission");
    SessionProvider sessionProvider = null;
    try {
      sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      Session session = sessionProvider.getSession(
                                                   repositoryService.getCurrentRepository()
                                                                    .getConfiguration()
                                                                    .getDefaultWorkspaceName(),
                                                   repositoryService.getCurrentRepository());
      QueryManager qm = session.getWorkspace().getQueryManager();
      int limit = 10, offset = 0;
      // Set the date
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
      Date afterDate = dateFormat.parse("2023-11-06");
      // get nodes created after the 2023-11-06 date.
      String stringQuery =
                         "select * from nt:folder WHERE jcr:path LIKE '/Groups/spaces/%/notes' AND exo:dateCreated > TIMESTAMP '"
                             + afterDate.toInstant().toString() + "'";
      Query jcrQuery = qm.createQuery(stringQuery, Query.SQL);
      boolean hasMoreElements = true;
      while (hasMoreElements) {
        ((QueryImpl) jcrQuery).setOffset(offset);
        ((QueryImpl) jcrQuery).setLimit(limit);
        NodeIterator nodeIterator = jcrQuery.execute().getNodes();
        if (nodeIterator != null) {
          while (nodeIterator.hasNext()) {
            Node notesNode = nodeIterator.nextNode();
            updateNotesNodePermissions(notesNode);
          }
          if (nodeIterator.getSize() < limit) {
            // no more elements
            hasMoreElements = false;
          } else {
            offset += limit;
          }
        }
      }
      LOG.info("End updating of '{}' space notes node permissions. It took {} ms.",
               notesCount,
               (System.currentTimeMillis() - startupTime));

    } catch (Exception e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("An unexpected error occurs when updating notes jcr node permissions:", e);
      }
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  private void updateNotesNodePermissions(Node node) {
    try {
      String nodePath = node.getPath();
      String groupId = nodePath.substring(nodePath.indexOf("/spaces/"), nodePath.lastIndexOf("/"));
      if (node.canAddMixin("exo:privilegeable")) {
        node.addMixin("exo:privilegeable");
      }
      // check if the permission is equal to the group id without the *:
      boolean isWrongPermission = ((ExtendedNode) node).getACL()
                                                       .getPermissionEntries()
                                                       .stream()
                                                       .anyMatch(accessControlEntry -> accessControlEntry.getIdentity()
                                                                                                         .equals(groupId));
      if (isWrongPermission) {
        // remove the wrong permission
        ((ExtendedNode) node).removePermission(groupId);
        // add the correct space permission
        ((ExtendedNode) node).setPermission("*:"
            + groupId, new String[] { PermissionType.READ, PermissionType.ADD_NODE, PermissionType.SET_PROPERTY });
        node.save();
        this.notesCount += 1;
      }
    } catch (RepositoryException e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("An unexpected error occurs when updating notes jcr node permissions:", e);
      }
    }
  }
}
