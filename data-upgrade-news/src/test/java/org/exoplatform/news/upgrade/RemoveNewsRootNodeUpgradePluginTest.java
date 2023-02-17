/*
 * Copyright (C) 2023 eXo Platform SAS.
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

package org.exoplatform.news.upgrade;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*", "jdk.internal.reflect.*", "javax.xml.*", "org.apache.xerces.*", "org.xml.*" })
public class RemoveNewsRootNodeUpgradePluginTest {

  @Mock
  private RepositoryService      repositoryService;

  @Mock
  private SessionProviderService sessionProviderService;

  @Mock
  SessionProvider                sessionProvider;

  @Mock
  Session                        session;
  @Mock
  ManageableRepository   repository;

  @Mock
  RepositoryEntry repositoryEntry;

  public static final String     APPLICATION_DATA_PATH = "/Application Data";

  public static final String     NEWS_NODES_FOLDER     = "News";

  @Test
  public void testRemoveNewsRootNode() throws RepositoryException {
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);
    RemoveNewsRootNodeUpgradePlugin removeNewsRootNodeUpgradePlugin = new RemoveNewsRootNodeUpgradePlugin(initParams,
                                                                                                          repositoryService,
                                                                                                          sessionProviderService);
    Node applicationDataNode = mock(Node.class);
    Node newsRootNode = mock(Node.class);
    when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession(any(), any())).thenReturn(session);
    when((Node)session.getItem(APPLICATION_DATA_PATH)).thenReturn(applicationDataNode);

    when(applicationDataNode.hasNode(NEWS_NODES_FOLDER)).thenReturn(false);
    doNothing().when(newsRootNode).remove();
    doNothing().when(applicationDataNode).save();

    removeNewsRootNodeUpgradePlugin.processUpgrade("v1", "v2");
    verify(applicationDataNode, times(0)).getNode(anyString());
    verify(applicationDataNode, times(0)).save();
    verify(newsRootNode, times(0)).remove();

    when(applicationDataNode.hasNode(NEWS_NODES_FOLDER)).thenReturn(true);
    when(applicationDataNode.getNode(NEWS_NODES_FOLDER)).thenReturn(newsRootNode);
    removeNewsRootNodeUpgradePlugin.processUpgrade("v1", "v2");
    verify(applicationDataNode, times(1)).getNode(anyString());
    verify(applicationDataNode, times(1)).save();
    verify(newsRootNode, times(1)).remove();
  }
}
