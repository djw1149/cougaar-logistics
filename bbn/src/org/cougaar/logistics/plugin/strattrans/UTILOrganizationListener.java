/*
 * <copyright>
 *  Copyright 1997-2003 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects Agency (DARPA).
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the Cougaar Open Source License as published by
 *  DARPA on the Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THE COUGAAR SOFTWARE AND ANY DERIVATIVE SUPPLIED BY LICENSOR IS
 *  PROVIDED 'AS IS' WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS OR
 *  IMPLIED, INCLUDING (BUT NOT LIMITED TO) ALL IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, AND WITHOUT
 *  ANY WARRANTIES AS TO NON-INFRINGEMENT.  IN NO EVENT SHALL COPYRIGHT
 *  HOLDER BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT OR CONSEQUENTIAL
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE OF DATA OR PROFITS,
 *  TORTIOUS CONDUCT, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.
 * </copyright>
 */

package org.cougaar.logistics.plugin.strattrans;

import java.util.Enumeration;

import org.cougaar.glm.ldm.asset.Organization;

import org.cougaar.lib.callback.UTILFilterCallbackListener;

/**
 * Organization listener -- can be used with OrganizationCallback.
 */

public interface UTILOrganizationListener extends UTILFilterCallbackListener {

  /** 
   * Defines organizations you find interesting. 
   * @param an Organization to check for interest
   * @return boolean true if organizations is interesting
   */
  public boolean interestingOrganization (Organization a);

  /**
   * Place to handle updated organizations.
   * @param Enumeration e: new organizations found in the container
   */
  public void handleNewOrganizations     (Enumeration e);

  /**
   * Place to handle changed organizations.
   * @param Enumeration e: changed organizations found in the container
   */
  public void handleChangedOrganizations (Enumeration e);
}
        
        
                
                        
                
        
        