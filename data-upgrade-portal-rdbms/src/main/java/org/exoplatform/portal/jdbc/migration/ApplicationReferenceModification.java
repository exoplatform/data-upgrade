package org.exoplatform.portal.jdbc.migration;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.container.component.BaseComponentPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ObjectParameter;

public class ApplicationReferenceModification extends BaseComponentPlugin {

  private String modificationType = ApplicationReferenceModificationType.UPDATE.name();

  private String oldApplicationName;

  private String oldPortletName;

  private String newApplicationName;

  private String newPortletName;

  /**
   * This constructor is used for Kernel injection of component plugin
   * 
   * @param params {@link InitParams} injected by kernel containing an object
   *          containing real fields of {@link ApplicationReferenceModification}
   */
  public ApplicationReferenceModification(InitParams params) {
    if (params != null && params.getObjectParamIterator() != null && params.getObjectParamIterator().hasNext()) {
      ObjectParameter parameter = params.getObjectParamIterator().next();
      if (parameter != null && parameter.getObject() instanceof ApplicationReferenceModification) {
        this.oldApplicationName = ((ApplicationReferenceModification) parameter.getObject()).getOldApplicationName();
        this.oldPortletName = ((ApplicationReferenceModification) parameter.getObject()).getOldPortletName();
        this.newApplicationName = ((ApplicationReferenceModification) parameter.getObject()).getNewApplicationName();
        this.newPortletName = ((ApplicationReferenceModification) parameter.getObject()).getNewPortletName();
        this.modificationType = ((ApplicationReferenceModification) parameter.getObject()).getModificationType();
      }
    }
  }

  /**
   * This constructor is used for init parameter initialization
   */
  public ApplicationReferenceModification() {
  }

  public String getOldApplicationName() {
    return oldApplicationName;
  }

  public void setOldApplicationName(String oldApplicationName) {
    this.oldApplicationName = oldApplicationName;
  }

  public String getOldPortletName() {
    return oldPortletName;
  }

  public void setOldPortletName(String oldPortletName) {
    this.oldPortletName = oldPortletName;
  }

  public String getNewApplicationName() {
    return newApplicationName;
  }

  public void setNewApplicationName(String newApplicationName) {
    this.newApplicationName = newApplicationName;
  }

  public String getNewPortletName() {
    return newPortletName;
  }

  public void setNewPortletName(String newPortletName) {
    this.newPortletName = newPortletName;
  }

  public String getModificationType() {
    return modificationType;
  }

  public String getNewContentId() {
    return getNewApplicationName() + "/" + getNewPortletName();
  }

  public String getOldContentId() {
    return getOldApplicationName() + "/" + getOldPortletName();
  }

  public void setModificationType(String modificationType) {
    this.modificationType = modificationType;
  }

  public boolean isModification() {
    return StringUtils.equalsIgnoreCase(ApplicationReferenceModificationType.UPDATE.name(), modificationType);
  }

  public boolean isRemoval() {
    return StringUtils.equalsIgnoreCase(ApplicationReferenceModificationType.REMOVE.name(), modificationType);
  }

  public enum ApplicationReferenceModificationType {
    REMOVE,
    UPDATE;
  }
}
