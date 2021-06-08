package org.exoplatform.news.upgrade.activities;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;

@RunWith(PowerMockRunner.class)
public class SharedNewsActivitiesUpgradePluginTest {

  @Mock
  RepositoryService    repositoryService;

  @Mock
  ActivityManager      activityManager;

  @Mock
  ManageableRepository repository;

  @Mock
  RepositoryEntry      repositoryEntry;

  @Mock
  ExtendedSession      session;

  @Mock
  ExoSocialActivity    sharedNewsActivity;

  @Test
  public void testOldSharedNewsActivitiesMigration() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.addons.news");
    initParams.addParameter(valueParam);

    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repository.getSystemSession(anyString())).thenReturn(session);
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
    when(nodeIterator.hasNext()).thenReturn(true, true, false);
    Node newsNode = mock(Node.class);
    Property property = mock(Property.class);
    when(newsNode.getProperty("exo:activities")).thenReturn(property);
    when(property.getString()).thenReturn("1:1;1:2;2:3", "2:4;1:5;2:6");
    when(nodeIterator.nextNode()).thenReturn(newsNode);
    when(activityManager.getActivity(anyString())).thenReturn(sharedNewsActivity);
    SharedNewsActivitiesUpgradePlugin sharedNewsActivitiesUpgradePlugin = new SharedNewsActivitiesUpgradePlugin(initParams,
                                                                                                                repositoryService,
                                                                                                                activityManager);
    sharedNewsActivitiesUpgradePlugin.processUpgrade(null, null);

    assertEquals(4, sharedNewsActivitiesUpgradePlugin.getSharedNewsActivitiesCount());
  }
}
