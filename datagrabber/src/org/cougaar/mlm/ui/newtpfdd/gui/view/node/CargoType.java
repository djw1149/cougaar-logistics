/*
 * <copyright>
 *  
 *  Copyright 2001-2004 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects
 *  Agency (DARPA).
 * 
 *  You can redistribute this software and/or modify it under the
 *  terms of the Cougaar Open Source License as published on the
 *  Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  
 * </copyright>
 */
package org.cougaar.mlm.ui.newtpfdd.gui.view.node;

import org.cougaar.mlm.ui.newtpfdd.gui.view.UIDGenerator;

public class CargoType extends DBUIDNode {
    private String cargoName;
    private String cargoType;
    private String cargoNameDBID;
    private String cargoTypeDBID;
  private boolean isLowFi;
    
    public CargoType(UIDGenerator generator, String dbuid) {
	  super (generator, dbuid);
	}

    public String getDisplayName() { return super.getDisplayName() + " (" + getChildCount() + ")"; }

  public int getType () { return UIDGenerator.ASSET_PROTOTYPE; }

    public String getCargoName() { return cargoName; }
    public void setCargoName(String cargoName) { this.cargoName = cargoName; }

    public String getCargoType() { return cargoType; }
    public void setCargoType(String cargoType) { this.cargoType = cargoType; }

    public String getCargoNameDBID() { return cargoNameDBID; }
    public void setCargoNameDBID(String cargoNameDBID) { this.cargoNameDBID = cargoNameDBID; }

    public String getCargoTypeDBID() { return cargoTypeDBID; }
    public void setCargoTypeDBID(String cargoTypeDBID) { this.cargoTypeDBID = cargoTypeDBID; }

    public boolean isLowFi() { return isLowFi; }
    public void setLowFi(boolean val) { this.isLowFi = val; }
}
