package org.exoplatform.ecms.upgrade.activities;


import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.storage.cache.CachedActivityStorage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TempalteParamsUpgradePluginTest {


  private  PortalContainer container;

  private  EntityManagerService entityManagerService;

  private ActivityManager activityManager;

  private CachedActivityStorage cachedActivityStorage ;

  @Before
  public void setUp() {
    container = PortalContainer.getInstance();
    activityManager = CommonsUtils.getService(ActivityManager.class);
    entityManagerService = CommonsUtils.getService(EntityManagerService.class);
    cachedActivityStorage = CommonsUtils.getService(CachedActivityStorage.class);
    begin();
  }
  protected void begin() {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  @After
  public void tearDown() {
    end();
  }

  @Test
  public void templateParamsUpgradePluginTest() {
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);
    valueParam.setName("new.template.params.key");
    valueParam.setValue("WORKSPACE");
    initParams.addParameter(valueParam);
    valueParam.setName("old.template.params.key");
    valueParam.setValue("WORKSPACE  ");
    initParams.addParameter(valueParam);

    //activity with correct template params key
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setType("MY_ACTIVITY");
    HashMap<String, String> templateParams = new HashMap<>();
    templateParams.put("WORKSPACE", "collaboration");
    activity.setTemplateParams(templateParams);
    activity.setUserId("1");
    activity.setTitle("ActivityWithCorrectTemplateParamsKey");
    Identity identity = mock(Identity.class);
    when(identity.isEnable()).thenReturn(true);
    when(identity.getId()).thenReturn("1");
    activityManager.saveActivityNoReturn(identity,activity);

    TemplateParamsUpgradePlugin
        templateParamsUpgradePlugin = new TemplateParamsUpgradePlugin(container, entityManagerService,initParams );
    //processUpgrade
    templateParamsUpgradePlugin.processUpgrade(null, null);

    //assert no update executed
    assertEquals(0,templateParamsUpgradePlugin.getTemplatePramasUpdatedCount());

    //activity with wrong template params key
    ExoSocialActivity activity1 = new ExoSocialActivityImpl();
    activity.setType("MY_ACTIVITY");
    HashMap<String, String> templateParams1 = new HashMap<>();
    templateParams1.put("WORKSPACE  ", "collaboration");
    activity1.setTemplateParams(templateParams1);
    activity1.setUserId("1");
    activity1.setTitle("ActivityWithWrongTemplateParamsKey");
    activityManager.saveActivityNoReturn(identity,activity1);

    //activity with lowercase template params key
    ExoSocialActivity activity2 = new ExoSocialActivityImpl();
    activity.setType("MY_ACTIVITY");
    HashMap<String, String> templateParams2 = new HashMap<>();
    templateParams2.put("workspace", "collaboration");
    activity2.setTemplateParams(templateParams2);
    activity2.setUserId("1");
    activity2.setTitle("ActivityWithlowercaseTemplateParamsKey");
    activityManager.saveActivityNoReturn(identity,activity2);
    templateParamsUpgradePlugin.processUpgrade(null, null);

    //assert 3 activities created
    assertEquals(3, activityManager.getActivitiesByPoster(identity).getSize());

    //assert update executed once for the wrong activity template params key
    assertEquals(1,templateParamsUpgradePlugin.getTemplatePramasUpdatedCount());

    cachedActivityStorage.clearOwnerCache(identity.getId());
    List<ExoSocialActivity> exoSocialActivityList = activityManager.getActivitiesByPoster(identity).loadAsList(0, 3);

    //assert wrong activity template params key updated
    assertTrue( exoSocialActivityList.get(1).getTemplateParams().containsKey("WORKSPACE"));
    assertTrue( !exoSocialActivityList.get(1).getTemplateParams().containsKey("WORKSPACE  "));
  }

  protected void end() {
    RequestLifeCycle.end();
  }
}
