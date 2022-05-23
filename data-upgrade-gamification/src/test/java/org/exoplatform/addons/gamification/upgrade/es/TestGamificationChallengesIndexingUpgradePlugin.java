package org.exoplatform.addons.gamification.upgrade.es;

import org.exoplatform.addons.gamification.service.ChallengeService;
import org.exoplatform.addons.gamification.service.dto.configuration.Challenge;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*", "jdk.internal.reflect.*", "javax.xml.*", "org.apache.xerces.*", "org.xml.*" })

public class TestGamificationChallengesIndexingUpgradePlugin {

  @Mock
  IndexingService indexingService;

  @PrepareForTest({ CommonsUtils.class })
  @Test
  public void testOldChallengesIndexing() {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.addons.gamification");
    initParams.addParameter(valueParam);

    Challenge challenge1 = new Challenge(1l,
                                         "Challenge 1",
                                         "description 1",
                                         1l,
                                         new Date(System.currentTimeMillis()).toString(),
                                         new Date(System.currentTimeMillis() + 1).toString(),
                                         Collections.emptyList(),
                                         10L,
                                         "gamification");
    Challenge challenge2 = new Challenge(1l,
                                         "Challenge 2",
                                         "description 2",
                                         1l,
                                         new Date(System.currentTimeMillis()).toString(),
                                         new Date(System.currentTimeMillis() + 1).toString(),
                                         Collections.emptyList(),
                                         10L,
                                         "gamification");
    List<Challenge> challenges = new ArrayList<>();
    challenges.add(challenge1);
    challenges.add(challenge2);
    ChallengeService challengeService = mock(ChallengeService.class);
    PowerMockito.mockStatic(CommonsUtils.class);
    when(CommonsUtils.getService(ChallengeService.class)).thenReturn(challengeService);
    when(challengeService.getAllChallenges(0, 20)).thenReturn(challenges);
    GamificationChallengesIndexingUpgradePlugin gamificationChallengesIndexingUpgradePlugin =
                                                                                            new GamificationChallengesIndexingUpgradePlugin(initParams,
                                                                                                                                            indexingService);
    gamificationChallengesIndexingUpgradePlugin.processUpgrade(null, null);
    assertEquals(2, gamificationChallengesIndexingUpgradePlugin.getIndexingCount());
  }

}
