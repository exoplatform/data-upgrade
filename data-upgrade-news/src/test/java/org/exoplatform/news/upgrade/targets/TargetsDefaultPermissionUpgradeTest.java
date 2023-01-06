package org.exoplatform.news.upgrade.targets;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

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

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*", "jdk.internal.*", "javax.xml.*", "org.apache.xerces.*", "org.xml.*",
    "com.sun.org.apache.*", "org.w3c.*" })
@PrepareForTest({ ExoContainerContext.class, PortalContainer.class, RequestLifeCycle.class })
public class TargetsDefaultPermissionUpgradeTest {

  @Mock
  private MetadataService metadataService;

  @Mock
  private MetadataStorage metadataStorage;

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
