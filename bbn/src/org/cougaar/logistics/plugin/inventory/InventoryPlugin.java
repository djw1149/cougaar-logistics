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

import org.cougaar.core.adaptivity.OMCRange;
import org.cougaar.core.adaptivity.OMCRangeList;
import org.cougaar.core.adaptivity.OperatingMode;
import org.cougaar.core.adaptivity.OperatingModeImpl;
import org.cougaar.core.agent.service.alarm.Alarm;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.component.ServiceRevokedEvent;
import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.logging.LoggingServiceWithPrefix;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.node.NodeIdentificationService;
import org.cougaar.core.plugin.ComponentPlugin;
import org.cougaar.core.service.*;
import org.cougaar.glm.ldm.asset.*;
import org.cougaar.glm.ldm.oplan.Oplan;
import org.cougaar.glm.ldm.oplan.OrgActivity;
import org.cougaar.logistics.ldm.Constants;
import org.cougaar.logistics.plugin.utils.QuiescenceAccumulator;
import org.cougaar.logistics.plugin.utils.ScheduleUtils;
import org.cougaar.logistics.plugin.utils.TaskScheduler;
import org.cougaar.logistics.plugin.utils.TaskSchedulingPolicy;
import org.cougaar.logistics.servlet.CommStatus;
import org.cougaar.logistics.servlet.LogisticsInventoryServlet;
import org.cougaar.planning.ldm.PlanningFactory;
import org.cougaar.planning.ldm.asset.Asset;
import org.cougaar.planning.ldm.asset.NewItemIdentificationPG;
import org.cougaar.planning.ldm.asset.NewTypeIdentificationPG;
import org.cougaar.planning.ldm.plan.*;
import org.cougaar.planning.plugin.util.AllocationResultHelper;
import org.cougaar.planning.plugin.util.PluginHelper;
import org.cougaar.util.*;

import java.lang.reflect.Constructor;
import java.util.*;

/** The InventoryPlugin is the Glue of inventory management.
 *  It handles all blackboard services for its modules,
 *  facilitates inter-module communication and manages the
 *  subscriptions.  The InventoryPlugin also creates inventories.
 *  All modules are called from the InventoryPlugin.
 **/

