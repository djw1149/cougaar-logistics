/*
 * <copyright>
 *  Copyright 1997-2001 BBNT Solutions, LLC
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

import org.cougaar.core.agent.*;
import org.cougaar.glm.ldm.asset.*;
import org.cougaar.planning.ldm.asset.*;
import org.cougaar.glm.plugins.FileUtils;
import org.cougaar.glm.plugins.ScheduleUtils;

import org.cougaar.glm.ldm.asset.Inventory;
import org.cougaar.glm.ldm.asset.Organization;
import org.cougaar.glm.ldm.asset.ScheduledContentPG;
import org.cougaar.glm.ldm.asset.NewScheduledContentPG;
import org.cougaar.glm.ldm.Constants;
import org.cougaar.core.plugin.ComponentPlugin;
import org.cougaar.core.agent.ClusterIdentifier;
import org.cougaar.planning.ldm.plan.*;
import org.cougaar.core.domain.RootFactory;
import org.cougaar.core.service.DomainService;

import org.cougaar.util.UnaryPredicate;
import org.cougaar.util.Enumerator;
import org.cougaar.core.plugin.util.PluginHelper;

import org.cougaar.core.blackboard.*;
import org.cougaar.core.component.Component;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.plugin.LDMService;
import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;

import org.cougaar.core.adaptivity.OMCRange;
import org.cougaar.core.adaptivity.OMCRangeList;
import org.cougaar.core.adaptivity.OperatingMode;
import org.cougaar.core.adaptivity.OperatingModeImpl;

/** The InventoryPlugin is the Glue of inventory management.
 *  It handles all blackboard services for its modules, 
 *  facilitates inter-module communication and manages the
 *  subscriptions.  The InventoryPlugin also creates inventories.
 *  All modules are called from the InventoryPlugin.
 **/

public class InventoryPlugin extends ComponentPlugin {

  private boolean initialized = false;
  private boolean firstTimeThrough = true;
  private DomainService domainService;
  private LoggingService logger;
  private TaskUtils taskUtils;
  private TimeUtils timeUtils;
  private AssetUtils AssetUtils;
  private HashMap pluginParams;
  private HashMap inventoryHash;
  private HashMap inventoryInitHash;
  private HashSet touchedInventories;
    // private HashSet backwardFlowInventories;  // ### Captures Inventories with unchanged demand
  private boolean touchedProjections;
  private boolean touchedChangedProjections = false;
  private String supplyType;
  private String inventoryFile;
//   private boolean fillToCapacity; Will be added bug #1482
//   private boolean maintainAtCapacity; Will be added bug #1482
  private DetReqAggHandler detReqHandler;
  private Organization myOrganization;
  private String myOrgName;
  private SupplyExpander supplyExpander;
  private ExternalAllocator externalAllocator;
  private RefillGenerator refillGenerator;
  private RefillProjectionGenerator refillProjGenerator;
  private RefillComparator refillComparator;
  private AllocationAssessor allocationAssessor;
  private long startTime;
  private long cycleStamp;
  private boolean logToCSV=false;
  private transient ArrayList newRefills = new ArrayList();
  private boolean rehydrateInvs = false;

  public final String SUPPLY_TYPE = "SUPPLY_TYPE";
  public final String INVENTORY_FILE = "INVENTORY_FILE";
  public final String ENABLE_CSV_LOGGING = "ENABLE_CSV_LOGGING";

  // as a default make the max the end of the oplan (225)
  public final Integer LEVEL_2_MIN = new Integer(40); // later, these should be parameters to plugin...
  public final Integer LEVEL_2_MAX = new Integer(225);
  public final Integer LEVEL_6_MIN = new Integer(20);
  public final Integer LEVEL_6_MAX = new Integer(225);
  // then default to the end of the oplan (max)
  public final String  LEVEL_2_TIME_HORIZON = "Level2TimeHorizon";
  public final Integer LEVEL_2_TIME_HORIZON_DEFAULT = LEVEL_2_MAX;
  public final String  LEVEL_6_TIME_HORIZON = "Level6TimeHorizon";
  public final Integer LEVEL_6_TIME_HORIZON_DEFAULT = LEVEL_6_MAX;

  // OPlan variable
  LogisticsOPlan logOPlan = null;

  // Policy variables
  private InventoryPolicy inventoryPolicy = null;
  private int criticalLevel = 3;
  private int reorderPeriod = 3;
  private int bucketSize = 1;

  public void load() {
    super.load();
    logger = getLoggingService(this);
    timeUtils = new TimeUtils(this);
    AssetUtils = new AssetUtils(this);
    taskUtils = new TaskUtils(this);
    detReqHandler = new DetReqAggHandler(this);
    // readParameters() initializes supplyType and inventoryFile
    pluginParams = readParameters();
    supplyExpander = new SupplyExpander(this);
    externalAllocator = new ExternalAllocator(this,getRole(supplyType));
    refillGenerator = new RefillGenerator(this);
    refillProjGenerator = new RefillProjectionGenerator(this);
    refillComparator = new RefillComparator(this);
    allocationAssessor = new AllocationAssessor(this,getRole(supplyType));
    inventoryHash = new HashMap();
    inventoryInitHash = new HashMap();
    touchedInventories = new HashSet();
    //backwardFlowInventories = new HashSet();
    touchedProjections = false;
    startTime = currentTimeMillis();

    
    domainService = (DomainService) 
	getServiceBroker().getService(this,
				      DomainService.class,
      new ServiceRevokedListener() {
	  public void serviceRevoked(ServiceRevokedEvent re) {
	      if (DomainService.class.equals(re.getService()))
	      domainService  = null;
	  }
      });
  //   System.out.println("\n LOADING InventoryPlugin of type: " + supplyType +
//  		       "in org: " + getBindingSite().getAgentIdentifier().toString() +
//    		       " this plugin is: " + this);
  }

