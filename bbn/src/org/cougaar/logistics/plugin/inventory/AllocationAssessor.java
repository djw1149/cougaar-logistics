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

package org.cougaar.logistics.plugin.inventory;

import java.util.*;

import org.cougaar.glm.ldm.Constants;
import org.cougaar.glm.ldm.asset.Inventory;
import org.cougaar.glm.ldm.plan.AlpineAspectType;

import org.cougaar.planning.plugin.util.PluginHelper;

import org.cougaar.planning.ldm.plan.Allocation;
import org.cougaar.planning.ldm.plan.AllocationResult;
import org.cougaar.planning.ldm.plan.AspectRate;
import org.cougaar.planning.ldm.plan.AspectType;
import org.cougaar.planning.ldm.plan.AspectValue;
import org.cougaar.planning.ldm.plan.Task;
import org.cougaar.planning.ldm.plan.PlanElement;
import org.cougaar.planning.ldm.plan.Role;

import org.cougaar.planning.ldm.measure.Count;
import org.cougaar.planning.ldm.measure.CountRate;
import org.cougaar.planning.ldm.measure.Duration;
import org.cougaar.planning.ldm.measure.FlowRate;
import org.cougaar.planning.ldm.measure.Volume;


/** AllocationAssessor module is a module of the InventoryPlugin looks at
 *  Refill results and Inventory levels to allocate Withdraws 
 *  against the Inventories.
 *  Right now this is implemented with first come first serve, but it
 *  should be changed to allocate withdraws that have the highest score
 *  first where the score is something like quantity * time late or
 *  scoring function scores.
 *  Note that this allocator does NOT allocate split shipments.
 **/

public class AllocationAssessor extends InventoryLevelGenerator {
  public class AllocPhase {
    public int startBucket; // first bucket where allocation occurs
    public int endBucket; // bucket beyond where allocation occurs
    public double amount; //amount allocated (per bucket) in the phase
    public AllocPhase (int startBucket, double amount){
      this.startBucket=startBucket;
      this.endBucket=startBucket+1;
      this.amount=amount;
    }
    
  }
  
  public class TaskDeficit {
    public ArrayList allocated = new ArrayList();
    public AllocPhase lastPhase;
    public Task task;
    public Task getTask (){
      return task;
    }
    LogisticsInventoryPG thePG;
    public double remainingQty;
    public double getRemainingQty(){
      return remainingQty;
    }
    public void setRemainingQty(double rq){
      remainingQty=rq;
    }
    public double backlog = 0.0;
    public void incrementBacklog(double bl){
      backlog=bl+backlog;
    }
    public Collection getAllocationPhases() {
      return allocated;
    }

    public TaskDeficit(Task withdraw, double qty, LogisticsInventoryPG thePG){
      task = withdraw;
      remainingQty=qty;
      this.thePG=thePG;
    }
    
    public void addPhase(double amount, int currentBucket){
      if (amount <= 0.0) {
        return;
      } else if (task.getVerb().equals(Constants.Verb.PROJECTWITHDRAW) &&
                 lastPhase !=null &&
                 currentBucket==lastPhase.endBucket &&
                 amount==lastPhase.amount ) {
        // same as last phase so just extend last phase
        lastPhase.endBucket=currentBucket+1;
      } else {
        // add new phase
        lastPhase = new AllocPhase(currentBucket, amount);
        allocated.add(lastPhase);
      }
      if(remainingQty==amount){
        if(backlog>0.0) {
          remainingQty= taskQtyInBucket(task, currentBucket, thePG);
          backlog=backlog-remainingQty;
        } else {
          remainingQty=0.0;
        }
      } else {
        remainingQty = remainingQty-amount;
      }
    }
  }

  private transient HashMap trailingPointersHash = new HashMap();
  private transient ArrayList trailingPointers = new ArrayList();
  private transient ArrayList trailingPointersRemove = new ArrayList();
  private Role myRole;

  /** Constructor for this module
   *  @param imPlugin The Plugin calling this module.
   *  @param role  The role the Plugin is playing.
   **/
  public AllocationAssessor(InventoryPlugin imPlugin, Role role) {
    super(imPlugin);
    myRole = role;    
  }

