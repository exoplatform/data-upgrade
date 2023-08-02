package org.exoplatform.news.upgrade.jcr;

import static org.exoplatform.news.upgrade.jcr.PublishedNewsImagesPermissionsUpgradePlugin.EXO_PRIVILEGEABLE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.ArrayList;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.PortalContainer;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.*", "org.w3c.*", "javax.naming.*", "javax.xml.*", "org.xml.*", "javax.management.*"})
@PrepareForTest({CommonsUtils.class, PortalContainer.class})
public class PublishedNewsImagesPermissionsUpgradePluginTest {

  @Mock
  RepositoryService      repositoryService;

  @Mock
  ManageableRepository   repository;

  @Mock
  RepositoryEntry        repositoryEntry;

  @Mock
  Session                session;

  @Mock
  SessionProviderService sessionProviderService;

  @Mock
  SessionProvider        sessionProvider;

  @Test
  public void publishedNewsImagesPermissionsUpgradePluginTest() throws Exception {
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.news");

    when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(sessionProvider.getSession(any(), any())).thenReturn(session);
    QueryManager qm = mock(QueryManager.class);
    Workspace workSpace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workSpace);
    when(workSpace.getQueryManager()).thenReturn(qm);
    Query query = mock(QueryImpl.class);
    when(qm.createQuery(anyString(), anyString())).thenReturn(query);
    QueryResult queryResult = mock(QueryResult.class);
    when(query.execute()).thenReturn(queryResult);
    NodeIterator nodeIterator = mock(NodeIterator.class);
    when(queryResult.getNodes()).thenReturn(nodeIterator);
    when(nodeIterator.hasNext()).thenReturn(true, false);
    Node newsNode = mock(Node.class);
    Property property = mock(Property.class);
    when(nodeIterator.nextNode()).thenReturn(newsNode);
    when(newsNode.hasProperty("exo:body")).thenReturn(true);
    when(newsNode.getProperty("exo:body")).thenReturn(property);
    when(property.getString()).thenReturn("news body with image src=\"/portal/rest/images/repository/collaboration/123\"");
    ExtendedNode imageNode = mock(ExtendedNode.class);
    when(session.getNodeByUUID("123")).thenReturn(imageNode);
    when(imageNode.canAddMixin(EXO_PRIVILEGEABLE)).thenReturn(true);
    AccessControlList accessControlList = mock(AccessControlList.class);
    when(imageNode.getACL()).thenReturn(accessControlList);
    when(accessControlList.getPermissionEntries()).thenReturn(new ArrayList<>());
    // when
    PublishedNewsImagesPermissionsUpgradePlugin publishedNewsImagesPermissionsUpgradePlugin =
                                                                                            new PublishedNewsImagesPermissionsUpgradePlugin(initParams,
                                                                                                                                            repositoryService,
                                                                                                                                            sessionProviderService);
    publishedNewsImagesPermissionsUpgradePlugin.processUpgrade(null, null);
    // then
    verify(imageNode, times(1)).setPermission("*:/platform/users", new String[] { "read" });
    verify(imageNode, times(1)).save();

    //
    when(nodeIterator.hasNext()).thenReturn(true, false);
    when(property.getString()).thenReturn("news body with image src=\"https://exoplatform.com/portal/rest/jcr/repository/collaboration/Groups/spaces/test/testimage\"");
    String currentDomainName = "https://exoplatform.com";
    String currentPortalContainerName = "portal";
    String restContextName = "rest";
    PowerMockito.mockStatic(CommonsUtils.class);
    PowerMockito.mockStatic(PortalContainer.class);
    when(CommonsUtils.getRestContextName()).thenReturn(restContextName);
    when(PortalContainer.getCurrentPortalContainerName()).thenReturn(currentPortalContainerName);
    when(CommonsUtils.getCurrentDomain()).thenReturn(currentDomainName);
    ExtendedNode existingUploadImageNode = mock(ExtendedNode.class);
    when(existingUploadImageNode.canAddMixin(EXO_PRIVILEGEABLE)).thenReturn(true);
    when(session.getItem(nullable(String.class))).thenReturn(existingUploadImageNode);
    when(existingUploadImageNode.getACL()).thenReturn(accessControlList);

    publishedNewsImagesPermissionsUpgradePlugin.processUpgrade(null, null);
    // then
    verify(existingUploadImageNode, times(1)).setPermission("*:/platform/users", new String[] { "read" });
    verify(existingUploadImageNode, times(1)).save();

  }
}
