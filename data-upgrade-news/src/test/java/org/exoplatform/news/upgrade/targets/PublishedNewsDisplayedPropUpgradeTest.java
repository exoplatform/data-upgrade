package org.exoplatform.news.upgrade.targets;

import static org.jgroups.util.Util.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.hibernate.Transaction;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.news.model.News;
import org.exoplatform.news.service.NewsService;
import org.exoplatform.social.core.jpa.storage.entity.MetadataEntity;
import org.exoplatform.social.core.jpa.storage.entity.MetadataItemEntity;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.Metadata;
import org.exoplatform.social.metadata.model.MetadataType;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PublishedNewsDisplayedPropUpgradeTest {

  private static final MockedStatic<ExoContainerContext> EXO_CONTAINER_CONTEXT = mockStatic(ExoContainerContext.class);

  private static final MockedStatic<PortalContainer>     PORTAL_CONTAINER      = mockStatic(PortalContainer.class);

  private static final MockedStatic<RequestLifeCycle>    REQUEST_LIFECYCLE     = mockStatic(RequestLifeCycle.class);

  @Mock
  private EntityManagerService                           entityManagerService;

  @Mock
  private NewsService                                    newsService;

  @Mock
  private MetadataService                                metadataService;

  @Mock
  private EntityManager                                  entityManager;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    EXO_CONTAINER_CONTEXT.close();
    PORTAL_CONTAINER.close();
    REQUEST_LIFECYCLE.close();
  }

  @Test
  public void processUpgrade() throws Exception {
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);
    MetadataType metadataType = new MetadataType(4, "newsTarget");
    List<Metadata> newsTargets = new LinkedList<>();
    Metadata sliderNews = new Metadata();
    sliderNews.setName("sliderNews");
    sliderNews.setCreatedDate(100);
    HashMap<String, String> sliderNewsProperties = new HashMap<>();
    sliderNewsProperties.put("label", "slider news");
    sliderNews.setProperties(sliderNewsProperties);
    sliderNews.setId(1);
    newsTargets.add(sliderNews);
    MetadataEntity metadataEntity = new MetadataEntity();
    metadataEntity.setCreatorId(1);
    metadataEntity.setId(1l);
    metadataEntity.setProperties(sliderNewsProperties);
    metadataEntity.setAudienceId(0);
    MetadataItemEntity metadataItemEntity = new MetadataItemEntity();
    metadataItemEntity.setCreatorId(1);
    metadataItemEntity.setId(1l);
    metadataItemEntity.setObjectId("1");
    metadataItemEntity.setMetadata(metadataEntity);
    List<MetadataItemEntity> metadataItemEntities = new LinkedList<>();
    metadataItemEntities.add(metadataItemEntity);
    News news = new News();
    news.setId("1");
    news.setArchived(false);
    news.setPublicationState("published");

    Transaction transaction = mock(Transaction.class);
    PortalContainer container = mock(PortalContainer.class);

    Query deleteQuery = mock(Query.class);
    Query insertQuery = mock(Query.class);
    Query getQuery = mock(Query.class);

    EXO_CONTAINER_CONTEXT.when(() -> ExoContainerContext.getCurrentContainer()).thenReturn(container);
    EXO_CONTAINER_CONTEXT.when(() -> ExoContainerContext.getService(EntityManagerService.class)).thenReturn(entityManagerService);
    when(container.getComponentInstanceOfType(EntityManagerService.class)).thenReturn(entityManagerService);
    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    when(entityManager.getTransaction()).thenReturn(transaction);
    when(transaction.isActive()).thenReturn(true);
    doNothing().when(transaction).begin();

    when(metadataService.getMetadatas(metadataType.getName(), 0)).thenReturn(newsTargets);
    when(newsService.getNewsById("1", false)).thenReturn(news);

    when(entityManager.createNativeQuery(PublishedNewsDisplayedPropUpgrade.DELETE_NEWS_TARGETS_METADATA_ITEMS_PROPS,
                                         MetadataItemEntity.class)).thenReturn(deleteQuery);
    when(deleteQuery.executeUpdate()).thenReturn(1);

    when(entityManager.createNativeQuery(PublishedNewsDisplayedPropUpgrade.INSERT_NEWS_TARGETS_METADATA_ITEMS_PROPS,
                                         MetadataItemEntity.class)).thenReturn(insertQuery);
    when(insertQuery.executeUpdate()).thenReturn(1);

    when(entityManager.createNativeQuery(PublishedNewsDisplayedPropUpgrade.GET_NEWS_TARGET_METADATA_ITEMS,
                                         MetadataItemEntity.class)).thenReturn(getQuery);
    when(getQuery.getResultList()).thenReturn(metadataItemEntities);

    PublishedNewsDisplayedPropUpgrade publishedNewsDisplayedPropUpgradePlugin = new PublishedNewsDisplayedPropUpgrade(initParams,
                                                                                                                      entityManagerService,
                                                                                                                      newsService,
                                                                                                                      metadataService);

    publishedNewsDisplayedPropUpgradePlugin.processUpgrade(null, null);
    assertEquals(1, publishedNewsDisplayedPropUpgradePlugin.getMigratedPublishedNewsCount());
  }
}
