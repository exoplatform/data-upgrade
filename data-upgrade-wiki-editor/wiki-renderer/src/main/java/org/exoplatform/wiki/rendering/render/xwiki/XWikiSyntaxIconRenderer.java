/*
 * Copyright (C) 2003-2012 eXo Platform SAS.
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

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;

import org.xwiki.rendering.internal.renderer.xwiki20.XWikiSyntaxEscapeWikiPrinter;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.transformation.icon.IconTransformationConfiguration;

/**
 * Generate a Xwiki syntax string representation of an ResourceReference
 */
public class XWikiSyntaxIconRenderer {
  
  private IconTransformationConfiguration iconTransformationConfiguration;

  public XWikiSyntaxIconRenderer(IconTransformationConfiguration iconTransformationConfiguration) {
    this.iconTransformationConfiguration = iconTransformationConfiguration;
  }

  public void renderIcon(XWikiSyntaxEscapeWikiPrinter printer, ResourceReference image) {
    Properties iconMappings = this.iconTransformationConfiguration.getMappings();
    Iterator<Entry<Object, Object>> iter = iconMappings.entrySet().iterator();
    while (iter.hasNext()) {
      Entry<Object, Object> entry = iter.next();
      if (entry.getValue().equals(image.getReference())) {
        printer.print(entry.getKey().toString());
        return;
      }
    }   
  }
}
