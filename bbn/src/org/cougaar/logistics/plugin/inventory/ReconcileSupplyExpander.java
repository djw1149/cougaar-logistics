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

import org.cougaar.core.agent.service.alarm.Alarm;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.glm.ldm.asset.Inventory;
import org.cougaar.glm.ldm.plan.AlpineAspectType;
import org.cougaar.logistics.ldm.Constants;
import org.cougaar.logistics.servlet.CommStatus;
import org.cougaar.planning.ldm.asset.Asset;
import org.cougaar.planning.ldm.plan.*;
import org.cougaar.planning.plugin.util.AllocationResultHelper;
import org.cougaar.planning.plugin.util.PluginHelper;
import org.cougaar.util.Collectors;
import org.cougaar.util.Filters;
import org.cougaar.util.Thunk;
import org.cougaar.util.UnaryPredicate;

import java.util.*;

public class ReconcileSupplyExpander extends InventoryModule implements ExpanderModule {

  /**
   * Define an ARA that can deal with the expansion of a
   * ProjectSupply task. Mostly, we just clone the result of the
   * ProjectWithdraw task.
   **/
  private static class ProjectionARA implements AllocationResultAggregator {
    public AllocationResult calculate(Workflow wf, TaskScoreTable tst, AllocationResult currentar) {
      if (tst.size() != 1)
        throw new IllegalArgumentException("projectionARA: multiple subtasks");
      AllocationResult ar = tst.getAllocationResult(0);
      if (ar == null) return null;
      if (ar.isEqual(currentar)) return currentar;
      return (AllocationResult) ar.clone();
    }
  }

  private static class SupplyARA implements AllocationResultAggregator {
    public AllocationResult calculate(Workflow wf, TaskScoreTable tst, AllocationResult currentar) {
      AspectValue[] merged = new AspectValue[AlpineAspectType.LAST_ALPINE_ASPECT + 1];
      long startTime = Long.MAX_VALUE;
      long endTime = Long.MIN_VALUE;
      boolean success = true;
      float rating = 0.0f;
      int tstSize = tst.size();
      AllocationResult withdrawAR = null; // Remember this when we see it
      Task parentTask = wf.getParentTask();
      AllocationResultHelper helper = new AllocationResultHelper(parentTask, null);
      AllocationResult bestAR = helper.getAllocationResult();
      AspectValue[] curr = bestAR.getAspectValueResults();

      for (int i = 0; i < curr.length; i++) {
        AspectValue av = curr[i];
        int type = av.getAspectType();
        merged[type] = av;
        switch (type) {
          case START_TIME:
            startTime = (long) av.getValue();
            break;
          case END_TIME:
            endTime = (long) av.getValue();
            break;
        }
      }
      for (int i = 0; i < tstSize; i++) {
        AllocationResult ar = tst.getAllocationResult(i);
        if (ar == null) return null; // bail if undefined
        Task t = tst.getTask(i);
        Verb verb = t.getVerb();
        boolean isWithdraw =
            verb.equals(Constants.Verb.Withdraw)
            || verb.equals(Constants.Verb.ProjectWithdraw);
        if (isWithdraw) {
          if (ar == null) return null;
          withdrawAR = ar;
        }
        AspectValue[] avs = ar.getAspectValueResults();
        success = success && ar.isSuccess();
        rating += ar.getConfidenceRating();
        for (int j = 0; j < avs.length; j++) {
          int type = avs[j].getAspectType();
          switch (type) {
            case AspectType.START_TIME:
              break;
            case AspectType.END_TIME:
              break;
            case AspectType.QUANTITY:
              if (isWithdraw) merged[AspectType.QUANTITY] = avs[j];
              break;
            default:
              if (!isWithdraw) merged[type] = avs[j];
          }
        }
      }
      List mergedPhasedResults = new ArrayList();
      // this seems strange why do we assume everything is phased?
      //for now check if this is null (as all the isphased seem to be true
      // and if it is null get the nonphased results.
      List withdrawPhasedResults = withdrawAR.getPhasedAspectValueResults();
      if (withdrawPhasedResults != null) {
        for (int i = 0, n = withdrawPhasedResults.size(); i < n; i++) {
          AspectValue[] oneResult = (AspectValue[]) withdrawPhasedResults.get(i);
          mergedPhasedResults.add(merge(merged, oneResult));
        }
      } else {
        AspectValue[] npresult = withdrawAR.getAspectValueResults();
        mergedPhasedResults.add(merge(merged, npresult));
      }
      return new AllocationResult(rating / tstSize, success,
                                  merge(merged, null), mergedPhasedResults);
    }