    public void unload() {
      super.unload();
      if (domainService != null) {
        getServiceBroker().releaseService(this, DomainService.class, domainService);
      }
    }

  public TaskUtils      getTaskUtils() {return taskUtils;}
  public TimeUtils      getTimeUtils() {return timeUtils;}
  public AssetUtils     getAssetUtils() {return AssetUtils;}  
  public String         getSupplyType() {return supplyType; }
  private String getInventoryFileName() {return inventoryFile; }
  public Organization   getMyOrganization() {return myOrganization;}
    public long getCurrentTimeMillis() { return currentTimeMillis(); }

  public boolean publishAdd(Object o) {
    boolean success = getBlackboardService().publishAdd(o);
    if (!success && logger.isErrorEnabled()) {
      logger.error (getMyOrganization() + " - publishAdd failed for " + o);
    }
    return success;
  }

  public void publishAddExpansion(Expansion expansion) {
    PluginHelper.publishAddExpansion(getBlackboardService(), expansion);
  }

    public boolean publishChange(Object o) {
	return getBlackboardService().publishChange(o);
    }

    public boolean publishRemove(Object o) {
	return getBlackboardService().publishRemove(o);
    }

    public RootFactory getRootFactory() { 
	RootFactory rootFactory=null;
	if(domainService != null) {
		rootFactory = domainService.getFactory();
	}
	return rootFactory; 
    }

  public LoggingService getLoggingService(Object requestor) {
    return (LoggingService) 
      getServiceBroker().getService(requestor,
				    LoggingService.class,
				    null);
  }
  
  protected void execute() {
    //clear our new refill list
    newRefills.clear();
    if (inventoryPolicy == null) {
      updateInventoryPolicy(inventoryPolicySubscription);
    }
    updateInventoryPolicy(inventoryPolicySubscription.getChangedCollection());
    processDetReq(detReqSubscription.getAddedCollection());
    cycleStamp = (new Date()).getTime();

    if (inventoryPolicy ==null) {
      if (logger.isInfoEnabled ()) {
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
      inventoryFile = getInventoryFile(supplyType);
      getInventoryData();
      initialized = true;
    } 

    if ((logOPlan == null) || logisticsOPlanSubscription.hasChanged()) {
      Collection c = logisticsOPlanSubscription.getCollection();
      for (Iterator i = c.iterator(); i.hasNext();) {
	logOPlan = (LogisticsOPlan)i.next();
	break;
      }
    }

    if ((detReqHandler.getDetermineRequirementsTask(aggMILSubscription) != null) &&
	(logOPlan != null)) {
      if (rehydrateInvs) {
        addRehydratedInventories(blackboard.query(new InventoryPredicate(supplyType)));
        rehydrateInvs = false;
      }
      boolean touchedRemovedProjections = 
	supplyExpander.handleRemovedProjections(projectWithdrawTaskSubscription.getRemovedCollection());
      supplyExpander.handleRemovedRequisitions(withdrawTaskSubscription.getRemovedCollection());
      handleRemovedRefills(refillSubscription.getRemovedCollection());

      // If its the first time we've gotten this far we now have our policy, org and others.
      // However, we may have missed some tasks on the added list in previous executes.
      // If its the first time through use the underlying subscription collection instead of the added list.
      if (firstTimeThrough) {
        Enumeration newReqs = supplyTaskSubscription.elements();
        ArrayList newReqsCollection = new ArrayList();
        while (newReqs.hasMoreElements()) {
          newReqsCollection.add(newReqs.nextElement());
        }
        expandIncomingRequisitions(getTasksWithoutPEs(newReqsCollection));
        Enumeration newProjReqs = projectionTaskSubscription.elements();
        ArrayList newProjReqsCollection = new ArrayList();
        while (newProjReqs.hasMoreElements()) {
          newProjReqsCollection.add(newProjReqs.nextElement());
        }
        touchedProjections = expandIncomingProjections(getTasksWithoutPEs(newProjReqsCollection));
        firstTimeThrough = false;
      } else {
        Collection addedSupply = supplyTaskSubscription.getAddedCollection();
        if (!addedSupply.isEmpty()){
          expandIncomingRequisitions(getTasksWithoutPEs(addedSupply)); // fix for bug #1695
        }
	Collection changedSupply = supplyTaskSubscription.getChangedCollection();
	if (!changedSupply.isEmpty()){
	    supplyExpander.updateChangedRequisitions(changedSupply);
	}

        Collection addedProjections = projectionTaskSubscription.getAddedCollection();
        if (!addedProjections.isEmpty()) {
           // getTasksWithoutPEs is fix for bug #1695
          touchedProjections = expandIncomingProjections(getTasksWithoutPEs(addedProjections));
        }
	Collection changedProjections = projectionTaskSubscription.getChangedCollection();
	if (!changedProjections.isEmpty()) {
	    supplyExpander.updateChangedProjections(changedProjections);
            touchedChangedProjections = true;
            // System.out.println("Touched changed projections in " + getAgentIdentifier() +
//                                " type is" + getSupplyType());
	}

        // call the Refill Generators if we have new demand
        if (! getTouchedInventories().isEmpty()) {
	  //check to see if we have new projections
	  if (touchedProjections || touchedRemovedProjections || touchedChangedProjections) {
            refillProjGenerator.calculateRefillProjections(getTouchedInventories(), 
                                                           criticalLevel, 
                                                           getEndOfLevelSix(), 
                                                           getEndOfLevelTwo(), 
                                                           refillComparator);
	  }
 	  refillGenerator.calculateRefills(getTouchedInventories(), refillComparator);
          externalAllocator.allocateRefillTasks(newRefills);
          
          //we might get new demand where we don't need to generate any new refills
          // such as small demand from the stimulator servlet - when this happens we
          // need to kick the allocation assessor to allocate the withdraws
          allocationAssessor.reconcileInventoryLevels(getActionableInventories());
         
        } 
	//        externalAllocator.updateAllocationResult(getActionableRefillAllocations()); 
	//        allocationAssessor.reconcileInventoryLevels(backwardFlowInventories); 

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
          
          allocationAssessor.reconcileInventoryLevels(backwardFlowTouched); 
        }

        // Kludge for now to make sure that all Subsistence withdraw tasks
        // get allocated.  Bug is that when falling behind conditions change,
        // some withdraw task's are ignored, but nothing necessarily kicks the inventory later
        // when we come out of falling behind to allocated hose withdraw tasks since
        // no level 2 work is done for Subsistence.
        // Try to limit the amount of times we do this using the changed list from the
        // level 6 om subscription.
        if ((getSupplyType().equals("Subsistence")) 
            && (! Level6OMSubscription.getChangedCollection().isEmpty()) &&
            (((Integer)level6Horizon.getValue()).equals(LEVEL_6_MAX)) ) {
          //System.out.println("Reconciling all inventory levels at: " + getAgentIdentifier());
          refillProjGenerator.calculateRefillProjections(getInventories(), 
                                                         criticalLevel, 
                                                         getEndOfLevelSix(), 
                                                         getEndOfLevelTwo(), 
                                                         refillComparator);
          externalAllocator.allocateRefillTasks(newRefills);
          allocationAssessor.reconcileInventoryLevels(getInventories());
        }
        
        // update the Maintain Inventory Expansion results
        PluginHelper.updateAllocationResult(MIExpansionSubscription);
        PluginHelper.updateAllocationResult(MITopExpansionSubscription);
        PluginHelper.updateAllocationResult(DetReqInvExpansionSubscription);
	  
	if (backwardFlowTouched != null) {
	  takeInventorySnapshot(backwardFlowTouched);
	}
	takeInventorySnapshot(getTouchedInventories());
        
        // touchedInventories should not be cleared until the end of transaction
        touchedInventories.clear();
	//backwardFlowInventories.clear(); //###
        touchedProjections = false;
        touchedChangedProjections = false;
        //testBG();
      }
    }
  }

