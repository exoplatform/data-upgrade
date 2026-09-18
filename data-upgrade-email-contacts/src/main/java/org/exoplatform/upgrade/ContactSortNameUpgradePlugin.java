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
import org.exoplatform.emailConnector.service.EmailContactService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.common.ContainerTransactional;

/**
 * Refiles the email contacts that carry no structured names: their stored sort
 * key used to be the display name as typed ("JOHN DOE", under J) and now
 * follows the contact form's reading of it ("DOE JOHN", under D). The key is
 * derived once at write time, so rows written before the change keep the old
 * key until recomputed. The recomputation itself lives in the email connector's
 * contact service; this plugin only triggers it once. The service is a Spring
 * bean of the connector's context, resolved when the plugin runs and after the
 * server has finished starting: taken in the constructor, the Kernel would
 * create it, and its persistence graph, while that context is still starting.
 * The plugin is registered under the connector's profile and asynchronous:
 * the wait for the server startup is refused on the startup thread.
 */
public class ContactSortNameUpgradePlugin extends UpgradeProductPlugin {

  private static final Log    LOG         = ExoLogger.getExoLogger(ContactSortNameUpgradePlugin.class);

  private static final String DESCRIPTION = "Email contacts sort key upgrade";

  public ContactSortNameUpgradePlugin(InitParams initParams) {
    super(initParams);
  }

  @Override
  @ContainerTransactional
  public void processUpgrade(String oldVersion, String newVersion) {
    upgrade();
  }

  void upgrade() {
    long start = System.currentTimeMillis();
    try {
      awaitServerStartup();
      int refiled = contactService().recomputeContactSortNames();
      LOG.info("{}: {} contacts refiled in {} ms", DESCRIPTION, refiled, System.currentTimeMillis() - start);
    } catch (Exception e) {
      throw new UpgradePluginException(DESCRIPTION + " did not complete, it will be attempted again at a later startup", e);
    }
  }

  void awaitServerStartup() throws InterruptedException {
    ServerStartupWaiter.awaitServerStartup(PortalContainer.getInstance(), DESCRIPTION);
  }

  EmailContactService contactService() {
    return ExoContainerContext.getService(EmailContactService.class);
  }
}