public class InventoryPlugin extends ComponentPlugin
    implements InventoryManager, ClassicRefillGeneratorInventoryManager,
    ReconcileSupplyExpanderInventoryManager, LevelOfDetailInventoryManager {

  protected boolean initialized = false;
//  protected boolean firstTimeThrough = true;
  protected DomainService domainService;
  protected NodeIdentificationService nodeIdService;
  protected LoggingService logger;
  protected UIDService uidService = null;
  protected TaskUtils taskUtils;
  protected TimeUtils timeUtils;
  protected AssetUtils AssetUtils;
  protected ScheduleUtils scheduleUtils;
  protected HashMap pluginParams;
  protected HashMap inventoryHash;
    //  protected HashMap inventoryInitHash;
  protected HashSet touchedInventories;
  // inventoriesWithDeletions used to refresh snapshots.
  // Ensures correct graphs during deletion periods.
  protected HashSet inventoriesWithDeletions;
  // protected HashSet backwardFlowInventories;  // ### Captures Inventories with unchanged demand
  protected boolean touchedProjections;
  protected boolean touchedChangedProjections = false;
  protected String supplyType;
    //  protected String inventoryFile;
//   protected boolean fillToCapacity; Will be added bug #1482
//   protected boolean maintainAtCapacity; Will be added bug #1482
  protected DetReqAggHandler detReqHandler;
  protected Organization myOrganization;
  protected String myOrgName;
  protected ExpanderModule supplyExpander;
  protected AllocatorModule externalAllocator;
  protected RefillGeneratorModule refillGenerator;
  protected RefillProjectionGeneratorModule refillProjGenerator;
  protected ComparatorModule refillComparator;
  protected AllocationAssessor allocationAssessor;
  protected LogisticsPlanModule logisticsPlan;
  protected long startTime;
  protected long cycleStamp;
  protected boolean logToCSV = false;
  protected transient ArrayList newRefills = new ArrayList();
  protected boolean rehydrateInvs = false;
  protected boolean OMChange = false;
  protected long prevLevel6;
  protected boolean turnOnTaskSched=false;
  protected int prepoArrivalOffset=3;

  public final String SUPPLY_TYPE = "SUPPLY_TYPE";
  public final String INVENTORY_FILE = "INVENTORY_FILE";
  public final String ENABLE_CSV_LOGGING = "ENABLE_CSV_LOGGING";
  public final String PREPO_ARRIVAL_OFFSET = "PREPO_ARRIVAL_OFFSET";
  public final String TASK_SCHEDULER_ON = "TASK_SCHEDULER_ON";

  // as a default make the max the end of the oplan (225)
  public final Integer LEVEL_2_MIN = new Integer(40); // later, these should be parameters to plugin...
  public final Integer LEVEL_2_MAX = new Integer(225);
  public final Integer LEVEL_6_MIN = new Integer(20);
  public final Integer LEVEL_6_MAX = new Integer(225);
  // then default to the end of the oplan (max)
  public final String LEVEL_2_TIME_HORIZON = "Level2TimeHorizon";
  public final Integer LEVEL_2_TIME_HORIZON_DEFAULT = LEVEL_2_MAX;
  public final String LEVEL_6_TIME_HORIZON = "Level6TimeHorizon";
  public final Integer LEVEL_6_TIME_HORIZON_DEFAULT = LEVEL_6_MAX;

  // OPlan variable
  protected LogisticsOPlan logOPlan = null;

  // Policy variables
  protected InventoryPolicy inventoryPolicy = null;
  protected int criticalLevel = 3;
  protected int reorderPeriod = 3;
  protected long bucketSize = TimeUtils.MSEC_PER_DAY;
  protected boolean fillToCapacity = false;

  public void load() {
    super.load();
    logger = getLoggingService(this);
    timeUtils = new TimeUtils(this);
    AssetUtils = new AssetUtils(this);
    taskUtils = new TaskUtils(this);
    scheduleUtils = new ScheduleUtils(this);
    detReqHandler = new DetReqAggHandler(this);
    // readParameters() initializes supplyType and inventoryFile
    pluginParams = readParameters();
    supplyExpander = getExpanderModule();
    externalAllocator = getAllocatorModule();
    refillGenerator = getRefillGeneratorModule();
    refillProjGenerator = getRefillProjectionGeneratorModule();
    refillComparator = getComparatorModule();
    allocationAssessor = new AllocationAssessor(this, getRole(supplyType));
    logisticsPlan = new LogisticsPlan(getAgentIdentifier());
    inventoryHash = new HashMap();
    //    inventoryInitHash = new HashMap();
    touchedInventories = new HashSet();
    inventoriesWithDeletions = new HashSet();
    //backwardFlowInventories = new HashSet();
    touchedProjections = false;
    startTime = currentTimeMillis();


    domainService = (DomainService)
        getServiceBroker().getService(this,
                                      DomainService.class,
                                      new ServiceRevokedListener() {
                                        public void serviceRevoked(ServiceRevokedEvent re) {
                                          if (DomainService.class.equals(re.getService()))
                                            domainService = null;
                                        }
                                      });

    nodeIdService = (NodeIdentificationService)
       getServiceBroker().getService( this,
                                    NodeIdentificationService.class,
                                    null);
    if (nodeIdService == null) {
      throw new RuntimeException("Unable to obtain node-id service.");
    }

    if (uidService == null) {
	uidService = (UIDService)
	    getServiceBroker().getService(this,
					  UIDService.class,
					  new ServiceRevokedListener() {
					      public void serviceRevoked(ServiceRevokedEvent re) {
						  if (UIDService.class.equals(re.getService()))
						  uidService = null;
					      }
					  });
    }

    //   System.out.println("\n LOADING InventoryPlugin of type: " + supplyType +
//  		       "in org: " + getAgentIdentifier().toString() +
//    		       " this plugin is: " + this);
  }

  public void unload() {
    super.unload();
    if (domainService != null) {
      getServiceBroker().releaseService(this, DomainService.class, domainService);
    }
    if (nodeIdService != null) {
      getServiceBroker().releaseService( this, NodeIdentificationService.class, nodeIdService);
    }
  }

  public TaskUtils getTaskUtils() {
    return taskUtils;
  }

  public TimeUtils getTimeUtils() {
    return timeUtils;
  }

  public AssetUtils getAssetUtils() {
    return AssetUtils;
  }

  public ScheduleUtils getScheduleUtils() {
    return scheduleUtils;
  }

  public String getSupplyType() {
    return supplyType;
  }

//   protected String getInventoryFileName() {
//     return inventoryFile;
//   }

  public Organization getMyOrganization() {
    return myOrganization;
  }

  public String getOrgName() {
    return myOrgName;
  }

  public long getCurrentTimeMillis() {
    return currentTimeMillis();
  }

  public void publishAdd(Object o) {
    getBlackboardService().publishAdd(o);
  }

  public BlackboardService getBBService() {
    return getBlackboardService();
  }

  public NodeIdentificationService getNodeIdService() {
    return nodeIdService;
  }

  public UIDService getUIDService() {
    return uidService;
  }

  public void publishAddExpansion(Expansion expansion) {
    PluginHelper.publishAddExpansion(getBlackboardService(), expansion);
  }

  public void publishChange(Object o) {
    getBlackboardService().publishChange(o);
  }

  public void publishRemove(Object o) {
    getBlackboardService().publishRemove(o);
  }

  public void removeSubTask(Task taskToRemove) {
    PluginHelper.removeSubTask(getBlackboardService(), taskToRemove);
    publishRemove(taskToRemove);
  }

  public PlanningFactory getPlanningFactory() {
    PlanningFactory rootFactory = null;
    if (domainService != null) {
      rootFactory = (PlanningFactory) domainService.getFactory("planning");
    }
    return rootFactory;
  }

  public LoggingService getLoggingService(Object requestor) {
    LoggingService ls = (LoggingService)
        getServiceBroker().getService(requestor,
                                      LoggingService.class,
                                      null);
    return LoggingServiceWithPrefix.add(ls, getAgentIdentifier() + ": ");
  }


  protected void execute() {
    //clear our new refill list
    newRefills.clear();

    // need to call these at beginning of execute cycle
    supplyTaskScheduler.initForExecuteCycle();
    projectionTaskScheduler.initForExecuteCycle();

    // if the OM changed and the window is further out then before
    // then mark the flag true so we process previously ignored tasks
    // and allocation results.  If it went down - don't undo work
    if (!Level6OMSubscription.getChangedCollection().isEmpty()) {
      long currentLevel6 = getEndOfLevelSix();
      if (logger.isInfoEnabled()) {
        logger.info("Inv Mgr got changed OM ... new end of level 6 window is: " +
                    currentLevel6 + " in agent: " + getAgentIdentifier() +
                    " supply type: " + getSupplyType());
      }
      if (currentLevel6 > prevLevel6) {
        OMChange = true;
      }
      //reset the previous level 6 to the current
      prevLevel6 = currentLevel6;
    }

    if (inventoryPolicy == null) {
      updateInventoryPolicy(inventoryPolicySubscription);
    }
    updateInventoryPolicy(inventoryPolicySubscription.getChangedCollection());
    processDetReq(detReqSubscription.getAddedCollection());
    cycleStamp = (new Date()).getTime();

    if (inventoryPolicy == null) {
      if (logger.isInfoEnabled()) {
        logger.info("\n InventoryPlugin " + supplyType +
                    " not ready to process tasks yet." +
                    " my inv policy is: " + inventoryPolicy + " in " + getMyOrganization());
      }
      return;
    }

    if (myOrganization == null) {
      myOrganization = getMyOrganization(selfOrganizations.elements());
    }

    if (myOrganization == null) {
      if (logger.isInfoEnabled()) {
        logger.info("\n InventoryPlugin " + supplyType +
                    " not ready to process tasks yet." +
                    " my org is: " + myOrganization);
      }
      return;
    }

    if (!initialized) {
      myOrgName = myOrganization.getItemIdentificationPG().getItemIdentification();
//       inventoryFile = getInventoryFile(supplyType);
//       getInventoryData();
      initialized = true;
    }


    logOPlan = logisticsPlan.updateOrgActivities(oplanSubscription, orgActSubscription);

    if ((logOPlan != null) && orgActSubscription.hasChanged()) {
      resetLogOPlanForInventories();
    }

    // if our top level MI task got removed, clean out the references .
    if (! aggMILSubscription.getRemovedCollection().isEmpty()) {
      detReqHandler.resetAggMITask();
      if (logger.isDebugEnabled()) {
        logger.debug("Agent: " + getAgentIdentifier().toString() + " Top Level MI removed["+supplyType+"]");
      }
    }

    //Extra debugging to check for same object on add and remove
    if (logger.isDebugEnabled()) {
      Collection addedRefills = refillSubscription.getAddedCollection();
      Collection removedRefills = refillSubscription.getRemovedCollection();
      Iterator addRefIt = addedRefills.iterator();
      while (addRefIt.hasNext()) {
        Task addedRefillTask = (Task) addRefIt.next();
        if (removedRefills.contains(addedRefillTask)) {
          logger.debug("Agent " + getAgentIdentifier().toString() + "InvPlugin[" +
                             getSupplyType() + "] Got a Refill Task added and removed in my subscription " +
                             addedRefillTask.getVerb() + " "+ addedRefillTask.getUID());
        }
      }

      Collection addedWithdraws = withdrawTaskSubscription.getAddedCollection();
      Collection removedWithdraws = withdrawTaskSubscription.getRemovedCollection();
      Iterator addWD = addedWithdraws.iterator();
      while (addWD.hasNext()) {
        Task addedWDTask = (Task) addWD.next();
        if (removedWithdraws.contains(addedWDTask)) {
          logger.debug("Agent " + getAgentIdentifier().toString() + "InvPlugin[" +
                       getSupplyType() + "] Got a Withdraw Task added and removed in my subscription " +
                       addedWDTask.getVerb() + " "+ addedWDTask.getUID());
        }
      }
    }
    if ((detReqHandler.getDetermineRequirementsTask(aggMILSubscription) != null) &&
        (logOPlan != null)) {
      if (rehydrateInvs) {
        addRehydratedInventories(blackboard.query(new InventoryPredicate(supplyType)));
        if (logger.isDebugEnabled()) {
          logger.debug("Agent: " + getAgentIdentifier().toString() + "Rehydrating Inventories for InvPlugin["+supplyType+"]" );
        }
        rehydrateInvs = false;
      }

      //Process all the removes at once - note that the supplyExpander.handleRemovedProjections sets the
      //touchedRemovedProjections boolean for use later.
      boolean touchedRemovedProjections = processRemoves();
      Collection addedSupply = supplyTaskScheduler.getAddedCollection();

      if (! commStatusSub.isEmpty()) {
        supplyExpander.determineCommStatus(commStatusSub, addedSupply);
      }
      TimeSpan timeSpan = null;
      if (turnOnTaskSched) {
        timeSpan = supplyTaskScheduler.getCurrentTimeSpan();
      } else {
        timeSpan = new ScheduleElementImpl(getLogOPlanStartTime(),
                                           getLogOPlanEndTime());
      }
      if (!addedSupply.isEmpty()) {
        expandIncomingRequisitions(getTasksWithoutPEs(addedSupply)); // fix for bug #1695
      }
      Collection changedSupply = supplyTaskScheduler.getChangedCollection();
      if (!changedSupply.isEmpty()) {
        supplyExpander.updateChangedRequisitions(changedSupply);
      }
      supplyTaskScheduler.finishedExecuteCycle();

      Collection addedProjections = projectionTaskScheduler.getAddedCollection();
      TimeSpan timeSpan2 = null;
      if (turnOnTaskSched) {
        timeSpan2 = projectionTaskScheduler.getCurrentTimeSpan();
      } else {
        timeSpan2 = new ScheduleElementImpl(getLogOPlanStartTime(),
                                            getLogOPlanEndTime());
      }
      if (!addedProjections.isEmpty()) {
        // getTasksWithoutPEs is fix for bug #1695
        touchedProjections = expandIncomingProjections(getTasksWithoutPEs(addedProjections));
      }
      Collection changedProjections = projectionTaskScheduler.getChangedCollection();
      if (!changedProjections.isEmpty()) {
        supplyExpander.updateChangedProjections(changedProjections);
        touchedChangedProjections = true;
// System.out.println("Touched changed projections in " + getAgentIdentifier() +
//                                " type is" + getSupplyType());
      }
      projectionTaskScheduler.finishedExecuteCycle();

      // Rescind tasks that no longer have a provider
      // and
      // Allocate any refill tasks from previous executions that were not allocated to providers
      // but only if we are not about to rip out previous work we have done
      if (didOrgRelationshipsChange()) {
        if (logger.isDebugEnabled()) {
          logger.debug("ORG RELATIONSHIPS CHANGED SDSD myorg: " + myOrganization + " supply type:" +
                      supplyType + " role: " + getRole(supplyType) + "\n");
        }
        getOverlappingTasks(refillAllocationSubscription, Constants.Verb.Supply);
        getOverlappingTasks(refillAllocationSubscription, Constants.Verb.ProjectSupply);

        // Handle unallocated tasks
        Collection unalloc = null;
        if (addedSupply.isEmpty() && changedSupply.isEmpty()) {
          unalloc = getTaskUtils().getUnallocatedTasks(refillSubscription,
                                                       Constants.Verb.Supply);
          if (!unalloc.isEmpty()){
            if (logger.isInfoEnabled())
              logger.info("TRYING TO ALLOCATE SUPPLY REFILL TASKS...");
            externalAllocator.allocateRefillTasks(unalloc);
          }
        }
        if (addedProjections.isEmpty() && changedProjections.isEmpty()) {
          unalloc = getTaskUtils().getUnallocatedTasks(refillSubscription,
                                                       Constants.Verb.ProjectSupply);
          if (!unalloc.isEmpty()) {
            if (logger.isInfoEnabled())
              logger.info("TRYING TO ALLOCATE PROJECTION REFILL TASKS...");
            externalAllocator.allocateRefillTasks(unalloc);
          }
        }
      }

      // call the Refill Generators if we have new demand
      if (!getTouchedInventories().isEmpty()) {
        // update the inventory customer hash tables when we have new demand
        rebuildPGCustomerHash();
        //check to see if we have new projections
        if (touchedProjections || touchedRemovedProjections || touchedChangedProjections) {
          if (logger.isDebugEnabled()) {
            logger.debug("Agent: " + getAgentIdentifier().toString() + "InvPlugin" + supplyType+"]" +
                         "something touched: tprojections: " + touchedProjections +
                         " tRemovedProj" + touchedRemovedProjections + " tChangedProj: " + touchedChangedProjections);
          }
          //check to see if the OM changed.  If it did process all inventories
          //since we probably ignored some demand tasks before the change
          if (OMChange) {
            refillProjGenerator.calculateRefillProjections(getInventories(),
                                                           criticalLevel,
                                                           getEndOfLevelSix(),
                                                           getEndOfLevelTwo(),
                                                           refillComparator);
          } else {
            refillProjGenerator.calculateRefillProjections(getTouchedInventories(),
                                                           criticalLevel,
                                                           getEndOfLevelSix(),
                                                           getEndOfLevelTwo(),
                                                           refillComparator);
          }
        }
        refillGenerator.calculateRefills(getTouchedInventories(), refillComparator);
        externalAllocator.allocateRefillTasks(newRefills);

        //we might get new demand where we don't need to generate any new refills
        // such as small demand from the stimulator servlet - when this happens we
        // need to kick the allocation assessor to allocate the withdraws
        allocationAssessor.reconcileInventoryLevels(getActionableInventories());

      }

      // if we are in downward flow ONLY check the withdraw expansion results
      // note we may go through the whole list multiple times - but this seems like the
      // simplest fix to get rid of places where we miss change reports because the AA
      // compares previous results to new ones and leaves the old ones if they are equal.
      // note that the updates only occur if the reported result is not equal to the estimated
      // so we will not be waking up the whole chain by checking these more than once.
      HashSet backwardFlowTouched = null;
      if (getTouchedInventories().isEmpty()) {
        supplyExpander.updateAllocationResult(expansionSubscription);
        backwardFlowTouched =
            externalAllocator.updateAllocationResult(refillAllocationSubscription);
        // if the OM changed, process ALL inventories for demand projections and
        // allocation results since some results were likely ignored before
        // the OM level 6 window changed.
        if (OMChange) {
          refillProjGenerator.calculateRefillProjections(getInventories(),
                                                         criticalLevel,
                                                         getEndOfLevelSix(),
                                                         getEndOfLevelTwo(),
                                                         refillComparator);
          externalAllocator.allocateRefillTasks(newRefills);
          allocationAssessor.reconcileInventoryLevels(getInventories());
        } else {
          allocationAssessor.reconcileInventoryLevels(backwardFlowTouched);
        }
      } else {
        // if the we are not in backwards flow but the OM changed
        // process ARs anyway because we may not get woken up again to
        // process them if they have all come in already
        if (OMChange) {
          allocationAssessor.reconcileInventoryLevels(getInventories());
        }
      }

      // update the Maintain Inventory Expansion results
      PluginHelper.updateAllocationResult(MIExpansionSubscription);
      PluginHelper.updateAllocationResult(MITopExpansionSubscription);
      PluginHelper.updateAllocationResult(DetReqInvExpansionSubscription);

      if (backwardFlowTouched != null) {
        takeInventorySnapshot(backwardFlowTouched);
      }
      takeInventorySnapshot(getTouchedInventories());
      takeInventorySnapshot(getInventoriesWithDeletions());

      // touchedInventories and inventoriesWithDeletions should not be cleared until
      // the end of transaction
      touchedInventories.clear();
      inventoriesWithDeletions.clear();
      //backwardFlowInventories.clear(); //###
      touchedProjections = false;
      touchedChangedProjections = false;
      OMChange = false;
      //testBG();
    } else {
      //process any removes if the DetReq task returns null - this should catch the case where it gets rescinded.
      // to reduce the amount of times this gets run at start up - only do it if we don't have
      // a log oplan also.  Note that if the oplan gets rescinded this might need to change.
      if (logOPlan != null) {
        processRemoves();
        takeInventorySnapshot(getTouchedInventories());
        touchedInventories.clear();
      }
    }
    supplyExpander.checkCommStatusAlarms();
  }

  protected boolean processRemoves() {
    if (logger.isDebugEnabled()) {
      logger.debug("Agent: " + getAgentIdentifier().toString() + " Processing Subscription Removes["+supplyType+"]");
    }
    detReqHandler.handleMILTasks(milSubscription);
    boolean touchedRemovedProjections =
      supplyExpander.handleRemovedProjections(projectWithdrawTaskSubscription.getRemovedCollection());
    supplyExpander.handleRemovedRequisitions(withdrawTaskSubscription.getRemovedCollection());
    handleRemovedRefills(refillSubscription.getRemovedCollection());
    Collection removedDispositions = dispositions.getRemovedCollection();
    if (! removedDispositions.isEmpty()) {
      supplyExpander.handleRemovedDispositions(removedDispositions);
    }
    return touchedRemovedProjections;
  }

  protected void getProviderDates(HashMap providerStartDates, HashMap providerEndDates) {
    RelationshipSchedule relSched = myOrganization.getRelationshipSchedule();
    Collection relationships = relSched.getMatchingRelationships(TimeSpan.MIN_VALUE,
                                                                 TimeSpan.MAX_VALUE);
    Iterator rit = relationships.iterator();
    Role myRole = getRole(supplyType);
// 	  System.out.println("SDSD myorg: " + myOrganization + " supply type:" +
// 			       supplyType + " role: " + getRole(supplyType) + "\n");
    Date date;
    while (rit.hasNext()) {
      Relationship r = (Relationship) rit.next();
      HasRelationships hr = relSched.getOther(r);
      if (hr instanceof Organization) {
        Role role = relSched.getOtherRole(r);
        if (role == myRole) {
	    date = r.getEndDate();
	    providerEndDates.put(hr, date);
	    date = r.getStartDate();
	    providerStartDates.put(hr, date);
          //	  System.out.println("SDSD   org: " + hr + " end:" + date + "\n");
        }
      }
    }
  }

  protected HashMap relationshipScheduleMap() {
    HashMap providerAvailSched = new HashMap();
    RelationshipSchedule relSched = myOrganization.getRelationshipSchedule();
    Role myRole = getRole(supplyType);
    Collection relationships = relSched.getMatchingRelationships(myRole, new TimeSpan.Span(TimeSpan.MIN_VALUE,
                                                                 TimeSpan.MAX_VALUE));
    Iterator rit = relationships.iterator();
    Schedule sched = null;

    while (rit.hasNext()) {
      Relationship r = (Relationship) rit.next();
      HasRelationships hr = relSched.getOther(r);
      //String provider;
      if (hr instanceof Organization) {
        if (! providerAvailSched.containsKey(hr)) {
          sched = new ScheduleImpl();
          sched.add(r);
          providerAvailSched.put(hr, sched);
          if (myOrgName.indexOf("1-35-ARBN") >= 0 && supplyType.equals("BulkPOL") && logger.isDebugEnabled()) {
            logger.debug(" adding a provider to the map " + hr);
          }
        }
        else {
          sched = (Schedule) providerAvailSched.get(hr);
          if (myOrgName.indexOf("1-35-ARBN") >= 0 && supplyType.equals("BulkPOL") && logger.isDebugEnabled()) {
            logger.debug(" adding a relationship to the provider map " + hr);
          }
          sched.add(r);
        }
        if (myOrgName.indexOf("1-35-ARBN") >= 0 && supplyType.equals("BulkPOL") && logger.isDebugEnabled()) {
          logger.debug("Time spans are " + r.getStartDate() + " - " + r.getEndDate() + hr);
        }
      }
    }
    return providerAvailSched;
  }
  // TODO:  may not need this
  protected Set uniqueProviders() {
    RelationshipSchedule myOrgRelSched = myOrganization.getRelationshipSchedule();
    Role myRole = getRole(supplyType);
    Collection relationships = myOrgRelSched.getMatchingRelationships(myRole, new TimeSpan.Span(TimeSpan.MIN_VALUE,
                                                                                                TimeSpan.MAX_VALUE));
    Iterator rit = relationships.iterator();
    Set uniqueProviders = new HashSet();
    while (rit.hasNext()) {
      Relationship r = (Relationship) rit.next();
      HasRelationships hr = myOrgRelSched.getOther(r);
      if (hr instanceof Organization) {
        uniqueProviders.add(hr);
      }
    }
    return uniqueProviders;
  }


  protected class EnclosedPredicate implements UnaryPredicate {
    protected RelationshipSchedule schedule;
    protected Role role;
    HasRelationships other;
    long start, end;

    public EnclosedPredicate(RelationshipSchedule schedule, Role role, HasRelationships other, long start, long end) {
      this.schedule = schedule;
      this.role = role;
      this.other = other;
      this.start = start;
      this.end = end;
    }


    public boolean execute(Object obj) {
      Relationship relationship = (Relationship)obj;
      return ((schedule.getOtherRole(relationship).equals(role)) &&
          (schedule.getOther(relationship).equals(other)) &&
          ((relationship.getStartTime() <= start) &&
          (relationship.getEndTime() >= end)));
    }
  }

  protected class EnclosedSchedPredicate implements UnaryPredicate {
    long start;
    long end;
    public EnclosedSchedPredicate(long start, long end) {
      this.start = start;
      this.end = end;
    }
    public boolean execute(Object obj) {
      if (! (obj instanceof ScheduleElement)) {
        throw new IllegalArgumentException(new StringBuffer().append(
            " EnclosedSchedPredicate was expecting a ScheduleElement ")
                                           .append(obj)
                                           .toString());
      }
      ScheduleElement se = (ScheduleElement) obj;
      return se.getStartTime() <= start && se.getEndTime() >= end;
    }
  }

  protected Collection getUncoveredTasks(Collection tasks) {
    ArrayList uncoveredTasks = new ArrayList();
    HashMap providersSched = relationshipScheduleMap();
    boolean overlap;

    for (Iterator iterator = tasks.iterator(); iterator.hasNext();) {
      Task task = (Task) iterator.next();
      overlap = false;
      Iterator it = providersSched.values().iterator();
      while (it.hasNext()) {
        Schedule sched =  (Schedule) it.next();
        if (task.getVerb().equals(Constants.Verb.ProjectSupply)) {
          long start = TaskUtils.getStartTime(task);
          long end = TaskUtils.getEndTime(task);
          if (sched.getEncapsulatedScheduleElements(start, end).size() > 0 ||
              sched.getOverlappingScheduleElements(start,end).size() > 0) {
            overlap = true;
          }
        } else { // It is a Supply Task
          if (sched.getScheduleElementsWithTime(TaskUtils.getEndTime(task)).size() > 0) {
            overlap = true;
          }
        }
        if (! overlap) {
          uncoveredTasks.add(task);
        }
      }
    }
    return uncoveredTasks;
  }

  protected void getOverlappingTasks(Collection refill_allocations, Verb verb) {
    //RelationshipSchedule myOrgRelSched = myOrganization.getRelationshipSchedule();
    HashMap providersSched = relationshipScheduleMap();
    Iterator raIt = refill_allocations.iterator();
    ArrayList unprovidedTasks = new ArrayList();
    ArrayList partial = new ArrayList();
    Role myRole = getRole(supplyType);
    while (raIt.hasNext()) {
      Allocation alloc = (Allocation)raIt.next();
      Task task = alloc.getTask();
      if ((alloc != null) && (task.getVerb().equals(verb))){
        long taskEnd = TaskUtils.getEndTime(task);
        //if the task is totally in the past, don't touch it!
        if (currentTimeMillis() > taskEnd) {
          continue;
        }
        Organization provider  = (Organization)alloc.getAsset();
        if (alloc.getRole() != getRole(supplyType)) {
          if (logger.isWarnEnabled())
            logger.warn("SDSD MISMATCH: " + alloc.getRole() + " " + task + "\n");
        }
        Schedule availSched = (Schedule) providersSched.get(provider);

        Date start = null;         // just a place holder
        Collection enclosed;
        if (availSched != null) {
          if (verb.equals(Constants.Verb.ProjectSupply)) {
            // only need provider from start to end - bucketSize
            long taskStart = TaskUtils.getStartTime(task);
            start = new Date(taskStart);

            //EnclosedPredicate enclosedPred = new EnclosedPredicate(myOrgRelSched, myRole, provider, taskStart, taskEnd);
            EnclosedSchedPredicate enclosedPred = new EnclosedSchedPredicate(taskStart, taskEnd);

            int size = availSched.filter(enclosedPred).size();
            //System.out.println("Task times " + new Date(taskStart) + " " + start + " size of enclosed" + size);
            if (size == 0) {
              //if (availSched.getEncapsulatedScheduleElements(taskStart, taskEnd - bucketSize).size() == 0) {
              if (availSched.getOverlappingScheduleElements(taskStart, taskEnd - bucketSize).size() > 0) {
                partial.add(task);
              } else {
                unprovidedTasks.add(task);
              }
            }
          } else { // it is Supply Task
            enclosed = availSched.getScheduleElementsWithTime(taskEnd);
            if (enclosed.size() == 0) {
              if (myOrgName.indexOf("1-35-ARBN") >= 0 && supplyType.equals("BulkPOL") && logger.isDebugEnabled()) {
                logger.debug("Adding task to list  " + start + "  " + new Date(taskEnd) +
                             unprovidedTasks.size());
              }
              unprovidedTasks.add(task);
            }
          }
        }
      }
    }
    if (myOrgName.indexOf("1-35-ARBN") >= 0 && supplyType.equals("BulkPOL") && logger.isDebugEnabled()) {
      logger.debug("number of unprovided tasks is " + unprovidedTasks.size());
    }
    if (!unprovidedTasks.isEmpty()){
      if (logger.isInfoEnabled()) {
        logger.info("Trying to rescind and reallocate unprovided supply refill tasks...");
      }
      externalAllocator.rescindTaskAllocations(unprovidedTasks);
      externalAllocator.allocateRefillTasks(unprovidedTasks);
    }
    if (! partial.isEmpty()) {
      ArrayList originalTasks = new ArrayList();
      ArrayList partialsToAlloc = new ArrayList();
      Iterator partIt = partial.iterator();
      ArrayList failedSplits = new ArrayList();
      while (partIt.hasNext()) {
        Task taskToSplit = (Task) partIt.next();
        List splitTimes = getSplitTimes(taskToSplit, providersSched);
        if (splitTimes.isEmpty()) {
          failedSplits.add(taskToSplit);
          continue;
        }
        Collection newPartialTasks = getTaskUtils().splitProjection(taskToSplit, splitTimes, this);
        originalTasks.add(taskToSplit);
        partialsToAlloc.addAll(newPartialTasks);
      }

      if (! failedSplits.isEmpty()) {
        externalAllocator.rescindTaskAllocations(failedSplits);
        externalAllocator.allocateRefillTasks(failedSplits);
      }
      if (! partialsToAlloc.isEmpty()) {
        externalAllocator.rescindTaskAllocations(originalTasks);
        externalAllocator.allocateRefillTasks(partialsToAlloc);
      }
    }
    //return unprovidedTasks;
  }

  protected List getSplitTimes(Task task, HashMap providerSched) {
    ArrayList splits = new ArrayList();
    long start = TaskUtils.getStartTime(task);
    long end = TaskUtils.getEndTime(task);
    PlanElement pe = task.getPlanElement();
    Organization provider = null;
    if (pe != null && pe instanceof Allocation) {
      provider = (Organization) ((Allocation)pe).getAsset();
    }
    Schedule sched = (Schedule)providerSched.get(provider);
    Collection overlaps = sched.getOverlappingScheduleElements(start, end);
    if (overlaps.isEmpty()) {
      logger.error("InvPlugin got no overlapping elements for task start " +
                   new Date(start) + " end " + new Date(end) +
                   " provider is: " + provider);
      return Collections.EMPTY_LIST;
    }
    ScheduleElement firstlap = (ScheduleElement) overlaps.iterator().next();
    TimeSpan first = new MutableTimeSpan();
    TimeSpan second = new MutableTimeSpan();
    if (start >= firstlap.getStartTime()) {
      if (logger.isInfoEnabled()) {
        logger.info("GetSplitTimes... task is: " + new Date (start) + " ... " +
                     new Date(end) + "  overlapping relationship is: " +
                     new Date(firstlap.getStartTime()) + " ... " +
                     new Date(firstlap.getEndTime()));
      }
      if (! (isValidSpan(start, firstlap.getEndTime()) && isValidSpan(firstlap.getEndTime(), end))) {
        return Collections.EMPTY_LIST;
      }
      ((NewTimeSpan)first).setTimeSpan(start, firstlap.getEndTime());
      ((NewTimeSpan)second).setTimeSpan(firstlap.getEndTime(), end);
    } else {
      if (logger.isInfoEnabled()) {
        logger.info("GetSplitTimes... task is: " + new Date (start) + " ... " +
                    new Date(end) + "  overlapping relationship is: " +
                    new Date(firstlap.getStartTime()) + " ... " +
                    new Date(firstlap.getEndTime()));
      }
      if (! (isValidSpan(firstlap.getStartTime(), end) && isValidSpan(start, firstlap.getStartTime()))) {
        return Collections.EMPTY_LIST;
      }
      ((NewTimeSpan) first).setTimeSpan(firstlap.getStartTime(), end);
      ((NewTimeSpan) second).setTimeSpan(start, firstlap.getStartTime());
    }
    splits.add(first);
    splits.add(second);
    return splits;
  }

  protected boolean isValidSpan(long start, long end) {
    if (start < end) {
      return true;
    } else {
      if (logger.isErrorEnabled()) {
        logger.error(" Invalid time span in Inventory plugin, start  " + new Date(start) + " end " + new Date(end));
      }
      return false;
    }
  }

  /**
   * @param task
   * @return split times
   */
  public List getNewTaskSplitTimes(Task task) {
    HashMap provHashMap = relationshipScheduleMap();
    ArrayList splits = new ArrayList();
    long start = TaskUtils.getStartTime(task);
    long end = TaskUtils.getEndTime(task);
    Collection values = provHashMap.values();
    Schedule tmp = new ScheduleImpl();
    Iterator valIt = values.iterator();
    while (valIt.hasNext()) {
      Schedule sched = (Schedule) valIt.next();
      tmp.addAll(sched);
    }
    // Filter out the elements that enclose the span; if they exist, the relationship
    // schedule was updated and we are covered. Return an empty list and let the plugin run
    // again with the updated info.
    Collection enclosed = tmp.filter(new EnclosedSchedPredicate(start, end));
    if (! enclosed.isEmpty()) {
      return splits;
    }
    // if we make it here, then there can only be partial overlaps or none
    Collection overlaps = tmp.getOverlappingScheduleElements(start, end);
    if (overlaps.isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    ScheduleElement firstlap = (ScheduleElement) overlaps.iterator().next();
    TimeSpan first = new MutableTimeSpan();
    TimeSpan second = new MutableTimeSpan();
    if (start >= firstlap.getStartTime()) {
      if (! (isValidSpan(firstlap.getEndTime(), end) && isValidSpan(start, firstlap.getEndTime()))) {
        return Collections.EMPTY_LIST;
      }
      ((NewTimeSpan)first).setTimeSpan(start, firstlap.getEndTime());
      ((NewTimeSpan)second).setTimeSpan(firstlap.getEndTime(), end);
    } else {
      if (! (isValidSpan(firstlap.getStartTime(), end) && isValidSpan(start, firstlap.getStartTime()))) {
        return Collections.EMPTY_LIST;
      }
      ((NewTimeSpan) first).setTimeSpan(firstlap.getStartTime(), end);
      ((NewTimeSpan) second).setTimeSpan(start, firstlap.getStartTime());
    }
    splits.add(first);
    splits.add(second);
    return splits;
  }

  protected Collection getUnprovidedTasks(Collection refill_allocations, Verb verb,
                                          HashMap providerStartDates, HashMap providerEndDates) {
    Iterator raIt = refill_allocations.iterator();
    ArrayList unprovidedTasks = new ArrayList();
    Task task;
    Organization provider;
    Allocation alloc;
    long taskEnd, taskStart;
    Date providerEndDate, providerStartDate;
    while (raIt.hasNext()) {
      alloc = (Allocation)raIt.next();
      task = alloc.getTask();
      if (task.getVerb().equals(verb)){
        taskEnd = TaskUtils.getEndTime(task);
        provider = (Organization)alloc.getAsset();
        if (alloc.getRole() != getRole(supplyType)) {
	    if (logger.isWarnEnabled()) {
		logger.warn("SDSD MISMATCH: " + alloc.getRole() + " " + task + "\n");
	    }
        }
        providerEndDate = (Date) providerEndDates.get(provider);
        if (verb.equals(Constants.Verb.ProjectSupply)) {
          taskStart = TaskUtils.getStartTime(task);
          providerStartDate = (Date) providerStartDates.get(provider);

          // only need provider from start to end - bucketSize
          if ((providerEndDate != null && providerEndDate.getTime() < taskEnd - bucketSize) ||
              (providerStartDate != null && providerStartDate.getTime() > taskStart))  {

            unprovidedTasks.add(task);
          }
        } else {
          if (providerEndDate != null && providerEndDate.getTime() < taskEnd) {
            unprovidedTasks.add(task);
          }
        }
      }
    }
//       if (! unprovidedTasks.isEmpty()) {
// 	  System.out.println("SDSD unprovided: " + unprovidedTasks + "\n");
//       }
    return unprovidedTasks;
  }

  /** Subscription for aggregatable support requests. **/
  protected IncrementalSubscription detReqSubscription;

  /** Subscription for the aggregated support request **/
  protected IncrementalSubscription aggMILSubscription;

  /** Subscription for the MIL tasks **/
  protected IncrementalSubscription milSubscription;

  /** Subscription for the Organization(s) in which this plugin resides **/
  protected IncrementalSubscription selfOrganizations;

  /** Subscription for incoming Supply tasks **/
  protected TaskScheduler supplyTaskScheduler;
//  protected IncrementalSubscription supplyTaskSubscription;

  /** Subscription for incoming Projection tasks **/
  protected TaskScheduler projectionTaskScheduler;
//  protected IncrementalSubscription projectionTaskSubscription;

  /** Subscription for Allocations on outgoing Refill (Supply & ProjectSupply) tasks **/
  protected IncrementalSubscription refillAllocationSubscription;

  /** Subscription for my Refill (Supply & ProjectSupply) tasks **/
  protected IncrementalSubscription refillSubscription;

  /** Subscription for my Non-Refill (Supply & ProjectSupply) tasks **/
  protected IncrementalSubscription nonrefillSubscription;

  /** Subscription for Supply/ProjectSupply Expansions **/
  protected IncrementalSubscription expansionSubscription;

  /** Subscription for InventoryPolicy **/
  protected IncrementalSubscription inventoryPolicySubscription;

  /** Subscription for OPlan object **/
  protected IncrementalSubscription oplanSubscription;

  /** Subscription for Withdraw tasks created by this plugin **/
  protected IncrementalSubscription withdrawTaskSubscription;

  /** Subscription for ProjectWithdraw tasks created by this plugin **/
  protected IncrementalSubscription projectWithdrawTaskSubscription;

  /** Subscription for MaintainInventory Expansion PlanElements created by this plugin**/
  protected IncrementalSubscription MIExpansionSubscription;

  /** Subscription for MaintainInventory Expansion for Top level MI task (Aggregate task) **/
  protected IncrementalSubscription MITopExpansionSubscription;

  /** Subscription for DetermineRequirements of type MaintainInventory Expansion **/
  protected IncrementalSubscription DetReqInvExpansionSubscription;

  /** special subscription to oms only used in subsistence to deal with the level2 -> level6
   *  issue that occurs because subsistence does not generate level 2 tasks
   **/
  protected IncrementalSubscription Level6OMSubscription;

    //Org Activity subscription
  protected IncrementalSubscription orgActSubscription;

  /** Subscription for CommStatus object **/
  protected IncrementalSubscription commStatusSub;

  /** Subscription for removed dispositions, need to reconcile with prediction tasks **/
  protected IncrementalSubscription dispositions;

  protected IncrementalSubscription shortfallSummary;

  protected void setupSubscriptions() {
    if (!getBlackboardService().didRehydrate()) {
      setupOperatingModes();
      prevLevel6 = getEndOfLevelSix();
    } else {
      // if we did rehydrate set a flag to rehydrate the inventories
      //when we are ready in the execute block
      rehydrateInvs = true;
      Collection level2OMs = getBlackboardService().
          query(new OperatingModePredicate(supplyType, LEVEL_2_TIME_HORIZON));
      //there should only be one.
      Iterator level2it = level2OMs.iterator();
      if (level2it.hasNext()) {
        level2Horizon = (OperatingMode) level2it.next();
      }
      Collection level6OMs = getBlackboardService().
          query(new OperatingModePredicate(supplyType, LEVEL_6_TIME_HORIZON));
      //there should only be one.
      Iterator level6it = level6OMs.iterator();
      if (level6it.hasNext()) {
        level6Horizon = (OperatingMode) level6it.next();
      }
      prevLevel6 = getEndOfLevelSix();
      if (level2Horizon == null || level6Horizon == null) {
        if (logger.isErrorEnabled()) {
          logger.error("InventoryPlugin in agent: " + getAgentIdentifier() +
                       " of supply type: " + supplyType +
                       " is missing operating modes upon rehydration... level2 OM is: " +
                       level2Horizon + " level 6 OM is: " + level6Horizon);
        }
      }
    }

//    Level6OMSubscription = (IncrementalSubscription) blackboard.subscribe(new InventoryPredicateHelper.OperatingModePredicate(supplyType, LEVEL_6_TIME_HORIZON));
    Level6OMSubscription = (IncrementalSubscription) blackboard.subscribe(new OperatingModePredicate(supplyType, LEVEL_6_TIME_HORIZON));
    detReqSubscription = (IncrementalSubscription) blackboard.subscribe(new DetInvReqPredicate(taskUtils));
    aggMILSubscription = (IncrementalSubscription) blackboard.subscribe(new AggMILPredicate());
    milSubscription = (IncrementalSubscription) blackboard.subscribe(new MILPredicate());
    detReqHandler.addMILTasks(milSubscription.elements());
    selfOrganizations = (IncrementalSubscription) blackboard.subscribe(orgsPredicate);
    inventoryPolicySubscription = (IncrementalSubscription) blackboard.subscribe(new InventoryPolicyPredicate(supplyType));
    oplanSubscription = (IncrementalSubscription) blackboard.subscribe(new OplanPredicate());
    //logisticsOPlanSubscription = (IncrementalSubscription) blackboard.subscribe(new LogisticsOPlanPredicate());
    withdrawTaskSubscription = (IncrementalSubscription) blackboard.subscribe(new WithdrawPredicate(supplyType));
    projectWithdrawTaskSubscription = (IncrementalSubscription) blackboard.subscribe(new ProjectWithdrawPredicate(supplyType));
    MIExpansionSubscription = (IncrementalSubscription) blackboard.subscribe(new MIExpansionPredicate(supplyType, taskUtils));
    MITopExpansionSubscription = (IncrementalSubscription) blackboard.subscribe(new MITopExpansionPredicate());
    DetReqInvExpansionSubscription = (IncrementalSubscription) blackboard.subscribe(new DetReqInvExpansionPredicate(taskUtils));
    commStatusSub = (IncrementalSubscription) blackboard.subscribe(new CommStatusPredicate());
    dispositions = (IncrementalSubscription) blackboard.subscribe(new DispositionsPredicate(supplyType, taskUtils));

    if (getAgentIdentifier() == null && logger.isErrorEnabled()) {
      logger.error("No agentIdentifier ... subscriptions need this info!!  In plugin: " + this);
    }
    refillAllocationSubscription = (IncrementalSubscription) blackboard.
        subscribe(new RefillAllocPredicate(supplyType, getAgentIdentifier().toString(), taskUtils));
    expansionSubscription = (IncrementalSubscription) blackboard.
        subscribe(new ExpansionPredicate(supplyType, getAgentIdentifier().toString(), taskUtils));
    refillSubscription = (IncrementalSubscription) blackboard.
        subscribe(new RefillPredicate(supplyType, getAgentIdentifier().toString(), taskUtils));
    nonrefillSubscription = (IncrementalSubscription) blackboard.
        subscribe(new NonRefillPredicate(supplyType, getAgentIdentifier().toString(), taskUtils));

    shortfallSummary = (IncrementalSubscription) blackboard.
	subscribe(new ShortfallSumPredicate(getSupplyType()));


    //LogOPlan replacment
    orgActSubscription = (IncrementalSubscription) blackboard.subscribe(new OrgActivitiesPredicate());


    // Setup TaskSchedulers
    setupTaskSchedulers();
  }

  protected void setupTaskSchedulers() {
    String taskScheduler = (String)pluginParams.get(TASK_SCHEDULER_ON);
    turnOnTaskSched = new Boolean(taskScheduler).booleanValue();
    QuiescenceReportService qrs = (QuiescenceReportService)
        getServiceBroker().getService(this, QuiescenceReportService.class, null);
    AgentIdentificationService ais = (AgentIdentificationService)
        getServiceBroker().getService(this, AgentIdentificationService.class, null);
    qrs.setAgentIdentificationService(ais);
    QuiescenceAccumulator q = new QuiescenceAccumulator (qrs);
    String id = getAgentIdentifier().toString();
    if (turnOnTaskSched) {
      if (logger.isInfoEnabled())
        logger.info("Inv Plugin TASK SCHEDULER ON for "+id + getSupplyType());
      java.io.InputStream is = null;
      try {
        is = getConfigFinder().open ("supplyTaskPolicy.xml");
      } catch (Exception e) {
        logger.error ("Could not find file supplyTaskPolicy.xml");
      }
      supplyTaskScheduler = new TaskScheduler
          (new SupplyTaskPredicate(supplyType, id, taskUtils),
           TaskSchedulingPolicy.fromXML (is, this, getAlarmService()),
           blackboard, q, logger, "supplyTasks for " + getBlackboardClientName());
      try {
        is = getConfigFinder().open ("projectionTaskPolicy.xml");
      } catch (Exception e) {
        logger.error ("Could not find file projectionTaskPolicy.xml");
      }
      projectionTaskScheduler = new TaskScheduler
          (new ProjectionTaskPredicate(supplyType, id, taskUtils),
           TaskSchedulingPolicy.fromXML (is, this, getAlarmService()),
           blackboard, q, logger, "projTasks for " + getBlackboardClientName());
    } else { // TaskScheduler OFF
      if (logger.isInfoEnabled())
        logger.info("Inv Plugin TASK SCHEDULER OFF for "+id+ getSupplyType());
      supplyTaskScheduler = new TaskScheduler
          (new SupplyTaskPredicate(supplyType, id, taskUtils),
           new TaskSchedulingPolicy (new TaskSchedulingPolicy.Predicate[]
           {TaskSchedulingPolicy.PASSALL}),
           blackboard, q, logger, "supplyTasks for " + getBlackboardClientName());

      projectionTaskScheduler = new TaskScheduler
          (new ProjectionTaskPredicate(supplyType, id, taskUtils),
           new TaskSchedulingPolicy (new TaskSchedulingPolicy.Predicate[]
           {TaskSchedulingPolicy.PASSALL}),
           blackboard, q, logger, "projTasks for " + getBlackboardClientName());
    }
//    supplyTaskSubscription = (IncrementalSubscription) blackboard.
//        subscribe(new SupplyTaskPredicate(supplyType, id, taskUtils));
//    projectionTaskSubscription = (IncrementalSubscription) blackboard.
//        subscribe(new ProjectionTaskPredicate(supplyType, id, taskUtils));
  }

  protected static class OrgActivitiesPredicate implements UnaryPredicate {
    public OrgActivitiesPredicate() {
    }
    public boolean execute(Object o) {
      if (o instanceof OrgActivity) {
	  return true;
      }
      return false;
    }
  }

  protected static UnaryPredicate orgsPredicate = new UnaryPredicate() {
    public boolean execute(Object o) {
      if (o instanceof Organization) {
        return ((Organization) o).isSelf();
      }
      return false;
    }
  };

  protected static class ShortfallSumPredicate implements UnaryPredicate {

    String supplyType;

    public ShortfallSumPredicate(String aSupplyType) {
	supplyType = aSupplyType;
    }

    public boolean execute(Object o) {
      if (o instanceof ShortfallSummary) {
        return (((ShortfallSummary) o).getSupplyType().equals(supplyType));
      }
      return false;
    }
  };


  protected static class SupplyTaskPredicate
      implements TaskSchedulingPolicy.Predicate {
    String supplyType;
    String orgName;
    TaskUtils taskUtils;

    public SupplyTaskPredicate(String type, String myOrg, TaskUtils aTaskUtils) {
      supplyType = type;
      orgName = myOrg;
      taskUtils = aTaskUtils;
    }

    public boolean execute(Task task) {
      return task.getVerb().equals(Constants.Verb.SUPPLY) &&
          taskUtils.isDirectObjectOfType(task, supplyType) &&
          (!taskUtils.isMyRefillTask(task, orgName)) &&
          (taskUtils.getQuantity(task) > 0);
    }
  }


  protected static class ProjectionTaskPredicate
      implements TaskSchedulingPolicy.Predicate {
    String supplyType;
    String orgName;
    TaskUtils taskUtils;

    public ProjectionTaskPredicate(String type, String orgname, TaskUtils aTaskUtils) {
      supplyType = type;
      orgName = orgname;
      taskUtils = aTaskUtils;
    }

    public boolean execute(Task task) {
      return task.getVerb().equals(Constants.Verb.PROJECTSUPPLY) &&
          taskUtils.isDirectObjectOfType(task, supplyType) &&
          (!taskUtils.isMyInventoryProjection(task, orgName));
    }
  }

  /**
   Passes DetermineRequirements tasks of type MaintainInventory.
   **/
  protected static class DetInvReqPredicate implements UnaryPredicate {

    protected TaskUtils taskUtils;

    public DetInvReqPredicate(TaskUtils aTaskUtils) {
      taskUtils = aTaskUtils;
    }

    public boolean execute(Object o) {
      if (o instanceof Task) {
        Task t = (Task) o;
        if (t.getVerb().equals(Constants.Verb.DETERMINEREQUIREMENTS)) {
          return taskUtils.isTaskOfType(t, "MaintainInventory");
        }
      }
      return false;
    }
  }

  /** Grab the Expansion of DetReq MaintainInventory and update ARs **/
  protected static class DetReqInvExpansionPredicate implements UnaryPredicate {
    private TaskUtils taskUtils;

    public DetReqInvExpansionPredicate(TaskUtils aTaskUtils) {
      taskUtils = aTaskUtils;
    }

    public boolean execute(Object o) {
      if (o instanceof Expansion) {
        Task parent = ((Expansion) o).getTask();
        if (parent.getVerb().equals(Constants.Verb.DETERMINEREQUIREMENTS)) {
          return taskUtils.isTaskOfType(parent, "MaintainInventory");
        }
      }
      return false;
    }
  }

  /**
   Selects the per-inventory MaintainInventory tasks.
   **/
  protected static class MILPredicate implements UnaryPredicate {
    public boolean execute(Object o) {
      if (o instanceof Task) {
        Task t = (Task) o;
        if (t.getVerb().equals(Constants.Verb.MAINTAININVENTORY)) {
          return t.getDirectObject() != null; // true if this is the agg task
        }
      }
      return false;
    }
  }

  /** get the Expansion for the TOP MI task
   * note that this means each instance for each class of supply will
   * be looking for the same task - but since the results are checked and only
   * changed if there's a difference it shouldn't be too bad.
   **/
  protected static class MITopExpansionPredicate implements UnaryPredicate {
    public boolean execute(Object o) {
      if (o instanceof Expansion) {
        Task parent = ((Expansion) o).getTask();
        if (parent.getVerb().equals(Constants.Verb.MAINTAININVENTORY)) {
          if (parent.getDirectObject() == null) {
            return true;
          }
        }
      }
      return false;
    }
  }

  /**
   Selects the aggregate MaintainInventory task
   **/
  protected static class AggMILPredicate implements UnaryPredicate {
    public boolean execute(Object o) {
      if (o instanceof Task) {
        Task t = (Task) o;
        if (t.getVerb().equals(Constants.Verb.MAINTAININVENTORY)) {
          return t.getDirectObject() == null; // true if this is not the agg task
        }
      }
      return false;
    }
  }

  /** Selects the MaintainInventory Expansions we create **/
  protected static class MIExpansionPredicate implements UnaryPredicate {
    String supplyType;
    TaskUtils taskUtils;

    public MIExpansionPredicate(String type, TaskUtils utils) {
      supplyType = type;
      taskUtils = utils;
    }

    public boolean execute(Object o) {
      if (o instanceof Expansion) {
        Task parent = ((Expansion) o).getTask();
        if (parent.getVerb().equals(Constants.Verb.MAINTAININVENTORY)) {
          Asset directObject = parent.getDirectObject();
          if (directObject != null && directObject instanceof Inventory) {
            LogisticsInventoryPG thePG = (LogisticsInventoryPG) ((Inventory) directObject).
                searchForPropertyGroup(LogisticsInventoryPG.class);
            Asset resource = thePG.getResource();
            SupplyClassPG pg = (SupplyClassPG) resource.searchForPropertyGroup(SupplyClassPG.class);
            if (pg != null) {
              if (supplyType.equals(pg.getSupplyType())) {
                //            if (taskUtils.isDirectObjectOfType(parent, supplyType)) {
                return true;
              }
            }
          }
        }
      }
      return false;
    }
  }

  /**
   Selects the Oplan objects
   **/
  protected static class OplanPredicate implements UnaryPredicate {
    public boolean execute(Object o) {
      return o instanceof Oplan;
    }
  }

  /**
   Passes Inventory assets that have a valid LogisticsInventoryPG
   **/

  protected static class InventoryPredicate implements UnaryPredicate {
    String supplyType;

    public InventoryPredicate(String type) {
      supplyType = type;
    }

    public boolean execute(Object o) {
      if (o instanceof Inventory) {
        Inventory inv = (Inventory) o;
        LogisticsInventoryPG logInvpg =
            (LogisticsInventoryPG)
            inv.searchForPropertyGroup(LogisticsInventoryPG.class);
        if (logInvpg != null) {
          String type = getAssetType(logInvpg);
          if (supplyType.equals(type)) {
            return true;
          }
        }
      }
      return false;
    }

    protected String getAssetType(LogisticsInventoryPG invpg) {
      Asset a = invpg.getResource();
      if (a == null) return null;
      SupplyClassPG pg = (SupplyClassPG)
          a.searchForPropertyGroup(SupplyClassPG.class);
      return pg.getSupplyType();
    }
  }


  //Allocation of refill tasks
  protected static class RefillAllocPredicate implements UnaryPredicate {
    String type_;
    String orgName_;
    TaskUtils taskUtils;

    public RefillAllocPredicate(String type, String orgName, TaskUtils aTaskUtils) {
      type_ = type;
      orgName_ = orgName;
      taskUtils = aTaskUtils;
    }

    public boolean execute(Object o) {
      if (o instanceof Allocation) {
        Task task = ((Allocation) o).getTask();
        if (task.getVerb().equals(Constants.Verb.SUPPLY) ||
            task.getVerb().equals(Constants.Verb.PROJECTSUPPLY)) {
          if (taskUtils.isDirectObjectOfType(task, type_)) {
            // need to check if externally allocated
            if (((Allocation) o).getAsset() instanceof Organization) {
              //if (taskUtils.isMyRefillTask(task, orgName_)){
              return true;
              //}
            }
          }
        }
      }
      return false;
    }
  }

  static class CommStatusPredicate implements UnaryPredicate {
    public boolean execute(Object o) {
      return o instanceof CommStatus;
    }
  }

  static class DispositionsPredicate implements UnaryPredicate {
    String type_;
    TaskUtils taskUtils;

    public DispositionsPredicate(String type, TaskUtils aTaskUtils) {
      type_ = type;
      taskUtils = aTaskUtils;
    }

    public boolean execute(Object o) {
      if (o instanceof Disposition) {
        Task task = ((Disposition) o).getTask();
        if (task.getVerb().equals(Constants.Verb.SUPPLY) && taskUtils.isDirectObjectOfType(task, type_)) {
          return true;
        }
      }
      return false;
    }
  }

  //Refill tasks
  protected static class RefillPredicate implements UnaryPredicate {
    String type_;
    String orgName_;
    TaskUtils taskUtils;

    public RefillPredicate(String type, String orgName, TaskUtils aTaskUtils) {
      type_ = type;
      orgName_ = orgName;
      taskUtils = aTaskUtils;
    }

    public boolean execute(Object o) {
      if (o instanceof Task) {
        Task task = (Task) o;
        if (task.getVerb().equals(Constants.Verb.SUPPLY) ||
            task.getVerb().equals(Constants.Verb.PROJECTSUPPLY)) {
          if (taskUtils.isDirectObjectOfType(task, type_)) {
            if (taskUtils.isMyRefillTask(task, orgName_)) {
              return true;
            }
          }
        }
      }
      return false;
    }
  }

  //Non-Refill tasks
  static class NonRefillPredicate implements UnaryPredicate {
    String type_;
    String orgName_;
    TaskUtils taskUtils;

    public NonRefillPredicate(String type, String orgName, TaskUtils aTaskUtils) {
      type_ = type;
      orgName_ = orgName;
      taskUtils = aTaskUtils;
    }

    public boolean execute(Object o) {
      if (o instanceof Task) {
        Task task = (Task) o;
        if (task.getVerb().equals(Constants.Verb.SUPPLY) ||
            task.getVerb().equals(Constants.Verb.PROJECTSUPPLY)) {
          if (taskUtils.isDirectObjectOfType(task, type_)) {
            if (taskUtils.isMyNonRefillTask(task, orgName_)) {
              return true;
            }
          }
        }
      }
      return false;
    }
  }

  protected class ExpansionPredicate implements UnaryPredicate {
    String supplyType;
    String orgName;
    TaskUtils taskUtils;

    public ExpansionPredicate(String type, String orgname, TaskUtils taskUtils) {
      supplyType = type;
      orgName = orgname;
      this.taskUtils = taskUtils;
    }

    public boolean execute(Object o) {
      if (o instanceof Expansion) {
        Task task = ((Expansion) o).getTask();
        if (task.getVerb().equals(Constants.Verb.SUPPLY) ||
            task.getVerb().equals(Constants.Verb.PROJECTSUPPLY)) {
          if (taskUtils.isDirectObjectOfType(task, supplyType)) {
            if (!taskUtils.isMyRefillTask(task, orgName)) {
              return true;
            }
          }
        }
      }
      return false;
    }
  }

  protected class InventoryPolicyPredicate implements UnaryPredicate {
    String type;

    public InventoryPolicyPredicate(String type) {
      this.type = type;
    }

    public boolean execute(Object o) {
      if (o instanceof org.cougaar.logistics.plugin.inventory.InventoryPolicy) {
        String type = ((InventoryPolicy) o).getResourceType();
        if (type.equals(this.type)) {
          if (logger.isInfoEnabled()) {
            logger.info("Found an inventory policy for " + this.type + "agent is: " +
                        getMyOrganization());
          }
          return true;
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Ignoring type of: " + type + " in " +
                         getMyOrganization() + " this type is: " +
                         this.type);
          }
        }
      }
      return false;
    }
  }

  /**
   * Filters out tasks that already have PEs -- fix for bug #1695
   * @param tasks - possibly from added list
   * @return Collection - tasks that have no PEs
   */
  protected Collection getTasksWithoutPEs(Collection tasks) {
    Set tasksWithoutPEs = new HashSet();
    for (Iterator iter = tasks.iterator(); iter.hasNext();) {
      Task task = (Task) iter.next();

      if (task.getPlanElement() != null) {
        if (logger.isDebugEnabled()) {
          logger.debug(getMyOrganization() + " - found task that already had a p.e. attached? : " +
                       task.getUID() + " - so skipping it.");
        }
      } else {
        tasksWithoutPEs.add(task);
      }
    }

    return tasksWithoutPEs;
  }

  protected class WithdrawPredicate implements UnaryPredicate {
    String supplyType;

    public WithdrawPredicate(String type) {
      supplyType = type;
    }

    public boolean execute(Object o) {
      if (o instanceof Task) {
        Task task = (Task) o;
        if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
          if (taskUtils.isDirectObjectOfType(task, supplyType)) {
            return true;
          }
        }
      }
      return false;
    }
  }

  protected class ProjectWithdrawPredicate implements UnaryPredicate {
    String supplyType;

    public ProjectWithdrawPredicate(String type) {
      supplyType = type;
    }

    public boolean execute(Object o) {
      if (o instanceof Task) {
        Task task = (Task) o;
        if (task.getVerb().equals(Constants.Verb.PROJECTWITHDRAW)) {
          if (taskUtils.isDirectObjectOfType(task, supplyType)) {
            return true;
          }
        }
      }
      return false;
    }
  }

  protected class OperatingModePredicate implements UnaryPredicate {
    String supplyType;
    String level;

    public OperatingModePredicate(String type, String level) {
      supplyType = type;
      this.level = level;
    }

    public boolean execute(Object o) {
      if (o instanceof OperatingMode) {
        OperatingMode om = (OperatingMode) o;
        if (om.getName().equals(level + "_" + supplyType)) {
          return true;
        }
      }
      return false;
    }
  }

  protected void expandIncomingRequisitions(Collection tasks) {
    supplyExpander.expandAndDistributeRequisitions(tasks);
  }

  protected boolean expandIncomingProjections(Collection tasks) {
    return supplyExpander.expandAndDistributeProjections(tasks);
  }

  /**
   Add some inventories to the inventoryHash.
   Method called during rehydration to populate inventory hash
   **/
  protected void addRehydratedInventories(Collection inventories) {
    for (Iterator i = inventories.iterator(); i.hasNext();) {
      Inventory inv = (Inventory) i.next();
      LogisticsInventoryPG logInvPG = (LogisticsInventoryPG) inv.searchForPropertyGroup(LogisticsInventoryPG.class);
      logInvPG.setStartCDay(logOPlan.getOplanCday());
      logInvPG.reinitialize(logToCSV, this);
      addInventory(inv);
    }
  }


  //AHF
  protected void resetLogOPlanForInventories() {
    Iterator inventories = getInventories().iterator();
    while(inventories.hasNext()) {
      Inventory inv = (Inventory) inventories.next();
      LogisticsInventoryPG logInvPG = (LogisticsInventoryPG)
          inv.searchForPropertyGroup(LogisticsInventoryPG.class);
      if(logInvPG.getArrivalTime() != getOPlanArrivalInTheaterTime()) {
	if(logInvPG.getSupplierArrivalTime() != -1) {
	    long newSupplierArrivalTime = logInvPG.getSupplierArrivalTime() +
		(getOPlanArrivalInTheaterTime() - logInvPG.getArrivalTime());
	    ((NewLogisticsInventoryPG)logInvPG).setSupplierArrivalTime(newSupplierArrivalTime);
	}
	logInvPG.setArrivalTime(getOPlanArrivalInTheaterTime());
        logInvPG.setStartCDay(logOPlan.getOplanCday());
        publishChange(inv);
        touchInventory(inv);
        touchedChangedProjections = true;
      }
    }
  }


  protected void addInventory(Inventory inventory) {
    String item = getInventoryType(inventory);
    inventoryHash.put(item, inventory);
  }

  protected void removeInventories(Enumeration inventories) {
    while (inventories.hasMoreElements()) {
      removeInventory((Inventory) inventories.nextElement());
    }
  }

  protected void removeInventory(Inventory inventory) {
    String item = getInventoryType(inventory);
    inventoryHash.remove(item);
  }

  public String getInventoryType(Inventory inventory) {
    ScheduledContentPG scp = inventory.getScheduledContentPG();
    Asset proto = scp.getAsset();
    if (proto == null) {
      if (logger.isErrorEnabled()) {
        logger.error("getInventoryType failed to get asset for " +
                     inventory.getScheduledContentPG().getAsset().getTypeIdentificationPG());
      }
      return "";
    }
    return proto.getTypeIdentificationPG().getTypeIdentification();
  }

  public void touchInventoryForTask (Task taskWithInventory, Inventory inventory) {
    touchInventory(inventory);
  }

  public Inventory findOrMakeInventory(Task task) {
    throw new IllegalArgumentException("this method should not be called in AL.");
     //return null;
  }

  public Inventory findOrMakeInventory(Asset resource) {
    Inventory inventory = null;
    String item = resource.getTypeIdentificationPG().getTypeIdentification();
    inventory = (Inventory) inventoryHash.get(item);
    if (inventory != null) {
      if (logger.isDebugEnabled()) {
          logger.debug("findOrMakeInventory(), FOUND inventory bing for: " +
                AssetUtils.assetDesc(inventory.getScheduledContentPG().getAsset()));
      }
      return inventory;
    } else {
      inventory = createInventory(resource, item);
      addInventory(inventory);
      publishAdd(inventory);
      detReqHandler.findOrMakeMILTask(inventory, aggMILSubscription);
    }
    if (logger.isDebugEnabled()) {
        logger.debug("findOrMakeInventory(), CREATED inventory bin for: " +
                     AssetUtils.assetDesc(inventory.getScheduledContentPG().getAsset()));
    }
    return inventory;
  }

  protected Inventory createInventory(Asset resource, String item) {
      //    double levels[] = null;
    Inventory inventory = null;
//     levels = (double[]) inventoryInitHash.get(item);
//     if (levels == null) {
//	levels = new double[2];
	double capacity = getFuelCapacity(item);
	double initialLevel = getInitialLevel(item);
//     }
    inventory = (Inventory) getPlanningFactory().createAsset("Inventory");
    NewLogisticsInventoryPG logInvPG =
	(NewLogisticsInventoryPG) PropertyGroupFactory.newLogisticsInventoryPG();
    inventory.addOtherPropertyGroup(logInvPG);
    if (getAssetUtils().isLevel2Asset(resource)) {
        logInvPG.setIsLevel2(true); // will need to key off asset to identify level2 item
        // Need to distinguish Level2Package aggregates of different supply types otherwise
        // they are determined to be the same asset and are removed from the blackboard
        ((NewItemIdentificationPG) inventory.getItemIdentificationPG()).setItemIdentification("Inventory:" + item + ":" + supplyType);
    } else {
        logInvPG.setIsLevel2(false);
        ((NewItemIdentificationPG) inventory.getItemIdentificationPG()).setItemIdentification("Inventory:" + item);
    }
    logInvPG.setCapacity(capacity);
    logInvPG.setFillToCapacity(fillToCapacity);
    logInvPG.setInitialLevel(initialLevel);
    logInvPG.setResource(resource);
    logInvPG.setOrg(getMyOrganization());
    logInvPG.setSupplierArrivalTime(getSupplierArrivalTime());
    logInvPG.setLogInvBG(new LogisticsInventoryBG(logInvPG));
    logInvPG.initialize(startTime, this.getOPlanEndTime(), criticalLevel, reorderPeriod, getOrderShipTime(), bucketSize, getCurrentTimeMillis(), logToCSV, this);
    logInvPG.setArrivalTime(getOPlanArrivalInTheaterTime());
    logInvPG.setStartCDay(logOPlan.getOplanCday());

    NewTypeIdentificationPG ti =
	(NewTypeIdentificationPG) inventory.getTypeIdentificationPG();
    ti.setTypeIdentification("InventoryAsset");
    ti.setNomenclature("Inventory Asset");

    NewScheduledContentPG scp;
    scp = (NewScheduledContentPG) inventory.getScheduledContentPG();
    scp.setAsset(resource);
    scp.setSchedule(scheduleUtils.buildSimpleQuantitySchedule(initialLevel, startTime,
                                                                startTime + (TimeUtils.MSEC_PER_DAY * 10)));

    return inventory;
  }

    // Override these in a subclass if necessary
    protected double getFuelCapacity(String item) { return 0.0; }
    protected double getInitialLevel(String item) { return 0.0; }


  public void touchInventory(Inventory inventory) {
    if (!touchedInventories.contains(inventory)) {
      touchedInventories.add(inventory);
    }
  }

  public Collection getTouchedInventories() {
    return touchedInventories;
  }

  public Collection getInventories() {
    return inventoryHash.values();
  }

  public void touchInventoryWithDeletions(Inventory inventory) {
    if (!inventoriesWithDeletions.contains(inventory)) {
      inventoriesWithDeletions.add(inventory);
    }
  }

  public Collection getInventoriesWithDeletions() {
    return inventoriesWithDeletions;
  }

  public void takeInventorySnapshot(Collection inventories) {
    Inventory inv;
    Iterator inv_it = inventories.iterator();
    LogisticsInventoryPG logInvPG = null;
    while (inv_it.hasNext()) {
      inv = (Inventory) inv_it.next();
      logInvPG = (LogisticsInventoryPG) inv.searchForPropertyGroup(LogisticsInventoryPG.class);
      logInvPG.takeSnapshot(inv);
      if (logToCSV) {
        logInvPG.logAllToCSVFile(cycleStamp);
      }

      // Force incremental persistence snapshots to include changes to these
      publishChange(inv);
    }
    checkShortfallStatus(inventories);
  }

  public void checkShortfallStatus(Collection inventories) {
    Inventory inv;
    Iterator inv_it = inventories.iterator();
    LogisticsInventoryPG logInvPG = null;
    ArrayList shortfallInvs = new ArrayList();
    ArrayList nonShortfallInvs = new ArrayList();
    while (inv_it.hasNext()) {
      inv = (Inventory) inv_it.next();
      String invID = LogisticsInventoryServlet.getNomenclature(inv);

      ShortfallInventory shortfallInv=checkForShortfall(inv);
      if(shortfallInv != null) {
	  shortfallInvs.add(shortfallInv);
      }
      else {
	  nonShortfallInvs.add(invID);
      }
    }
    ShortfallSummary shortSum =null;
    if(!shortfallSummary.isEmpty()) {
	shortSum = (ShortfallSummary)shortfallSummary.iterator().next();
    }
    //If the shortfall summary doesn't exist yet and there are shortfall inventories
    //Create and publish add the shortfall summary.
    if (shortSum == null) {
	if(!shortfallInvs.isEmpty()){
	    shortSum = new ShortfallSummary(getSupplyType(),uidService.nextUID(), bucketSize);
	    Iterator it = shortfallInvs.iterator();
	    shortSum.setShortfallInventories(shortfallInvs);
	    publishAdd(shortSum);
	}
	return;
    }
    boolean addedShort = shortSum.addShortfallInventories(shortfallInvs);
    boolean removedShort = shortSum.removeShortfallInventories(nonShortfallInvs);

    if(addedShort || removedShort){
	if(!shortSum.getShortfallInventories().isEmpty()) {
	    /***
	    if(getOrgName().startsWith("47-FSB")) {
		logger.warn("47-FSB - num short:" + shortSum);
	    }
	    **/
	    publishChange(shortSum);
	}
	else {
	    publishRemove(shortSum);
	    /***
	    if(getOrgName().startsWith("47-FSB")) {
		logger.warn("47-FSB - Removing ShortfallSummary");
	    }
	    **/

	}
    }

  }


    public ShortfallInventory checkForShortfall(Inventory inv) {
      String invID = LogisticsInventoryServlet.getNomenclature(inv);
      LogisticsInventoryPG logInvPG = (LogisticsInventoryPG)inv.searchForPropertyGroup(LogisticsInventoryPG.class);
      String unitOfIssue = LogisticsInventoryFormatter.getUnitForAsset(inv);
      return logInvPG.checkForShortfall(invID,unitOfIssue);
    }

  /**
   Read the Plugin parameters(Accepts key/value pairs)
   Initializes supplyType and inventoryFile
   **/
  protected HashMap readParameters() {
    Collection p = getParameters();

    if (p.isEmpty()) {
      if (logger.isErrorEnabled()) {
        logger.error("No parameters: InventoryPlugin requires 1 parameter, Supply Type.  Additional parameter for csv logging, default is disabled.   e.g. org.cougaar.logistics.plugin.inventory.InventoryPlugin("
                     +SUPPLY_TYPE
                     +"=BulkPOL, ENABLE_CSV_LOGGING=true)");
      }
      return null;
    }
    HashMap map = new HashMap();
    int idx;

    for (Iterator i = p.iterator(); i.hasNext();) {
      String s = (String) i.next();
      if ((idx = s.indexOf('=')) != -1) {
        String key = new String(s.substring(0, idx));
        String value = new String(s.substring(idx + 1, s.length()));
        map.put(key.trim(), value.trim());
      }
    }
    supplyType = (String) map.get(SUPPLY_TYPE);
//      inventoryFile = (String)map.get(INVENTORY_FILE);
    if (supplyType == null && logger.isErrorEnabled()) {
      logger.error("No SUPPLY_TYPE parameter: InventoryPlugin requires 1 parameter, Supply Type.  Additional parameter for csv logging, default is disabled.   e.g. org.cougaar.logistics.plugin.inventory.InventoryPlugin("
                   +SUPPLY_TYPE
                   +"=BulkPOL, ENABLE_CSV_LOGGING=true)");
    }
    String loggingEnabled = (String) map.get(ENABLE_CSV_LOGGING);
    if ((loggingEnabled != null) &&
        (loggingEnabled.trim().equals("true"))) {
      logToCSV = true;
    }

    String prepoOffsetStr = (String) map.get(PREPO_ARRIVAL_OFFSET);
    if((prepoOffsetStr != null) &&
        !(prepoOffsetStr.trim().equals(""))) {
      try {
        int prepoOffset = Integer.parseInt(prepoOffsetStr);
        prepoArrivalOffset = prepoOffset;
      }
      catch(NumberFormatException ex) {
        logger.error("InventoryPlugin(" + PREPO_ARRIVAL_OFFSET + "=" + prepoOffsetStr +
                     ") value is not a parseable integer.  Defaulting to " + prepoArrivalOffset);
      }

    }
    return map;
  }

