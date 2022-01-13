package org.exoplatform.news.upgrade.targets;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.news.rest.NewsTargetingEntity;
import org.exoplatform.news.service.NewsTargetingService;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*", "jdk.internal.reflect.*", "javax.xml.*", "org.apache.xerces.*", "org.xml.*" })

public class DeleteNewsTargetsUpgradePluginTest {

  @Mock
  NewsTargetingService newsTargetingService;
  
  @Test
  public void testNewsDeleteTargetsMigration() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.addons.platform");
    initParams.addParameter(valueParam);
    
    NewsTargetingEntity newsTarget1 = new NewsTargetingEntity();
    newsTarget1.setName("target1");
    newsTarget1.setName("target 1");
    
    NewsTargetingEntity newsTarget2 = new NewsTargetingEntity();
    newsTarget1.setName("target2");
    newsTarget1.setName("target 2");
    
    List<NewsTargetingEntity> newsTargets = new ArrayList<>();
    newsTargets.add(newsTarget1);
    newsTargets.add(newsTarget2);

    when(newsTargetingService.getTargets()).thenReturn(newsTargets);
    DeleteNewsTargetsUpgradePlugin deleteNewsTargetsUpgradePlugin = new DeleteNewsTargetsUpgradePlugin(initParams, newsTargetingService);
    deleteNewsTargetsUpgradePlugin.processUpgrade(null, null);
    verify(newsTargetingService, times(2)).deleteTargetByName(any());
    assertEquals(2, deleteNewsTargetsUpgradePlugin.getNewsTargetsCount());
  }
}
