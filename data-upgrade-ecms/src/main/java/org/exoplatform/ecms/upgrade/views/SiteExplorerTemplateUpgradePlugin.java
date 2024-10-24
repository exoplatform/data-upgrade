/*
 * Copyright (C) 2003-2021 eXo Platform SAS.
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
package org.exoplatform.ecms.upgrade.views;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.cms.BasePath;
import org.exoplatform.services.cms.impl.DMSConfiguration;
import org.exoplatform.services.cms.impl.DMSRepositoryConfiguration;
import org.exoplatform.services.cms.views.ManageViewService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Created by The eXo Platform SAS 
 * Author : Nguyen Anh Vu 
 * vuna@exoplatform.com
 * Feb 24, 2012
 * 
 * This class will be used to upgrade pre-defined templates of Site
 * Explorer. Templates with desire of manual upgrade can be specified in file
 * configuration.properties.<br>
 * Syntax :<br>
 * unchanged-site-explorer-templates={templates name list} For examples :<br>
 * unchanged-site-explorer-templates=ThumbnailsView, ContentView
 */
public class SiteExplorerTemplateUpgradePlugin extends UpgradeProductPlugin {

  /**
   * @param initParams
   */
  public SiteExplorerTemplateUpgradePlugin(InitParams initParams,
                                           NodeHierarchyCreator nodeHierarchyCreator,
                                           RepositoryService repoService,
                                           DMSConfiguration dmsConfiguration,
                                           ManageViewService manageViewService) {
    super(initParams);
    this.nodeHierarchyCreator = nodeHierarchyCreator;
    this.repositoryService = repoService;
    this.dmsConfiguration = dmsConfiguration;
    this.manageViewService = manageViewService;
  }

  private static final Log     log = ExoLogger.getLogger(SiteExplorerTemplateUpgradePlugin.class.getName());

  private NodeHierarchyCreator nodeHierarchyCreator;

  private DMSConfiguration     dmsConfiguration;

  private RepositoryService    repositoryService;

  private ManageViewService    manageViewService;

  private int                  siteExplorerTemplatesUpdatedCount;

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    log.info("Start upgrade of site explorer templates");

    String unchangedViews = System.getProperty("unchanged-site-explorer-templates");
    SessionProvider sessionProvider = null;
    if (StringUtils.isEmpty(unchangedViews)) {
      unchangedViews = "";
    }
    try {
      Set<String> unchangedViewSet = new HashSet<String>();
      // Force load all configured templates
      manageViewService.init();
      Set<String> configuredTemplates = manageViewService.getConfiguredTemplates();
      List<Node> removedNodes = new ArrayList<Node>();
      for (String unchangedView : unchangedViews.split(",")) {
        unchangedViewSet.add(unchangedView.trim());
      }
      // get all old query nodes that need to be removed.
      sessionProvider = SessionProvider.createSystemProvider();
      DMSRepositoryConfiguration dmsRepoConfig = dmsConfiguration.getConfig();
      Session session = sessionProvider.getSession(dmsRepoConfig.getSystemWorkspace(),
                                                   repositoryService.getCurrentRepository());

      String ecmExplorerViewNodePath = nodeHierarchyCreator.getJcrPath(BasePath.ECM_EXPLORER_TEMPLATES);
      Node ecmExplorerViewNode = (Node) session.getItem(ecmExplorerViewNodePath);
      NodeIterator iter = ecmExplorerViewNode.getNodes();
      while (iter.hasNext()) {
        Node viewNode = iter.nextNode();
        if (!unchangedViewSet.contains(viewNode.getName()) && configuredTemplates.contains(viewNode.getName())) {
          removedNodes.add(viewNode);
        }
      }
      // remove the old query nodes
      for (Node removedNode : removedNodes) {
        try {
          removedNode.remove();
          ecmExplorerViewNode.save();
          siteExplorerTemplatesUpdatedCount ++;
        } catch (Exception e) {
          if (log.isInfoEnabled()) {
            log.error("Error in " + this.getClass().getName() + ": Can not remove old query node: " + removedNode.getPath());
          }
        }
      }
      // re-initialize new views
      manageViewService.init();
      log.info("End upgrade of '{}' site explorer templates. It took {} ms",
               siteExplorerTemplatesUpdatedCount,
               (System.currentTimeMillis() - startupTime));
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("An unexpected error occurs when migrating Site Explorer views:", e);
      }
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  /**
   * @return the siteExplorerTemplatesUpdatedCount
   */
  public int getSiteExplorerTemplatesUpdatedCount() {
    return siteExplorerTemplatesUpdatedCount;
  }
}
