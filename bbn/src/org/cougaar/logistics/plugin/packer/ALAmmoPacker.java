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

package org.cougaar.logistics.plugin.packer; 

import java.util.*;

import org.cougaar.planning.ldm.plan.AllocationResultDistributor;
import org.cougaar.planning.ldm.plan.AspectType;
import org.cougaar.planning.ldm.plan.PlanElement;
import org.cougaar.planning.ldm.plan.Task;
import org.cougaar.planning.ldm.plan.Verb;

import org.cougaar.util.Sortings;
import org.cougaar.util.UnaryPredicate;

import org.cougaar.logistics.ldm.Constants;
import org.cougaar.glm.ldm.asset.Ammunition;
import org.cougaar.glm.packer.Packer;
import org.cougaar.glm.packer.AggregationClosure;
import org.cougaar.glm.packer.AmmoTransport;
import org.cougaar.planning.plugin.util.PluginHelper;
import org.cougaar.planning.ldm.plan.AllocationResult;
import org.cougaar.planning.ldm.plan.AllocationResultDistributor;
import org.cougaar.planning.ldm.plan.AspectType;
import org.cougaar.planning.ldm.plan.AspectValue;
import org.cougaar.planning.ldm.plan.PlanElement;
import org.cougaar.planning.ldm.plan.Task;
import org.cougaar.planning.ldm.plan.Verb;
import org.cougaar.core.blackboard.IncrementalSubscription;

import org.cougaar.glm.packer.GenericPlugin;

/**
 * ALAmmoPacker - handles packing ammo supply requests
 * 
 */
public class ALAmmoPacker extends Packer {

  /**
   * ALAmmoPacker - constructor 
   */
  public ALAmmoPacker() {
    super();
  }

  protected void execute() {
      super.execute();
      
      Collection unplannedInternal = getBlackboardService().query (new UnaryPredicate () {
	    public boolean execute (Object obj) {
		if (obj instanceof Task) {
		    Task task = (Task) obj;
		    return ((task.getPrepositionalPhrase(GenericPlugin.INTERNAL) !=null) &&
			    task.getPlanElement () == null);
		}
		return false;
	    }
	}
								 );
      if (!unplannedInternal.isEmpty()) {
	  if (getLoggingService().isInfoEnabled()) {
	      getLoggingService().info ("found " + unplannedInternal.size () + " unplanned internal tasks.");
	  }
	  handleUnplanned (unplannedInternal);
      }
  }


  /**
   * getTaskPredicate - returns predicate which screens for ammo supply tasks
   * 
   * @return UnaryPredicate screens for incoming tasks which the packer should
   * handle
   */
  public UnaryPredicate getTaskPredicate() {
    return ALAmmoPackerPredicate.getInputTaskPredicate();
  }

  /**
   * getPlanElementPredicate - returns predicate which screens for plan 
   * elements which will need to have allocation results set. In this case, 
   * plan elements associated with Ammunition Supply tasks
   *
    * @return UnaryPredicate screens for plan elements which the packer is 
   * reponsible
   */
  public UnaryPredicate getPlanElementPredicate() {
    return ALAmmoPackerPredicate.getPlanElementPredicate();
  }

  /*
   * getAggregationClosure - returns AggregationClosure for transporting ammo
   */
  public AggregationClosure getAggregationClosure(ArrayList tasks) {
    // BOZO - source and destination should be taken from the tasks not
    // hardcoded.
    AmmoTransport ac = new AmmoTransport();

    ac.setGenericPlugin(this);
    ac.setDestinations (tasks);
    
    return ac;
  }

  protected Collection groupByAggregationClosure(Collection tasks) {
    return AmmoTransport.getTransportGroups(tasks);
  }
  protected void updateAllocationResult(IncrementalSubscription planElements) {
    // Make sure that quantity preferences get returned on the allocation
    // results. Transport thread may not have filled them in.
    Enumeration changedPEs = planElements.getChangedList();
    while (changedPEs.hasMoreElements()) {
      PlanElement pe = (PlanElement)changedPEs.nextElement();
      
      if (!PluginHelper.checkChangeReports (planElements.getChangeReports(pe), 
					    PlanElement.ReportedResultChangeReport.class))
	  continue;

      if (PluginHelper.updatePlanElement(pe)) {
        boolean needToCorrectQuantity = false;
        
        AllocationResult estimatedAR = pe.getEstimatedResult();
        double prefValue = 
          pe.getTask().getPreference(AspectType.QUANTITY).getScoringFunction().getBest().getAspectValue().getValue();
        
        AspectValue[] aspectValues = estimatedAR.getAspectValueResults();
        
	boolean foundQuantity = false;
        for (int i = 0; i < aspectValues.length; i++) {
          if (aspectValues[i].getAspectType() == AspectType.QUANTITY) {
            if (aspectValues[i].getValue() != prefValue) {
              // set the quantity to be the preference quantity
              aspectValues[i] = aspectValues[i].dupAspectValue(prefValue); 
              needToCorrectQuantity = true;
            }
	    foundQuantity = true;
            break;
          }
        }

	// BOZO - 
	// Gordon Vidaver 08/16/02 - 
	// someone should really fix this eventually
	// some expansion is not setting the QUANTITY aspect on the aspect value, and Inventory Manager
	// gets upset about this missing aspect in findCommittedRefill
	if (!foundQuantity) {
	  AspectValue [] copy = new AspectValue [aspectValues.length+1];
	  System.arraycopy(aspectValues,0,copy,0,aspectValues.length);
	  copy[aspectValues.length] = AspectValue.newAspectValue (AspectType.QUANTITY, prefValue);
	  aspectValues=copy;
	  if (getLoggingService().isDebugEnabled()) {
            getLoggingService().debug("Packer.updateAllocationResult - fixing quantity on estimated AR of pe " + pe.getUID() + 
				      " task " + pe.getTask().getUID());
	  }
	}

        if (needToCorrectQuantity || !foundQuantity) {
	  if (getLoggingService().isDebugEnabled()) {
            getLoggingService().debug("Packer.updateAllocationResult - fixing quantity on estimated AR of pe " + pe.getUID());
	  } 

          AllocationResult correctedAR = 
            new AllocationResult(estimatedAR.getConfidenceRating(),
                                 estimatedAR.isSuccess(),
                                 aspectValues);

          pe.setEstimatedResult(correctedAR);          
        }
        
        
        publishChange(pe);
      }
    }
  }
}