//   protected String getInventoryFile(String type) {
//     String result = null;
//     // if defined in plugin argument list
//     String inv_file = null;
//     if ((inv_file = (String) pluginParams.get(INVENTORY_FILE)) != null) {
//       result = inv_file;
//       //   }
//       //    else {
// //       result = getClusterSuffix(myOrganization.getClusterPG().getMessageAddress().toString()) +
// // 	"_"+type.toLowerCase()+".inv";
//     } else if (type.equals("Ammunition")) {
//       result = getAgentPrefix(getAgentIdentifier().toString()) +
//           "_" + type.toLowerCase() + ".inv";
//     } else {
//       result = getClusterSuffix(getAgentPrefix(getAgentIdentifier().toString())) +
//           "_" + type.toLowerCase() + ".inv";
//     }
//     return result;
//   }

  protected String getAgentPrefix(String agentId) {
      int i=agentId.indexOf(".");
      if(i<=0) {
	  return agentId;
      }
      else {
	  return agentId.substring(0,i);
      }
  }

  protected String getClusterSuffix(String clusterId) {
    String result = null;
    int i = clusterId.lastIndexOf("-");
    if (i == -1) {
      result = clusterId;
    } else {
      result = clusterId.substring(i + 1);
    }
    return result;
  }

  public void publishAddToExpansion(Task parent, Task subtask) {
    //attach the subtask to its parent and the parent's workflow
    PlanElement pe = parent.getPlanElement();
    Expansion expansion;
    NewWorkflow wf;
    ((NewTask) subtask).setParentTask(parent);
    ((NewTask) subtask).setContext(parent.getContext());
    ((NewTask) subtask).setPlan(parent.getPlan());
    // Task has not been expanded, create an expansion
    if (pe instanceof Disposition) {
      publishRemove(pe);
      pe = null;
    }
    if (pe == null) {
      PlanningFactory factory = getPlanningFactory();
      // Create workflow
      wf = (NewWorkflow) factory.newWorkflow();
      wf.setParentTask(parent);
      wf.setIsPropagatingToSubtasks(true);
      wf.addTask(subtask);
      ((NewTask) subtask).setWorkflow(wf);
      // Build Expansion
      expansion = factory.createExpansion(parent.getPlan(), parent, wf, null);
      // Publish Expansion
      publishAdd(expansion);
      if (logger.isDebugEnabled()) {
        logger.debug("Agent: " + getAgentIdentifier().toString() + "Inv Plugin[" + supplyType+"]" +
                     "publish adding expansion: " + expansion.getUID()+
                     " parent " + parent.getVerb() + " task is: " + parent.getUID());
      }
    }
    // Task already has expansion, add task to the workflow and publish the change
    else if (pe instanceof Expansion) {
      expansion = (Expansion) pe;
      wf = (NewWorkflow) expansion.getWorkflow();
      wf.addTask(subtask);
      ((NewTask) subtask).setWorkflow(wf);
      publishChange(expansion);
      if (logger.isDebugEnabled()) {
        logger.debug("Agent: " + getAgentIdentifier().toString() + "Inv Plugin[" + supplyType+"]" +
                     "publish changing expansion: " + expansion.getUID()+
                     " parent " + parent.getVerb() + " task is: " + parent.getUID());
      }
    } else {
      if (logger.isErrorEnabled()) {
        logger.error("publishAddToExpansion: problem pe not Expansion? " + pe);
      }
    }

    // Publish new task
    publishAdd(subtask);
    if (logger.isDebugEnabled()) {
        logger.debug("Agent: " + getAgentIdentifier().toString() + "Inv Plugin[" + supplyType+"]" +
                     "publish adding a " + subtask.getVerb() + " subtask: " + subtask.getUID());
    }

    if (((subtask.getVerb().equals(Constants.Verb.SUPPLY)) ||
        (subtask.getVerb().equals(Constants.Verb.PROJECTSUPPLY))) &&
        (subtask.getPrepositionalPhrase("SplitTask") == null)) {
      newRefills.add(subtask);
    }
  }

  // called by the RefillGenerator to hook up the refill task to the maintain
  // inventory parent task and workflow.
  public boolean publishRefillTask(Task task, Inventory inventory) {
    Task milTask = detReqHandler.findOrMakeMILTask(inventory,
                                                   aggMILSubscription);
    if (milTask != null) {
      publishAddToExpansion(milTask, task);
      return true;
    }
    return false;
  }

  public void disposeOfUnusedMILTask(Inventory inventory, boolean noRefills) {
    if (noRefills) {
      Task milTask = detReqHandler.findOrMakeMILTask(inventory,
                                                     aggMILSubscription);
      // If the milTask couldn't be made - just return
      if (milTask == null) {
          return;
      }
      PlanElement pe = milTask.getPlanElement();
      // If the PlanElement is already a Disposition then do nothing
      if (!(pe instanceof Disposition)) {
        // If the PlanElement is not null then it is an expansion.
        // Only Remove expansion if there is nothing in the workflow.
        if (pe instanceof Expansion) {
          Enumeration tasks = ((Expansion)pe).getWorkflow().getTasks();
          if (!tasks.hasMoreElements()) {
            publishRemove(pe);
            pe = null;
          }
        }
        if (pe == null) {
          // Create Disposition
          AllocationResultHelper helper = new AllocationResultHelper(milTask, null);
          AllocationResult dispAR = helper.getAllocationResult(Constants.Confidence.OBSERVED, true);
          Disposition disposition =
            getPlanningFactory().createDisposition(milTask.getPlan(), milTask, dispAR);
          publishAdd(disposition);
        }
      }
    }
  }

  protected Organization getMyOrganization(Enumeration orgs) {
    Organization myOrg = null;
    // look for this organization
    if (orgs.hasMoreElements()) {
      myOrg = (Organization) orgs.nextElement();
    }
    return myOrg;
  }

  public long getOPlanStartTime() {
    return logOPlan.getStartTime();
  }

  public long getOPlanEndTime() {
    return logOPlan.getEndTime();
  }

  public long getOPlanArrivalInTheaterTime() {
      //Before the arrivalt time is updated in the log oplan
      //the default value is just the start time.
    if(logOPlan.getArrivalTime() == Long.MIN_VALUE) {
      if(logger.isErrorEnabled()) {
	logger.error("Asking for arrival time in theater, before it is known");
      }
      return startTime;
    }
    else {
      return logOPlan.getArrivalTime();
    }
  }

  public long getPrepoArrivalTime() {
    return getOPlanArrivalInTheaterTime() - (bucketSize * prepoArrivalOffset);
  }

