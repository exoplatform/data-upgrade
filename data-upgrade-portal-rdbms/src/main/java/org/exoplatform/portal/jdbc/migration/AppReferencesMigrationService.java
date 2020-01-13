package org.exoplatform.portal.jdbc.migration;

import java.util.*;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.application.registry.*;
import org.exoplatform.portal.config.model.ApplicationType;
import org.exoplatform.portal.mop.jdbc.dao.WindowDAO;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AppReferencesMigrationService {
  public static final String                            EVENT_LISTENER_KEY                = "PORTAL_APPLICATIONS_MIGRATION";

  private static final Log                              LOG                               =
                                                            ExoLogger.getExoLogger(AppReferencesMigrationService.class);

  private ApplicationRegistryService                    appService;

  private WindowDAO                                     windowDAO;

  private Map<String, ApplicationReferenceModification> applicationReferenceModifications = new HashMap<>();

  public AppReferencesMigrationService(ApplicationRegistryService appService, WindowDAO windowDAO) {
    this.appService = appService;
    this.windowDAO = windowDAO;
  }

  public void doMigration() throws Exception {
    if (applicationReferenceModifications.isEmpty()) {
      return;
    }
    migrateApplicationRegistryEntries();
    migrateApplicationReferencesInMOP();
  }

  private void migrateApplicationReferencesInMOP() {
    long t = System.currentTimeMillis();
    LOG.info("| \\ START::Application References migration from pages and sites ---------------------------------");

    ArrayList<String> contentIdsToUpdate = new ArrayList<>(applicationReferenceModifications.keySet());
    for (String oldContentId : contentIdsToUpdate) {
      ApplicationReferenceModification applicationModification = applicationReferenceModifications.get(oldContentId);
      int modifiedLines = 0;
      if (applicationModification.isModification()) {
        modifiedLines = windowDAO.updateContentId(oldContentId, applicationModification.getNewContentId());
      } else if (applicationModification.isRemoval()) {
        modifiedLines = windowDAO.deleteByContentId(oldContentId);
      }
      MigrationContext.restartTransaction();
      if (modifiedLines > 0) {
        LOG.info("| -- UPDATE::Application Reference '{}' ({} items) migrated successfully", oldContentId, modifiedLines);
      }
    }

    LOG.info("| / END::Application References migration from app registry in {}ms", System.currentTimeMillis() - t);
  }

  private void migrateApplicationRegistryEntries() throws Exception {
    long t = System.currentTimeMillis();
    LOG.info("| \\ START::Application References migration from app registry ---------------------------------");
    List<ApplicationCategory> categories = appService.getApplicationCategories();
    if (categories == null || categories.isEmpty()) {
      return;
    }

    for (ApplicationCategory category : categories) {
      if (MigrationContext.isForceStop()) {
        break;
      }

      long t1 = System.currentTimeMillis();
      try {
        for (Application app : category.getApplications()) {
          if (app == null || !ApplicationType.PORTLET.equals(app.getType()) || isApplicationToRemove(app)) {
            continue;
          }
          String oldContentId = app.getContentId();
          if (isApplicationToRemove(app)) {
            LOG.info("|  -- UPDATE::remove application reference from registry '{}'", oldContentId);
            appService.remove(app);
          } else if (isApplicationToModify(app)) {
            modifyApplicationReference(app);
            LOG.info("|  -- UPDATE::migrate application reference in registry from '{}' to '{}'",
                     oldContentId,
                     app.getContentId());
            appService.save(category, app);
          }
        }
      } catch (Exception ex) {
        LOG.error("|  / END::migrate Application References from app registry category {} in {}ms",
                  category.getName(),
                  System.currentTimeMillis() - t1,
                  ex);
      } finally {
        MigrationContext.restartTransaction();
      }
    }
    LOG.info("| / END::Application References migration from app registry in {}ms", System.currentTimeMillis() - t);
  }

  public void addApplicationModification(ApplicationReferenceModification applicationReferenceModification) {
    if (applicationReferenceModification == null) {
      throw new IllegalArgumentException("applicationReferenceModification plugin is mandatory");
    }
    applicationReferenceModifications.put(applicationReferenceModification.getOldApplicationName() + "/"
        + applicationReferenceModification.getOldPortletName(), applicationReferenceModification);
  }

  public boolean isApplicationToRemove(Object app) {
    String contentId = getApplicationContentId(app);
    return StringUtils.isNotBlank(contentId) && applicationReferenceModifications.containsKey(contentId)
        && applicationReferenceModifications.get(contentId).isRemoval();
  }

  public boolean isApplicationToModify(Object app) {
    String contentId = getApplicationContentId(app);
    return StringUtils.isNotBlank(contentId) && applicationReferenceModifications.containsKey(contentId)
        && applicationReferenceModifications.get(contentId).isModification();
  }

  public String modifyApplicationReference(Object app) {
    if (app instanceof Application) {
      Application application = ((Application) app);
      String contentId = getApplicationContentId(application);
      ApplicationReferenceModification applicationReferenceModification = applicationReferenceModifications.get(contentId);
      String newContentId = applicationReferenceModification.getNewContentId();

      application.setApplicationName(applicationReferenceModification.getNewPortletName());
      application.setContentId(newContentId);

      return newContentId;
    } else if (app instanceof String) {
      ApplicationReferenceModification applicationReferenceModification = applicationReferenceModifications.get(app.toString());
      return applicationReferenceModification == null ? null : applicationReferenceModification.getNewContentId();
    }
    return null;
  }

  private String getApplicationContentId(Object app) {
    String contentId = null;
    if (app instanceof Application) {
      contentId = ((Application) app).getContentId();
    } else if (app instanceof String) {
      contentId = app.toString();
    }
    return contentId;
  }

}