  /** Called by the InventoryPlugin when we are processing in Backwards Flow
   *  (which is allocation result notifications) to try and allocated
   *  withdraw tasks.  It also updates the BG's Inventory Levels.
   *  @param inventories  The collection of inventories to be processed
   **/
  public void reconcileInventoryLevels(Collection inventories) {
    Iterator inv_list = inventories.iterator();
    long today = inventoryPlugin.getCurrentTimeMillis();
    int today_bucket;
    Inventory inventory;
    LogisticsInventoryPG thePG;
    long endOfLevel2 = inventoryPlugin.getEndOfLevelTwo();
    while (inv_list.hasNext()) {
      // clear out the trailing pointers every time we get another inventory
      trailingPointersHash = new HashMap();;
      trailingPointers = new ArrayList();
      trailingPointersRemove = new ArrayList();
      inventory = (Inventory)inv_list.next();
      thePG = (LogisticsInventoryPG)
        inventory.searchForPropertyGroup(LogisticsInventoryPG.class);

      today_bucket = thePG.convertTimeToBucket(today, true);
      reconcileThePast(today_bucket, thePG);
      int end_bucket = thePG.getLastDemandBucket();
      int endOfLevel2Bucket = thePG.convertTimeToBucket(endOfLevel2, true);
      if (end_bucket > endOfLevel2Bucket) {
	end_bucket = endOfLevel2Bucket;
      }
      createAllocations(today_bucket, end_bucket, inventory, thePG);
      allocateEarlyProjections(inventory, thePG);
    }
  }

  /** Update the inventory levels from time zero to today.
   *  @param today_bucket  Representation of today.
   *  @param thePG The PG for the Inventory Asset we are working with.
   **/
  public void reconcileThePast(int today_bucket, LogisticsInventoryPG thePG) {
    calculateInventoryLevels(0, today_bucket, thePG);
  }

    
  /** Create and update Withdraw and ProjectWithdraw Task Allocations for a particular Inventory
   *  @param todayBucket This is the starting bucket to process
   *  @param endBucket Whats the last valid bucket for the inventory
   *  @param inv The Inventory we are processing
   *  @param the PG This is the PG for the Inventory we are processing
   **/
  private void createAllocations(int todayBucket, int endBucket, 
				 Inventory inv, LogisticsInventoryPG thePG) {
    int currentBucket = todayBucket;
    double qty = 0;
    double todayLevel, todayRefill;
    Task withdraw;

    // DEBUG
    //     String myOrgName = inventoryPlugin.getMyOrganization().getItemIdentificationPG().getItemIdentification();
    //     String myItemId = thePG.getResource().getTypeIdentificationPG().getTypeIdentification();
 
    


    // loop through the buckets in the inventory
    while (currentBucket <= endBucket) {

      todayRefill = findCommittedRefill(currentBucket, thePG, true);
      todayLevel = thePG.getLevel(currentBucket - 1) + todayRefill;
      

      // First try to fill any previous deficits
      Iterator tpIt = trailingPointers.iterator();
      while (tpIt.hasNext()) {
        TaskDeficit td = (TaskDeficit) tpIt.next();
        withdraw = td.getTask();
        qty = td.getRemainingQty();
        // check the level
        if (todayLevel >= qty) {
	  // Can completely fill known deficit
	  fillDeficit(td,currentBucket,inv,thePG);
	  todayLevel = todayLevel - qty;
        } else if (todayLevel==0.0){
	  break;
	} else {
	  //this withdraw has previously had a deficit we cannot fill the deficit entirely during this bucket`
	  //  leave the TaskDeficit in the same place on the queue -- it still needs to be filled with its old priority
	  td.addPhase(todayLevel, currentBucket);
	  todayLevel = 0.0;
	  break; // nothing more to allocate
        }
      }
      // remove any trailing pointers we filled
      trailingPointers.removeAll(trailingPointersRemove);
      trailingPointersRemove.clear();

      // Fill any counted tasks with remaining inventory (if any)

      Collection wdTasks = thePG.getActualDemandTasks(currentBucket);
      Iterator wdIter = wdTasks.iterator();
      while(wdIter.hasNext()) {
	withdraw = (Task)wdIter.next();
	qty = taskQtyInBucket(withdraw, currentBucket, thePG);
	// check the level
	if ((todayLevel - qty)>-.00000005) {
	  // enough inventory to fill task completely

	  //  NOTE: we have had a case where todayLevel= 12.999999999999995
	  //   and quantity = 12.999999999999998

	  fulfillTask(withdraw,currentBucket,inv,thePG); 
	  todayLevel = Math.max(0.0, todayLevel - qty);
	} else {
	  // can't fill this task totally -- create deficit on this task
	  // if it already has a pe - rescind it 
	  PlanElement pe = withdraw.getPlanElement();
	  if (pe != null) inventoryPlugin.publishRemove(pe);	  
	  TaskDeficit td = getTaskDeficit(withdraw,currentBucket,thePG);
	  td.addPhase(todayLevel, currentBucket);
	  trailingPointers.add(td);
	  // this task depletes the inventory level
	  todayLevel = 0.0;
	}        
      }
      
      //when we are done going through all the tasks for the day set the level
      thePG.setLevel(currentBucket, todayLevel);
      currentBucket = currentBucket + 1;
    }

    // DEBUG inventory levels
//     if ((myOrgName.indexOf("MSB") > 0) && (myItemId.indexOf("768439") > 0)) {
//       for(currentBucket = 1; currentBucket<180; currentBucket++){
// 	if (thePG.convertBucketToTime(currentBucket) >= 1128643200000L) {
// 	  System.out.println("### createAlloc Inventory Level = "+
// 			     thePG.getLevel(currentBucket)+ " on "+
// 			     getTimeUtils().dateString(thePG.convertBucketToTime(currentBucket)));
// 	}
//       }
//     }

    //when we are finished, if we have things left in trailingPointers, fail them
    Iterator tpIt = trailingPointers.iterator();
    while (tpIt.hasNext()) {
      TaskDeficit td = (TaskDeficit) tpIt.next();
      createPhasedAllocationResult(td, currentBucket, inv, thePG, false);      
    }
  }
    