    /**
     * Merges an array of AspectValue indexed by AspectType and an
     * unindexed array of AspectValues into an unindexed array of
     * AspectValues.
     **/
    private AspectValue[] merge(AspectValue[] rollup, AspectValue[] phased) {
      if (phased != null) {
        rollup = (AspectValue[]) rollup.clone(); // Don't clobber the original
        for (int i = 0; i < phased.length; i++) {
          AspectValue av = phased[i];
          if (av != null) rollup[av.getAspectType()] = av;
        }
      }
      int nAspects = 0;
      for (int i = 0; i < rollup.length; i++) {
        if (rollup[i] != null) nAspects++;
      }
      AspectValue[] result = new AspectValue[nAspects];
      int aspect = 0;
      for (int i = 0; i < rollup.length; i++) {
        if (rollup[i] != null) result[aspect++] = rollup[i];
      }
      return result;
    }
  }

  protected static final long MSEC_PER_MIN = 60 * 1000;
  protected static final long MSEC_PER_HOUR = MSEC_PER_MIN * 60;
  public static final long DEFAULT_ORDER_AND_SHIPTIME = 24 * MSEC_PER_HOUR; // second day

  public static final Verb WITHDRAWVERB = Constants.Verb.Withdraw;
  public static final Verb PROJECTWITHDRAWVERB = Constants.Verb.ProjectWithdraw;
  public static final Verb TRANSPORTVERB = Constants.Verb.Transport;


  //private Organization myOrg;
  protected boolean addTransport; // Add load tasks when expanding supply tasks
  private long ost;
  private static AllocationResultAggregator projectionARA = new ProjectionARA();
  private static AllocationResultAggregator supplyARA = new SupplyARA();
  private static final long COMMS_UP_DELAY = 120000L; // 2 minutes
  private MessageAddress clusterId;
  private TaskUtils taskUtils = getTaskUtils();

  public ReconcileSupplyExpander(ReconcileInventoryPlugin imPlugin) {
    super(imPlugin);
    ost = DEFAULT_ORDER_AND_SHIPTIME;  //In the future plugin should supply from suppliers predictor the OST - MWD
    addTransport = false;
    clusterId = imPlugin.getClusterId();
  }

  public boolean expandAndDistributeProjections(Collection tasks) {
    boolean newProjections = false;
    LogisticsInventoryPG logInvPG;
    Task aTask, wdrawTask;
    Iterator taskIter = tasks.iterator();
    while (taskIter.hasNext()) {
      aTask = (Task) taskIter.next();
      wdrawTask = expandDemandTask(aTask, createProjectWithdrawTask(aTask));
      logInvPG = getLogisticsInventoryPG(wdrawTask);
      if (logInvPG != null) {
        logInvPG.addWithdrawProjection(wdrawTask);
        // if we have atleast one new projection - set this to true.
        newProjections = true;
      }
      ((NewWorkflow) wdrawTask.getWorkflow()).setAllocationResultAggregator(projectionARA);
    }
    return newProjections;
  }

  public void expandAndDistributeRequisitions(Collection tasks) {
    LogisticsInventoryPG logInvPG;
    Task aTask, wdrawTask;
    Iterator taskIter = tasks.iterator();
    while (taskIter.hasNext()) {
      aTask = (Task) taskIter.next();
      if (logger.isDebugEnabled() && debugAgent()) {
        if (isPrediction(aTask)) {
          logger.debug(" Received prediction task  " + aTask);
        }
      }
      if (commStatusExists(getCustomerName(aTask))) {  // if any status objects exist
        if (customerCommsUp(getCustomerName(aTask))) {
          // CUSTOMER COMM IS UP
          if (isPrediction(aTask)) {
            // we ignore committed predictions and rescind uncommitted ones
            removeUncommittedPredictions(aTask);
            continue;
          }
        } else {
          // CUSTOMER COMM IS DOWN
          CustomerState cs = (CustomerState) customerStates.get(getCustomerName(aTask));
          if (isPrediction(aTask)) {
            if (beforeTheGap(aTask, cs.getLatestEndTime())) {
              removeUncommittedPredictions(aTask);  // otherwise we ignore the task
              continue;
            }
            if (afterTheGap(cs.customerLeadTime, aTask)) {
              inventoryPlugin.publishRemove(aTask);  // don't need to check commitment should be beyond
              if (logger.isDebugEnabled() && debugAgent()) {
                logger.debug("Comm is down, removing prediction after the gap " + printTheGap(aTask));
              }
              continue;
            }
          } else {  // ignore demand tasks in or after the gap
            if (inTheGap(cs.getLatestEndTime(), cs.customerLeadTime, aTask)
                || afterTheGap(cs.customerLeadTime, aTask)) {
              if (logger.isDebugEnabled() && debugAgent()) {
                logger.debug("Comm is down, ignoring demand task in/after the gap " + printTheGap(aTask));
              }
              continue;
            }
          }
        }
      }
      Date commitDate = aTask.getCommitmentDate();
      if (commitDate != null) {
        wdrawTask = expandDemandTask(aTask, createWithdrawTask(aTask));
        logInvPG = getLogisticsInventoryPG(wdrawTask);
        if (logInvPG != null) {
          logInvPG.addWithdrawRequisition(wdrawTask);
        }
        ((NewWorkflow) wdrawTask.getWorkflow()).setAllocationResultAggregator(supplyARA);
        if (logger.isDebugEnabled() && debugAgent())
          logger.debug("Epanding task " + aTask);
      } else {
        if (isPrediction(aTask)) {
          logger.error(" Prediction ");
        }
        logger.error(" task has null commit date: " + commitDate);
        continue;
      }
    }
  }

