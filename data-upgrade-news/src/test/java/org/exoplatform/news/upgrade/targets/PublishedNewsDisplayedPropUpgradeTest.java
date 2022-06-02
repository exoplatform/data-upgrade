package org.exoplatform.news.upgrade.targets;

import static org.jgroups.util.Util.assertEquals;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.hibernate.Transaction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

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

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*", "jdk.internal.*", "javax.xml.*", "org.apache.xerces.*", "org.xml.*",
    "com.sun.org.apache.*", "org.w3c.*" })
@PrepareForTest({ExoContainerContext.class, PortalContainer.class, RequestLifeCycle.class})
public class PublishedNewsDisplayedPropUpgradeTest {

  @Mock
  private EntityManagerService entityManagerService;
  
  @Mock
  private NewsService          newsService;

  @Mock
  private MetadataService      metadataService;

  @Mock
  private EntityManager entityManager;

  @Before
  public void setUp() throws Exception {
    PowerMockito.mockStatic(ExoContainerContext.class);
    PowerMockito.mockStatic(PortalContainer.class);
    PowerMockito.mockStatic(RequestLifeCycle.class);
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
    
    Query nativeQuery1 = mock(Query.class);
    Query nativeQuery2 = mock(Query.class);
    Query nativeQuery3 = mock(Query.class);
    
    when(ExoContainerContext.getCurrentContainer()).thenReturn(container);
    when(container.getComponentInstanceOfType(EntityManagerService.class)).thenReturn(entityManagerService);
    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    when(entityManager.getTransaction()).thenReturn(transaction);
    when(transaction.isActive()).thenReturn(true);
    doNothing().when(transaction).begin();
    
    when(metadataService.getMetadatas(metadataType.getName(), 0)).thenReturn(newsTargets);
    when(newsService.getNewsById("1", false)).thenReturn(news);

    when(entityManager.createNativeQuery(Mockito.anyString(), Mockito.eq(MetadataItemEntity.class))).thenReturn(nativeQuery1);
    when(nativeQuery1.getResultList()).thenReturn(metadataItemEntities);

    when(entityManager.createNativeQuery(Mockito.anyString(), Mockito.eq(MetadataItemEntity.class))).thenReturn(nativeQuery2);
    when(nativeQuery2.executeUpdate()).thenReturn(1);
    
    when(entityManager.createNativeQuery(Mockito.anyString(), Mockito.eq(MetadataItemEntity.class))).thenReturn(nativeQuery3);
    when(nativeQuery3.executeUpdate()).thenReturn(1);
    
    PublishedNewsDisplayedPropUpgrade publishedNewsDisplayedPropUpgradePlugin = new PublishedNewsDisplayedPropUpgrade(initParams,
                                                                                                                      entityManagerService,
                                                                                                                      newsService,
                                                                                                                      metadataService);

    publishedNewsDisplayedPropUpgradePlugin.processUpgrade(null, null);
    assertEquals(1, publishedNewsDisplayedPropUpgradePlugin.getMigratedPublishedNewsCount());
  }
}
