/*
 * <copyright>
 *  Copyright 2001-2003 BBNT Solutions, LLC
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

public class CategoryNode extends DBUIDNode {

    public static final String SEA = "Sea";
    public static final String AIR = "Air";
    public static final String GROUND = "Land";
    public static final String SELF = "Land: Self-Propelled";
    public static final String MISC= "Misc";

    public CategoryNode(UIDGenerator generator, String dbuid) {
	super(generator, dbuid);
	setMode(Node.MODE_AGGREGATE);
    }
    
    public boolean isLeaf () {
	return false;
    }
    
    public int getType () { return UIDGenerator.CATEGORY; };
    
    public String toString() {
	return "CategoryNode " + getDisplayName () + ", uid " + getUID(); 
    }
}
