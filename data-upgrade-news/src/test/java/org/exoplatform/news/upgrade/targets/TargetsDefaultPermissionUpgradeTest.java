package org.exoplatform.news.upgrade.targets;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.news.utils.NewsUtils;
import org.exoplatform.social.core.metadata.storage.MetadataStorage;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.Metadata;
import org.exoplatform.social.metadata.model.MetadataType;

@RunWith(MockitoJUnitRunner.class)
public class TargetsDefaultPermissionUpgradeTest {

  private static final MockedStatic<ExoContainerContext> EXO_CONTAINER_CONTEXT = mockStatic(ExoContainerContext.class);

  private static final MockedStatic<PortalContainer>     PORTAL_CONTAINER      = mockStatic(PortalContainer.class);

  private static final MockedStatic<RequestLifeCycle>    REQUEST_LIFECYCLE     = mockStatic(RequestLifeCycle.class);

  @Mock
  private MetadataService metadataService;

  @Mock
  private MetadataStorage metadataStorage;

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
    
    Metadata latestNews = new Metadata();
    latestNews.setName("latestNews");
    latestNews.setCreatedDate(100);
    HashMap<String, String> latestNewsProperties = new HashMap<>();
    latestNewsProperties.put("label", "latest news");
    latestNewsProperties.put(NewsUtils.TARGET_PERMISSIONS, "space:1");
    latestNews.setProperties(latestNewsProperties);
    latestNews.setId(2);
    newsTargets.add(latestNews);
    
    Metadata testNews = new Metadata();
    testNews.setName("testNews");
    testNews.setCreatedDate(100);
    HashMap<String, String> testNewsProperties = new HashMap<>();
    testNewsProperties.put("label", "test news");
    testNews.setProperties(testNewsProperties);
    testNews.setId(3);
    newsTargets.add(testNews);

    when(metadataService.getMetadatas(metadataType.getName(), 0)).thenReturn(newsTargets);

    TargetsDefaultPermissionUpgrade targetsDefaultPermissionUpgrade = new TargetsDefaultPermissionUpgrade(initParams,
                                                                                                          metadataService,
                                                                                                          metadataStorage);

    targetsDefaultPermissionUpgrade.processUpgrade(null, null);
    verify(metadataStorage, times(2)).updateMetadata(any());
    assertEquals(2, targetsDefaultPermissionUpgrade.getMigratedNoDefaultPermissionTargetsCount());
  }
}