  private void removeUncommittedPredictions(Task aTask) {
    if (! isCommitted(aTask)) {
      inventoryPlugin.publishRemove(aTask);
      if (logger.isDebugEnabled() && debugAgent()) {
        logger.debug("Comm is up, removing uncommitted prediction: " + printTheGap(aTask));
      }
    }
  }

  public void handleRemovedRequisitions(Collection tasks) {
    Iterator taskIter = tasks.iterator();
    while (taskIter.hasNext()) {
      Task aTask = (Task) taskIter.next();
      LogisticsInventoryPG thePG = getLogisticsInventoryPG(aTask);
      if (thePG != null) {
        thePG.removeWithdrawRequisition(aTask);
      }
    }
  }

  // Review:  Target for deletion
  public void handleRemovedRealRequisitions(Collection tasks) {
  }

  public boolean handleRemovedProjections(Collection tasks) {
    boolean removedProjections = false;
    LogisticsInventoryPG thePG;
    Task aTask;
    Iterator taskIter = tasks.iterator();
    while (taskIter.hasNext()) {
      aTask = (Task) taskIter.next();
      thePG = getLogisticsInventoryPG(aTask);
      if (thePG != null) {
        thePG.removeWithdrawProjection(aTask);
        removedProjections = true;
      }
    }
    return removedProjections;
  }

