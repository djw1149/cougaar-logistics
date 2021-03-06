/*
* <copyright>
*  
*  Copyright 1997-2004 BBNT Solutions, LLC
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

package org.cougaar.logistics.plugin.inventory;

import java.util.*;

import org.cougaar.logistics.ldm.Constants;
import org.cougaar.glm.ldm.asset.Inventory;
import org.cougaar.glm.ldm.plan.AlpineAspectType;
import org.cougaar.glm.ldm.plan.ObjectScheduleElement;

import org.cougaar.planning.plugin.util.PluginHelper;

import org.cougaar.planning.ldm.plan.*;
import org.cougaar.planning.ldm.asset.TypeIdentificationPG;

import org.cougaar.planning.ldm.measure.*;


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

    public String toString(LogisticsInventoryPG thePG) {
      return ("AllocPhase of amount: " + amount + " from " + getTimeUtils().dateString(thePG.convertBucketToTime(startBucket)) + " to " + getTimeUtils().dateString(thePG.convertBucketToTime(endBucket)));
    }
  }

  public class TaskDeficit {


    public Task task;
    public Task getTask (){
      return task;
    }
    LogisticsInventoryPG thePG;

    public ArrayList rateBlocks = new ArrayList();

    public double getRemainingQty(int currentBucket){
      ConstantRateBlock block = getRateBlock(currentBucket);
      if(block != null) {
        return block.getRemainingQty();
      }
      return 0.0d;
    }
    public void setRemainingQty(int currentBucket, double rq){
      ConstantRateBlock block = getRateBlock(currentBucket);
      if(block != null) {
        block.setRemainingQty(rq);
      }
    }

    public void incrementBacklog(int currentBucket, double bl){
      ConstantRateBlock block = getRateBlock(currentBucket);
      if(block != null) {
        block.incrementBacklog(bl);
      }
    }
    public Collection getDeficitAllocationPhases() {
      ArrayList defPhases = new ArrayList();
      Iterator rateBlocksIt = rateBlocks.iterator();

      while (rateBlocksIt.hasNext()) {
        ConstantRateBlock currentBlock = (ConstantRateBlock) rateBlocksIt.next();
        defPhases.addAll(currentBlock.getDeficitAllocationPhases());
      }
      return defPhases;
    }




    public void initializeRateBlocks() {
      long taskStartTime = getTaskUtils().getStartTime(task);
      long taskEndTime = getTaskUtils().getEndTime(task);
      PrepositionalPhrase pp_rate = task.getPrepositionalPhrase(Constants.Preposition.DEMANDRATE);
      if (pp_rate != null) {
        Object indObj = pp_rate.getIndirectObject();
        if (indObj instanceof Schedule) {
          Schedule sched = (Schedule) indObj;
          Collection rate_elems =
            sched.getOverlappingScheduleElements(taskStartTime, taskEndTime);
          int n = (rate_elems == null ? 0 : rate_elems.size());
          if (n == 1) {
            rateBlocks.add(new ConstantRateBlock(taskStartTime,taskEndTime,0.0d));
          } else if (n > 1) {
            // return a schedule of daily rates
            rateBlocks = new ArrayList(n);
            for (Iterator iter = rate_elems.iterator(); iter.hasNext(); ) {
              ObjectScheduleElement ose = (ObjectScheduleElement) iter.next();
              rateBlocks.add(new ConstantRateBlock(ose.getStartTime(),ose.getEndTime(),0.0d));
            }
          }
        }
      }
      else {
        rateBlocks.add(new ConstantRateBlock(taskStartTime,taskEndTime,0.0d));
      }
    }


    //Fill in the gaps in the deficit buckets with successful buckets
    public Collection generateAllAllocationPhases() {
      Iterator rateBlocksIt = rateBlocks.iterator();


      ArrayList allPhases = new ArrayList();

      while (rateBlocksIt.hasNext()) {
        ConstantRateBlock currentBlock = (ConstantRateBlock) rateBlocksIt.next();
        allPhases.addAll(currentBlock.generateAllAllocationPhases(task,thePG));
      }
      return allPhases;
    }



      public TaskDeficit(Task withdraw, int currentBucket, double qty, LogisticsInventoryPG thePG) {
        task = withdraw;
        this.thePG=thePG;
        initializeRateBlocks();
        setRemainingQty(currentBucket,qty);
      }

      public ConstantRateBlock getRateBlock(int bucket) {
        long time = thePG.convertBucketToTime(bucket);
        return getRateBlock(time);
      }

      public ConstantRateBlock getRateBlock(long time) {
        Iterator rateBlocksIt = rateBlocks.iterator();

        while (rateBlocksIt.hasNext()) {
          ConstantRateBlock currentBlock = (ConstantRateBlock) rateBlocksIt.next();
          if((currentBlock.startTime <= time) && (currentBlock.endTime > time)) {
            return currentBlock;
          }
        }
        return null;
      }

      public void addPhase(double amount, int currentBucket){
        ConstantRateBlock block = getRateBlock(currentBucket);
        if(block != null) {
          block.addPhase(task,thePG,amount,currentBucket);
        }
      }
  }


  public class ConstantRateBlock {
    public long startTime;
    public long endTime;
    public ArrayList allocated = new ArrayList();
    public AllocPhase lastPhase;

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
    public Collection getDeficitAllocationPhases() {
      return allocated;
    }

    //Fill in the gaps in the deficit buckets with successful buckets
    public Collection generateAllAllocationPhases(Task task,LogisticsInventoryPG thePG) {
      Iterator defPhasesIt = allocated.iterator();

      int rateBlockStartBucket = thePG.convertTimeToBucket(startTime, false);

      int rateBlockEndBucket = thePG.convertTimeToBucket(endTime, true);

      int lastBucket = rateBlockStartBucket;

      ArrayList allPhases = new ArrayList();

      while (defPhasesIt.hasNext()) {
        AllocPhase currentPhase = (AllocPhase) defPhasesIt.next();
        if (currentPhase.startBucket > lastBucket) {
          /*if ((inventoryPlugin.getClusterId().toString().indexOf("2-NLOS-BTY") >= 0) &&
             (task.getDirectObject().getTypeIdentificationPG().getTypeIdentification().indexOf("155mm-DPICM") >= 0)) {
            System.out.println("Got a Phase after the end time of the task..." +
                               " qty of phase is " + currentPhase.amount + " " +
                               task.getUID() + " late phase starts on " +
                               new Date(thePG.convertBucketToTime(currentPhase.startBucket)));
          }*/
          //TODO - EPD This best quantity fill in code causes lots of problems!!!!
          long lastTime = thePG.convertBucketToTime(lastBucket);
          AllocPhase betweenPhase = new AllocPhase(lastBucket, getBestBucketQty(task, thePG, lastTime));
          betweenPhase.endBucket = currentPhase.startBucket;
          allPhases.add(betweenPhase);
        }
        allPhases.add(currentPhase);
        lastBucket = currentPhase.endBucket;
      }

      if (lastBucket < rateBlockEndBucket) {
        long lastTime = thePG.convertBucketToTime(lastBucket);
        AllocPhase lastPhase = new AllocPhase(lastBucket, getBestBucketQty(task,thePG,lastTime));
        lastPhase.endBucket = rateBlockEndBucket;
        allPhases.add(lastPhase);
      }
      return allPhases;
    }




      public ConstantRateBlock(long startTime, long endTime, double qty) {
        //task = withdraw;
        lastPhase = null;
        remainingQty=qty;
        this.startTime = startTime;
        this.endTime = endTime;
        //this.thePG=thePG;
      }

      public void addPhase(Task task, LogisticsInventoryPG thePG, double amount, int currentBucket){
       if(amount < 0.0) {
         if(logger.isInfoEnabled()) {
           String itemId = getTaskUtils().getTaskItemName(task);
           TypeIdentificationPG typeIdPG = thePG.getResource().getTypeIdentificationPG();
           String nomenclature = null;
           String orgId = thePG.getOrg().getItemIdentificationPG().getItemIdentification();
           if (typeIdPG == null) {
             logger.warn("No typeIdentificationPG for asset");
           } else {
             nomenclature = typeIdPG.getNomenclature();
           }
           if (nomenclature == null) {
             nomenclature = itemId;
           }
           logger.info(" Adding a phase of a negative amount==" + amount + " at bucket " + getTimeUtils().dateString(thePG.convertBucketToTime(currentBucket)) + "for task: " + task.getUID() + " org is " + inventoryPlugin.getOrgName() + " and the item is : "  + nomenclature);
         }
         amount = 0.0;
       }
      if (amount == 0.0) {
        if(task.getVerb().equals(Constants.Verb.WITHDRAW)) {
          return;
        }
      }
      if (task.getVerb().equals(Constants.Verb.PROJECTWITHDRAW) &&
              lastPhase !=null &&
              currentBucket==lastPhase.endBucket &&
              amount==lastPhase.amount ) {
        // same as last phase so just extend last phase
        lastPhase.endBucket=currentBucket+1;
      } else {

        // find existing phase for bucket if exists
        Iterator phases = allocated.iterator();
        AllocPhase aPhase=null, thePhase=null;
        while (phases.hasNext()) {
          aPhase = (AllocPhase)phases.next();
          if ((aPhase.startBucket <= currentBucket) &&
              (aPhase.endBucket > currentBucket)) {
            thePhase = aPhase;
            break;
          }
        }
        if (thePhase == null) {
//          if ((inventoryPlugin.getClusterId().toString().indexOf("2-NLOS-BTY") >= 0) &&
//              (task.getDirectObject().getTypeIdentificationPG().getTypeIdentification().indexOf("155mm-DPICM") >= 0)) {
//            System.out.println("Adding new last phase to fill deficit... task " + task.getUID() + " bucket " +
//                               new Date (thePG.convertBucketToTime(currentBucket)) + " amount " + amount);
//          }
          // add new phase
          lastPhase = new AllocPhase(currentBucket, amount);
          allocated.add(lastPhase);
        } else {
          thePhase.amount += amount;
        }
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
  public AllocationAssessor(InventoryManager imPlugin, Role role) {
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
    long currentTime = inventoryPlugin.getCurrentTimeMillis();
    int today_bucket;
    Inventory inventory;
    LogisticsInventoryPG thePG;
    long endOfLevel2 = ((LevelOfDetailInventoryManager)inventoryPlugin).getEndOfLevelTwo();
    while (inv_list.hasNext()) {
      inventory = (Inventory)inv_list.next();
      resetTrailingPointers();
      thePG = (LogisticsInventoryPG)
              inventory.searchForPropertyGroup(LogisticsInventoryPG.class);
      long inventoryStart = thePG.getStartTime();
      long today = Math.max(inventoryStart, currentTime);
      today_bucket = thePG.convertTimeToBucket(today, false);
      reconcileThePast(today_bucket, thePG);
      int end_bucket = thePG.getLastDemandBucket();
      int endOfLevel2Bucket = thePG.convertTimeToBucket(endOfLevel2, false);
      if (end_bucket > endOfLevel2Bucket) {
        end_bucket = endOfLevel2Bucket;
      }
      createAllocations(today_bucket, end_bucket, inventory, thePG);
      allocateNotCountedProjections(inventory, thePG);
      allocateCountedEarlyProjections(today_bucket, inventory, thePG);
    }
  }

  /**
   * Reset Pointers Map, list of pointers, and pointer remove list
   */
  protected void resetTrailingPointers() {
    // clear out the trailing pointers every time we get another inventory
    trailingPointersHash = new HashMap();
    trailingPointers = new ArrayList();
    trailingPointersRemove = new ArrayList();
  }

  /** Update the inventory levels from time zero to today.
   *  @param today_bucket  Representation of today.
   *  @param thePG The PG for the Inventory Asset we are working with.
   **/
  public void reconcileThePast(int today_bucket, LogisticsInventoryPG thePG) {
    calculateInventoryLevels(thePG.getStartBucket(), today_bucket, thePG);
  }

  public double getBestBucketQty(Task task, LogisticsInventoryPG thePG, long time) {
      Rate r = getTaskUtils().getRate(task, time);
      return getQuantityForDuration(r, thePG.getBucketMillis());
    }

    public double getQuantityForDuration(Rate r, long duration){
        Duration d = Duration.newMilliseconds(duration);
        Scalar scalar = (Scalar) r.computeNumerator(d);
        return getTaskUtils().getDouble(scalar);
    }


  /** Create and update Withdraw and ProjectWithdraw Task Allocations for a particular Inventory
   *  @param todayBucket This is the starting bucket to process
   *  @param endBucket Whats the last valid bucket for the inventory
   *  @param inv The Inventory we are processing
   *  @param thePG This is the PG for the Inventory we are processing
   **/
  protected void createAllocations(int todayBucket, int endBucket,
                                 Inventory inv, LogisticsInventoryPG thePG) {

    int currentBucket = todayBucket;
    double qty = 0;
    double todayLevel, todayRefill;
    Task withdraw;

    // DEBUG
    String myOrgName = inventoryPlugin.getClusterId().toString();
    String myItemId = inv.getItemIdentificationPG().getItemIdentification();
    LogisticsInventoryPG logInvPG =  (LogisticsInventoryPG) inv.searchForPropertyGroup(LogisticsInventoryPG.class);
    String nomenclature = logInvPG.getResource().getTypeIdentificationPG().getNomenclature();

    /*if ((myOrgName.indexOf("2-NLOS-BTY") >= 0) && (myItemId.indexOf("155mm-DPICM") >= 0)) {
    	      System.out.println("### createAlloc : Assessing allocations RIGHT ORG NAME COMBO - " + myOrgName + " - " + myItemId);
    }*/


    // loop through the buckets in the inventory
    while (currentBucket <= endBucket) {

      todayRefill = findCommittedRefill(currentBucket, thePG, true);
      todayLevel = thePG.getLevel(currentBucket - 1) + todayRefill;

      /***
       **

      if((inventoryPlugin.getOrgName().indexOf("47-FSB") != -1) &&
         (thePG.getItemName().indexOf("9140002865294") != -1)) {
        logger.error("createAllocations - bucket is "+getTimeUtils().dateString(thePG.convertBucketToTime(currentBucket))+
                     ", committed refill "+todayRefill+" and today's inventory level "+todayLevel);
      }

      **/

      // First try to fill any previous deficits
      Iterator tpIt = trailingPointers.iterator();
      while (tpIt.hasNext()) {
        TaskDeficit td = (TaskDeficit) tpIt.next();
        withdraw = td.getTask();
        //TODO - EPD HACK for LAT 1.0 delivery to stop late fills of projections
        // related problem is in the TD code that "fills in" bare spots.
        if (withdraw.getVerb().equals(Constants.Verb.PROJECTWITHDRAW)) {
          long taskEndTime = getTaskUtils().getEndTime(withdraw);
          int taskEndBucket = thePG.convertTimeToBucket(taskEndTime,true);
          if (taskEndBucket < currentBucket ) {
            continue;
          }
        }
        qty = td.getRemainingQty(currentBucket);
	// DEBUG Task deficits
	//if ((myOrgName.indexOf("592-ORDCO") >= 0) && (myItemId.indexOf("Level2Am") >= 0)) {
        //if ((myOrgName.indexOf("2-NLOS-BTY") >= 0) && (myItemId.indexOf("155mm-DPICM") >= 0)) {
          //System.out.println("### createAlloc : trying to fill a PREVIOUS deficit for task:|" + withdraw.getUID() + "| : todaylevel|" + todayLevel + "| and qty is |" + qty + "| and the date is " +  getTimeUtils().dateString(thePG.convertBucketToTime(currentBucket)));
        //}
	//}
        // check the level
        if (todayLevel <= 0.0) {
//          if ((myOrgName.indexOf("2-NLOS-BTY") >= 0) && (myItemId.indexOf("155mm-DPICM") >= 0)) {
//            System.out.println("TodayLevel is " + todayLevel + " adding 0 phase for bucket " +
//                               new Date(thePG.convertBucketToTime(currentBucket)) + " task " +
//                               td.getTask().getUID());
//          }
          td.addPhase(0.0, currentBucket);
          break;
        } else if (todayLevel >= qty) {
          // Can completely fill known deficit
          /***
           **
          if((inventoryPlugin.getOrgName().indexOf("47-FSB") != -1) &&
             (thePG.getItemName().indexOf("9140002865294") != -1)) {
            logger.error("calculateAllocations - filling deficit on day "+
                         getTimeUtils().dateString(thePG.convertBucketToTime(currentBucket))+
                         " and the remaining qty is "+qty+" and new inventory level is "+todayLevel+" for task "+
                         withdraw.getUID() + " task request qty " + getTaskUtils().getDailyQuantity(withdraw));               
          }        
          **/

          fillDeficit(td,currentBucket,inv,thePG);
          todayLevel = todayLevel - qty;
        } else {
          //this withdraw has previously had a deficit we cannot fill the deficit entirely during this bucket`
          //  leave the TaskDeficit in the same place on the queue -- it still needs to be filled with its old priority
          
          /****
           ***

          if((inventoryPlugin.getOrgName().indexOf("47-FSB") != -1) &&
             (thePG.getItemName().indexOf("9140002865294") != -1)) {
            logger.error("calculateAllocations - adding phase on day "+
                         getTimeUtils().dateString(thePG.convertBucketToTime(currentBucket))+
                         " and td.remaining qty is " + qty + " today level is " + todayLevel + " setting today level to 0. This is for task " + withdraw.getUID() +  " task request daily quantity " + getTaskUtils().getDailyQuantity(withdraw ));               
          }  

          **/

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




//          if((inventoryPlugin.getOrgName().indexOf("47-FSB") != -1) &&
//             (thePG.getItemName().indexOf("9140002865294") != -1)) {
//            logger.error("calculateAllocations - fulfill task on day "+
//                         getTimeUtils().dateString(thePG.convertBucketToTime(currentBucket))+
//                         " demand is "+qty+" and new inventory level is "+todayLevel+" for task "+
//                         withdraw.getUID() + " task qty " + getTaskUtils().getDailyQuantity(withdraw));
//          }
          

        } else {
          // can't fill this task totally -- create deficit on this task
          // if it already has a pe - rescind it
          PlanElement pe = withdraw.getPlanElement();
          if (pe != null) inventoryPlugin.publishRemove(pe);
          TaskDeficit td = getTaskDeficit(withdraw,currentBucket,thePG);
          td.addPhase(todayLevel, currentBucket);
	  // DEBUG Task deficits
	  //if ((myOrgName.indexOf("592-ORDCO") >= 0) && (myItemId.indexOf("Level2Amm") >= 0)) {
	  //    System.out.println("### createAlloc : adding a phase to a task deficit/deficit added to TRAILING POINTERS for task:|" 
	  //+ td.getTask().getUID() +"|  todaylevel|" + todayLevel + 
	  // "| and qty is |" + qty + "| and the date is " +  
	  //getTimeUtils().dateString(thePG.convertBucketToTime(currentBucket)));
	  //}

          /***
           **
          if((inventoryPlugin.getOrgName().indexOf("47-FSB") != -1) &&
             (thePG.getItemName().indexOf("9140002865294") != -1)) {
            logger.error("calculateAllocations - deficit for task on day "+
                         getTimeUtils().dateString(thePG.convertBucketToTime(currentBucket))+
                         " demand is "+qty+" and new inventory level is 0.0 and provided "+todayLevel+" to "+
                         withdraw.getUID()+ " task qty " + getTaskUtils().getDailyQuantity(withdraw) + " TD: remainingQty " + td.getRemainingQty() + " backlog " + td.backlog);
              if(td.lastPhase != null) {
                logger.error("And Last Phase is = " + td.lastPhase.toString(thePG));
              }
          }          

          ***
          **/
          
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
    //if ((myOrgName.indexOf("592-ORDCO") >= 0) && (myItemId.indexOf("Level2Amm") >= 0)) {
	//  for(currentBucket = 0; currentBucket<80; currentBucket++){
    //if (thePG.convertBucketToTime(currentBucket) >= 1128643200000L) {
	//System.out.println("###-" + myOrgName + 
    //"-createAlloc Inventory Level = "+
	//thePG.getLevel(currentBucket)+ " on ("+ currentBucket +") " +
	//getTimeUtils().dateString(thePG.convertBucketToTime(currentBucket)));
	//	}
    // }
	//}

    //when we are finished, if we have things left in trailingPointers, fail them
    Iterator tpIt = trailingPointers.iterator();
    while (tpIt.hasNext()) {
      TaskDeficit td = (TaskDeficit) tpIt.next();
//      if ((myOrgName.indexOf("2-NLOS-BTY") >= 0) && (myItemId.indexOf("155mm-DPICM") >= 0)) {
//        System.out.println("Creating failed phased AR. task deficit is " + td.getTask() + " " + td.getRemainingQty());
//      }
      //createPhasedAllocationResult(td, inv, thePG, false);
      createPhasedAllocationResult(td, inv, thePG, true);
    }
  }

  private TaskDeficit getTaskDeficit(Task task, int currentBucket, LogisticsInventoryPG thePG){
    double qty = taskQtyInBucket(task, currentBucket, thePG);
    TaskDeficit td = ((TaskDeficit)trailingPointersHash.get(task));
    if (td==null) {
      td = new TaskDeficit(task, currentBucket, qty, thePG);
      if(task.getVerb().equals(Constants.Verb.PROJECTWITHDRAW)) {
        trailingPointersHash.put(task, td);
      }
    } else if(td.getRemainingQty(currentBucket)>0.0){
      // can only happen when we have a projectWithdraw task which has a previous bucket still
      //  unfilled
      td.incrementBacklog(currentBucket,qty);
    } else {
      // can only happen when we have a projectWithdraw task which has no previous bucket still
      //  unfilled
      td.setRemainingQty(currentBucket,qty);
    }
    return td;
  }

  private void fillDeficit(TaskDeficit td, int currentBucket, Inventory inv, LogisticsInventoryPG thePG){
    // DEBUG      
      //String myOrgName = inventoryPlugin.getMyOrganization().getItemIdentificationPG().getItemIdentification();
      //String myItemId = inv.getItemIdentificationPG().getItemIdentification();
    Task task = td.getTask();
    if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
      // task is completed
      if (td.getDeficitAllocationPhases().isEmpty()) {
        createLateAllocation(task, thePG.convertBucketToTime(currentBucket), inv, thePG);
      } else {
        //BD added the td.addPhase line to make a phase to fill the deficit
        td.addPhase(td.getRemainingQty(currentBucket), currentBucket);
        createPhasedAllocationResult(td, inv, thePG, true);
      }
    } else {
      td.addPhase(td.getRemainingQty(currentBucket), currentBucket);
      if ((thePG.convertBucketToTime(currentBucket + 1) >=
              (long)PluginHelper.getPreferenceBestValue(task, AspectType.END_TIME))) {
	  //DEBUG
	  //  if ((myOrgName.indexOf("592") >= 0) && (myItemId.indexOf("Level2Amm") >= 0)) {
	  //  logger.debug("### fillDeficit : creating a phased allocation result for task: " + task.getUID() + " on the date of " + getTimeUtils().dateString(thePG.convertBucketToTime(currentBucket)));
	  //}
        createPhasedAllocationResult(td, inv, thePG, true);
      }
    }
    trailingPointersRemove.add(td);
  }

  private void fulfillTask (Task task, int currentBucket, Inventory inv, LogisticsInventoryPG thePG){
    if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
      //Safely check if the pe is null here and if we get one, pass it to the check method
      //instead of letting the check method get it since it may have been rescinded in between
      // the task.getPlanElement() calls which can cause an NPE in checkPlanElement
      PlanElement pe = task.getPlanElement();
      if (pe != null) {
        // previously allocated WITHDRAW task -- update plan element if needed
        checkPlanElement(task, pe);
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
          createPhasedAllocationResult(td, inv, thePG, true);
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

  public void createPhasedAllocationResult(TaskDeficit td,
                                           Inventory inv,
                                           LogisticsInventoryPG thePG,
                                           boolean success) {
    //if we are passing in a false - see if we already made a failed AR/PE. If we did just get out.
    Task task = td.getTask();
    PlanElement prevPE = task.getPlanElement();
    /**if (prevPE != null && !success) {
      if (!prevPE.getEstimatedResult().isSuccess()) {
        return;
      }
    }*/

    ArrayList phasedResults = new ArrayList();
    double rollupQty = 0;
    AspectValue avs[];

    if (shouldSkipMakingResult(task)) {
      return;
    }

    //initialize the rollup array depending on the verb --sigh...
    if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
      avs = new AspectValue[2];
    } else {
      avs = new AspectValue[3];
    }

    ArrayList phases = (ArrayList)td.getDeficitAllocationPhases();
    if (phases.isEmpty()) {
      //if we totally fail
      if ((inventoryPlugin.getClusterId().toString().indexOf("2-NLOS-BTY") >= 0) &&
              (task.getDirectObject().getTypeIdentificationPG().getTypeIdentification().indexOf("155mm-DPICM") >= 0)) {
        System.out.println("Totally failing task b/c there are not phases... " + task.getUID());
      }
      createFailedAllocation(task, inv, thePG);
      return;
    }


    if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
      int rollupEnd = ((AllocPhase) phases.get(phases.size() - 1)).endBucket;
      int rollupStart = ((AllocPhase) phases.get(0)).startBucket;
      avs[0] = AspectValue.newAspectValue(AspectType.END_TIME,
                                          thePG.convertBucketToTime(rollupEnd));
      avs[1] = AspectValue.newAspectValue(AspectType.START_TIME,
                                          thePG.convertBucketToTime(rollupStart));

      Iterator phasesIt = phases.iterator();
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

      phases = (ArrayList)td.generateAllAllocationPhases();

      int rollupEnd = ((AllocPhase) phases.get(phases.size() - 1)).endBucket;
      int rollupStart = ((AllocPhase) phases.get(0)).startBucket;
      avs[0] = AspectValue.newAspectValue(AspectType.END_TIME,
                                          thePG.convertBucketToTime(rollupEnd));
      avs[1] = AspectValue.newAspectValue(AspectType.START_TIME,
                                          thePG.convertBucketToTime(rollupStart));

      Iterator phasesIt = phases.iterator();
      
      phasesIt = phases.iterator();
      while(phasesIt.hasNext()) {
	      AllocPhase aPhase = (AllocPhase) phasesIt.next();
        rollupQty = rollupQty + ((aPhase.endBucket - aPhase.startBucket) * aPhase.amount);
        // take the max endBucket for the rollup end time
        if (aPhase.endBucket > rollupEnd) { rollupEnd = aPhase.endBucket;}
        // take the min startBucket for the rollup start time
        if (aPhase.startBucket < rollupStart) { rollupStart = aPhase.startBucket;}
        AspectValue thisPhase[] = new AspectValue[3];
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
            newPhasedAllocationResult(Constants.Confidence.SCHEDULED, success, avs, (new Vector(phasedResults)).elements());

    compareResults(estimatedResult, task, inv, thePG);
  }

  /**
   * Do not process tasks whose end times are beyond level 2
   * @param task
   * @return true if after level 2 horizon
   */
  protected boolean shouldSkipMakingResult(Task task) {
    long endOfLevel2 = ((LevelOfDetailInventoryManager)inventoryPlugin).getEndOfLevelTwo();
    long endOfTask = (long)PluginHelper.getPreferenceBestValue(task, AspectType.END_TIME);
    // Do not process tasks whose end times are beyond level 2
    return (endOfTask > endOfLevel2);
  }

  public double taskQtyInBucket(Task task, int currentBucket, LogisticsInventoryPG thePG){
    if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
      return getTaskUtils().getPreference(task, AspectType.QUANTITY);
    } else {
      long start = (long)PluginHelper.getPreferenceBestValue(task, AspectType.START_TIME);
      long end = (long)PluginHelper.getPreferenceBestValue(task, AspectType.END_TIME);
      return thePG.getProjectionTaskDemand(task, currentBucket, start, end);
    }
  }


  /** Create best allocations for Projections that are not being counted.
   *  These are likely early projections that are not counted because
   *  Supply tasks (actuals) are being counted in their place.
   *  Allocate these with yes or best.
   *  Note that projections that span the not counted and counted projection
   *  windows are not allocated here.
   *  @param inventory The Inventory we are processing
   *  @param thePG This is the PG for the Inventory we are processing
   **/
  protected void allocateNotCountedProjections(Inventory inventory, LogisticsInventoryPG thePG) {
//      String myOrgName = inventoryPlugin.getMyOrganization().getItemIdentificationPG().getItemIdentification();
    //String myItemId = thePG.getResource().getTypeIdentificationPG().getTypeIdentification();

    HashMap customerHash = thePG.getCustomerHash();
    Set keys = customerHash.keySet();
    Iterator keysIt = keys.iterator();
    while (keysIt.hasNext()) {
      Object org = keysIt.next();
      int countedBucket = thePG.convertTimeToBucket(((Long)customerHash.get(org)).longValue(), false) + 1;
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

  /** Create best allocations for Projections that are counted but in the past.
   *  These may need to be re-allocated in the past if the optempo changes
   *  and these tasks are still counted.
   *  @param today_bucket The current day of the society
   *  @param inventory The Inventory we are processing
   *  @param thePG This is the PG for the Inventory we are processing
   **/
  protected void allocateCountedEarlyProjections(int today_bucket, Inventory inventory,
                                               LogisticsInventoryPG thePG) {
    int currentBucket = 0;
    // loop through the buckets in the inventory
    while (currentBucket < today_bucket) {
      Collection wdprojs = thePG.getProjectedDemandTasks(currentBucket);
      Iterator wdpIter = wdprojs.iterator();
      while(wdpIter.hasNext()) {
        Task withdrawProj = (Task)wdpIter.next();
        double endTimePref = getTaskUtils().getPreference(withdrawProj, AspectType.END_TIME);
        //make sure there is an end time pref AND that the
        //bucket of the end time pref is not equal to or past the today_bucket
        //Since endTime is not inclusive of the bucket it falls in decrement by 1
        if ((endTimePref != Double.NaN) &&
                ((thePG.convertTimeToBucket((long)endTimePref, true) -1) < today_bucket)) {
          if (withdrawProj.getPlanElement() == null) {
            createBestAllocation(withdrawProj, inventory, thePG);
          }
        }
      }
      currentBucket = currentBucket + 1;
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
    AllocationResult estimatedResult = null;
    if (withdraw.getVerb().equals(Constants.Verb.WITHDRAW)) {
      estimatedResult = PluginHelper.
            createEstimatedAllocationResult(withdraw, inventoryPlugin.getPlanningFactory(),
                                            Constants.Confidence.SCHEDULED, true);
    }
    else {
      long taskStartTime = getTaskUtils().getStartTime(withdraw);
      long taskEndTime = getTaskUtils().getEndTime(withdraw);
      PrepositionalPhrase pp_rate = withdraw.getPrepositionalPhrase(Constants.Preposition.DEMANDRATE);
      if (pp_rate != null) {
        Object indObj = pp_rate.getIndirectObject();
        if (indObj instanceof Schedule) {
          Schedule sched = (Schedule) indObj;
          Collection rate_elems =
            sched.getOverlappingScheduleElements(taskStartTime, taskEndTime);
          int n = (rate_elems == null ? 0 : rate_elems.size());
          ArrayList phasedResults = new ArrayList(n);
          double rollupQty = 0;
          AspectValue avs[];

          avs = new AspectValue[3];

          // return a schedule of daily rates
          int rollupEnd = thePG.convertTimeToBucket(taskEndTime,true);
          int rollupStart = thePG.convertTimeToBucket(taskStartTime,false);

          avs[0] = AspectValue.newAspectValue(AspectType.END_TIME,
                  taskEndTime);
          avs[1] = AspectValue.newAspectValue(AspectType.START_TIME,
                  taskStartTime);

          for (Iterator iter = rate_elems.iterator(); iter.hasNext(); ) {
            ObjectScheduleElement ose = (ObjectScheduleElement) iter.next();
            int rateStartBucket = thePG.convertTimeToBucket(ose.getStartTime(),false);
            int rateEndBucket = thePG.convertTimeToBucket(ose.getEndTime(),true);
            Rate currentRate = (Rate) ose.getObject();


              rollupQty = rollupQty + this.getQuantityForDuration(currentRate,(rateEndBucket - rateStartBucket));
              // take the max endBucket for the rollup end time
              if (rateEndBucket > rollupEnd) { rollupEnd = rateEndBucket;}
              // take the min startBucket for the rollup start time
              if (rateStartBucket < rollupStart) { rollupStart = rateStartBucket;}
              AspectValue thisPhase[] = new AspectValue[3];
              thisPhase[0] = AspectValue.newAspectValue(AspectType.END_TIME, thePG.convertBucketToTime(rateEndBucket));
              thisPhase[1] = AspectValue.newAspectValue(AspectType.START_TIME, thePG.convertBucketToTime(rateStartBucket));
              thisPhase[2] = getDemandRateAV(getQuantityForDuration(currentRate,thePG.getBucketMillis()), thePG.getBucketMillis());
              // add this phase to our phased results list
              phasedResults.add(thisPhase);
            }
            avs[2] = getDemandRateAV(rollupQty, thePG.convertBucketToTime(rollupEnd) -
                                                thePG.convertBucketToTime( rollupStart));

            estimatedResult = inventoryPlugin.getPlanningFactory().
                  newPhasedAllocationResult(Constants.Confidence.SCHEDULED, true, avs, (new Vector(phasedResults)).elements());

          }
      }
      else {
        estimatedResult = PluginHelper.
            createEstimatedAllocationResult(withdraw, inventoryPlugin.getPlanningFactory(),
                                            Constants.Confidence.SCHEDULED, true);
      }
    }
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
            newAllocationResult(Constants.Confidence.SCHEDULED, true, avs);
    compareResults(estimatedResult, withdraw, inv, thePG);
  }


  private void createFailedAllocation(Task task, Inventory inventory, LogisticsInventoryPG thePG) {
    //check if the task already has a failed allocation on it - don't even bother to compare
    //failed should equal failed at this point.
    PlanElement prevPE = task.getPlanElement();
    if (prevPE != null) {
      if (!prevPE.getEstimatedResult().isSuccess()) {
        return;
      }
    }
    if (logger.isDebugEnabled()) {
      logger.debug("Failing task: " + getTaskUtils().taskDesc(task) + " at agent: " 
                   + inventoryPlugin.getClusterId().toString() +
                   " Inventory initial level is: " + thePG.getLevel(0));
    } 
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
      long failed_start = PluginHelper.getStartTime(task);
      AspectValue failedAV;
      Duration dur = new Duration(failed_time - failed_start, Duration.MILLISECONDS);
      if (getTaskUtils().isFlowRate(task)) {
        Volume vol = new Volume(0.0, Volume.GALLONS);
        failedAV = AspectValue.newAspectValue(AlpineAspectType.DEMANDRATE,
                                              new FlowRate(vol,dur));
      } else {
        Count cnt = new Count(0.0, Count.EACHES);
        failedAV = AspectValue.newAspectValue(AlpineAspectType.DEMANDRATE,
                                              new CountRate(cnt, dur));
      }
                                              
      avs[0] = AspectValue.newAspectValue(AspectType.START_TIME, failed_start);
      avs[1] = AspectValue.newAspectValue(AspectType.END_TIME, failed_time);
      avs[2] = failedAV;
    }
    AllocationResult failed =
            inventoryPlugin.getPlanningFactory().newAllocationResult(Constants.Confidence.SCHEDULED, false, avs);
    //PlanElement prevPE = task.getPlanElement();
    if (prevPE == null) {
      Allocation alloc = inventoryPlugin.getPlanningFactory().
              createAllocation(task.getPlan(), task, inventory,
                               failed, myRole);
      inventoryPlugin.publishAdd(alloc);
    } else {
      AllocationResult previous = prevPE.getEstimatedResult();
      if (!previous.isEqual(failed)) {
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
   *  @param pe The PlanElement associated with the Task.
   **/
  private void checkPlanElement(Task withdraw, PlanElement pe) {
    //if this task already has a pe - make sure the results are consistent
    // with best.
    //PlanElement pe = withdraw.getPlanElement();
    AllocationResult ar = pe.getEstimatedResult();
    AllocationResult estimatedResult = PluginHelper.
            createEstimatedAllocationResult(withdraw, inventoryPlugin.getPlanningFactory(),
                                            Constants.Confidence.SCHEDULED, true);
    if (ar == null || !ar.isEqual(estimatedResult)) {
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
      if (!previous.isEqual(estimatedResult)) {
        if (logger.isDebugEnabled()){
          logger.debug("Inside compareResults... results are !.equals..." +
                       "\n previous result: " + previous + " estimated result: " +
                       estimatedResult);
        }
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