//   public void getInventoryData() {
//     String invFile = getInventoryFileName();
//     if (invFile != null) {
//       Enumeration initialInv = FileUtils.readConfigFile(invFile, getConfigFinder());
//       if (initialInv != null) {
//         stashInventoryInformation(initialInv);
//       }
//     }
//   }


//   private void stashInventoryInformation(Enumeration initInv) {
//     String line;
//     String item = null;
//     double capacity, level;

//     while (initInv.hasMoreElements()) {
//       line = (String) initInv.nextElement();
//       // Find the fields in the line, values seperated by ','
//       Vector fields = FileUtils.findFields(line, ',');
//       if (fields.size() < 3)
//         continue;
//       item = (String) fields.elementAt(0);
//       capacity = Double.valueOf((String) fields.elementAt(1)).doubleValue();
//       level = Double.valueOf((String) fields.elementAt(2)).doubleValue();
//       double[] levels = {capacity, level};
//       inventoryInitHash.put(item, levels);
//     }
//   }

  protected Role getRole(String supply_type) {
    if (supply_type.equals("Ammunition"))
      return Constants.Role.AMMUNITIONPROVIDER;
    if (supply_type.equals("BulkPOL"))
      return Constants.Role.FUELSUPPLYPROVIDER;
    if (supply_type.equals("Consumable"))
      return Constants.Role.SPAREPARTSPROVIDER;
    if (supply_type.equals("PackagedPOL"))
      return Constants.Role.PACKAGEDPOLSUPPLYPROVIDER;
    if (supply_type.equals("Subsistence"))
      return Constants.Role.SUBSISTENCESUPPLYPROVIDER;
    if (logger.isErrorEnabled()) {
      logger.error("Unsupported Supply Type");
    }
    return null;
  }

  protected boolean updateInventoryPolicy(Collection policies) {
    InventoryPolicy pol;
    boolean changed = false;
    Iterator policy_iterator = policies.iterator();
    while (policy_iterator.hasNext()) {
      pol = (InventoryPolicy) policy_iterator.next();
      inventoryPolicy = pol;
      int cl = pol.getCriticalLevel();
      if ((cl >= 0) && (cl != criticalLevel)) {
        criticalLevel = cl;
        changed = true;
      }
      int rp = pol.getReorderPeriod();
      if ((rp >= 0) && (rp != reorderPeriod)) {
        reorderPeriod = rp;
        changed = true;
      }
      long bucket = pol.getBucketSize();
      if (bucket >= TimeUtils.MSEC_PER_HOUR) {
        bucketSize = bucket;
        changed = true;
      }
      boolean ftc = pol.getFillToCapacity();
      if (ftc != fillToCapacity) {
        fillToCapacity = ftc;
        changed = true;
      }
    }
    return changed;
  }

  public int getOrderShipTime() {
    return inventoryPolicy.getOrderShipTime();
  }

  public long getSupplierArrivalTime() {
    return inventoryPolicy.getSupplierArrivalTime();
  }

  public long getRefillStartTime() {
    return Math.max(getOPlanArrivalInTheaterTime(),getSupplierArrivalTime());
  }

  public int getMaxLeadTime() {
    return inventoryPolicy.getSupplierAdvanceNoticeTime() + getOrderShipTime();
  }

  public boolean getFillToCapacity() {
    return inventoryPolicy.getFillToCapacity();
  }

  public int getCriticalLevel() {
    return inventoryPolicy.getCriticalLevel();
  }

  /** VTH operating modes */
  protected OperatingMode level2Horizon, level6Horizon;

  /** create and publish VTH Operating Modes */
  protected void setupOperatingModes() {
    try {
      //getBlackboardService().openTransaction();
      OMCRange level2Range = new IntRange(LEVEL_2_MIN.intValue(), LEVEL_2_MAX.intValue());
      OMCRangeList rangeList = new OMCRangeList(level2Range);
      publishAdd(level2Horizon = new OperatingModeImpl(LEVEL_2_TIME_HORIZON + "_" + supplyType, rangeList,
                                                       LEVEL_2_TIME_HORIZON_DEFAULT));

      OMCRange level6Range = new IntRange(LEVEL_6_MIN.intValue(), LEVEL_6_MAX.intValue());
      rangeList = new OMCRangeList(level6Range);
      publishAdd(level6Horizon = new OperatingModeImpl(LEVEL_6_TIME_HORIZON + "_" + supplyType, rangeList,
                                                       LEVEL_6_TIME_HORIZON_DEFAULT));
    } catch (Exception e) {
      if (logger.isErrorEnabled()) {
        logger.error("" + getMyOrganization() + " got exception creating operating modes.", e);
      }
      //} finally {
      //getBlackboardService().closeTransaction();
    }

    if (logger.isInfoEnabled()) {
      logger.info("" + getMyOrganization() + " created operating modes - " +
                  "level 2 time horizon is " + level2Horizon +
                  " and level 6 is " + level6Horizon);
    }
  }

  /** tiny helper class for VTH Operating Modes */
  protected static class IntRange extends OMCRange {
    public IntRange(int a, int b) {
      super(a, b);
    }
  }

  /** relative to now -- this is correct, isn't it? */
  protected long getEndOfLevelSix() {
    long now = currentTimeMillis();
    int days = ((Integer) level6Horizon.getValue()).intValue();

    return timeUtils.addNDays(now, days);
  }

  /** relative to now -- this is correct, isn't it? */
  public long getEndOfLevelTwo() {
    long now = currentTimeMillis();
    int days = ((Integer) level2Horizon.getValue()).intValue();

    return timeUtils.addNDays(now, days);
  }

  /** When one of our Refill tasks gets removed (Supply or ProjectSupply),
   *  remove it from the BG list.
   *  @param removedTasks The collection of removed refill tasks.
   **/
  public void handleRemovedRefills(Collection removedTasks) {
    Iterator removedIter = removedTasks.iterator();
    while (removedIter.hasNext()) {
      Task removed = (Task) removedIter.next();
      if (logger.isDebugEnabled()) {
        logger.debug("Agent: " + getAgentIdentifier().toString() + "Inv Plugin[" + supplyType+"]" +
                     "processing removal of refill: " + removed.getUID());
      }
      String item = removed.getDirectObject().getTypeIdentificationPG().getTypeIdentification();
      Inventory inventory = (Inventory) inventoryHash.get(item);
      LogisticsInventoryPG invPG = (LogisticsInventoryPG)
          inventory.searchForPropertyGroup(LogisticsInventoryPG.class);
      if (removed.getVerb().equals(Constants.Verb.SUPPLY)) {
        invPG.removeRefillRequisition(removed);
      } else {
        invPG.removeRefillProjection(removed);
      }
      if (removed.isDeleted()) {
        touchInventoryWithDeletions(inventory);
      } else {
        touchInventory(inventory);
      }
    }
  }

  public MessageAddress getClusterId() {
    return getAgentIdentifier();
  }

  public TaskScheduler getSupplyTaskScheduler() {
    return supplyTaskScheduler;
  }

  public Collection getCommStatusSubscription() {
    return commStatusSub;
  }
  public Collection getSupplyTasks() {
    return supplyTaskScheduler.getAllTasksCollection();
  }

  public Alarm addAlarm(long timeOut) {
    Alarm alarm = new CougTimeAlarm(timeOut);
    alarmService.addAlarm(alarm);
    return alarm;
  }

  public Alarm addRealTimeAlarm(long timeOut) {
    Alarm alarm = new CougTimeAlarm(timeOut);
    alarmService.addRealTimeAlarm(alarm);
    return alarm;
  }
  //
  protected void processDetReq(Collection addedDRs) {
    // with one oplan we should only have one DR for MI.
    Iterator drIt = addedDRs.iterator();
    if (drIt.hasNext()) {
      Task detReq = (Task) drIt.next();
      //synch on the detReq task so only one instance of this plugin
      // checks and creates a single agg task and then creates an
      // empty expansion (wf) for the maintain inventory for each item tasks
      synchronized (detReq) {
        if (detReq.getPlanElement() == null) {
          detReqHandler.createAggTask(addedDRs);
        }
      }
    }
  }

  // We only want to process inventories that we have no new refills for.
  protected Collection getActionableInventories() {
    ArrayList actionableInvs = new ArrayList(touchedInventories);
    Task refill;
    Asset asset;
    Inventory inventory;
    Iterator refillIt = newRefills.iterator();
    while (refillIt.hasNext()) {
      refill = (Task) refillIt.next();
      asset = (Asset) refill.getDirectObject();
      inventory = findOrMakeInventory(asset);
      actionableInvs.remove(inventory);
    }
    return actionableInvs;
  }

  protected void rebuildPGCustomerHash() {
    Collection changedInventories = getTouchedInventories();
    Iterator invIter = changedInventories.iterator();
    Inventory inventory;
    LogisticsInventoryPG thePG;
    while (invIter.hasNext()) {
      inventory = (Inventory) invIter.next();
      thePG = (LogisticsInventoryPG)
          inventory.searchForPropertyGroup(LogisticsInventoryPG.class);
      thePG.rebuildCustomerHash();
    }
  }

  protected boolean didOrgRelationshipsChange() {
    boolean relSchedChange=false;
    if (selfOrganizations.hasChanged())  {
      Set changeReports = selfOrganizations.getChangeReports(getMyOrganization());

      Iterator crits = changeReports.iterator();
      while(crits.hasNext()) {
        if(crits.next() instanceof RelationshipSchedule.RelationshipScheduleChangeReport){
          relSchedChange=true;
          break;
        }
      }
    }
    return relSchedChange;
  }

  // get the first day in theater
  public long getLogOPlanStartTime() {
    return logOPlan.getStartTime();
  }

  // get the last day in theater
  public long getLogOPlanEndTime() {
    return logOPlan.getEndTime();
  }

  protected AllocatorModule getAllocatorModule() {
    return new ExternalAllocator(this, getRole(supplyType));
  }

  protected RefillGeneratorModule getRefillGeneratorModule() {
    return new RefillGenerator(this);
  }

  protected RefillProjectionGeneratorModule getRefillProjectionGeneratorModule() {
    return new RefillProjectionGenerator(this, this);
  }

  protected ComparatorModule getComparatorModule() {
    String comparatorClass = (String) pluginParams.get("COMPARATOR");
    if (comparatorClass == null) {
      return new DiffBasedComparator(this);
    } else {
      if (comparatorClass.indexOf('.') == -1) {
        comparatorClass = "org.cougaar.logistics.plugin.inventory." + comparatorClass;
      }
      try {
        Class[] paramTypes = {this.getClass()};
        Object[] initArgs = {this};
        Class cls = Class.forName(comparatorClass);
        Constructor constructor = cls.getConstructor(paramTypes);
        ComparatorModule comparator = (ComparatorModule) constructor.newInstance(initArgs);
        if (logger.isInfoEnabled())
          logger.info("Using comparator " + comparatorClass);
        return comparator;
      } catch (Exception e) {
        logger.error(e + " Unable to create Expander instance of " + comparatorClass + ". " +
                     "Loading default org.cougaar.logistics.plugin.inventory.DiffBasedComparator");
      }
    }
    return new RefillComparator(this);
  }

  protected ExpanderModule getExpanderModule() {
     ExpanderModule em;
     String expanderClass = (String) pluginParams.get("EXPANDER");
     if (expanderClass == null) {
       em = new SupplyExpander(this);
     } else {
       if (expanderClass.indexOf('.') == -1) {
         expanderClass = "org.cougaar.logistics.plugin.inventory." + expanderClass;
       }
       try {
         Class[] paramTypes = {this.getClass()};
         Object[] initArgs = {this};
         Class cls = Class.forName(expanderClass);
         Constructor constructor = cls.getConstructor(paramTypes);
         em = (ExpanderModule) constructor.newInstance(initArgs);
       } catch (Exception e) {
         logger.error(e + " Unable to create Expander instance of " + expanderClass + ". " +
                      "Loading default org.cougaar.logistics.plugin.inventory.SupplyExpander");
         em = new SupplyExpander(this);
       }
     }
    if (logger.isInfoEnabled()) {
      expanderClass = em.getClass().toString();
      logger.info("Using expander " + expanderClass);
    }
    return em;
  }

  public void updateStartAndEndTimes() {
      if(logOPlan != null) {
	  if(!orgActSubscription.isEmpty()) {
	      logOPlan.updateOrgActivities(orgActSubscription);
	  }
      }
  }

  public long getNextLegalRefillTime(long today) {
    TimeSpanSet orderedOrgActs = new TimeSpanSet(orgActSubscription);
    Iterator orgActIt = orderedOrgActs.iterator();
    boolean findNext=false;
    while(orgActIt.hasNext()) {
      OrgActivity orgAct = (OrgActivity) orgActIt.next();
      boolean legalOrg = ((!(orgAct.getActivityType().equals(orgAct.DEPLOYMENT))) &&
          (!(orgAct.getActivityType().equals("Transit"))));
      if((orgAct.getStartTime() <= today) &&
          (orgAct.getEndTime() >= today)) {
        if(legalOrg) {
          return today;
        }
        else {
          findNext=true;
        }
      }
      if(findNext && legalOrg) {
        return orgAct.getStartTime();
      }
    }

    if(!findNext) {
      return today;
    }
    else {
      return logOPlan.getEndTime();
    }
  }

  /**
   Self-Test
   **/
  public void automatedSelfTest() {
    if (logger.isErrorEnabled()) {
      if (supplyType == null) logger.error("No SupplyType Plugin parameter.");
      //      if (inventoryFile == null) logger.error("No Inventory File Plugin parameter.");
//       if (inventoryInitHash.isEmpty()) {
//         logger.error("No initial inventory information.  Inventory File is empty or non-existant.");
//         logger.error("Could not find Inventory file : " + inventoryFile);
//       }
      if (detReqHandler.getDetermineRequirementsTask(aggMILSubscription) == null)
        logger.error("Missing DetermineRequirements for MaintainInventory task.");
      if (logOPlan == null)
        logger.error("Missing LogisticsOPlan object. Is the LogisticsOPlanPlugin loaded?");
      if (myOrganization == null)
        logger.error("Missing myorganization");
      logger.error("Critical Level is " + criticalLevel);
      logger.error("Reorder Period is " + reorderPeriod);
      logger.error("Days per bucket is " + bucketSize);
    }
  }

  protected final class CougTimeAlarm implements Alarm {
    private long expirationTime;
    private boolean expired = false;

    public CougTimeAlarm(long expiration) {
      this.expirationTime = expiration;
    }

    public long getExpirationTime() {
      return expirationTime;
    }

    public synchronized void expire() {
      if (!expired) {
        expired = true;
        BlackboardService bb = getBlackboardService();
        if (bb != null) {
          bb.signalClientActivity();
        } else {
          if (logger != null && logger.isWarnEnabled()) {
            logger.warn(
                "Alarm to trigger at " + (new Date(expirationTime)) + " has expired," +
                " but the blackboard service is null.  Plugin " +
                " model state is " + getModelState());
          }
        }
      }
    }

    public synchronized boolean hasExpired() {
      return expired;
    }

    public synchronized boolean cancel() {
      boolean was = expired;
      expired = true;
      return was;
    }
  }
  protected void testBG() {
    Iterator inv_it = inventoryHash.values().iterator();
    Inventory inv;
    LogisticsInventoryPG logInvPG = null;
    cycleStamp = (new Date()).getTime();
    while (inv_it.hasNext()) {
      inv = (Inventory) inv_it.next();
      if (logger.isErrorEnabled()) {
        logger.error("***" + inv.getItemIdentificationPG().getItemIdentification());
      }
      logInvPG = (LogisticsInventoryPG) inv.searchForPropertyGroup(LogisticsInventoryPG.class);
      logInvPG.takeSnapshot(inv);
      if (logToCSV) {
        logInvPG.logAllToCSVFile(cycleStamp);
      }
      logInvPG.Test();
    }
  }

}

