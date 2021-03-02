package org.exoplatform.migration.dlp;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.Workspace;

public class DlpFolderAndDriveMigration extends UpgradeProductPlugin {

  private static final Log         LOG                     = ExoLogger.getLogger(DlpFolderAndDriveMigration.class);

  private static final String      OLD_NODE_PATH           = "old.nodePath";

  private static final String      NEW_NODE_PATH           = "new.nodePath";

  private static final String      WORKSPACE_COLLABORATION = "collaboration";

  private String                   oldNodePath;

  private String                   newNodePath;

  private final RepositoryService  repositoryService;

  private final ManageDriveService manageDriveService;

  public DlpFolderAndDriveMigration(InitParams initParams,
                                    SettingService settingService,
                                    RepositoryService repositoryService,
                                    ManageDriveService manageDriveService) {
    super(settingService, initParams);
    this.repositoryService = repositoryService;
    this.manageDriveService = manageDriveService;
    if (initParams.containsKey(OLD_NODE_PATH)) {
      oldNodePath = initParams.getValueParam(OLD_NODE_PATH).getValue();
    }
    if (initParams.containsKey(NEW_NODE_PATH)) {
      newNodePath = initParams.getValueParam(NEW_NODE_PATH).getValue();
    }
  }

  @Override
  public void processUpgrade(String s, String s1) {
    if (StringUtils.isBlank(oldNodePath)) {
      LOG.error("Couldn't process upgrade, the parameter '{}' is mandatory", OLD_NODE_PATH);
      return;
    }
    if (StringUtils.isBlank(newNodePath)) {
      LOG.error("Couldn't process upgrade, the parameter '{}' is mandatory", NEW_NODE_PATH);
      return;
    }
    SessionProvider sessionProvider = null;
    LOG.info("Start migration of Dlp folder and drive");
    try {
      sessionProvider = SessionProvider.createSystemProvider();
      Session session = sessionProvider.getSession(WORKSPACE_COLLABORATION, repositoryService.getCurrentRepository());
      Workspace workspace = session.getWorkspace();
      Node oldNode = (Node) session.getItem(oldNodePath);
      if (oldNode.hasNodes()) {
        NodeIterator iter = oldNode.getNodes();
        while (iter.hasNext()) {
          Node node = iter.nextNode();
          workspace.move(node.getPath(), newNodePath + "/" + node.getName());
          node.save();
        }
        manageDriveService.removeDrive(oldNode.getName());
        Node parent = oldNode.getParent();
        oldNode.remove();
        parent.save();
        session.save();
      }
    } catch (Exception e) {
      throw new RuntimeException("An error occurred while migration of Dlp folder and drive", e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion, String previousVersion) {
    return true;
  }
}
