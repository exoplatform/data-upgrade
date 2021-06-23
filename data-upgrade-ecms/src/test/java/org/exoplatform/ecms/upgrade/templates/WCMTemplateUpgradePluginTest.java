package org.exoplatform.ecms.upgrade.templates;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.exoplatform.commons.info.ProductInformations;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.cms.impl.Utils;
import org.exoplatform.services.cms.views.impl.ApplicationTemplateManagerServiceImpl;
import org.exoplatform.services.jcr.core.ExtendedSession;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Utils.class)
public class WCMTemplateUpgradePluginTest {

  @Mock
  ApplicationTemplateManagerServiceImpl applicationTemplateManagerService;

  @Mock
  ProductInformations                   productInformations;

  @Mock
  ExtendedSession                       session;

  @Mock
  NodeIterator                          nodeIterator;

  @Mock
  QueryManager                          queryManager;

  @Test
  public void testWCMTemplateMigration() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.ecms");
    initParams.addParameter(valueParam);

    Node templateHomeNode = mock(Node.class);
    when(applicationTemplateManagerService.getApplicationTemplateHome(any(), any())).thenReturn(templateHomeNode);
    when(templateHomeNode.getPath()).thenReturn("/templateHomeNodePath");
    Set<String> configuredTemplates = new HashSet<>();
    configuredTemplates.add("template1");
    configuredTemplates.add("template2");
    configuredTemplates.add("template3");
    when(applicationTemplateManagerService.getConfiguredAppTemplateMap(any())).thenReturn(configuredTemplates);

    when(templateHomeNode.getSession()).thenReturn(session);
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    Query query = mock(Query.class);
    when(queryManager.createQuery(anyString(), anyString())).thenReturn(query);
    QueryResult queryResult = mock(QueryResult.class);
    when(query.execute()).thenReturn(queryResult);
    when(queryResult.getNodes()).thenReturn(nodeIterator);

    when(nodeIterator.hasNext()).thenReturn(true, true, true, false);
    Node templateNode1 = mock(Node.class);
    when(templateNode1.getName()).thenReturn("template1");
    when(templateNode1.getPath()).thenReturn("/templateHomeNodePath/template1");
    Node templateNode2 = mock(Node.class);
    when(templateNode2.getName()).thenReturn("template2");
    when(templateNode2.getPath()).thenReturn("/templateHomeNodePath/template2");
    Node templateNode3 = mock(Node.class);
    when(templateNode3.getName()).thenReturn("template3");
    when(templateNode3.getPath()).thenReturn("/templateHomeNodePath/template3");

    when(nodeIterator.nextNode()).thenReturn(templateNode1).thenReturn(templateNode2).thenReturn(templateNode3);

    WCMTemplateUpgradePlugin wcmTemplateUpgradePlugin = new WCMTemplateUpgradePlugin(applicationTemplateManagerService, initParams);
    wcmTemplateUpgradePlugin.processUpgrade(null, null);
    verify(templateHomeNode, times(3)).save();
    verify(applicationTemplateManagerService, times(1)).start();
  }
}
