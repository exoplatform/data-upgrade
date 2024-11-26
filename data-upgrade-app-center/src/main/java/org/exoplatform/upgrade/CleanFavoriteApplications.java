package org.exoplatform.upgrade;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.appcenter.entity.ApplicationEntity;
import io.meeds.appcenter.entity.FavoriteApplicationEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

public class CleanFavoriteApplications extends UpgradeProductPlugin {

  private static final Log     log = ExoLogger.getLogger(CleanFavoriteApplications.class.getName());

  private EntityManagerService entityManagerService;

  public CleanFavoriteApplications(InitParams initParams, EntityManagerService entityManagerService) {
    super(initParams);
    this.entityManagerService = entityManagerService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    List<Object[]> duplicatedFavoriteAppsEntityList = getDuplicatedFavoriteAppsEntityList(entityManager);
    if (duplicatedFavoriteAppsEntityList.isEmpty()) {
      return;
    }
    log.info("Start upgrade of cleaning for favorite applications, {} favorite applications should be deleted",
             duplicatedFavoriteAppsEntityList.size());
    Set<String> uniqueUserNames = new HashSet<>();
    duplicatedFavoriteAppsEntityList.forEach(favoriteApplicationEntity -> uniqueUserNames.add((String) favoriteApplicationEntity[2]));
    log.info("{} favorite applications duplicated for {} users", duplicatedFavoriteAppsEntityList.size(), uniqueUserNames.size());
    int favoriteApplicationCount = cleanFavoriteApps(entityManager, duplicatedFavoriteAppsEntityList);
    log.info("End upgrade : Deleted {} favorite applications done it took {} ms",
             favoriteApplicationCount,
             System.currentTimeMillis() - startupTime);
  }

  private List<Object[]> getDuplicatedFavoriteAppsEntityList(EntityManager entityManager) {
    String selectQuery = "SELECT * FROM AC_FAVORITE_APPLICATION favoriteApp "
        + "WHERE (favoriteApp.APPLICATION_ID, favoriteApp.USER_NAME) IN ("
        + "    SELECT favoriteApplication.APPLICATION_ID, favoriteApplication.USER_NAME "
        + "    FROM AC_FAVORITE_APPLICATION favoriteApplication "
        + "    GROUP BY favoriteApplication.APPLICATION_ID, favoriteApplication.USER_NAME " + "    HAVING COUNT(*) > 1)";
    Query nativeQuery = entityManager.createNativeQuery(selectQuery);
    return nativeQuery.getResultList();
  }

  protected int cleanFavoriteApps(EntityManager entityManager, List<Object[]> duplicatedFavoriteApplicationsEntityList) {
    List<FavoriteApplicationEntity> duplicatedFavoriteAppsEntityList =
                                                                     toFavoriteAppsEntityList(duplicatedFavoriteApplicationsEntityList);
    List<FavoriteApplicationEntity> favoriteAppsEntityList = new ArrayList<>();
    int favoriteAppCount = 0;
    for (FavoriteApplicationEntity favoriteApplicationEntity : duplicatedFavoriteAppsEntityList) {
      FavoriteApplicationEntity favoriteAppEntityOfIdNull =
                                                          new FavoriteApplicationEntity(0L,
                                                                                        favoriteApplicationEntity.getApplication(),
                                                                                        favoriteApplicationEntity.getUserName(),
                                                                                        favoriteApplicationEntity.getOrder());
      if (favoriteAppsEntityList.contains(favoriteAppEntityOfIdNull)) {
        deleteFavoriteApplication(entityManager, favoriteApplicationEntity.getId());
        favoriteAppCount += 1;
      } else {
        favoriteAppsEntityList.add(favoriteAppEntityOfIdNull);
      }
    }
    return favoriteAppCount;
  }

  List<FavoriteApplicationEntity> toFavoriteAppsEntityList(List<Object[]> objects) {
    List<FavoriteApplicationEntity> favoriteAppsEntityList = new ArrayList<>();
    objects.forEach(object -> {
      ApplicationEntity applicationEntity = new ApplicationEntity();
      applicationEntity.setId((Long) object[1]);
      favoriteAppsEntityList.add(new FavoriteApplicationEntity((Long) object[0], applicationEntity, (String) object[2], null));
    });
    return favoriteAppsEntityList;
  }

  @ExoTransactional
  protected void deleteFavoriteApplication(EntityManager entityManager, Long favoriteApplicationId) {
    String deleteQuery = "DELETE FROM AC_FAVORITE_APPLICATION WHERE ID = " + favoriteApplicationId;
    Query query = entityManager.createNativeQuery(deleteQuery);
    query.executeUpdate();
  }
}
