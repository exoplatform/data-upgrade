/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.upgrade;

import org.exoplatform.commons.upgrade.UpgradePluginException;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.monitor.jvm.ServerStartupWaiter;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.emailConnector.service.EmailManagedDisconnectionService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Disconnects the users whose stored mail connector id is a number written in
 * another form than its canonical one ("012", "+12"). While stored ids were
 * compared as text, such a user was left out of the disconnection of a connector
 * whose provider changed and of the cleanup of a deleted connector, and may still
 * hold a setting for a connector that no longer applies. The selection and the
 * disconnections live in the email connector's disconnection service; this
 * plugin only triggers them once. The service is a Spring bean of the
 * connector's context, resolved when the plugin runs and after the server has
 * finished starting, as for the contact sort key upgrade of this module. A run
 * that leaves a user connected is reported failed, so the framework does not
 * record it and a later startup selects the users left.
 */
public class EmailConnectorIdUpgradePlugin extends UpgradeProductPlugin {

  private static final Log    LOG         = ExoLogger.getExoLogger(EmailConnectorIdUpgradePlugin.class);

  private static final String DESCRIPTION = "Email connector id upgrade";

  public EmailConnectorIdUpgradePlugin(InitParams initParams) {
    super(initParams);
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long start = System.currentTimeMillis();
    int failed;
    try {
      awaitServerStartup();
      failed = disconnectUsersWithNonCanonicalConnectorId();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UpgradePluginException(DESCRIPTION + " was interrupted, it will be attempted again at a later startup", e);
    } catch (Exception e) {
      throw new UpgradePluginException(DESCRIPTION + " did not complete, it will be attempted again at a later startup", e);
    }
    if (failed > 0) {
      throw new UpgradePluginException(DESCRIPTION + " left " + failed
          + " users connected, it will be attempted again at a later startup");
    }
    LOG.info("{} completed in {} ms", DESCRIPTION, System.currentTimeMillis() - start);
  }

  void awaitServerStartup() throws InterruptedException {
    ServerStartupWaiter.awaitServerStartup(PortalContainer.getInstance(), DESCRIPTION);
  }

  int disconnectUsersWithNonCanonicalConnectorId() throws Exception {
    return ExoContainerContext.getService(EmailManagedDisconnectionService.class).disconnectUsersWithNonCanonicalConnectorId();
  }
}
