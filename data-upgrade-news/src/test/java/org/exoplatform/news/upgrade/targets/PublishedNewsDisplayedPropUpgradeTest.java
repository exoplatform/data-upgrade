package org.exoplatform.news.upgrade.targets;

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
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataType;
import org.hibernate.Transaction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.*;

import static org.jgroups.util.Util.assertEquals;
import static org.powermock.api.mockito.PowerMockito.*;

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
    Date date = new Date(2020, 11, 21);
    Query nativeQuery = mock(Query.class);
    Query nativeQuery2 = mock(Query.class);

    Transaction transaction = mock(Transaction.class);
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
    List<MetadataItem> metadataItems = new LinkedList<>();
    MetadataItem metadataItem = new MetadataItem();
    metadataItem.setCreatedDate(100);
    metadataItem.setCreatorId(1);
    metadataItem.setId(1);
    metadataItem.setObjectId("1");
    metadataItem.setMetadata(sliderNews);
    metadataItems.add(metadataItem);

    MetadataEntity metadataEntity = new MetadataEntity();
    metadataEntity.setCreatedDate(date);
    metadataEntity.setCreatorId(1);
    metadataEntity.setId(1l);
    metadataEntity.setProperties(sliderNewsProperties);
    metadataEntity.setAudienceId(0);
    MetadataItemEntity metadataItemEntity = new MetadataItemEntity();
    metadataItemEntity.setCreatedDate(date);
    metadataItemEntity.setCreatorId(1);
    metadataItemEntity.setId(1l);
    metadataItemEntity.setObjectId("1");
    metadataItemEntity.setMetadata(metadataEntity);
    List<MetadataItemEntity> metadataItemEntities = new LinkedList<>();
    metadataItemEntities.add(metadataItemEntity);
    News news = new News();
    news.setId("1");
    news.setArchived(false);
    when(metadataService.getMetadatas(metadataType.getName(), 0)).thenReturn(newsTargets);
    when(newsService.getNewsById("1", false)).thenReturn(news);
    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    when(entityManager.getTransaction()).thenReturn(transaction);
    when(entityManager.getTransaction().isActive()).thenReturn(true);
    doNothing().when(transaction).begin();
    PortalContainer container = mock(PortalContainer.class);
    PowerMockito.when(PortalContainer.getInstance()).thenReturn(container);

    PublishedNewsDisplayedPropUpgrade PublishedNewsDisplayedPropUpgradePlugin = new PublishedNewsDisplayedPropUpgrade(initParams,
                                                                                        entityManagerService,
                                                                                        newsService,
                                                                                        metadataService,
                                                                                        container);
    String sqlString = "SELECT * FROM SOC_METADATA_ITEMS WHERE METADATA_ID = '" + metadataItem.getId() + "'";
    when(entityManager.createNativeQuery(sqlString,MetadataItemEntity.class)).thenReturn(nativeQuery);
    when(nativeQuery.getResultList()).thenReturn(metadataItemEntities);

    String sqlString2 = "DELETE FROM SOC_METADATA_ITEMS_PROPERTIES";
    when(entityManager.createNativeQuery(sqlString2)).thenReturn(nativeQuery2);
    when(nativeQuery2.executeUpdate()).thenReturn(1);

    PublishedNewsDisplayedPropUpgradePlugin.processUpgrade(null, null);
    assertEquals(1, PublishedNewsDisplayedPropUpgradePlugin.getPublishedNewsDisplayedPropCount());
  }
}