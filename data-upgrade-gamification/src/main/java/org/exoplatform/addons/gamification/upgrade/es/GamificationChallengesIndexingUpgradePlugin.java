package org.exoplatform.addons.gamification.upgrade.es;

import org.exoplatform.addons.gamification.connector.ChallengesIndexingServiceConnector;
import org.exoplatform.addons.gamification.service.ChallengeService;
import org.exoplatform.addons.gamification.service.dto.configuration.Challenge;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.List;

public class GamificationChallengesIndexingUpgradePlugin extends UpgradeProductPlugin {

  private static final Log      log = ExoLogger.getLogger(GamificationChallengesIndexingUpgradePlugin.class.getName());

  private final IndexingService indexingService;

  private ChallengeService      challengeService;

  private int                     challengesIndexingCount;

  public GamificationChallengesIndexingUpgradePlugin(InitParams initParams, IndexingService indexingService) {
    super(initParams);
    this.indexingService = indexingService;
  }

  @Override
  public void processUpgrade(String s, String s1) {
    log.info("Start indexing old challenges");
    long startupTime = System.currentTimeMillis();
    try {
      int limit = 20 ;
      int offset = 0 ;
      boolean hasNext = true ;
      while (hasNext) {
        List<Challenge> challenges = getChallengeService().getAllChallenges(offset, limit);
        hasNext = challenges.size() == limit ? true : false ;
        for (Challenge challenge : challenges) {
          indexingService.index(ChallengesIndexingServiceConnector.INDEX, String.valueOf(challenge.getId()));
          challengesIndexingCount++ ;
        }
        offset += limit;
      }
      log.info("End indexing of '{}' old challenges. It took {} ms",
              challengesIndexingCount,
               (System.currentTimeMillis() - startupTime));
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("An unexpected error occurs when indexing old challenges", e);
      }
    }
  }

  private ChallengeService getChallengeService() {
    if (challengeService == null) {
      challengeService = CommonsUtils.getService(ChallengeService.class);
    }
    return challengeService;
  }

  /**
   * @return the newsIndexingCount
   */
  public int getIndexingCount() {
    return challengesIndexingCount;
  }
}
