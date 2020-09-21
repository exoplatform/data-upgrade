/*
 * Copyright (C) 2003-2020 eXo Platform SAS.
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.cms.BasePath;
import org.exoplatform.services.cms.impl.DMSConfiguration;
import org.exoplatform.services.cms.impl.DMSRepositoryConfiguration;
import org.exoplatform.services.cms.views.ManageViewService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;

@RunWith(PowerMockRunner.class)
public class SiteExplorerTemplateUpgradePluginTest {

  @Mock
  NodeHierarchyCreator       nodeHierarchyCreator;

  @Mock
  RepositoryService          repositoryService;

  @Mock
  DMSConfiguration           dmsConfiguration;

  @Mock
  DMSRepositoryConfiguration dmsRepoConfig;

  @Mock
  ManageViewService          manageViewService;

  @Mock
  SessionProviderService     sessionProviderService;

  @Mock
  ManageableRepository       repository;

  @Mock
  RepositoryEntry            repositoryEntry;

  @Mock
  SessionProvider            sessionProvider;

  @Mock
  ExtendedSession            session;

  @Mock
  NodeIterator               nodeIterator;

  @Test
  public void testSiteExplorerTemplateMigration() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.ecms");
    initParams.addParameter(valueParam);
    Set<String> configuredtemplates = new HashSet<>(Arrays.asList("template1", "template2", "template3"));

    when(manageViewService.getConfiguredTemplates()).thenReturn(configuredtemplates);
    when(dmsConfiguration.getConfig()).thenReturn(dmsRepoConfig);
    when(dmsRepoConfig.getSystemWorkspace()).thenReturn("dms-system");
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repository.getSystemSession(anyString())).thenReturn(session);
    when(nodeHierarchyCreator.getJcrPath(anyString())).thenReturn(BasePath.ECM_EXPLORER_TEMPLATES);
    Node ecmExplorerViewNode = mock(Node.class);
    when((Node) session.getItem(anyString())).thenReturn(ecmExplorerViewNode);
    when(ecmExplorerViewNode.getNodes()).thenReturn(nodeIterator);
    when(nodeIterator.hasNext()).thenReturn(true, true, true, false);
    Node viewNode2 = mock(Node.class);
    when(viewNode2.getName()).thenReturn("template2");
    Node viewNode4 = mock(Node.class);
    when(viewNode4.getName()).thenReturn("template4");
    Node viewNode3 = mock(Node.class);
    when(viewNode3.getName()).thenReturn("template3");
    when(nodeIterator.nextNode()).thenReturn(viewNode2).thenReturn(viewNode4).thenReturn(viewNode3);
    SiteExplorerTemplateUpgradePlugin siteExplorerTemplateUpgradePlugin = new SiteExplorerTemplateUpgradePlugin(initParams,
                                                                                                                nodeHierarchyCreator,
                                                                                                                repositoryService,
                                                                                                                dmsConfiguration,
                                                                                                                manageViewService);
    siteExplorerTemplateUpgradePlugin.processUpgrade(null, null);
    verify(ecmExplorerViewNode, times(2)).save();
  }
}
