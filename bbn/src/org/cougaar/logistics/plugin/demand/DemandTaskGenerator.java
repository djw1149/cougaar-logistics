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

package org.cougaar.logistics.plugin.demand;



import java.util.HashMap;
import java.util.Iterator;
import java.util.Collection;
import java.util.Vector;
import java.util.ArrayList;
import java.util.Date;

import org.cougaar.planning.ldm.plan.*;
import org.cougaar.planning.ldm.asset.Asset;
import org.cougaar.planning.ldm.asset.TypeIdentificationPG;
import org.cougaar.planning.ldm.asset.ItemIdentificationPG;
import org.cougaar.glm.ldm.Constants;
import org.cougaar.glm.ldm.asset.Organization;
import org.cougaar.glm.ldm.plan.GeolocLocation;
import org.cougaar.planning.ldm.plan.AspectValue;
import org.cougaar.planning.ldm.measure.*;
import org.cougaar.logistics.plugin.inventory.MaintainedItem;
import org.cougaar.util.Random;




/**
 * The DemandTaskGenerator generates Supply tasks based on the based in ProjectSupply tasks.
 *
 * @see DemandGeneratorPlugin
 * @see DemandGeneratorModule
 **/

public class DemandTaskGenerator extends DemandGeneratorModule
    implements DemandTaskGeneratorIfc {


  HashMap projHash;

  Random poissonGen;

  public DemandTaskGenerator(DemandGeneratorPlugin demandGeneratorPlugin) {
    super(demandGeneratorPlugin);
    projHash = new HashMap();
    poissonGen = new Random();
  }

  /**
   * Generate actual demand tasks from the passed in projections
   **/

  public void generateDemandTasks(long start, long duration, Collection relevantProjectSupplys) {
    regenerateProjectionHash(relevantProjectSupplys);
    long end = start + duration;
    Iterator gpTasksIt = projHash.keySet().iterator();
    while (gpTasksIt.hasNext()) {
      Task gpTask = (Task) gpTasksIt.next();
      ArrayList supplyTasks = new ArrayList();
      HashMap assetMap = (HashMap) projHash.get(gpTask);
      Iterator assetsIt = assetMap.keySet().iterator();
      while (assetsIt.hasNext()) {
        Asset consumed = (Asset) assetsIt.next();
        Collection projTasks = (Collection) (assetMap.get(consumed));
        double totalQty = deriveTotalQty(start,end,projTasks);
        int poissonQty = poissonGen.nextPoisson(totalQty);
        Iterator projTaskIt = projTasks.iterator();
        //This should not be a while, but an if
        if((poissonQty > 0) &&
           (projTaskIt.hasNext())) {
            supplyTasks.add(createNewDemandTask(gpTask,
                                                  (Task) projTaskIt.next(),
                                                  consumed,
                                                  start,
                                                  end,
                                                  poissonQty));

        }

      }
      if(!supplyTasks.isEmpty()){
        addToAndPublishExpansion(gpTask,supplyTasks);
      }
    }

  }




  protected void regenerateProjectionHash(Collection tasks) {
    projHash.clear();
    Iterator taskIt = tasks.iterator();
    while (taskIt.hasNext()) {
      Task task = (Task) taskIt.next();
      Workflow wf = task.getWorkflow();
      if (wf == null) {
        logger.error("Projection task: " + task.getUID() + " at " + dgPlugin.getOrgName() + " has Null workflow");
        continue;
      }
      Task parTask = wf.getParentTask();
      if (parTask == null) {
        logger.error("Projection task with no parent");
        continue;
      }
      if (!(parTask.getVerb().equals
          (Constants.Verb.GENERATEPROJECTIONS))) {
        logger.error("Projection task with non generate projections parent");
        continue;
      }
      HashMap assetMap = (HashMap) projHash.get(parTask);
      if (assetMap == null) {
        assetMap = new HashMap();
        projHash.put(parTask, assetMap);
      }
      Asset asset = task.getDirectObject();
      ArrayList tasksWAsset = (ArrayList) assetMap.get(asset);
      if (tasksWAsset == null) {
        tasksWAsset = new ArrayList();
        assetMap.put(asset, tasksWAsset);
      }
      tasksWAsset.add(task);
    }
  }

  protected double deriveTotalQty(long bucketStart, long bucketEnd, Collection projTasks){
    Iterator tasksIt = projTasks.iterator();
    double totalQty = 0.0;
    while(tasksIt.hasNext()){
      Task projTask = (Task) tasksIt.next();
      long taskStart = getTaskUtils().getStartTime(projTask);
      long taskEnd  = getTaskUtils().getEndTime(projTask);
      long start = Math.max(taskStart, bucketStart);
      long end = Math.min(taskEnd, bucketEnd);
      //duration in seconds
      double duration = ((end - start) / 1000);
      Rate rate = getTaskUtils().getRate(projTask);
      double qty = (getBaseUnitPerSecond(rate) * duration);
      totalQty+=qty;
    }
    return totalQty;
  }

  protected double getBaseUnitPerSecond(Rate rate) {
  		if (rate instanceof CostRate) {
				  return ((CostRate) rate).getDollarsPerSecond();
			} else if (rate instanceof CountRate) {
				  return ((CountRate) rate).getEachesPerSecond();
			} else if (rate instanceof FlowRate) {
				  return ((FlowRate) rate).getGallonsPerSecond();
			} else if (rate instanceof MassTransferRate) {
				  return ((MassTransferRate)rate).getShortTonsPerSecond();
			} else if (rate instanceof TimeRate) {
				  return ((TimeRate) rate).getHoursPerSecond();
			} // if
      return 0.0;
  }

  protected void addToAndPublishExpansion(Task parent, Collection subtasks) {
    Expansion expansion = (Expansion) parent.getPlanElement();
    NewWorkflow wf = (NewWorkflow) expansion.getWorkflow();
    Iterator subtasksIT = subtasks.iterator();
    while (subtasksIT.hasNext()) {
      Task task = (Task) subtasksIT.next();
      dgPlugin.publishAdd(task);
      wf.addTask(task);
    }
    dgPlugin.publishChange(expansion);
  }


  protected NewTask createNewDemandTask(Task parentTask,
                                        Task projTask,
                                        Asset consumed,
                                        long start,
                                        long end,
                                        int qty) {

    Vector prefs = createDemandPreferences(start, end, qty);

    NewTask newTask = getPlanningFactory().newTask();

    newTask.setParentTask(parentTask);
    newTask.setPlan(parentTask.getPlan());

    //TODO: MWD Remove
    //newTask.setPrepositionalPhrases(parentTask.getPrepositionalPhrases());
    //newTask = addPrepositionalPhrase(newTask, prepPhrase);
    //TODO: MWD Remove - also remove createDemandPrepPhrases
    //Vector preps = createDemandPrepPhrases(parentTask, consumed, end);
    //newTask.setPrepositionalPhrases(preps.elements());

    newTask.setPrepositionalPhrases(projTask.getPrepositionalPhrases());

    newTask.setDirectObject(consumed);
    newTask.setVerb(Verb.getVerb(Constants.Verb.SUPPLY));


    newTask.setPreferences(prefs.elements());

    newTask.setCommitmentDate(new Date(end));


    return newTask;
  }


  /**
   *  Create FOR, TO, MAINTAIN, and OFTYPE prepositional phrases
   *  for use by the subclasses.
   *  @param parentTask - The parent GP Task
   *  @param resource
   *  @param time - used to find the OPlan and the geoloc for the TO preposition
   *  @return Vector of PrepostionalPhrases
   *
   **/

  /***
   * TODO: MWD Remove
   *

   protected Vector createDemandPrepPhrases
   (Task parentTask, Asset resource, long time) {

   Asset consumer = parentTask.getDirectObject();

   //  Create prepositions for the new demand task
   Vector pp_vector = new Vector();
   PrepositionalPhrase pp =
   parentTask.getPrepositionalPhrase(Constants.Preposition.OFTYPE);

   if (pp != null) {
   Object obj = pp.getIndirectObject();
   if (obj instanceof String) {
   pp_vector.addElement(newPrepositionalPhrase
   (Constants.Preposition.OFTYPE,  obj));
   }
   }

   pp_vector.addElement(newPrepositionalPhrase
   (Constants.Preposition.FOR, dgPlugin.getOrgName()));

   // when oplan id added to tasks....
   //   pp_vector.addElement (newPrepositionalPhrase (Constants.Preposition.FOROPLAN, oplan));

   GeolocLocation geoloc = getGeolocLocation(time, dgPlugin.getMyOrganization());
   if (geoloc != null) {
   pp_vector.addElement(newPrepositionalPhrase(Constants.Preposition.TO, geoloc));
   } else {
   // Try to use HomeLocation
   try {
   geoloc = (GeolocLocation)
   dgPlugin.getMyOrganization().getMilitaryOrgPG().getHomeLocation();
   pp_vector.addElement
   (newPrepositionalPhrase(Constants.Preposition.TO, geoloc));
   } catch (NullPointerException npe) {
   logger.error("Geoloc not found");
   }
   }

   if (consumer != null) {
   MaintainedItem itemID;
   if (consumer instanceof Asset) {
   TypeIdentificationPG tip = ((Asset) consumer).getTypeIdentificationPG();
   ItemIdentificationPG iip = ((Asset) consumer).getItemIdentificationPG();
   if (iip != null) {
   itemID = MaintainedItem.findOrMakeMaintainedItem
   ("Asset", tip.getTypeIdentification(), iip.getItemIdentification(),
   tip.getNomenclature(),dgPlugin);
   } else {
   itemID = MaintainedItem.findOrMakeMaintainedItem
   ("Asset", tip.getTypeIdentification(), null, tip.getNomenclature(), dgPlugin);
   }
   } else {
   itemID = MaintainedItem.findOrMakeMaintainedItem
   ("Other", consumer.toString(), null, null,dgPlugin);
   }
   pp_vector.addElement(newPrepositionalPhrase
   (Constants.Preposition.MAINTAINING, itemID));
   }

   return pp_vector;
   }


   public PrepositionalPhrase newPrepositionalPhrase(String preposition,
   Object io) {
   NewPrepositionalPhrase pp = getPlanningFactory().newPrepositionalPhrase();
   pp.setPreposition(preposition);
   pp.setIndirectObject(io);
   return pp;
   }
   **/

  /**
   *  @return   Geographic location of this organization at specifed time.
   * TODO: MWD Remove
   **
   public GeolocLocation getGeolocLocation(long time, Organization myOrg) {
   Enumeration geolocs =
   getAssetUtils().getGeolocLocationAtTime(myOrg, time);
   if (geolocs.hasMoreElements()) {
   return ((GeolocLocation) geolocs.nextElement());
   }
   return null;
   }

   **/

  protected Vector createDemandPreferences(long start, long end, double qty) {
    Vector prefs = new Vector();

    prefs.addElement(createTimeScoringFunctionPref(start, AspectType.START_TIME));
    prefs.addElement(createTimeScoringFunctionPref(end, AspectType.END_TIME));

    AspectValue av = AspectValue.newAspectValue(AspectType.QUANTITY, qty);
    ScoringFunction score = ScoringFunction.createStrictlyAtValue(av);
    prefs.addElement(getPlanningFactory().newPreference(AspectType.QUANTITY, score));

    return prefs;
  }


  protected Preference createTimeScoringFunctionPref
      (long bestTime, int aspectType) {

    long bucketStart = dgPlugin.getStartOfPeriod();
    long bucketEnd = bucketStart + dgPlugin.getFrequency();
    long earliestTime = dgPlugin.getLogOPlanStartTime();
// Get the later of now and org report time
    if (bucketStart > dgPlugin.getLogOPlanStartTime()) {
      earliestTime = bucketStart;
    }
    long theBadPast = bucketStart - 1;
    long latestTime = dgPlugin.getLogOPlanEndTime();

    double bucketsBetween = ((latestTime - bestTime) / dgPlugin.getFrequency()) - 1;
//Use .0033 as a slope for now
    double lateScore = .0033 * bucketsBetween;
// use some baseline score for earlier or 1 day late
    double alpha = .25;
    Vector points = new Vector();

    AspectScorePoint badest = new AspectScorePoint
        (AspectValue.newAspectValue(aspectType, theBadPast), 1.0);
    AspectScorePoint earliest = new AspectScorePoint
        (AspectValue.newAspectValue(aspectType, earliestTime), alpha);
    AspectScorePoint best = new AspectScorePoint
        (AspectValue.newAspectValue(aspectType, bestTime), 0.0);
    AspectScorePoint first_late = new AspectScorePoint
        (AspectValue.newAspectValue(aspectType, bestTime + dgPlugin.getFrequency()), alpha);
    AspectScorePoint latest = new AspectScorePoint
        (AspectValue.newAspectValue(aspectType, latestTime), alpha + lateScore);

    points.addElement(badest);
    points.addElement(earliest);
    points.addElement(best);
    points.addElement(first_late);
    points.addElement(latest);

    ScoringFunction score = ScoringFunction.createPiecewiseLinearScoringFunction(points.elements());

    //ScoringFunction score = ScoringFunction.createStrictlyAtValue(AspectValue.newAspectValue(aspectType, bestTime));

    return getPlanningFactory().newPreference(aspectType, score);
  }

}


