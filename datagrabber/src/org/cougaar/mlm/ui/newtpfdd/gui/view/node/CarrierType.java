/*
 * <copyright>
 *  Copyright 2001 BBNT Solutions, LLC
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
package org.cougaar.mlm.ui.newtpfdd.gui.view.node;

import org.cougaar.mlm.ui.newtpfdd.gui.view.UIDGenerator;

public class CarrierType extends DBUIDNode {
    private String carrierName;
    private String carrierType;
  //    private String carrierNameDBID;
  //    private String carrierTypeDBID;
  private boolean isSelfPropelled = false;
  
    public CarrierType (UIDGenerator generator, String dbuid) {
	  super (generator, dbuid);
	}

    public String getDisplayName() { return super.getDisplayName() + " (" + getChildCount() + ")"; }

  public int getType () { return UIDGenerator.CARRIER_PROTOTYPE; }

    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }

    public String getCarrierType() { return carrierType; }
    public void setCarrierType(String carrierType) { this.carrierType = carrierType; }

    public boolean isSelfPropelled() { return isSelfPropelled; }
    public void setSelfPropelled(boolean selfProp) { this.isSelfPropelled = selfProp; }

  //    public String getCarrierNameDBID() { return carrierNameDBID; }
  //    public void setCarrierNameDBID(String carrierNameDBID) { this.carrierNameDBID = carrierNameDBID; }

  //    public String getCarrierTypeDBID() { return carrierTypeDBID; }
  //    public void setCarrierTypeDBID(String carrierTypeDBID) { this.carrierTypeDBID = carrierTypeDBID; }
}