  /** Subscription for aggregatable support requests. **/
  private IncrementalSubscription detReqSubscription;
  
  /** Subscription for the aggregated support request **/
  private CollectionSubscription aggMILSubscription;
  
  /** Subscription for the MIL tasks **/
  private IncrementalSubscription milSubscription;

  /** Subscription for the Organization(s) in which this plugin resides **/
  private IncrementalSubscription selfOrganizations;

  /** Subscription for incoming Supply tasks **/
  private IncrementalSubscription supplyTaskSubscription;

  /** Subscription for incoming Projection tasks **/
  private IncrementalSubscription projectionTaskSubscription;

  /** Subscription for Allocations on outgoing Refill (Supply & ProjectSupply) tasks **/
  private IncrementalSubscription refillAllocationSubscription;

  /** Subscription for my Refill (Supply & ProjectSupply) tasks **/
  private IncrementalSubscription refillSubscription;

  /** Subscription for Supply/ProjectSupply Expansions **/
  private IncrementalSubscription expansionSubscription;

  /** Subscription for InventoryPolicy **/
  private IncrementalSubscription inventoryPolicySubscription;

  /** Subscription for LogisticsOPlan object **/
  private IncrementalSubscription logisticsOPlanSubscription;

  /** Subscription for Withdraw tasks created by this plugin **/
  private IncrementalSubscription withdrawTaskSubscription;

  /** Subscription for ProjectWithdraw tasks created by this plugin **/
  private IncrementalSubscription projectWithdrawTaskSubscription;

  /** Subscription for MaintainInventory Expansion PlanElements created by this plugin**/
  private IncrementalSubscription MIExpansionSubscription;

  /** Subscription for MaintainInventory Expansion for Top level MI task (Aggregate task) **/
  private IncrementalSubscription MITopExpansionSubscription;

  /** Subscription for DetermineRequirements of type MaintainInventory Expansion **/
  private IncrementalSubscription DetReqInvExpansionSubscription;

  /** special subscription to oms only used in subsistence to deal with the level2 -> level6
   *  issue that occurs because subsistence does not generate level 2 tasks
   **/
  private IncrementalSubscription Level6OMSubscription;
  

