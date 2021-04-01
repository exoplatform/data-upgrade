package org.exoplatform.ecms.upgrade.templates;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.cms.BasePath;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.List;

/**
 * plugin will be executed in order to remove administrators permission from
 * each space documents folder
 */
public class DocumentPermissionUpgradePlugin extends UpgradeProductPlugin {
    private static final Log log = ExoLogger.getLogger(DocumentPermissionUpgradePlugin.class.getName());

    private NodeHierarchyCreator nodeHierarchyCreator;

    private RepositoryService repositoryService;

    private SessionProviderService sessionProviderService;

    private SpaceService spaceService;

    public static final String ADMINISTRATORS_IDENTITY = "*:/platform/administrators";

    public static final String EXO_PRIVILEGEABLE = "exo:privilegeable";

    public DocumentPermissionUpgradePlugin(InitParams initParams, NodeHierarchyCreator nodeHierarchyCreator, RepositoryService repositoryService, SessionProviderService sessionProviderService, SpaceService spaceService) {
        super(initParams);
        this.nodeHierarchyCreator = nodeHierarchyCreator;
        this.repositoryService = repositoryService;
        this.sessionProviderService = sessionProviderService;
        this.spaceService = spaceService;
    }

    @Override
    public void processUpgrade(String oldVersion, String newVersion) {
        long startupTime = System.currentTimeMillis();
        log.info("Start upgrade of space documents jcr nodes");

        SessionProvider sessionProvider = null;
        try {
            List<Space> spaces = spaceService.getAllSpaces();
            List<String> spaceGroupIds = new ArrayList<>();
            if (spaces != null) {
                for (Space space : spaces) {
                    spaceGroupIds.add(space.getGroupId());
                }
                sessionProvider = sessionProviderService.getSystemSessionProvider(null);
                ManageableRepository repository = repositoryService.getCurrentRepository();
                Session session = sessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(), repository);
                NodeIterator nodeIter = null;
                for (String groupId : spaceGroupIds) {
                    String spacesNodePath = nodeHierarchyCreator.getJcrPath(BasePath.CMS_GROUPS_PATH) + groupId;
                    Node spacesRootNode = (Node) session.getItem(spacesNodePath);
                    NodeIterator iter = spacesRootNode.getNodes();
                    while (iter.hasNext()) {
                        Node spaceNode = iter.nextNode();
                        if (spaceNode != null && spaceNode.getName().equals("Documents")) {
                            nodeIter = spaceNode.getNodes();
                        }
                    }
                }
                if (nodeIter != null) {
                    while (nodeIter.hasNext()) {
                        Node spaceDocuments = nodeIter.nextNode();
                        if (!spaceDocuments.isNodeType(EXO_PRIVILEGEABLE)) {
                            spaceDocuments.addMixin(EXO_PRIVILEGEABLE);
                        }
                        ((ExtendedNode) spaceDocuments).removePermission(ADMINISTRATORS_IDENTITY);
                        spaceDocuments.save();
                    }
                }
            }
            log.info("End upgrade of '{}' space documents nodes. It took {} ms", (System.currentTimeMillis() - startupTime));
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("An unexpected error occurs when migrating space documents permissions:", e);
            }
        } finally {
            if (sessionProvider != null) {
                sessionProvider.close();
            }
        }
    }
}
