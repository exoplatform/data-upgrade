package org.exoplatform.ecms.upgrade.templates;

import org.exoplatform.commons.info.ProductInformations;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.cms.templates.TemplateService;
import org.exoplatform.services.cms.impl.Utils;
import org.exoplatform.services.cms.templates.impl.TemplateServiceImpl;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Workspace;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Utils.class)
public class NodeTypeTemplateUpgradePluginTest {
  
  @Mock
  TemplateServiceImpl templateService;
  
  @Mock
  ProductInformations productInformations;
  
  @Mock
  ExtendedSession session;
  @Mock
  NodeIterator      nodeIterator;
  
  @Test
  public void testNodeTypeTemplateMigration() throws Exception {
    InitParams initParams = new InitParams();
    
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.ecms");
    initParams.addParameter(valueParam);
  
    Node templateHomeNode = mock(Node.class);
    when(templateService.getTemplatesHome(any())).thenReturn(templateHomeNode);
    
    Set<String> configuredTemplates=new HashSet<>();
    configuredTemplates.add("template2");
    configuredTemplates.add("template3");
    configuredTemplates.add("template4");
    when(templateService.getAllConfiguredNodeTypes()).thenReturn(configuredTemplates);
    
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
  
    when(templateHomeNode.getSession()).thenReturn(session);
    when(templateHomeNode.getNodes()).thenReturn(nodeIterator);
    
    
    when(nodeIterator.hasNext()).thenReturn(true, true, true, false);
    Node templateNode1 = mock(Node.class);
    when(templateNode1.getName()).thenReturn("template2");
    Node templateNode2 = mock(Node.class);
    when(templateNode2.getName()).thenReturn("template4");
    Node templateNode3 = mock(Node.class);
    when(templateNode3.getName()).thenReturn("template3");
  
    when(nodeIterator.nextNode()).thenReturn(templateNode1).thenReturn(templateNode2).thenReturn(templateNode3);
  
    PowerMockito.mockStatic(Utils.class);
    Set<String> modifiedTemplateList=new HashSet<>();
    modifiedTemplateList.add("template2");
    when(Utils.getAllEditedConfiguredData(anyString(),anyString(),anyBoolean())).thenReturn(modifiedTemplateList);
    
   
    NodeTypeTemplateUpgradePlugin nodeTypeTemplateUpgradePlugin = new NodeTypeTemplateUpgradePlugin(templateService,
                                                                                                    productInformations,
                                                                                                    initParams);
    nodeTypeTemplateUpgradePlugin.processUpgrade(null, null);
    verify(templateHomeNode, times(3)).save();
    verify(templateService, times(1)).start();

  
  }
}