  protected void setupSubscriptions() {
    if (! getBlackboardService().didRehydrate()) {
      setupOperatingModes();
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
      if (level2Horizon == null || level6Horizon == null) {
        if (logger.isErrorEnabled()) {
          logger.error("InventoryPlugin in agent: " + getAgentIdentifier() +
                       " of supply type: " + supplyType + 
                       " is missing operating modes upon rehydration... level2 OM is: " +
                       level2Horizon + " level 6 OM is: " + level6Horizon);
        }
      }
    }
    if (getSupplyType().equals("Subsistence")) {
      Level6OMSubscription = (IncrementalSubscription) blackboard.subscribe(new OperatingModePredicate(supplyType, LEVEL_6_TIME_HORIZON));
    }
    detReqSubscription = (IncrementalSubscription) blackboard.subscribe(new DetInvReqPredicate(taskUtils));
    aggMILSubscription = (CollectionSubscription) blackboard.subscribe(new AggMILPredicate(), false);
    milSubscription = (IncrementalSubscription) blackboard.subscribe(new MILPredicate());
    detReqHandler.addMILTasks(milSubscription.elements());
    selfOrganizations = (IncrementalSubscription) blackboard.subscribe(orgsPredicate);
    inventoryPolicySubscription = (IncrementalSubscription) blackboard.subscribe(new InventoryPolicyPredicate(supplyType));
    logisticsOPlanSubscription = (IncrementalSubscription) blackboard.subscribe(new LogisticsOPlanPredicate());
    withdrawTaskSubscription = (IncrementalSubscription) blackboard.subscribe(new WithdrawPredicate(supplyType));
    projectWithdrawTaskSubscription = (IncrementalSubscription) blackboard.subscribe(new ProjectWithdrawPredicate(supplyType));
    MIExpansionSubscription = (IncrementalSubscription)blackboard.subscribe(new MIExpansionPredicate(supplyType, taskUtils));
    MITopExpansionSubscription = (IncrementalSubscription)blackboard.subscribe(new MITopExpansionPredicate());
    DetReqInvExpansionSubscription = (IncrementalSubscription)blackboard.subscribe(new DetReqInvExpansionPredicate(taskUtils));

    if (getAgentIdentifier() == null && logger.isErrorEnabled()) {
      logger.error("No agentIdentifier ... subscriptions need this info!!  In plugin: " + this);
    }
    refillAllocationSubscription = (IncrementalSubscription) blackboard.
      subscribe(new RefillAllocPredicate(supplyType, getAgentIdentifier().toString(), taskUtils));
    expansionSubscription = (IncrementalSubscription)blackboard.
      subscribe(new ExpansionPredicate(supplyType, getAgentIdentifier().toString(), taskUtils));
    refillSubscription = (IncrementalSubscription)blackboard.
      subscribe(new RefillPredicate(supplyType, getAgentIdentifier().toString(), taskUtils));
    supplyTaskSubscription = (IncrementalSubscription) blackboard.
      subscribe(new SupplyTaskPredicate(supplyType, getAgentIdentifier().toString(), taskUtils));
    projectionTaskSubscription = (IncrementalSubscription) blackboard.
      subscribe( new ProjectionTaskPredicate(supplyType, getAgentIdentifier().toString(), taskUtils));
  }

  private static UnaryPredicate orgsPredicate = new UnaryPredicate() {
      public boolean execute(Object o) {
	if (o instanceof Organization) {
	  return ((Organization)o).isSelf();
	}
	return false;
      }
    };  	

  private static class SupplyTaskPredicate implements UnaryPredicate
  {
    String supplyType;
    String orgName;
    TaskUtils taskUtils;
    
    public SupplyTaskPredicate(String type, String myOrg, TaskUtils aTaskUtils) {
      supplyType = type;
      orgName = myOrg;
      taskUtils = aTaskUtils;
    }
    
    public boolean execute(Object o) {
      if (o instanceof Task ) {
	Task task = (Task)o;
	if (task.getVerb().equals(Constants.Verb.SUPPLY)) {
	  if (taskUtils.isDirectObjectOfType(task, supplyType)) {
	    if (!taskUtils.isMyRefillTask(task, orgName)) {
	      if (taskUtils.getQuantity(task) > 0) {
		return true;
	      }
	    }
	  }
	}	
      }
      return false;
    }
  }
  

  private static class ProjectionTaskPredicate implements UnaryPredicate
    {
	String supplyType;
	String orgName;
	TaskUtils taskUtils;

	public ProjectionTaskPredicate(String type, String orgname,TaskUtils aTaskUtils) {
	    supplyType = type;
	    orgName = orgname;
	    taskUtils = aTaskUtils;
	}

	public boolean execute(Object o) {
	    if (o instanceof Task ) {
		Task task = (Task)o;
		if (task.getVerb().equals(Constants.Verb.PROJECTSUPPLY)) {
		    if (taskUtils.isDirectObjectOfType(task, supplyType)) {
			if (!taskUtils.isMyInventoryProjection(task, orgName)) {
			    return true;
			}
		    } 
		}	
	    }
	    return false;
	}
    }

  /**
     Passes DetermineRequirements tasks of type MaintainInventory.
  **/
  private static class DetInvReqPredicate implements UnaryPredicate {

