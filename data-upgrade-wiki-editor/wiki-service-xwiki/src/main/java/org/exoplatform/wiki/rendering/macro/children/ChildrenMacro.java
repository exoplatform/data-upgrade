/*
 * Copyright (C) 2003-2010 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.wiki.rendering.macro.children;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.wiki.rendering.context.MarkupContextManager;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.rendering.block.*;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component("children")
public class ChildrenMacro extends AbstractMacro<ChildrenMacroParameters> {
  private static final Log    log         = ExoLogger.getLogger(ChildrenMacro.class);

  /**
   * The description of the macro
   */
  private static final String DESCRIPTION = "Display children and descendants of a specific page";
  
  /**
   * Used to get the current syntax parser.
   */
  @Inject
  private ComponentManager    componentManager;
  
  /**
   * Used to get the current context
   */
  @Inject
  private Execution           execution;
  
  /**
   * Used to get the build context for document
   */
  @Inject
  private MarkupContextManager markupContextManager;
  
  public ChildrenMacro() {
    super("Children", DESCRIPTION, ChildrenMacroParameters.class);
    setDefaultCategory(DEFAULT_CATEGORY_NAVIGATION);
  }

  @Override
  public List<Block> execute(ChildrenMacroParameters parameters, String content, MacroTransformationContext context) throws MacroExecutionException {
    Block includePageBlock = new RawBlock("<exo-wiki-children-pages></exo-wiki-children-pages>", Syntax.XHTML_1_0);

    Block container = new GroupBlock(Arrays.asList(includePageBlock));
    container.setParameter("class", "wiki-children-pages");

    return Collections.singletonList(container);
  }

  @Override
  public boolean supportsInlineMode() {
    return true;
  }
}
