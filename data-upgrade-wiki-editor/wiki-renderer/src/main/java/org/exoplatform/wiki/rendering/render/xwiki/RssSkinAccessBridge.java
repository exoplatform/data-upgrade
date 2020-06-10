/*
 * Copyright (C) 2003-2013 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.wiki.rendering.render.xwiki;

import javax.inject.Singleton;

import org.xwiki.bridge.SkinAccessBridge;
import org.xwiki.component.annotation.Component;

/**
 * Created by The eXo Platform SAS
 * Author : eXoPlatform
 *          exo@exoplatform.com
 * Jun 12, 2013  
 */
@Component
@Singleton
public class RssSkinAccessBridge implements SkinAccessBridge {
  public static final String RSS_IMAGE = "/eXoSkin/skin/images/themes/default/Icons/skinIcons/16x16/RSS.gif";
  
  @Override
  public String getSkinFile(String fileName) {
    return RSS_IMAGE;
  }

  @Override
  public String getIconURL(String iconName) {
    return RSS_IMAGE;
  }
  
}