      private TaskUtils taskUtils;

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
  private static class DetReqInvExpansionPredicate implements UnaryPredicate {
      private TaskUtils taskUtils;
      public DetReqInvExpansionPredicate(TaskUtils aTaskUtils) {
	  taskUtils = aTaskUtils;
      }
    public boolean execute(Object o) {
      if (o instanceof Expansion) {
        Task parent = ((Expansion)o).getTask();
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
  private static class MILPredicate implements UnaryPredicate {
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
  private static class MITopExpansionPredicate implements UnaryPredicate {
    public boolean execute(Object o) {
      if (o instanceof Expansion) {
        Task parent = ((Expansion)o).getTask();
	if (parent.getVerb().equals(Constants.Verb.MAINTAININVENTORY)) {
	  if ( parent.getDirectObject() == null ) {
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
  private static class AggMILPredicate implements UnaryPredicate {
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
  private static class MIExpansionPredicate implements UnaryPredicate {
    String supplyType;
    TaskUtils taskUtils;
    public MIExpansionPredicate(String type, TaskUtils utils){ 
      supplyType = type;
      taskUtils = utils;
    }
    public boolean execute(Object o) {
      if (o instanceof Expansion) {
        Task parent = ((Expansion)o).getTask();
        if (parent.getVerb().equals(Constants.Verb.MAINTAININVENTORY)) {
          Asset directObject = parent.getDirectObject();
          if (directObject != null && directObject instanceof Inventory) {
            LogisticsInventoryPG thePG = (LogisticsInventoryPG) ((Inventory)directObject).
              searchForPropertyGroup(LogisticsInventoryPG.class);
            Asset resource = thePG.getResource();
            SupplyClassPG pg = (SupplyClassPG)resource.searchForPropertyGroup(SupplyClassPG.class);
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
      Selects the LogisticsOPlan objects
  **/
  private static class LogisticsOPlanPredicate implements UnaryPredicate{
    public boolean execute(Object o) {
 	return o instanceof LogisticsOPlan;
    }
  }
  
  /**
     Passes Inventory assets that have a valid LogisticsInventoryPG
  **/

 private static class InventoryPredicate implements UnaryPredicate {
    String supplyType;

     public InventoryPredicate(String type){
	 supplyType = type;
     }
     
     public boolean execute(Object o) {
	 if (o instanceof Inventory) {
	     Inventory inv = (Inventory) o;
	     LogisticsInventoryPG logInvpg =
		 (LogisticsInventoryPG) 
		 inv.searchForPropertyGroup(LogisticsInventoryPG.class);
	     if (logInvpg != null) {
		 String type = getAssetType(inv, logInvpg);
		 if (supplyType.equals(type)) {
		     return true;    
		 }
	     }
	 }
	 return false;
     }
     
     private String getAssetType(Inventory inventory, 
				 LogisticsInventoryPG invpg) {
	 Asset a = invpg.getResource();
	 if (a == null) return null;
	 SupplyClassPG pg = (SupplyClassPG) 
	     a.searchForPropertyGroup(SupplyClassPG.class);
	 return pg.getSupplyType();
     }
 }
    
    

    //Allocation of refill tasks
  static class RefillAllocPredicate implements UnaryPredicate {
    String type_;
    String orgName_;
    TaskUtils taskUtils;
    
    public RefillAllocPredicate(String type, String orgName, TaskUtils aTaskUtils) {
      type_ = type;
      orgName_ = orgName;
      taskUtils = aTaskUtils;
    }
    
    public boolean execute(Object o) {
      if (o instanceof Allocation ) {
	Task task = ((Allocation)o).getTask();
	if (task.getVerb().equals(Constants.Verb.SUPPLY) ||
	    task.getVerb().equals(Constants.Verb.PROJECTSUPPLY)) {
	  if (taskUtils.isDirectObjectOfType(task, type_)) {
	    // need to check if externally allocated
	    if(((Allocation)o).getAsset() instanceof Organization) {
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

  //Refill tasks
  static class RefillPredicate implements UnaryPredicate {
    String type_;
    String orgName_;
    TaskUtils taskUtils;
    
    public RefillPredicate(String type, String orgName, TaskUtils aTaskUtils) {
      type_ = type;
      orgName_ = orgName;
      taskUtils = aTaskUtils;
    }
    
    public boolean execute(Object o) {
      if (o instanceof Task ) {
	Task task = (Task)o;
	if (task.getVerb().equals(Constants.Verb.SUPPLY) ||
	    task.getVerb().equals(Constants.Verb.PROJECTSUPPLY)) {
	  if (taskUtils.isDirectObjectOfType(task, type_)) {
            if (taskUtils.isMyRefillTask(task, orgName_)){
              return true;
            }
          }
        }
      }
      return false;
    }
  }

  private class ExpansionPredicate implements UnaryPredicate {
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

  private class InventoryPolicyPredicate implements UnaryPredicate {
    String type;
    public InventoryPolicyPredicate(String type) {
      this.type = type;
    }
    public boolean execute(Object o) {
      if (o instanceof org.cougaar.logistics.plugin.inventory.InventoryPolicy) {
	String type = ((InventoryPolicy)o).getResourceType();
	if (type.equals(this.type)) {
	  if (logger.isInfoEnabled()) {
	    logger.info("Found an inventory policy for "+this.type + "agent is: " +
			getMyOrganization());
          }
	  return true;
	} else {
	  if (logger.isDebugEnabled()) { 
	    logger.debug("Ignoring type of: "+type + " in " +
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
  protected Collection getTasksWithoutPEs (Collection tasks) {
    Set tasksWithoutPEs = new HashSet();
    for (Iterator iter = tasks.iterator(); iter.hasNext(); ) {
      Task task = (Task) iter.next();
	
      if (task.getPlanElement() != null) {
	if (logger.isDebugEnabled()) {
	  logger.debug (getMyOrganization() + " - found task that already had a p.e. attached? : " + 
			task.getUID() + " - so skipping it.");
        }
      }
      else {
	tasksWithoutPEs.add (task);
      }
    }

    return tasksWithoutPEs;
  }

  private class WithdrawPredicate implements UnaryPredicate {
    String supplyType;

    public WithdrawPredicate(String type) {
      supplyType = type;
    }

    public boolean execute(Object o) {
      if (o instanceof Task) {
	Task task = (Task)o;
	if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
	  if (taskUtils.isDirectObjectOfType(task, supplyType)) {
	    return true;
	  }
	}	
      }
      return false;
    }
  }

  private class ProjectWithdrawPredicate implements UnaryPredicate {
    String supplyType;

    public ProjectWithdrawPredicate(String type) {
      supplyType = type;
    }

    public boolean execute(Object o) {
      if (o instanceof Task) {
	Task task = (Task)o;
	if (task.getVerb().equals(Constants.Verb.PROJECTWITHDRAW)) {
	  if (taskUtils.isDirectObjectOfType(task, supplyType)) {
	    return true;
	  }
	}	
      }
      return false;
    }
  }

  private class OperatingModePredicate implements UnaryPredicate {
    String supplyType;
    String level;
    
    public OperatingModePredicate(String type, String level) {
      supplyType = type;
      this.level = level;
    }
    
    public boolean execute(Object o) {
      if (o instanceof OperatingMode) {
        OperatingMode om = (OperatingMode)o;
        if (om.getName().equals(level+"_"+supplyType)) {
          return true;
        }	
      }
      return false;
    }
  }

  // Determines which tasks should be expanded and which should be
  // re-allocated to a supplier
  private Collection sortIncomingSupplyTasks(Collection tasks) {
    ArrayList expandList = new ArrayList();
    ArrayList passThruList = new ArrayList();
    Task t;
    Inventory inventory;
    Asset asset;
    for (Iterator i = tasks.iterator(); i.hasNext();) {
      t = (Task)i.next();
      asset = (Asset)t.getDirectObject();
      inventory = findOrMakeInventory(asset);
      if (inventory != null) {
	expandList.add(t);
      } else {  // allocate tasks to supplier?
	  passThruList.add(t);
      }
    }
    externalAllocator.forwardSupplyTasks(passThruList, myOrganization);
    return expandList;
  }

  private void expandIncomingRequisitions(Collection tasks) {
    Collection tasksToExpand = sortIncomingSupplyTasks(tasks);
    supplyExpander.expandAndDistributeRequisitions(tasksToExpand);
  }

  private boolean expandIncomingProjections(Collection tasks) {
    Collection tasksToExpand = sortIncomingSupplyTasks(tasks);
    return supplyExpander.expandAndDistributeProjections(tasksToExpand);
  }
    
  /**
     Add some inventories to the inventoryHash.
     Method called during rehydration to populate inventory hash
  **/
  private void addRehydratedInventories(Collection inventories) {
      for (Iterator i = inventories.iterator(); i.hasNext(); ) {
	  Inventory inv = (Inventory) i.next();
	  LogisticsInventoryPG logInvPG = (LogisticsInventoryPG)inv.searchForPropertyGroup(LogisticsInventoryPG.class);
	  logInvPG.reinitialize(logToCSV,new Date(logOPlan.getOplanCday()),this);
	  addInventory(inv);
    }
  }
  
  private void addInventory(Inventory inventory) {
    String item = getInventoryType(inventory);
    inventoryHash.put(item, inventory);
  }

  private void removeInventories(Enumeration inventories) {
    while (inventories.hasMoreElements()) {
      removeInventory((Inventory) inventories.nextElement());
    }
  }

  private void removeInventory(Inventory inventory) {
    String item = getInventoryType(inventory);
    inventoryHash.remove(item);
  }

  public String getInventoryType(Inventory inventory) {
    ScheduledContentPG scp = inventory.getScheduledContentPG();
    Asset proto = scp.getAsset();
    if (proto == null) {
      if (logger.isErrorEnabled()) {
        logger.error("getInventoryType failed to get asset for "+
                     inventory.getScheduledContentPG().getAsset().getTypeIdentificationPG());
      }
      return "";
    }
    return proto.getTypeIdentificationPG().getTypeIdentification();
  }

  public Inventory findOrMakeInventory(Asset resource) {
    Inventory inventory = null;
    String item = resource.getTypeIdentificationPG().getTypeIdentification();
    inventory = (Inventory) inventoryHash.get(item);
    if (inventory == null) {
      inventory = createInventory(resource, item);
      if (inventory != null) {
	addInventory(inventory);
	publishAdd(inventory);
	detReqHandler.findOrMakeMILTask(inventory, aggMILSubscription);
      }
    }
    if (inventory == null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Inventory is null for "+item);
      }
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("findOrMakeInventory(), CREATED inventory bin for: "+
                     AssetUtils.assetDesc(inventory.getScheduledContentPG().getAsset()));
      }
    }
    return inventory;
  }
  
  protected Inventory createInventory(Asset resource, String item) {
    double levels[] = null;
    Inventory inventory = null;
    levels = (double[])inventoryInitHash.get(item);
    if (levels != null) {
      inventory=(Inventory)getRootFactory().createAsset("Inventory");
      NewLogisticsInventoryPG logInvPG = 
	(NewLogisticsInventoryPG)PropertyGroupFactory.newLogisticsInventoryPG();
      inventory.addOtherPropertyGroup(logInvPG);
      if (resource.getTypeIdentificationPG().getTypeIdentification().endsWith("Aggregate")) {
        logInvPG.setIsLevel2(true); // will need to key off asset to identify level2 item
	// Need to distinguish Level2Package aggregates of different supply types otherwise
	// they are determined to be the same asset and are removed from the blackboard
	((NewItemIdentificationPG)inventory.getItemIdentificationPG()).setItemIdentification("Inventory:" + item+":"+supplyType);
      } else {
        logInvPG.setIsLevel2(false); 
	((NewItemIdentificationPG)inventory.getItemIdentificationPG()).setItemIdentification("Inventory:" + item);
      }
      logInvPG.setCapacity(levels[0]);
      logInvPG.setInitialLevel(levels[1]);
      logInvPG.setResource(resource);
      logInvPG.setOrg(getMyOrganization());
      logInvPG.setLogInvBG(new LogisticsInventoryBG(logInvPG));
      logInvPG.initialize(startTime, criticalLevel, reorderPeriod, bucketSize, getCurrentTimeMillis(), logToCSV, new Date(logOPlan.getOplanCday()), this);

      NewTypeIdentificationPG ti = 
	(NewTypeIdentificationPG)inventory.getTypeIdentificationPG();
      ti.setTypeIdentification("InventoryAsset");
      ti.setNomenclature("Inventory Asset");

      NewScheduledContentPG scp;
      scp = (NewScheduledContentPG)inventory.getScheduledContentPG();
      scp.setAsset(resource);
      scp.setSchedule(ScheduleUtils.buildSimpleQuantitySchedule(levels[1], startTime, 
								startTime+(TimeUtils.MSEC_PER_DAY*10)));
    }
    return inventory;
  }

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

  public void takeInventorySnapshot(Collection inventories) {
    Inventory inv;
    Iterator inv_it = inventories.iterator();
    LogisticsInventoryPG logInvPG = null;
    while (inv_it.hasNext()) {
      inv = (Inventory)inv_it.next();
      logInvPG = (LogisticsInventoryPG)inv.searchForPropertyGroup(LogisticsInventoryPG.class);
      logInvPG.takeSnapshot(inv);
      if(logToCSV) {
	  logInvPG.logAllToCSVFile(cycleStamp);
      }
    }
  }

  /**
     Read the Plugin parameters(Accepts key/value pairs)
     Initializes supplyType and inventoryFile
  **/
  private HashMap readParameters() {
    final String errorString = "InventoryPlugin requires 1 parameter, Supply Type.  Additional parameter for csv logging, default is disabled.   e.g. org.cougaar.logistics.plugin.inventory.InventoryPlugin("+SUPPLY_TYPE+"=BulkPOL, ENABLE_CSV_LOGGING=true);";
    Collection p = getParameters();
    
    if (p.isEmpty()) {
      if (logger.isErrorEnabled()) {
        logger.error(errorString);
      }
      return null;
    }
    HashMap map = new HashMap();
    int idx;
 
    for (Iterator i = p.iterator();i.hasNext();) {
      String s = (String)i.next();
      if ((idx=s.indexOf('=')) != -1) {
	String key = new String(s.substring(0, idx));
	String value = new String(s.substring(idx+1, s.length()));
	map.put(key.trim(), value.trim());
      }
    }
    supplyType = (String)map.get(SUPPLY_TYPE);
//      inventoryFile = (String)map.get(INVENTORY_FILE);
    if (supplyType == null && logger.isErrorEnabled()) {
      logger.error(errorString);
    }
    String loggingEnabled = (String)map.get(ENABLE_CSV_LOGGING);
    if((loggingEnabled != null) &&
       (loggingEnabled.trim().equals("true"))) {
	logToCSV = true;
    }
    return map;
  }

  private String getInventoryFile(String type) {
    String result = null;
    // if defined in plugin argument list
    String inv_file = null;
    if ((inv_file = (String)pluginParams.get(INVENTORY_FILE)) != null) {
      result = inv_file;
      //   } 
 //    else {
//       result = getClusterSuffix(myOrganization.getClusterPG().getClusterIdentifier().toString()) +
// 	"_"+type.toLowerCase()+".inv";
    } else {
       result = getClusterSuffix(getAgentIdentifier().toString()) +
 	"_"+type.toLowerCase()+".inv";
    }
    return result;
  }
  
  private String getClusterSuffix(String clusterId) {
    String result = null;
    int i = clusterId.lastIndexOf("-");
    if (i == -1) {
      result = clusterId;
    } 
    else {
      result = clusterId.substring(i+1);
    }
    return result;
  }
  
  public void publishAddToExpansion(Task parent, Task subtask) {
    //attach the subtask to its parent and the parent's workflow
    PlanElement pe = parent.getPlanElement();
    Expansion expansion;
    NewWorkflow wf;
    ((NewTask) subtask).setParentTask(parent);
    ((NewTask) subtask).setPlan(parent.getPlan());
    // Task has not been expanded, create an expansion
    if (pe == null) {
      RootFactory factory = getRootFactory();
      // Create workflow
      wf = (NewWorkflow)factory.newWorkflow();
      wf.setParentTask(parent);
      wf.setIsPropagatingToSubtasks(true);
      wf.addTask(subtask);
      ((NewTask) subtask).setWorkflow(wf);
      // Build Expansion
      expansion = factory.createExpansion(parent.getPlan(), parent, wf, null);
      // Publish Expansion
      publishAdd(expansion);
    }
    // Task already has expansion, add task to the workflow and publish the change
    else if (pe instanceof Expansion) {
      expansion =(Expansion)pe;
      wf = (NewWorkflow)expansion.getWorkflow();
      wf.addTask(subtask);
      ((NewTask) subtask).setWorkflow(wf);
      publishChange(expansion);
    }
    else {
      if (logger.isErrorEnabled()) {
        logger.error("publishAddToExpansion: problem pe not Expansion? "+pe);	    
      }
    }
 
   // Publish new task
    if (!publishAdd(subtask) && logger.isErrorEnabled()) {
      logger.error("publishAddToExpansion fail to publish task "+taskUtils.taskDesc(subtask));
    }
    if((subtask.getVerb().equals(Constants.Verb.SUPPLY)) ||
       (subtask.getVerb().equals(Constants.Verb.PROJECTSUPPLY))) {
      newRefills.add(subtask);
    }
  }

  // called by the RefillGenerator to hook up the refill task to the maintain
  // inventory parent task and workflow.
  public void publishRefillTask(Task task, Inventory inventory) {
    Task milTask = detReqHandler.findOrMakeMILTask(inventory,
						   aggMILSubscription);
    publishAddToExpansion(milTask, task);
  }

  private Organization getMyOrganization(Enumeration orgs) {
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
    return logOPlan.getArrivalTime();
  }

  public void getInventoryData() {
    String invFile = getInventoryFileName();
    if (invFile != null) {
      Enumeration initialInv = FileUtils.readConfigFile(invFile, getConfigFinder());
      if (initialInv != null) {
	stashInventoryInformation(initialInv);
      }
    }
  }

  private void stashInventoryInformation(Enumeration initInv){
    String line;
    String item = null;
    double capacity, level;

    while(initInv.hasMoreElements()) {
      line = (String) initInv.nextElement();
      // Find the fields in the line, values seperated by ','
      Vector fields = FileUtils.findFields(line, ',');
      if (fields.size() < 3)
	continue;
      item = (String)fields.elementAt(0);
      capacity = Double.valueOf((String)fields.elementAt(1)).doubleValue();
      level = Double.valueOf((String)fields.elementAt(2)).doubleValue();
      double[] levels = {capacity,level};
      inventoryInitHash.put(item, levels);
    }
  }

  private Role getRole(String supply_type) {
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
      pol = (InventoryPolicy)policy_iterator.next();
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
    }
    return changed;
  }

  public int getOrderShipTime() {
    return inventoryPolicy.getOrderShipTime();
  }

  public int getMaxLeadTime() {
    return inventoryPolicy.getSupplierAdvanceNoticeTime() + getOrderShipTime();
  }

  /** VTH operating modes */
  protected OperatingMode level2Horizon, level6Horizon;

  /** create and publish VTH Operating Modes */
  protected void setupOperatingModes () {
    try {
      //getBlackboardService().openTransaction();
      OMCRange level2Range = new IntRange (LEVEL_2_MIN.intValue(), LEVEL_2_MAX.intValue());
      OMCRangeList rangeList = new OMCRangeList (level2Range);
      publishAdd (level2Horizon = new OperatingModeImpl (LEVEL_2_TIME_HORIZON+"_"+supplyType, rangeList, 
							 LEVEL_2_TIME_HORIZON_DEFAULT));

      OMCRange level6Range = new IntRange (LEVEL_6_MIN.intValue(), LEVEL_6_MAX.intValue());
      rangeList = new OMCRangeList (level6Range);
      publishAdd (level6Horizon = new OperatingModeImpl (LEVEL_6_TIME_HORIZON+"_"+supplyType, rangeList,
							 LEVEL_6_TIME_HORIZON_DEFAULT));
    } catch (Exception e) {  
      if (logger.isErrorEnabled()) {
        logger.error ("" + getMyOrganization() + " got exception creating operating modes.", e); 
      }
      //} finally {
      //getBlackboardService().closeTransaction();
    }

    if(logger.isInfoEnabled()) {
      logger.info ("" + getMyOrganization() + " created operating modes - " + 
		   "level 2 time horizon is " + level2Horizon + 
		   " and level 6 is " + level6Horizon);
    }
  }

  /** tiny helper class for VTH Operating Modes */
  protected static class IntRange extends OMCRange {
    public IntRange (int a, int b) { super (a, b); }
  }

  /** relative to now -- this is correct, isn't it? */
  protected long getEndOfLevelSix () {
    long now = currentTimeMillis();
    int days = ((Integer)level6Horizon.getValue()).intValue();
    
    return timeUtils.addNDays(now, days);
  } 

  /** relative to now -- this is correct, isn't it? */
  protected long getEndOfLevelTwo () {
    long now = currentTimeMillis();
    int days = ((Integer)level2Horizon.getValue()).intValue();
    
    return timeUtils.addNDays(now, days);
  }

  /** When one of our Refill tasks gets removed (Supply or ProjectSupply),
   *  remove it from the BG list.
   *  @param Collection The collection of removed refill tasks.
   **/
  public void handleRemovedRefills(Collection removedTasks) {
    Iterator removedIter = removedTasks.iterator();
    while (removedIter.hasNext()) {
      Task removed = (Task) removedIter.next();
      String item = removed.getDirectObject().getTypeIdentificationPG().getTypeIdentification();
      Inventory inventory = (Inventory) inventoryHash.get(item);
      LogisticsInventoryPG invPG = (LogisticsInventoryPG)
        inventory.searchForPropertyGroup(LogisticsInventoryPG.class);
      if (removed.getVerb().equals(Constants.Verb.SUPPLY)) {
        invPG.removeRefillRequisition(removed);
      } else {
        invPG.removeRefillProjection(removed);
      }
    }
  }

  public ClusterIdentifier getClusterId() {
    return getAgentIdentifier();
  }

  //
  private void processDetReq(Collection addedDRs) {
    // with one oplan we should only have one DR for MI.
    Iterator drIt = addedDRs.iterator();
    if (drIt.hasNext()) {
      Task detReq = (Task) drIt.next();
      //synch on the detReq task so only one instance of this plugin
      // checks and creates a single agg task and then creates an
      // empty expansion (wf) for the maintain inventory for each item tasks
      synchronized(detReq) {
        if (detReq.getPlanElement() == null) {
          detReqHandler.createAggTask(addedDRs);
        }
      }
    }
  }

  // We only want to process inventories that we have no new refills for.
  private Collection getActionableInventories(){
    ArrayList actionableInvs = new ArrayList(touchedInventories);
    Task refill;
    Asset asset;
    Inventory inventory;
    Iterator refillIt = newRefills.iterator();
    while (refillIt.hasNext()) {
      refill = (Task)refillIt.next();
      asset = (Asset)refill.getDirectObject();
      inventory = findOrMakeInventory(asset);
      actionableInvs.remove(inventory);
    }
    return actionableInvs;
  }

  /**
     Self-Test
  **/
  public void automatedSelfTest() {
    if (logger.isErrorEnabled()) {
      if (supplyType == null) logger.error("No SupplyType Plugin parameter.");
      if (inventoryFile == null) logger.error("No Inventory File Plugin parameter.");
      if (inventoryInitHash.isEmpty()) {
	logger.error("No initial inventory information.  Inventory File is empty or non-existant.");
	logger.error("Could not find Inventory file : "+inventoryFile);
      }
      if (detReqHandler.getDetermineRequirementsTask(aggMILSubscription) == null)
        logger.error("Missing DetermineRequirements for MaintainInventory task.");
      if (logOPlan == null)
        logger.error("Missing LogisticsOPlan object. Is the LogisticsOPlanPlugin loaded?");
      if (myOrganization == null)
        logger.error("Missing myorganization");
      logger.error("Critical Level is "+criticalLevel);
      logger.error("Reorder Period is "+reorderPeriod);
      logger.error("Days per bucket is "+bucketSize);
    }
  }

  private void testBG() {
    Iterator inv_it = inventoryHash.values().iterator();
    Inventory inv;
    LogisticsInventoryPG logInvPG = null;
    cycleStamp = (new Date()).getTime();
    while (inv_it.hasNext()) {
      inv = (Inventory)inv_it.next();
      if (logger.isErrorEnabled()) {
        logger.error("***"+inv.getItemIdentificationPG().getItemIdentification());
      }
      logInvPG = (LogisticsInventoryPG)inv.searchForPropertyGroup(LogisticsInventoryPG.class);
      logInvPG.takeSnapshot(inv);
      if(logToCSV) {
	  logInvPG.logAllToCSVFile(cycleStamp);
      }
      logInvPG.Test();
    }
  }
}