    private TaskDeficit getTaskDeficit(Task task, int currentBucket, LogisticsInventoryPG thePG){
	double qty = taskQtyInBucket(task, currentBucket, thePG);
	TaskDeficit td = ((TaskDeficit)trailingPointersHash.get(task));
	if (td==null) {
	    td = new TaskDeficit(task, qty, thePG);
	    if(task.getVerb().equals(Constants.Verb.PROJECTWITHDRAW)) {
		trailingPointersHash.put(task, td);
	    }
	} else if(td.getRemainingQty()>0.0){
	    // can only happen when we have a projectWithdraw task which has a previous bucket still
	    //  unfilled
	    td.incrementBacklog(qty);
	} else {
	    // can only happen when we have a projectWithdraw task which has no previous bucket still
	    //  unfilled
	    td.setRemainingQty(qty);
	}
	return td;
    }

    private void fillDeficit(TaskDeficit td, int currentBucket, Inventory inv, LogisticsInventoryPG thePG){
	Task task = td.getTask();
	if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
	    // task is completed
	    if (td.getAllocationPhases().isEmpty()) {
		createLateAllocation(task, thePG.convertBucketToTime(currentBucket), inv, thePG);
	    } else {
              //BD added the td.addPhase line to make a phase to fill the deficit
              td.addPhase(td.getRemainingQty(), currentBucket);
              createPhasedAllocationResult(td, currentBucket, inv, thePG, true);
	    }
        } else {
	    td.addPhase(td.getRemainingQty(), currentBucket);
	    if ((thePG.convertBucketToTime(currentBucket + 1) >= 
		 (long)PluginHelper.getPreferenceBestValue(task, AspectType.END_TIME))) {
		createPhasedAllocationResult(td, currentBucket, inv, thePG, true);
	    }
	}
	trailingPointersRemove.add(td);
    }

    private void fulfillTask (Task task, int currentBucket, Inventory inv, LogisticsInventoryPG thePG){
	if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
	    if (task.getPlanElement() != null) {
		// previously allocated WITHDRAW task -- update plan element if needed
		checkPlanElement(task);
	    } else {
		// previously un-allocated WITHDRAW task
		createBestAllocation(task, inv, thePG);
	    }
	} else {
	    //projection
	    TaskDeficit td = (TaskDeficit) trailingPointersHash.get(task);
	    if (td != null) {
		//this project withdraw has previously had a deficit
		td.addPhase(taskQtyInBucket(task, currentBucket, thePG), currentBucket);
		if ((thePG.convertBucketToTime(currentBucket + 1) >= 
		     (long)PluginHelper.getPreferenceBestValue(task, AspectType.END_TIME))) {
		    // the projectWithdraw does end during this bucket
		    createPhasedAllocationResult(td, currentBucket, inv, thePG, true);
		} 
	    } else if (thePG.convertBucketToTime(currentBucket + 1) >= 
		       (long)PluginHelper.getPreferenceBestValue(task, AspectType.END_TIME)) {
		//this project withdraw ends during this bucket
		// it has not previously had a deficit
		createBestAllocation(task, inv, thePG);
	    } //else {
		//this project withdraw has never had a deficit and does not end during this bucket
		//  do nothing -- hope for createBestAllocation
	    //}
	}
    }

    public void createPhasedAllocationResult(TaskDeficit td, int currentBucket, 
                                             Inventory inv, LogisticsInventoryPG thePG,
                                             boolean success) {
      ArrayList phasedResults = new ArrayList();
      double rollupQty = 0;
      AspectValue avs[];
      
      Task task = td.getTask();
      long endOfLevel2 = inventoryPlugin.getEndOfLevelTwo();
      long endOfTask = (long)PluginHelper.getPreferenceBestValue(task, AspectType.END_TIME);
      // Do not process tasks whose end times are beyond level 2
      if (endOfTask > endOfLevel2) {
	return;
      }
      
      //initialize the rollup array depending on the verb --sigh...
      if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
	avs = new AspectValue[2];
      } else {
	avs = new AspectValue[3];
      }

      ArrayList phases = (ArrayList)td.getAllocationPhases();
      if (phases.isEmpty()) {
        //if we totally fail
        createFailedAllocation(task, inv, thePG);
        return;
      }
      int rollupEnd = ((AllocPhase) phases.get(phases.size() - 1)).endBucket;
      int rollupStart = ((AllocPhase) phases.get(0)).startBucket;
      avs[0] = AspectValue.newAspectValue(AspectType.END_TIME, 
					  thePG.convertBucketToTime(rollupEnd));
      avs[1] = AspectValue.newAspectValue(AspectType.START_TIME, 
					  thePG.convertBucketToTime(rollupStart));

      Iterator phasesIt = phases.iterator();
      
      if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
        //use end and qty
        while (phasesIt.hasNext()) {
          AllocPhase aPhase = (AllocPhase) phasesIt.next();
          AspectValue thisPhase[] = new AspectValue[2];
          rollupQty = rollupQty + aPhase.amount;
          thisPhase[0] = AspectValue.newAspectValue(AspectType.END_TIME, thePG.convertBucketToTime(aPhase.endBucket));
          thisPhase[1] = AspectValue.newAspectValue(AspectType.QUANTITY, aPhase.amount);
          phasedResults.add(thisPhase);
        }
	avs[1] = AspectValue.newAspectValue(AspectType.QUANTITY, rollupQty);
      } else {
        // project withdraw use start, end and rate
        while (phasesIt.hasNext()) {
          AllocPhase aPhase = (AllocPhase) phasesIt.next();
          AspectValue thisPhase[] = new AspectValue[3];
          // take the max endBucket for the rollup end time
          if (aPhase.endBucket > rollupEnd) { rollupEnd = aPhase.endBucket;}
          // take the min startBucket for the rollup start time
          if (aPhase.startBucket < rollupStart) { rollupStart = aPhase.startBucket;}
          rollupQty = rollupQty + ((aPhase.endBucket - aPhase.startBucket) * aPhase.amount);
          thisPhase[0] = AspectValue.newAspectValue(AspectType.END_TIME, thePG.convertBucketToTime(aPhase.endBucket));
          thisPhase[1] = AspectValue.newAspectValue(AspectType.START_TIME, thePG.convertBucketToTime(aPhase.startBucket));
          thisPhase[2] = getDemandRateAV(aPhase.amount, thePG.getBucketMillis());
          // add this phase to our phased results list
          phasedResults.add(thisPhase);
        }
        avs[2] = getDemandRateAV(rollupQty, thePG.convertBucketToTime(rollupEnd) - 
				 thePG.convertBucketToTime( rollupStart));
      }
      
      AllocationResult estimatedResult = inventoryPlugin.getPlanningFactory().
        newPhasedAllocationResult(0.9, success, avs, (new Vector(phasedResults)).elements());

      compareResults(estimatedResult, task, inv, thePG);
    }

    public double taskQtyInBucket(Task task, int currentBucket, LogisticsInventoryPG thePG){
	if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
	    return (double)getTaskUtils().getPreference(task, AspectType.QUANTITY);
        } else {
	    long start = (long)PluginHelper.getPreferenceBestValue(task, AspectType.START_TIME);
	    long end = (long)PluginHelper.getPreferenceBestValue(task, AspectType.END_TIME);
	    return (double)thePG.getProjectionTaskDemand(task, currentBucket, start, end);
        }
    }


  /** Create best allocations for Projections that are not being counted.
   *  These are likely early projections that are not counted because
   *  Supply tasks (actuals) are being counted in their place.
   *  Allocate these with yes or best.
   *  Note that projections that span the not counted and counted projection
   *  windows are not allocated here.
   *  @param inv The Inventory we are processing
   *  @param the PG This is the PG for the Inventory we are processing
   **/
  private void allocateEarlyProjections(Inventory inventory, LogisticsInventoryPG thePG) {
//      String myOrgName = inventoryPlugin.getMyOrganization().getItemIdentificationPG().getItemIdentification();
    //String myItemId = thePG.getResource().getTypeIdentificationPG().getTypeIdentification();

    HashMap customerHash = thePG.getCustomerHash();
    Set keys = customerHash.keySet();
    Iterator keysIt = keys.iterator();
    while (keysIt.hasNext()) {
      Object org = keysIt.next();
      int countedBucket = thePG.convertTimeToBucket(((Long)customerHash.get(org)).longValue(), true) + 1;
      int currentBucket = 0;
      // loop through the buckets in the inventory
      while (currentBucket < countedBucket) {
        Collection wdprojs = thePG.getProjectedDemandTasks(currentBucket);
        Iterator wdpIter = wdprojs.iterator();
        while(wdpIter.hasNext()) {
          Task withdrawProj = (Task)wdpIter.next();
          Object customerOrg = TaskUtils.getCustomer(withdrawProj);
            if (customerOrg.equals(org)) {
              double endTimePref = getTaskUtils().getPreference(withdrawProj, AspectType.END_TIME);
              //make sure there is an end time pref AND that the
              //bucket of the end time pref is not equal to or past the countedBucket
              //Since endTime is not inclusive of the bucket it falls in decrement by 1
              // countedBucket is the firstCountedProjection - if the projection spans both
              //the uncounted and counted windows - dont blindly allocate it here ... it should
              // be picked up by the counted projections allocation method.
              if ((endTimePref != Double.NaN) &&
                  ((thePG.convertTimeToBucket((long)endTimePref, true) -1) < countedBucket)) {
                if (withdrawProj.getPlanElement() == null) {
                  createBestAllocation(withdrawProj, inventory, thePG);
                }
                // if it already has a pe we could check it - but for now we won't
              }
            }
        }
        //bump the bucket
        currentBucket = currentBucket + 1;
      }
    }
  }


  /** Utility method to create an Allocation that matches the
   *  best preferences for the withdraw task
   *  @param withdraw The withdraw task we are allocating
   *  @param inv The Inventory we are allocating against
   *  @param thePG The PG of the Inventory we are allocating against
   **/
  private void createBestAllocation(Task withdraw, Inventory inv, 
                                    LogisticsInventoryPG thePG) {
    AllocationResult estimatedResult = PluginHelper.
      createEstimatedAllocationResult(withdraw, inventoryPlugin.getPlanningFactory(), 
                                      0.9, true);
    compareResults(estimatedResult, withdraw, inv, thePG);
  }

  /** Utility method to create a late Allocation
   *  @param withdraw The withdraw task to allocate
   *  @param end The end time of the window that it will be filled
   *  @param inv  The Inventory we are allocating against
   *  @param thePG  The PG for the Inventory we are allocating against
   *  Note that we are using a start and end preference because the allocation
   *  is based on a bucket that may span more than one day. So we want to say it
   *  will be filled sometime within the bucket start and end time.
   **/
  private void createLateAllocation(Task withdraw, long end,
                                    Inventory inv, LogisticsInventoryPG thePG) {
    AspectValue avs[] = new AspectValue[2];
    avs[0] = AspectValue.newAspectValue(AspectType.END_TIME, end);
    avs[1] = AspectValue.newAspectValue(AspectType.QUANTITY,
                                        getTaskUtils().getPreference(withdraw, AspectType.QUANTITY));
    AllocationResult estimatedResult = inventoryPlugin.getPlanningFactory().
      newAllocationResult(0.9, true, avs);
    compareResults(estimatedResult, withdraw, inv, thePG);
  }


  private void createFailedAllocation(Task task, Inventory inventory, LogisticsInventoryPG thePG) {
      // make the failed time the day after the end of the oplan
      long failed_time = inventoryPlugin.getOPlanEndTime() + TimeUtils.MSEC_PER_DAY;
      AspectValue avs[];

      if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
        avs = new AspectValue[2];
        avs[0] = AspectValue.newAspectValue(AspectType.END_TIME, failed_time);
        avs[1] = AspectValue.newAspectValue(AspectType.QUANTITY,
                                            PluginHelper.getPreferenceBestValue(task, AspectType.QUANTITY));
      } else {
        // projection... set start and end to failed_time and set the rate to the pref over 1 bucket
        avs = new AspectValue[3];
        avs[0] = AspectValue.newAspectValue(AspectType.START_TIME, failed_time);
        avs[1] = AspectValue.newAspectValue(AspectType.END_TIME, failed_time);
        avs[2] = getDemandRateAV(PluginHelper.getPreferenceBestValue(task, AlpineAspectType.DEMANDRATE),
                                 thePG.getBucketMillis());
      }
      AllocationResult failed = 
	  inventoryPlugin.getPlanningFactory().newAllocationResult(0.9, false, avs);
      PlanElement prevPE = task.getPlanElement();
      if (prevPE == null) {
	  Allocation alloc = inventoryPlugin.getPlanningFactory().
	      createAllocation(task.getPlan(), task, inventory, 
			       failed, myRole);
	  inventoryPlugin.publishAdd(alloc);
      } else {
	  AllocationResult previous = prevPE.getEstimatedResult();
	  if (!previous.equals(failed)) {
	      prevPE.setEstimatedResult(failed);
	      inventoryPlugin.publishChange(prevPE);
	  }
      }
  }


  /** Method which checks a previously created planelement for the withdraw task
   *  to make sure its consistent with the result we just calculated.
   *  This is called if we want to give a best result - so if the previous
   *  result was not best we will change it.
   *  @param withdraw The Withdraw Task we are allocating against the Inventory
   **/
  private void checkPlanElement(Task withdraw) {
    //if this task already has a pe - make sure the results are consistent
    // with best.
    PlanElement pe = withdraw.getPlanElement();
    AllocationResult ar = pe.getEstimatedResult();
    AllocationResult estimatedResult = PluginHelper.
        createEstimatedAllocationResult(withdraw, inventoryPlugin.getPlanningFactory(), 
                                        0.9, true);
    if (ar == null || !ar.equals(estimatedResult)) {
      pe.setEstimatedResult(estimatedResult);
      inventoryPlugin.publishChange(pe);
//       updatePG(withdraw, thePG);
    } 
  }

  public void compareResults(AllocationResult estimatedResult, Task withdraw, 
                             Inventory inv, LogisticsInventoryPG thePG) {
    PlanElement prevPE = withdraw.getPlanElement();
    if (prevPE == null) {
      Allocation alloc = inventoryPlugin.getPlanningFactory().
        createAllocation(withdraw.getPlan(), withdraw, inv, 
                         estimatedResult, myRole);
      inventoryPlugin.publishAdd(alloc);
    } else {
      AllocationResult previous = prevPE.getEstimatedResult();
      if (!previous.equals(estimatedResult)) {
        prevPE.setEstimatedResult(estimatedResult);
        inventoryPlugin.publishChange(prevPE);
      } else {
        // otherwise leave it alone and don't bother to update the PG
        return;
      }
    }
    
//     updatePG(withdraw, thePG);
  }

  public void updatePG(Task withdraw, LogisticsInventoryPG thePG) {
    if(withdraw.getVerb().equals(Constants.Verb.WITHDRAW)) {
      thePG.updateWithdrawRequisition(withdraw);
    } else {
      thePG.updateWithdrawProjection(withdraw);
    }
  }

  public AspectValue getDemandRateAV(double amount, long millis) {
    AspectValue demandRateAV = null; 
    Duration dur = new Duration(millis, Duration.MILLISECONDS);
    if (inventoryPlugin.getSupplyType().equals("BulkPOL")) {
      Volume vol = new Volume(amount, Volume.GALLONS);
      demandRateAV = AspectValue.newAspectValue(AlpineAspectType.DEMANDRATE, 
                                                new FlowRate(vol, dur));

    } else {
      Count cnt = new Count(amount, Count.EACHES);
      demandRateAV = AspectValue.newAspectValue(AlpineAspectType.DEMANDRATE, 
                                                new CountRate(cnt, dur));
    }
    return demandRateAV;
  }

}
