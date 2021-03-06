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
package org.cougaar.logistics.plugin.trans;

import java.util.*;

import org.cougaar.glm.ldm.asset.Organization;

import org.cougaar.lib.callback.UTILFilterCallback;
import org.cougaar.lib.callback.UTILGenericListener;
import org.cougaar.lib.callback.UTILWorkflowCallback;

import org.cougaar.lib.vishnu.client.XMLizer;
import org.cougaar.lib.vishnu.client.custom.CustomVishnuAllocatorPlugin;

import org.cougaar.logistics.ldm.Constants;

import org.cougaar.planning.ldm.asset.Asset;

import org.cougaar.planning.ldm.plan.AspectType;
import org.cougaar.planning.ldm.plan.AspectValue;
import org.cougaar.planning.ldm.plan.PlanElement;
import org.cougaar.planning.ldm.plan.Preference;
import org.cougaar.planning.ldm.plan.Relationship;
import org.cougaar.planning.ldm.plan.RelationshipType;
import org.cougaar.planning.ldm.plan.Role;
import org.cougaar.planning.ldm.plan.ScoringFunction;
import org.cougaar.planning.ldm.plan.Task;

import org.cougaar.util.TimeSpan;
import org.cougaar.util.UnaryPredicate;

import org.w3c.dom.Document;

public class TranscomRouterVishnuPlugin extends TranscomVishnuPlugin {
  protected void addExpectedProviders() {
    try {
      String EURO_TRANSCOM_ID = (getMyParams().hasParam ("EuroTranscomRole")) ?
	getMyParams().getStringParam("EuroTranscomRole") :
	"EuroStrategicTransportationProvider";

      String CONUS_TRANSCOM_ID = (getMyParams().hasParam ("ConusTranscomRole")) ?
	getMyParams().getStringParam("ConusTranscomRole") :
	"ConusStrategicTransportationProvider";

      String AMMO_TRANSCOM_ID = (getMyParams().hasParam ("AmmoTranscomRole")) ?
	getMyParams().getStringParam("AmmoTranscomRole") :
	"AmmoStrategicTransportationProvider";
      
      expectedIDs.add (EURO_TRANSCOM_ID);
      expectedIDs.add (CONUS_TRANSCOM_ID);
      expectedIDs.add (AMMO_TRANSCOM_ID);
    } catch(Exception e) {
      error ("got really unexpected exception " + e);
    } 
  }

  /**
   * <pre>
   * Callback for input tasks 
   *
   * Provide the callback that is paired with the buffering thread, which is a
   * listener.  The buffering thread is the listener to the callback
   *
   * Creates an instance of the WorkflowCallback, which means the plugin
   * is looking for tasks that are part of workflows.
   *
   * </pre>
   * @param bufferingThread -- the thread the callback informs when there are new input tasks
   * @return a WorkflowCallback with the buffering thread as its listener
   */
  protected UTILFilterCallback createThreadCallback (UTILGenericListener bufferingThread) { 
    if (isInfoEnabled())
      info (getName () + " Filtering for naked tasks...");

    myWorkflowCallback = new UTILWorkflowCallback  (bufferingThread, logger) {
	protected UnaryPredicate getPredicate () {
	  return new UnaryPredicate() {
	      public boolean execute(Object o) {
		if (o instanceof Task) {
		  Task subtask = (Task) o;

		  //	    if (subtask.getWorkflow() == null) {
		  //	      return false;
		  //	    }

		  boolean hasBeenAllocated =
		    (subtask.getPlanElement () != null);

		  UTILGenericListener genericListener = 
		    (UTILGenericListener) myListener;

		  boolean interesting = 
		    (!hasBeenAllocated && 
		     genericListener.interestingTask (subtask));

		  if (logger.isDebugEnabled()) {
		    logger.debug ("UTILWorkflowCallback : " + myListener + 
				  " examined task " + subtask.getUID());
		  }
		
		  return interesting;
		}
		return false;
	      }
	    };
	}
      }; 

    return myWorkflowCallback;
  } 

  /**
   * Creates an allocation or dispostion if the start and end times
   * violate the preferences.  
   *
   * Has to slightly decrease the quantity
   * because sometimes allocating with the requested quantity will
   * yield a score of 1.0.
   * 
   * @param task task being assigned to asset
   * @param asset asset handling the task
   * @param start start of the task
   * @param end   end   of the task
   * @param confidence of the allocation
   * @param role of the allocation
   * @param quantity of stuff for the task
   * @return Allocation or Disposition
   */
  protected PlanElement createAllocation (Task task,
					  Asset asset,
					  Date start,
					  Date end,
					  double confidence,
					  Role role, 
					  double quantity) {
    
    double prefQuantity = 0;
    double allocQuantity = 0;

    Preference pref = prefHelper.getPrefWithAspectType (task, AspectType.QUANTITY);
    if (pref != null) {
      ScoringFunction sfunc = pref.getScoringFunction ();
      prefQuantity = sfunc.getBest ().getAspectValue().getValue();
      allocQuantity = prefQuantity - 0.001;
      if (isInfoEnabled()) {
	info ("called this " + task.getUID() + 
	      " alloc quantity " + allocQuantity + 
	      " vs pref " + prefQuantity);
      }
    } 

    return super.createAllocation (task, asset, 
				   start, end, 
				   confidence, role, 
				   allocQuantity);
  }
}