  public void updateChangedRequisitions(Collection tasks) {
    LogisticsInventoryPG thePG;
    Expansion exp;
    Task task, supply;
    Iterator taskIter = tasks.iterator();
    while (taskIter.hasNext()) {
      supply = (Task) taskIter.next();
      if (supply.getPlanElement() instanceof Expansion) {
        exp = (Expansion) supply.getPlanElement();
        Workflow wf = exp.getWorkflow();
        Enumeration subTasks = wf.getTasks();
        while (subTasks.hasMoreElements()) {
          task = (Task) subTasks.nextElement();
          thePG = getLogisticsInventoryPG(supply);
          if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
            if ((getTaskUtils().getEndTime(task) < inventoryPlugin.getOPlanArrivalInTheaterTime()) && (logger.isErrorEnabled())) {
              logger.error("At " + inventoryPlugin.getOrgName() + "- Requisition Task:" +
                           task.getUID() + " item: " + getTaskUtils().getTaskItemName(task) +
                           " has an endTime of " +
                           getTimeUtils().dateString(getTaskUtils().getEndTime(task)) +
                           " which is before this orgs arrival time of " +
                           getTimeUtils().dateString(inventoryPlugin.getOPlanArrivalInTheaterTime()));
            }
            if (thePG != null) {
              thePG.updateWithdrawRequisition(task);
              synchronized (supply) {
                ((NewTask) task).setPreferences(supply.getPreferences());
              }
              inventoryPlugin.publishChange(task);
            }
          } else if (task.getVerb().equals(Constants.Verb.TRANSPORT)) {
            ((NewTask) task).setPrepositionalPhrases(supply.getPrepositionalPhrases());
            inventoryPlugin.publishChange(task);
          }
        }
      }
    }
  }

  public void updateChangedProjections(Collection tasks) {
    LogisticsInventoryPG thePG;
    Expansion exp;
    Task projSupply, task;
    Iterator taskIter = tasks.iterator();
    while (taskIter.hasNext()) {
      projSupply = (Task) taskIter.next();
      if (projSupply.getPlanElement() instanceof Expansion) {
        exp = (Expansion) projSupply.getPlanElement();
        Workflow wf = exp.getWorkflow();
        Enumeration subTasks = wf.getTasks();
        while (subTasks.hasMoreElements()) {
          task = (Task) subTasks.nextElement();
          if (task.getVerb().equals(Constants.Verb.PROJECTWITHDRAW)) {
            thePG = getLogisticsInventoryPG(projSupply);
            if ((getTaskUtils().getStartTime(projSupply) < inventoryPlugin.getOPlanArrivalInTheaterTime()) && (logger.isErrorEnabled())) {
              logger.error("At " + inventoryPlugin.getOrgName() +
                           "- Projection Task:" + task.getUID() + " item: " +
                           getTaskUtils().getTaskItemName(projSupply) + " has an start time of " +
                           getTimeUtils().dateString(getTaskUtils().getStartTime(projSupply)) +
                           " which is before this orgs arrival time of " +
                           getTimeUtils().dateString(inventoryPlugin.getOPlanArrivalInTheaterTime()));
            }
            if (thePG != null) {
              thePG.removeWithdrawProjection(task);
              if (task.getPlanElement() != null) {
                inventoryPlugin.publishRemove(task.getPlanElement());
              }
              synchronized (projSupply) {
                ((NewTask) task).setPreferences(projSupply.getPreferences());
              }
              inventoryPlugin.publishChange(task);
              //BD Why is this here?  We never removed the task from the wf???
              // commenting this code out
              //((NewWorkflow)wf).addTask(task);
              thePG.addWithdrawProjection(task);
              //inventoryPlugin.publishChange(wf);
            }
          } else if (task.getVerb().equals(Constants.Verb.TRANSPORT)) {
            ((NewTask) task).setPrepositionalPhrases(projSupply.getPrepositionalPhrases());
            inventoryPlugin.publishChange(task);
          }
        }
      }
    }
  }

  public LogisticsInventoryPG getLogisticsInventoryPG(Task wdrawTask) {
    LogisticsInventoryPG logInvPG = null;
    Asset asset = wdrawTask.getDirectObject();
    // we might be looking at pass-thru's from the change list
    Inventory inventory = inventoryPlugin.findOrMakeInventory(asset);
    if (inventory != null) {
      inventoryPlugin.touchInventory(inventory);
      logInvPG = (LogisticsInventoryPG)
          inventory.searchForPropertyGroup(LogisticsInventoryPG.class);
    }
    return logInvPG;
  }

  private static boolean isPrediction(Task aTask) {
    PrepositionalPhrase for_pp = aTask.getPrepositionalPhrase(Constants.Preposition.FOR);
    String forOrgName = (String) for_pp.getIndirectObject();
    String fromOrgName = aTask.getSource().toString();
    return !forOrgName.equals(fromOrgName);
  }

  private Task expandDemandTask(Task parentTask, Task withdrawTask) {
    Vector expand_tasks = new Vector();
    expand_tasks.addElement(withdrawTask);
    NewTask transportTask = null;
    if (addTransport) {
      transportTask = createTransportTask(parentTask, withdrawTask);
      expand_tasks.addElement(transportTask);
    }
    Expansion expansion = PluginHelper.wireExpansion(parentTask, expand_tasks, inventoryPlugin.getPlanningFactory());
    inventoryPlugin.publishAddExpansion(expansion);
    NewWorkflow wf = (NewWorkflow) expansion.getWorkflow();
    wf.setAllocationResultAggregator(new AllocationResultAggregator.DefaultARA());
    return withdrawTask;
  }

  protected NewTask createWithdrawTask(Task parentTask) {

    NewTask subtask = createVanillaWithdrawTask(parentTask);

//This method does the Supply specific stuff

    long anticipation = 0L;
    if (addTransport) anticipation += ost;

    subtask.setVerb(WITHDRAWVERB);
    //TODO Figure out what to offset the task by for transport
//addEndTimePref(subtask, getTaskUtils().getEndTime(parentTask) - anticipation);

    return subtask;
  }

  protected NewTask createProjectWithdrawTask(Task parentTask) {

    NewTask subtask = createVanillaWithdrawTask(parentTask);

//This method does the ProjectSupply specific stuff

    long anticipation = 0L;
    if (addTransport) anticipation += ost;

    subtask.setVerb(PROJECTWITHDRAWVERB);
//design issue:
//MWD do we build in ost anticipation to end time pref
//like above if there is a
//PROJECTTRANSPORT in theatre transportation.

    return subtask;
  }

  /** creates a Withdraw task from a Supply task **/
  protected NewTask createVanillaWithdrawTask(Task parentTask) {

// Create new task
    Asset prototype = parentTask.getDirectObject();
    NewTask subtask = inventoryPlugin.getPlanningFactory().newTask();
// attach withdraw task to parent and fill it in
    subtask.setDirectObject(prototype);
    subtask.setParentTask(parentTask);
    subtask.setPlan(parentTask.getPlan());
    subtask.setPrepositionalPhrases(parentTask.getPrepositionalPhrases());
    subtask.setPriority(parentTask.getPriority());
    subtask.setSource(clusterId);

// Copy all preferences
    synchronized (parentTask) {
      subtask.setPreferences(parentTask.getPreferences());
    }
    return subtask;
  }

  /** creates a Transport or ProjectTransport task from a Supply and Withdraw
   ** or ProjectSupply and ProjectWithdraw task.
   ** Must fill in.
   **/
  protected NewTask createTransportTask(Task parentTask, Task wdraw_task) {
    return null;
  }

  public void updateAllocationResult(IncrementalSubscription sub) {
    Iterator subIt = sub.iterator();
    while (subIt.hasNext()) {
      PlanElement pe = (PlanElement) subIt.next();
      if (PluginHelper.updatePlanElement(pe)) {
        inventoryPlugin.publishChange(pe);
      }
    }
  }

  // TODO:  New Predicition/supply task reconciliation
  private Map customerStates = new HashMap();

  // This method shuold be called in setup subscriptions to ensure that we have the correct
  // state and are rehydration safe
  // called when comms are down to set the state and set the customer lead time
  // FIXME:
  protected void initializeState(Collection demandTasks, Collection commStatus) {
   // updateCommStatus(commStatus);
   // for (Iterator iterator = demandTasks.iterator(); iterator.hasNext();) {
    //  Task task = (Task) iterator.next();

    //}


  //if (logger.isWarnEnabled()) {
    //          logger.warn("SupplyExpander rescinding redundant prediction: " + aTask);
      //      }
        //  } else {
  }

  public void determineCommStatus(IncrementalSubscription commStatusSub) {
    updateCommStatus(commStatusSub.getAddedCollection());
    updateCommStatus(commStatusSub.getChangedCollection());
  }

  public void checkCommStatusAlarms() {
    for (Iterator iter = customerStates.entrySet().iterator(); iter.hasNext();) {
      Map.Entry entry = (Map.Entry) iter.next();
      CustomerState state = (CustomerState) entry.getValue();
      Alarm thisAlarm = state.getCommsUpAlarm();
      if (thisAlarm != null && thisAlarm.hasExpired()) {
        if (logger.isDebugEnabled() && debugAgent()) {
          logger.debug(" The expire time is " + new Date(thisAlarm.getExpirationTime()) + "  the current time is " +
                       new Date(inventoryPlugin.getCurrentTimeMillis()));

        }
        if (logger.isDebugEnabled() && debugAgent()) {
          logger.debug("Alarm expired on customer " + entry.getKey());
        }
        state.setCommsUpAlarm(null);
        reconcile((String) entry.getKey(), state);
      }
    }
  }

  private void reconcile(String customerName, CustomerState state) {
    UnaryPredicate tasksGapPred = new TasksInTheGap(customerName, state.getLatestEndTime(),
                                                       state.getCustomerLeadTime());
    UnaryPredicate predictionsGapPred = new PredictionsInTheGap(customerName, state.getLatestEndTime(),
                                                                state.getCustomerLeadTime());
    Collection supplyTasks = tasksInTheGap(tasksGapPred);
    Collection predictionTasks = tasksInTheGap(predictionsGapPred);
    reconcilePredictions(supplyTasks, predictionTasks);
  }

  private void updateCommStatus(Collection commStatus) {
    for (Iterator iter = commStatus.iterator(); iter.hasNext();) {
      CommStatus cs = (CommStatus) iter.next();
      String customerName = cs.getConnectedAgentName();
      CustomerState state = (CustomerState) customerStates.get(customerName);
      if (state == null) {
        state = new CustomerState(cs);
        customerStates.put(customerName, state);
      }
      if (state.isCommsUp()) {
        if (cs.isCommUp()) {
          // state is still up
          continue;
        }
        long latestEndTime = findLastSupplyTaskTime(customerName);
        long customerLeadTime = latestEndTime - cs.getCommLossTime();
        state.setCustomerLeadTime(customerLeadTime);
        state.setLatestEndTime(latestEndTime);
        state.setCommsUp(false);
        if (logger.isDebugEnabled() && debugAgent()) {
          logger.debug("Supply Type is : " + inventoryPlugin.getSupplyType() + "\n  Setting customer state for " + customerName + " Comm Loss time : " +
                       new Date(cs.getCommLossTime()) + "\t latest end time found " +
                       new Date(latestEndTime) + "\n customer lead time is " + customerLeadTime/86400000) ;
        }
      } else { // customer state is down
        if (! cs.isCommUp()) {
          // state is still down
          continue;
        }
        if (logger.isDebugEnabled() && debugAgent()) {
          logger.debug("Comms came up for  " + customerName);
        }
        Alarm alarm = ((ReconcileInventoryPlugin) inventoryPlugin).addRealTimeAlarm(COMMS_UP_DELAY);
        state.setCommsUpAlarm(alarm);
        state.setCommsUp(true);
      }
    }
  }

  private long findLastSupplyTaskTime(String customerName) {
    MaxEndThunk thunk = new MaxEndThunk(customerName);
    Collectors.apply(thunk, ((ReconcileInventoryPlugin) inventoryPlugin).getSupplyTasks());
    return thunk.getMaxEndTime();
  }

  private void reconcilePredictions(Collection demandTasks, Collection committedPreds) {
    List sortedTasks = sortTasksByEndTime(demandTasks);
    List sortedPreds = sortTasksByEndTime(committedPreds);
    if (logger.isDebugEnabled() && debugAgent()) {
      logger.debug("Number of tasks in the gap Predictions:  " + sortedPreds.size()
                   + " \n\t\t DemandTasks: " + sortedTasks.size());
    }
    int i = 0;
    int lastIndex = sortedTasks.size() -1;
    for(i = 0; i <= lastIndex; i++) {
      Task task = (Task) sortedTasks.get(i);
      long endTime = taskUtils.getEndTime(task);
      double quantity = 0.0;
      long maxEndTime = endTime;
      List taskPhasedValues = new ArrayList();
      for (Iterator iter = sortedPreds.iterator(); iter.hasNext();) {
        Task pred = (Task) iter.next();
        long predEndTime = taskUtils.getEndTime(pred);
        if (predEndTime > endTime && i < lastIndex) {
          break;
        }
        PlanElement pe = pred.getPlanElement();
        AllocationResult ar = pe.getEstimatedResult();
        if (! ar.isSuccess()) {
          // prediction failed no reconcilation needed
          if (logger.isDebugEnabled()) {
            logger.debug("Found a failed prediction, no reconcilation");
          }
          continue;
        }
        List phasedResults = ar.getPhasedAspectValueResults();
        taskPhasedValues.addAll(phasedResults);
        for (Iterator phaseIter = phasedResults.iterator(); phaseIter.hasNext();) {
          AspectValue[] aspectValues = (AspectValue[]) phaseIter.next();
          for (int l = 0; l < aspectValues.length; l++) {
            AspectValue aspectValue = aspectValues[l];
            switch (aspectValue.getAspectType()) {
              case AspectType.QUANTITY:
                quantity += aspectValue.getValue();
                break;
              case AspectType.END_TIME:
                maxEndTime = Math.max(maxEndTime, aspectValue.longValue());
                break;
              default: logger.warn("Unexpected aspect type " + aspectValue.getAspectType());
            }
          }
        }
      }

      AspectValue[] rollup = { AspectValue.newAspectValue(AspectType.QUANTITY, quantity),
                               AspectValue.newAspectValue(AspectType.END_TIME, maxEndTime)
      };
      AllocationResult ar = new AllocationResult(1.0, true, rollup, taskPhasedValues);
      if (logger.isDebugEnabled() && debugAgent()) {
        logger.debug("Published new disposition on task " + task.getUID() + " end Time " +
                     new Date(endTime) + " original quantity " + taskUtils.getQuantity(task) +
                     " new quantity " + quantity);

      }
      inventoryPlugin.publishRemove(task.getPlanElement());
      Disposition disp = inventoryPlugin.getPlanningFactory().createDisposition(task.getPlan(), task, ar);
      inventoryPlugin.publishAdd(disp);
    }
  }

  private boolean beforeTheGap(Task task, long lastEndTime) {
    long endTime = taskUtils.getEndTime(task);
    boolean retVal = false;
    retVal = endTime < lastEndTime;
    if (retVal == true && logger.isDebugEnabled() && debugAgent()) {
      if (isPrediction(task))
        logger.error(" Prediction ");
      logger.error(" task has end date before the GAP: " + new Date(endTime));
    }
    return retVal;
  }


  private boolean inTheGap(long leftEdge, long leadTime, Task task) {
    boolean retVal = false;
    long rightEdge = inventoryPlugin.getCurrentTimeMillis() + leadTime;
    long endTime = taskUtils.getEndTime(task);
    retVal = endTime > leftEdge && endTime < rightEdge;
    if (retVal == true && logger.isDebugEnabled() && debugAgent()) {
      if (isPrediction(task))
        logger.debug(" Prediction ");
      logger.debug(" task in the GAP:  end time is " + new Date(endTime));
    }
    return  retVal;
  }

  private boolean inTheOutage(long leftEdge, long rightEdge, Task task) {
    long commitTime = task.getCommitmentDate().getTime();
    if (logger.isDebugEnabled() && debugAgent()) {
      if (isPrediction(task))
        logger.debug("Prediction ");
      logger.debug(" task commitment date is: " + task.getCommitmentDate() + " in the outage? " +
                   new Date(leftEdge) + " - " + new Date(rightEdge));
    }
    return  (commitTime > leftEdge && commitTime < rightEdge);
  }

  private boolean afterTheGap(long leadTime, Task task) {
    long rightEdge = inventoryPlugin.getCurrentTimeMillis() + leadTime;
    long endTime = taskUtils.getEndTime(task);
    boolean retVal = false;
    retVal = endTime > rightEdge;
    if (retVal == true && logger.isDebugEnabled() && debugAgent()) {
      if (isPrediction(task))
        logger.error(" Prediction ");
      logger.error(" task after the GAP, end time is: " + new Date(endTime));
    }
    return retVal;
  }

  private boolean commStatusExists(String name) {
    if (((ReconcileInventoryPlugin) inventoryPlugin).getCommStatusSubscription().isEmpty())
      return false;
    CustomerState state;
    state = (CustomerState) customerStates.get(name);
    if (state != null) {
      return true;
    }
    return false;
  }

  private boolean customerCommsUp(String customerName) {
    CustomerState state = (CustomerState) customerStates.get(customerName);
    return state.isCommsUp();
  }

  private String printTheGap(Task t) {
    String theTask = new Date(taskUtils.getEndTime(t)) + "\n gap is --> ";
    CustomerState state = (CustomerState) customerStates.get(getCustomerName(t));
    Date leftSide = new Date(state.getLatestEndTime());
    Date rightSide = new Date(inventoryPlugin.getCurrentTimeMillis() + state.customerLeadTime);
    String theGap = leftSide.toString() + "  --  " + rightSide.toString();
    return theTask+theGap;
  }

  // Review:Is it possible that there is no supply task after the comms go down???

  // An instance of a Supply Expander handles one supply type, but it may have multiple customers
  // with differing policies therefore, customer lead time may differ.
  private static class CustomerState {
    private long customerLeadTime = 0;
    // previous state of comms
    private boolean commsUp = true;
    private Alarm alarm;
    private long latestEndTime = 0;
    private CommStatus cs = null;

    public CustomerState(CommStatus cs) {
      this.cs = cs;
    }

    public void setCommsUp(boolean state) {
      commsUp = state;
    }

    public boolean isCommsUp() {
      return commsUp && alarm == null;
    }

    public void setCustomerLeadTime(long newLeadTime) {
      customerLeadTime = newLeadTime;
    }

    public long getCustomerLeadTime() {
      return customerLeadTime;
    }

    public void setCommsUpAlarm(Alarm alarm) {
      this.alarm = alarm;
    }

    public Alarm getCommsUpAlarm() {
      return this.alarm;
    }

    public void setLatestEndTime(long endTime) {
      latestEndTime = endTime;
    }

    public long getLatestEndTime() {
      return latestEndTime;
    }

    public long getCommLossTime(){
      return this.cs.getCommLossTime();
    }

    public long getCommRestoreTime() {
      return this.cs.getCommRestoreTime();
    }
  }


  private class MaxEndThunk implements Thunk {
    long maxEnd = Long.MIN_VALUE;
    String customerName;
    public MaxEndThunk (String customerName) {
      this.customerName = customerName;
    }
    public void apply(Object o) {
      if (o instanceof Task) {
        Task task = (Task) o;
        if (isPrediction(task)
            || ! getCustomerName(task).equals(this.customerName)) {
          return;
        }
        long endTime = taskUtils.getEndTime(task);
        if (endTime > maxEnd) {
          maxEnd = endTime;
        }
      }
    }

    public long getMaxEndTime(){
      return maxEnd;
    }
  }

  private class TasksInTheOutage implements UnaryPredicate {
    String customerName;
    long commLossTime;
    long commRestoreTime;
    public TasksInTheOutage(String customerName, long lossTime, long restoreTime) {
      this.customerName = customerName;
      commLossTime = lossTime;
      commRestoreTime = restoreTime;
    }
    public boolean execute(Object o) {
      if (o instanceof Task) {
        Task task = (Task) o;
        if (task.getVerb().equals(Constants.Verb.SUPPLY)) {
          return (getCustomerName(task).equals(customerName) && !isPrediction(task) && isCommitted(task)
              && inTheOutage(commLossTime, commRestoreTime, task));
        }
      }
      return false;
    }
  }

  private class TasksInTheGap implements UnaryPredicate {
    String customerName;
    long lastDemandTime;
    long leadTime;
    public TasksInTheGap(String customerName, long lastDemandTime, long leadTime) {
      this.customerName = customerName;
      this.lastDemandTime = lastDemandTime;
      this.leadTime = leadTime;
    }
    public boolean execute(Object o) {
      if (o instanceof Task) {
        Task task = (Task) o;
        if (task.getVerb().equals(Constants.Verb.SUPPLY)) {
          return (getCustomerName(task).equals(customerName) && !isPrediction(task)
              && inTheGap(lastDemandTime, leadTime, task));
        }
      }
      return false;
    }
  }
  private boolean isCommitted(Task t) {
    return t.beforeCommitment(new Date(inventoryPlugin.getCurrentTimeMillis()));
  }

  private String getCustomerName(Task t) {
    return taskUtils.getCustomer(t).toString();
  }

  public Collection filter(UnaryPredicate predicate) {
    return Filters.filter(((ReconcileInventoryPlugin)inventoryPlugin).getSupplyTasks(), predicate);
  }

  public Collection tasksInTheOutage(UnaryPredicate outagePredicate) {
    return filter(outagePredicate);
  }

  public Collection tasksInTheGap(UnaryPredicate gapPredicate) {
    return filter(gapPredicate);
  }

  private class PredictionsInTheOutage implements UnaryPredicate {
    String customerName;
    long commLossTime;
    long commRestoreTime;

    public PredictionsInTheOutage(String customerName, long lossTime, long restoreTime) {
      this.customerName = customerName;
      commLossTime = lossTime;
      commRestoreTime = restoreTime;
    }
    public boolean execute(Object o) {
      if (o instanceof Task) {
        Task task = (Task) o;
        return (getCustomerName(task).equals(customerName) && isPrediction(task)
            && isCommitted(task) && inTheGap(commLossTime, commRestoreTime, task));
      }
      return false;
    }
  }

   private class PredictionsInTheGap implements UnaryPredicate {
    String customerName;
    long lastDemandTime;
    long leadTime;

    public PredictionsInTheGap(String customerName, long lastDemandTime, long leadTime) {
      this.customerName = customerName;
      this.lastDemandTime = lastDemandTime;
      this.leadTime = leadTime;
    }
    public boolean execute(Object o) {
      if (o instanceof Task) {
        Task task = (Task) o;
        return (getCustomerName(task).equals(customerName) && isPrediction(task)
            && inTheGap(lastDemandTime, leadTime, task));
      }
      return false;
    }
  }
  public boolean debugAgent() {
    String myOrgName = inventoryPlugin.getOrgName();
    return (myOrgName.indexOf("123-MSB") >= 0);
  }

  private List sortTasksByEndTime(Collection tasks) {
    List result;
    if (tasks instanceof List) {
      result = (List) tasks;
    } else {
      result = new ArrayList(tasks);
    }
    Collections.sort(result, new Comparator () {
      public int compare (Object a, Object b) {
        Task task1 = (Task) a;
        Task task2 = (Task) b;
        long end1 = taskUtils.getEndTime(task1);
        long end2 = taskUtils.getEndTime(task2);
        if (end1 < end2) return -1;
        if (end1 > end2) return +1;
        return 0;
      }});
    return result;
  }
}