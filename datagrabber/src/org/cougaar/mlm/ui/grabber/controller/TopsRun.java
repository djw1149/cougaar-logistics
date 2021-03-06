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
package org.cougaar.mlm.ui.grabber.controller;

import org.cougaar.mlm.ui.grabber.logger.Logger;
import org.cougaar.mlm.ui.grabber.workqueue.ResultHandler;
import org.cougaar.mlm.ui.grabber.workqueue.Result;
import org.cougaar.mlm.ui.grabber.workqueue.ResultQueue;
import org.cougaar.mlm.ui.grabber.workqueue.WorkQueue;
import org.cougaar.mlm.ui.grabber.config.DataGrabberConfig;
import org.cougaar.mlm.ui.grabber.config.DataGathererPSPConfig;
import org.cougaar.mlm.ui.grabber.config.HierarchyPSPConfig;
import org.cougaar.mlm.ui.grabber.connect.HierarchyPrepareDBTables;
import org.cougaar.mlm.ui.grabber.connect.HierarchyConnection;
import org.cougaar.mlm.ui.grabber.connect.HierarchyPostPass;
import org.cougaar.mlm.ui.grabber.connect.DGPSPConstants;
import org.cougaar.mlm.ui.grabber.connect.DGPSPConnection;
import org.cougaar.mlm.ui.grabber.connect.DGPSPPrepareDBTables;
import org.cougaar.mlm.ui.grabber.connect.DGPSPRegistrationConnection;
import org.cougaar.mlm.ui.grabber.connect.DGPSPLegConnection;
import org.cougaar.mlm.ui.grabber.connect.DGPSPInstanceConnection;
import org.cougaar.mlm.ui.grabber.connect.DGPSPPrototypeConnection;
import org.cougaar.mlm.ui.grabber.connect.DGPSPPopulationConnection;
import org.cougaar.mlm.ui.grabber.connect.DGPSPLocationConnection;
import org.cougaar.mlm.ui.grabber.connect.DGPSPRouteConnection;
import org.cougaar.mlm.ui.grabber.connect.DGPSPConvoyConnection;
import org.cougaar.mlm.ui.grabber.connect.DGPSPUnregistrationConnection;
import org.cougaar.mlm.ui.grabber.connect.DGPSPPostPass;
import org.cougaar.mlm.ui.grabber.derived.PrepareDerivedTables;

import java.sql.Connection;
import java.sql.Statement;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Represents a single gatherer run
 *
 * @since 2/12/01
 **/
public class TopsRun extends Run{

  //Constants:
  ////////////

  public static final String DESC_PREP_HIERARCHY="Prepare Hierarchy";
  public static final String DESC_PREP_DGPSP="Prepare DataGatherer PSP";

  public static final String DESC_TOPS_HIERARCHY="Tops Hierarchy";
  public static final String DESC_DEMAND_HIERARCHY="Demand Hierarchy";

  public static final String DESC_HIERARCHY_POST_PASS="Process Hierarchy";
  public static final String DESC_DGPSP_POST_PASS="Process DGPSP";

  public static final String DESC_PREPARE_DERIVED_TABLES="Derived Tables";

  //Variables:
  ////////////

  protected WorkGroup workGroup; //should this be promoted to Run?
  protected Set clusters=new HashSet();//Set of strings of all clusters in tops

  protected Map clusterToDGPSPConfig;//Maps a cluster to its config

  private Set hConfigs;
  Set expectedLegs, expectedInstances, expectedPopulations;

  //  boolean doInstancesWithLegsAndPopulation = true;

  //Constructors:
  ///////////////

  public TopsRun(){
    clusterToDGPSPConfig=new HashMap(31);
  }

  //Members:
  //////////

  //From Run:

  /**
   * Begin the run.  This will usually be enqueing the 
   * first piece(s) of work
   **/
  public boolean start(){
    setStatus("Starting");
    workGroup = new WorkGroup(workQ,resultQ);
    prepDB();
    return true;
  }

  protected synchronized void haltAllInWorkQ(){
    logMessage(Logger.IMPORTANT,Logger.GENERIC,
	       "Halting "+workGroup.size()+" piece(s) of work");
    int haltNum=workGroup.haltAllInGroup();
    logMessage(Logger.IMPORTANT,Logger.GENERIC,
	       "Halted "+haltNum+" piece(s) of work");
  }

  /**
   * call back to process results waiting in the resultQ
   * returns true iff something was done.
   **/
  public synchronized boolean processResults(){
    switch(getEpoch()){
    case EPOCH_INITIALIZING:
    case EPOCH_PREP_DB:
      return processResultsPrepDB();
    case EPOCH_OBTAIN_HIERARCHY:
      return processResultsObtainHierarchy();
    case EPOCH_HIERARCHY_POST_PASS:
      return processResultsHierarchyPostPass();
    case EPOCH_INIT_SESSIONS:
      return processResultsInitSessions();
    case EPOCH_OBTAIN_LEGS:
    case EPOCH_OBTAIN_LEGS_INSTANCES_POPULATIONS:
      return processResultsObtainLegs();
      //    case EPOCH_OBTAIN_INSTANCES:
      //      return processResultsObtainInstances();
    case EPOCH_OBTAIN_PROTOTYPES:
      return processResultsObtainPrototypes();
      //    case EPOCH_OBTAIN_POPULATIONS:
      //      return processResultsObtainPopulations();
    case EPOCH_OBTAIN_LOCATIONS:
      return processResultsObtainLocations();
    case EPOCH_OBTAIN_ROUTES:
      return processResultsObtainRoutes();
    case EPOCH_OBTAIN_CONVOYS:
      return processResultsObtainConvoys();
    case EPOCH_CLOSE_SESSIONS:
      return processResultsCloseSessions();
    case EPOCH_DGPSP_POST_PASS:
      return processResultsDGPSPPostPass();
    case EPOCH_PREPARE_DERIVED_TABLES:
      return processResultsPrepareDerivedTables();
    case EPOCH_COMPLETED:
    default:
      logMessage(Logger.ERROR,Logger.GENERIC,
		 "Unexpected epoch while processing results");
    }
    return false;
  }

  //Epochs:
  //-------

  //PREP_DB:

  protected void prepDB(){
    setEpoch(EPOCH_PREP_DB);
    workGroup.add(startPrepareHierarchy(),
		  DESC_PREP_HIERARCHY);
    workGroup.add(startPrepareDGPSP(),
		  DESC_PREP_DGPSP);
  }

  protected boolean processResultsPrepDB(){
    boolean ret=false;
    Result result=workGroup.getResult();
    while(result!=null){
      String desc=workGroup.getDesc(result.getID());
      workGroup.remove(result.getID());
      if(!errorFailure(result)){
	logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		   "Done '"+desc+"'");
	ret=true;
      }else 
	return false;
      result=workGroup.getResult();
    }
    if(workGroup.isEmpty())
      obtainHierarchy();
    return ret;
  }
  
  //OBTAIN_HIERARCHY:

  protected void obtainHierarchy(){
    setEpoch(EPOCH_OBTAIN_HIERARCHY);
    hConfigs=new HashSet();
    HierarchyPSPConfig hConfig;
    hConfig=dgConfig.getHierarchyConfig(HierarchyConnection.SOC_TOPS);
    hConfigs.add(hConfig);
    Iterator iter=hConfig.getClusterIterator();
    while(iter.hasNext()){
      String cluster=(String)iter.next();
      workGroup.add(startHierarchy(new HierarchyPSPConfig(hConfig,cluster)),
		    DESC_TOPS_HIERARCHY);
    }
    hConfig=dgConfig.getHierarchyConfig(HierarchyConnection.SOC_DEMAND);
    hConfigs.add(hConfig);
    iter=hConfig.getClusterIterator();
    while(iter.hasNext()){
      String cluster=(String)iter.next();
      workGroup.add(startHierarchy(new HierarchyPSPConfig(hConfig,cluster)),
		    DESC_DEMAND_HIERARCHY+":"+cluster);
    }
  }

  protected boolean processResultsObtainHierarchy(){
    boolean ret=false;
    Result result=workGroup.getResult();
    while(result!=null){
      String desc=workGroup.getDesc(result.getID());

      if (isMinorEnabled()) {
	logMessage(Logger.MINOR,Logger.STATE_CHANGE,
		   "Removing work "+desc);
      }

      workGroup.remove(result.getID());

      if(errorFailure(result)) // error - punt!
	return false;

      HierarchyConnection.HierarchyResult hierarchyResult = 
	(HierarchyConnection.HierarchyResult) result;
      if (hierarchyResult.didTimeOut ()) {
	logMessage(Logger.WARNING,Logger.STATE_CHANGE,
		   "Query Timed Out : "+desc);
      }
      else {
	if(desc.startsWith(DESC_TOPS_HIERARCHY)) {
	  processTransportationHierarchy (hierarchyResult);
	} else {
	  processDemandHierarchy (hierarchyResult);
	}
	if (isNormalEnabled()) {
	  logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		     "Obtained portion of "+desc);
	}
      }
      ret=true;
      result=workGroup.getResult();
    }
    if(workGroup.isEmpty()){ 
      // we're done with this step, go to next step
      hierarchyPostPass();
    }
    else {
      if (isMinorEnabled()) {
	String detailedPendingWork = 
	  (workGroup.size () > 20) ? 
	  "- more than 20 items so not reporting each one..." :
	  " : " + workGroup.reportPendingWork ();

	logMessage(Logger.MINOR,Logger.STATE_CHANGE,
		   "Work queue has " + workGroup.size () + 
		   " work items left " + detailedPendingWork);
      }
    }
    return ret;
  }

  /** recurse on subordinates, if any */
  protected void processTransportationHierarchy (HierarchyConnection.HierarchyResult result) {
    addClusterNames(result);

    HierarchyPSPConfig hConfig=
      dgConfig.getHierarchyConfig(HierarchyConnection.SOC_TOPS);
	  
    if (hConfig.sourceIsFile ()) {
      clusters.addAll(result.getClusterNameSet());
    } else {
      for (Iterator iter=result.getClusterNameSet ().iterator();
	   iter.hasNext ();) {
	String clusterName = (String) iter.next();
	workGroup.add(startHierarchy(new HierarchyPSPConfig(hConfig,clusterName)),
		      DESC_TOPS_HIERARCHY + " : " + clusterName);
      }
    }
  }

  /** recurse on subordinates, if any */
  protected void processDemandHierarchy (HierarchyConnection.HierarchyResult result) {
    HierarchyPSPConfig hConfig=
      dgConfig.getHierarchyConfig(HierarchyConnection.SOC_DEMAND);
	  
    if (!hConfig.sourceIsFile ()) {
      for (Iterator iter=result.getClusterNameSet ().iterator();
	   iter.hasNext ();) {
	String clusterName = (String) iter.next();
	workGroup.add(startHierarchy(new HierarchyPSPConfig(hConfig,clusterName)),
		      DESC_DEMAND_HIERARCHY + " : " + clusterName);
      }
    }
  }

  //HIERARCHY_POST_PASS

  protected void hierarchyPostPass(){
    setEpoch(EPOCH_HIERARCHY_POST_PASS);
    workGroup.add(startHierarchyPostPass(),
		  DESC_HIERARCHY_POST_PASS);
  }

  protected boolean processResultsHierarchyPostPass(){
    boolean ret=false;
    Result result=workGroup.getResult();
    while(result!=null){
      String desc=workGroup.getDesc(result.getID());

      if (isMinorEnabled()) {
	logMessage(Logger.MINOR,Logger.STATE_CHANGE, "Removing work "+desc);
      }

      workGroup.remove(result.getID());
      if(!errorFailure(result)){
	if (isNormalEnabled()) {
	  logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		     "Post-processing of hierarchy completed");
	}
	ret=true;
      }else
	return false;
      result=workGroup.getResult();
    }
    if(workGroup.isEmpty())
      initSessions();
    else {
      if (isMinorEnabled()) {
	logMessage(Logger.MINOR,Logger.STATE_CHANGE,
		   "Work queue has " + workGroup.size () + " work items left : " +
		   workGroup.reportPendingWork ());
      }
    }
    return ret;
  }
  
  //INIT_SESSIONS:

  protected void initSessions(){
    setEpoch(EPOCH_INIT_SESSIONS);
    Iterator iter=clusters.iterator();
    while(iter.hasNext()){
      String cluster=(String)iter.next();
      workGroup.add(startDGPSPRegistration(cluster),
		    cluster);
    }
  }

  protected boolean processResultsInitSessions(){
    boolean ret=false;
    Result result=workGroup.getResult();
    while(result!=null){
      String desc=workGroup.getDesc(result.getID());
      workGroup.remove(result.getID());
      if(!warningFailure(result)){
	DGPSPRegistrationConnection.RegistrationRunResult r=
	  (DGPSPRegistrationConnection.RegistrationRunResult)result;
	setSessionID(desc,r.getSessionID());
	if (isNormalEnabled()) {
	  logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		     "Registration with "+desc+" resulted in session id: "+
		     r.getSessionID());
	}
	ret=true;
      }
      result=workGroup.getResult();
    }
    if(workGroup.isEmpty())
      obtainLegs();
    else {
      if (isMinorEnabled()) {
	logMessage(Logger.MINOR,Logger.STATE_CHANGE,
		   "InitSessions - Work queue has " + workGroup.size () + 
		   " work items left : " +
		   workGroup.reportPendingWork ());
      }
    }
    return ret;
  }

  //OBTAIN_LEGS:
  protected void obtainLegs(){
    setAutoCommitFalse ();
    //    if (doInstancesWithLegsAndPopulation) {
    setEpoch(EPOCH_OBTAIN_LEGS_INSTANCES_POPULATIONS);
      //    }
      //    else {
    //       setEpoch(EPOCH_OBTAIN_LEGS);
      //    }

    expectedLegs = new HashSet();
    expectedInstances = new HashSet();
    expectedPopulations = new HashSet();

    Iterator iter=clusterToDGPSPConfig.keySet().iterator();
    int alternator = 0;
    while(iter.hasNext()){
      String cluster=(String)iter.next();

      expectedLegs.add (cluster);
      expectedInstances.add (cluster);
      expectedPopulations.add (cluster);

      DataGathererPSPConfig dgc=(DataGathererPSPConfig)
	clusterToDGPSPConfig.get(cluster);

      alternator++;

      if (alternator % 3 == 0) {
	workGroup.add(startDGPSPLegConnection(dgc),
		      cluster);
	expectedLegs.remove (cluster);
      }
      else if (alternator % 3 == 1) {
	workGroup.add(startDGPSPInstanceConnection(dgc),
		      cluster);
	expectedInstances.remove (cluster);
      }
      else {
	workGroup.add(startDGPSPPopulationConnection(dgc),
		      cluster);
	expectedPopulations.remove (cluster);
      }

      //      if (doInstancesWithLegsAndPopulation) {
      /*
	DataGathererPSPConfig dgc2= new DataGathererPSPConfig (dgc);
	workGroup.add(startDGPSPInstanceConnection(dgc2),
		      cluster);

	DataGathererPSPConfig dgc3= new DataGathererPSPConfig (dgc);
	workGroup.add(startDGPSPPopulationConnection(dgc3),
		      cluster);
      */
	//      }
    }
  }

  /** 
   * We do one after another - avoid database table contention
   * by doing a leg query on agent 1, an instance query on agent 2,
   * and a population query on agent 3, etc. simultaneously.  
   *
   * After getting a leg query, we do an instance, after an instance,
   * we do a population, after a population we do a leg.
   *
   * In this way we act to guarantee that all agents are busy
   * on some query, but the database is processing multiple queries
   * from different tables at the same time.  The leg query writes
   * to the leg table, etc.
   *
   * Before, when we did all instances at once, all queries would all 
   * contend for the lock on the same table.
   */
  protected boolean processResultsObtainLegs(){
    boolean ret=false;
    Result result=workGroup.getResult();
    while(result!=null){
      String desc=workGroup.getDesc(result.getID());

      if (isMinorEnabled()) {
	logMessage(Logger.MINOR,Logger.STATE_CHANGE,
		   "Removing work "+desc);
      }

      workGroup.remove(result.getID());

      if(errorFailure(result)) // error - punt!
	return false;

      if (isMinorEnabled()) {
	logMessage(Logger.MINOR,Logger.STATE_CHANGE,
		   "Obtained result of "+desc + " result is " + result);
      }

      if (result instanceof SuccessRunResult) {
	String cluster = ((SuccessRunResult) result).getCluster();
	DataGathererPSPConfig dgc=(DataGathererPSPConfig)
	  clusterToDGPSPConfig.get(cluster);
	String creator = ((SuccessRunResult) result).getCreator();
	    
	// Leg -> Instance -> Pop -> Leg
	if (creator.startsWith ("DGPSPLeg")) {
	  if (isNormalEnabled()) {
	    logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		       "Got leg, making instance for " + cluster);
	  }

	  if (expectedInstances.contains(cluster)) {
	    workGroup.add(startDGPSPInstanceConnection(dgc), cluster);
	    expectedInstances.remove (cluster);
	  }

	  if (expectedInstances.isEmpty()) {
	    if (isNormalEnabled()) {
	      logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
			 "All instances created.");
	    }
	  }
	}
	else if (creator.startsWith ("DGPSPInstance")) {
	  if (isNormalEnabled()) {
	    logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		       "Got instance, making pop for " + cluster);
	  }

	  if (expectedPopulations.contains(cluster)) {
	    workGroup.add(startDGPSPPopulationConnection(dgc), cluster);
	    expectedPopulations.remove (cluster);
	  }

	  if (expectedPopulations.isEmpty()) {
	    if (isNormalEnabled()) {
	      logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
			 "All pops created.");
	    }
	  }
	}
	else if (creator.startsWith ("DGPSPPopulation")) {
	  if (isNormalEnabled()) {
	    logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		       "Got pop, making leg for " + cluster);
	  }

	  if (expectedLegs.contains(cluster)) {
	    workGroup.add(startDGPSPLegConnection(dgc),
			  cluster);
	    expectedLegs.remove (cluster);
	  }

	  if (expectedLegs.isEmpty()) {
	    if (isNormalEnabled()) {
	      logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
			 "All legs created.");
	    }
	  }
	}
      }
	
      ret=true;
      result=workGroup.getResult();
    }

    if(workGroup.isEmpty()) {
      setAutoCommitTrue (); // commit leg batches
      //      if (doInstancesWithLegsAndPopulation) {
      obtainPrototypes ();
      //      }
      //      else {
      //		obtainInstances();
      //      }
    }
    return ret;
  }

  //OBTAIN_INSTANCES:

  /*
  protected void obtainInstances(){
    setAutoCommitFalse();
    setEpoch(EPOCH_OBTAIN_INSTANCES);
    Iterator iter=clusterToDGPSPConfig.keySet().iterator();
    while(iter.hasNext()){
      String cluster=(String)iter.next();
      DataGathererPSPConfig dgc=(DataGathererPSPConfig)
	clusterToDGPSPConfig.get(cluster);
      workGroup.add(startDGPSPInstanceConnection(dgc),
		    cluster);
    }
  }
  */

  /** after getting instances we get prototypes */
  protected boolean processResultsObtainInstances(){
    //Later we may want to process the container information by setting up
    //to query another PSP based on the manifestUIDs returned by
    //The InstanceRunResult object that we get back here.  But for now we'll
    //just drop this info on the floor.
    boolean ret=genericWarningProcessResults("Obtained instance information");
    if(workGroup.isEmpty()) {
      setAutoCommitTrue (); // commit instance batches
      obtainPrototypes();
    }
    return ret;
  }

  protected void setAutoCommitFalse () {
    try { 
      if (isTrivialEnabled()) {
	logMessage(Logger.TRIVIAL,Logger.GENERIC, Thread.currentThread () + " - doing setAutoCommit (false)");
      }

      for (Iterator iter = getAllDBConnections ().iterator(); iter.hasNext(); )
	((Connection) iter.next()).setAutoCommit(false); 

      if (isTrivialEnabled()) {
	logMessage(Logger.TRIVIAL,Logger.GENERIC, Thread.currentThread () + " - did   setAutoCommit (false)");
      }
    } 
    catch (Exception e) { 
      logMessage(Logger.ERROR,Logger.GENERIC, "Got SQL error doing setAutoCommit (false) - " + e);
    }
  }

  protected void setAutoCommitTrue () {
    try { 
      if (isTrivialEnabled()) {
	logMessage(Logger.TRIVIAL,Logger.GENERIC, Thread.currentThread () + " - doing commit.");
      }

      for (Iterator iter = getAllDBConnections ().iterator(); iter.hasNext(); )
	((Connection) iter.next()).commit(); 

      if (isMinorEnabled()) {
	logMessage(Logger.MINOR,Logger.GENERIC, Thread.currentThread () + " - did   commit.");
      }

      if (isTrivialEnabled()) {
	logMessage(Logger.TRIVIAL,Logger.GENERIC, Thread.currentThread () + " - doing setAutoCommit (true).");
      }

      for (Iterator iter = getAllDBConnections ().iterator(); iter.hasNext(); )
	((Connection) iter.next()).setAutoCommit(true); 

      if (isTrivialEnabled()) {
	logMessage(Logger.TRIVIAL,Logger.GENERIC, Thread.currentThread () + " - did   setAutoCommit (true).");
      }
    } 
    catch (Exception e) { 
      logMessage(Logger.ERROR,Logger.GENERIC, "Got SQL error doing commit - " + e);
    }
  }

  //OBTAIN_PROTOTYPES:

  protected void obtainPrototypes(){
    setEpoch(EPOCH_OBTAIN_PROTOTYPES);
    Iterator iter=clusterToDGPSPConfig.keySet().iterator();
    while(iter.hasNext()){
      String cluster=(String)iter.next();
      DataGathererPSPConfig dgc=(DataGathererPSPConfig)
	clusterToDGPSPConfig.get(cluster);
      workGroup.add(startDGPSPPrototypeConnection(dgc),
		    cluster);
    }
  }

  /** After getting prototypes we get locations */
  protected boolean processResultsObtainPrototypes(){
    boolean ret=genericWarningProcessResults("Obtained prototype information");
    if(workGroup.isEmpty()) {
      //      if (doInstancesWithLegsAndPopulation) {
      obtainLocations();
	//      }
	//      else {
      //	obtainPopulations();
	//      }
    }
    return ret;
  }

  //OBTAIN_POPULATIONS:

  /*
  protected void obtainPopulations(){
    setEpoch(EPOCH_OBTAIN_POPULATIONS);
    Iterator iter=clusterToDGPSPConfig.keySet().iterator();
    while(iter.hasNext()){
      String cluster=(String)iter.next();
      DataGathererPSPConfig dgc=(DataGathererPSPConfig)
	clusterToDGPSPConfig.get(cluster);
      workGroup.add(startDGPSPPopulationConnection(dgc),
		    cluster);
    }
  }
  */

  /** After getting populations we get prototypes */
  /*
  protected boolean processResultsObtainPopulations(){
    boolean ret=genericWarningProcessResults
      ("Obtained population information");
    if(workGroup.isEmpty()) {
      //      if (doInstancesWithLegsAndPopulation) {
      //	obtainPrototypes();
      //
      //      }
      //      else {
	obtainLocations();
	//      }
    }
    return ret;
  }
  */

  //OBTAIN_LOCATIONS:

  protected void obtainLocations(){
    setEpoch(EPOCH_OBTAIN_LOCATIONS);
    Iterator iter=clusterToDGPSPConfig.keySet().iterator();
    while(iter.hasNext()){
      String cluster=(String)iter.next();
      DataGathererPSPConfig dgc=(DataGathererPSPConfig)
	clusterToDGPSPConfig.get(cluster);
      workGroup.add(startDGPSPLocationConnection(dgc),
		    cluster);
    }
  }

  protected boolean processResultsObtainLocations(){
    boolean ret=genericWarningProcessResults
      ("Obtained location information");
    if(workGroup.isEmpty())
      obtainRoutes();
    return ret;
  }

  //OBTAIN_ROUTES:

  protected void obtainRoutes(){
    setEpoch(EPOCH_OBTAIN_ROUTES);
    Iterator iter=clusterToDGPSPConfig.keySet().iterator();
    while(iter.hasNext()){
      String cluster=(String)iter.next();
      DataGathererPSPConfig dgc=(DataGathererPSPConfig)
	clusterToDGPSPConfig.get(cluster);
      workGroup.add(startDGPSPRouteConnection(dgc),
		    cluster);
    }
  }

  protected boolean processResultsObtainRoutes(){
    boolean ret=genericWarningProcessResults
      ("Obtained route information");
    if(workGroup.isEmpty())
      obtainConvoys();
    return ret;
  }

  //OBTAIN_CONVOYS:

  protected void obtainConvoys(){
    setEpoch(EPOCH_OBTAIN_CONVOYS);
    Iterator iter=clusterToDGPSPConfig.keySet().iterator();
    while(iter.hasNext()){
      String cluster=(String)iter.next();
      DataGathererPSPConfig dgc=(DataGathererPSPConfig)
	clusterToDGPSPConfig.get(cluster);
      workGroup.add(startDGPSPConvoyConnection(dgc),
		    cluster);
    }
  }

  protected boolean processResultsObtainConvoys(){
    boolean ret=genericWarningProcessResults
      ("Obtained convoy information");
    if(workGroup.isEmpty())
      closeSessions();
    return ret;
  }

  //CLOSE_SESSIONS:

  protected void closeSessions(){
    setEpoch(EPOCH_CLOSE_SESSIONS);
    Iterator iter=clusterToDGPSPConfig.keySet().iterator();
    while(iter.hasNext()){
      String cluster=(String)iter.next();
      DataGathererPSPConfig dgc=(DataGathererPSPConfig)
	clusterToDGPSPConfig.get(cluster);
      workGroup.add(startDGPSPUnregistration(dgc),
		    cluster);
    }
  }

  protected boolean processResultsCloseSessions(){
    boolean ret=genericWarningProcessResults("Unregistration successful");
    if(workGroup.isEmpty())
      DGPSPPostPass();
    return ret;
  }

  //DGPSP_POST_PASS

  protected void DGPSPPostPass(){
    setEpoch(EPOCH_DGPSP_POST_PASS);
    workGroup.add(startDGPSPPostPass(),
		  DESC_DGPSP_POST_PASS);
  }

  protected boolean processResultsDGPSPPostPass(){
    boolean ret=false;
    Result result=workGroup.getResult();
    while(result!=null){
      String desc=workGroup.getDesc(result.getID());
      workGroup.remove(result.getID());
      if(!errorFailure(result)){
	if (isNormalEnabled()) {
	  logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		     "Post-processing of DGPSP completed");
	}

	ret=true;
      }else
	return false;
      result=workGroup.getResult();
    }
    if(workGroup.isEmpty())
      prepareDerivedTables();
    return ret;
  }


  //PREPARE_DERIVED_TABLES:

  protected void prepareDerivedTables(){
    setEpoch(EPOCH_PREPARE_DERIVED_TABLES);
    workGroup.add(startPrepareDerivedTables(),
		  DESC_PREPARE_DERIVED_TABLES);
  }

  protected boolean processResultsPrepareDerivedTables(){
    boolean ret=genericWarningProcessResults
      ("Preparation of Derived Tables completed");
    if(workGroup.isEmpty())
      completed();
    return ret;
  }
  
  //COMPLETED:

  protected void completed(){
    runComplete(COND_COMPLETED);
  }

  //Start individual tasks:
  //-----------------------

  protected int startPrepareHierarchy(){
    int workID=workQ.getValidID();
    setStatus("Starting to prepare hierarchy tables");
    HierarchyPrepareDBTables hpdbt=
      new HierarchyPrepareDBTables(workID,id,getDBConfig(),
				   getDBConnection(),this);
    if (isNormalEnabled()) {
      logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		 "Requesting prepartion of hierarchy tables");
    }

    workQ.enque(hpdbt);
    return workID;
  }

  protected int startPrepareDGPSP(){
    int workID=workQ.getValidID();
    setStatus("Starting to prepare DataGathererPSP tables");
    DGPSPPrepareDBTables dgpdbt=
      new DGPSPPrepareDBTables(workID,id,getDBConfig(),
			       getDBConnection(),this);
    if (isNormalEnabled()) {
      logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		 "Requesting prepartion of DataGathererPSP tables");
    }

    workQ.enque(dgpdbt);
    return workID;
  }

  protected int startHierarchy(HierarchyPSPConfig hConfig){
    String societyStr=HierarchyConnection.SOCIETIES[hConfig.getSociety()];
    String cluster=hConfig.getUrlConnection().getClusterName();
    setStatus("Starting "+societyStr+"."+cluster+" hierarchy request");
    int workID=workQ.getValidID();
    HierarchyConnection hc = 
      new HierarchyConnection(workID,id,hConfig,
			      dgConfig.getDBConfig(),
			      getDBConnection(),
			      this);
    if (isNormalEnabled()) {
      logMessage(Logger.NORMAL,Logger.STATE_CHANGE,"Requesting "+
		 societyStr+"."+cluster+" Hierarchy");
    }

    workQ.enque(hc);
    return workID;
  }

  protected int startHierarchyPostPass(){
    setStatus("Starting hierarchy post-processing");
    int workID=workQ.getValidID();
    HierarchyPostPass hpp = 
      new HierarchyPostPass(workID,id,
			    hConfigs,
			    dgConfig.getDBConfig(),
			    getDBConnection(),
			    this);
    if (isNormalEnabled()) {
      logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		 "Requesting hierarchy post-processing");
    }

    workQ.enque(hpp);
    return workID;
  }

  protected int startDGPSPRegistration(String cluster){
    int workID=workQ.getValidID();
    setStatus("Starting DGPSPRegistration for: "+cluster);
    DataGathererPSPConfig base = dgConfig.getDGPSPConfig();
    DataGathererPSPConfig dgc = 
      new DataGathererPSPConfig(base.getUrlConnection(),cluster, base.includeTransitLegs());
    dgc.setQuery(DGPSPConstants.QUERY_REGISTRATION);
    clusterToDGPSPConfig.put(cluster,dgc);

    DGPSPRegistrationConnection reg=
      new DGPSPRegistrationConnection(workID,id, dgc,
				      getDBConfig(),
				      getDBConnection(),this);
    if (isNormalEnabled()) {
      logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		 "Requesting DGPSPRegistration for: "+cluster);
    }
    workQ.enque(reg);
    return workID;
  }

  protected int startDGPSPUnregistration(DataGathererPSPConfig dgc){
    int workID=workQ.getValidID();
    genericStartDGPSPConnection(dgc, DGPSPConstants.QUERY_UNREGISTRATION, new
      DGPSPUnregistrationConnection(workID, id, dgc, getDBConfig(),
				    getDBConnection(), this));
    return workID;
  }

  protected int startDGPSPLegConnection(DataGathererPSPConfig dgc){
    int workID=workQ.getValidID();
    genericStartDGPSPConnection(dgc, DGPSPConstants.QUERY_LEG, new
      DGPSPLegConnection(workID, id, dgc, getDBConfig(),
			 getDBConnection(), this));
    return workID;
  }

  protected int startDGPSPInstanceConnection(DataGathererPSPConfig dgc){
    int workID=workQ.getValidID();
    genericStartDGPSPConnection(dgc, DGPSPConstants.QUERY_INSTANCE, new
      DGPSPInstanceConnection(workID, id, dgc, getDBConfig(),
			      getDBConnection(), this));
    return workID;
  }


  protected int startDGPSPPrototypeConnection(DataGathererPSPConfig dgc){
    int workID=workQ.getValidID();
    genericStartDGPSPConnection(dgc, DGPSPConstants.QUERY_PROTOTYPE, new
      DGPSPPrototypeConnection(workID, id, dgc, getDBConfig(),
			      getDBConnection(), this));
    return workID;
  }

  protected int startDGPSPPopulationConnection(DataGathererPSPConfig dgc){
    int workID=workQ.getValidID();
    genericStartDGPSPConnection(dgc, DGPSPConstants.QUERY_POPULATION, new
      DGPSPPopulationConnection(workID, id, dgc, getDBConfig(),
			      getDBConnection(), this));
    return workID;
  }

  protected int startDGPSPLocationConnection(DataGathererPSPConfig dgc){
    int workID=workQ.getValidID();
    genericStartDGPSPConnection(dgc, DGPSPConstants.QUERY_LOCATION, new
      DGPSPLocationConnection(workID, id, dgc, getDBConfig(),
			      getDBConnection(), this));
    return workID;
  }

  protected int startDGPSPRouteConnection(DataGathererPSPConfig dgc){
    int workID=workQ.getValidID();
    genericStartDGPSPConnection(dgc, DGPSPConstants.QUERY_ROUTES, new
      DGPSPRouteConnection(workID, id, dgc, getDBConfig(),
			   getDBConnection(), this));
    return workID;
  }

  protected int startDGPSPConvoyConnection(DataGathererPSPConfig dgc){
    int workID=workQ.getValidID();
    genericStartDGPSPConnection(dgc, DGPSPConstants.QUERY_CONVOYS, new
      DGPSPConvoyConnection(workID, id, dgc, getDBConfig(),
			    getDBConnection(), this));
    return workID;
  }

  protected int startDGPSPPostPass(){
    int workID=workQ.getValidID();
    setStatus("Starting DataGathererPSP post pass");
    DGPSPPostPass dgpp=
      new DGPSPPostPass(workID,id,getDBConfig(),
			getDBConnection(),this);
    if (isNormalEnabled()) {
      logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		 "Requesting DataGathererPSP post pass");
    }
    workQ.enque(dgpp);
    return workID;
  }

  protected int startPrepareDerivedTables(){
    int workId=workQ.getValidID();
    setStatus("starting to Prepare Derived Tables");
    PrepareDerivedTables pdt=
      new PrepareDerivedTables(workId, id, getDBConfig(),
			       // getDBConnection(),
			       getAllDBConnections(),
			       getDGConfig().getDerivedTablesConfig(),
			       this);
    if (isNormalEnabled()) {
      logMessage(Logger.NORMAL, Logger.STATE_CHANGE,
		 "Requesting PrepareDerivedTables");
    }
    workQ.enque(pdt);
    return workId;
  }

  //Helpers:
  //--------

  protected void addClusterNames(HierarchyConnection.HierarchyResult hr){
    // clusters.addAll(hr.getClusterNameSet());
    clusters.add(hr.getParent());
    Iterator iter=hr.getClusterNameSet().iterator();
    StringBuffer sb=new StringBuffer();
    sb.append("Tops Hierarchy found clusters: {");
    while(iter.hasNext()){
      sb.append(iter.next());
      sb.append((iter.hasNext() ? ", " : ""));
    }
    sb.append("}");
    if (isNormalEnabled()) {
      logMessage(Logger.NORMAL,Logger.RESULT,sb.toString());
    }
  }

  protected void setSessionID(String cluster, String sessionID){
    DataGathererPSPConfig dgc=(DataGathererPSPConfig)
      clusterToDGPSPConfig.get(cluster);
    if(dgc==null){
      logMessage(Logger.WARNING,Logger.RESULT,
		 "Could not find cluster's DGPSPConfig: "+cluster);
      return;
    }
    dgc.setSessionID(sessionID);
  }

  //Generic calls that EPOCH handlers and starters can call:

  protected boolean genericWarningProcessResults(String description){
    boolean ret=false;
    Result result=workGroup.getResult();
    while(result!=null){
      String desc=workGroup.getDesc(result.getID());
      workGroup.remove(result.getID());
      if(!warningFailure(result)){
	if (isNormalEnabled()) {
	  logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		     description+": "+desc);
	}
	ret=true;
      }
      result=workGroup.getResult();
    }
    return ret;
  }

  protected void genericStartDGPSPConnection(DataGathererPSPConfig dgc,
					     String query,
					     DGPSPConnection dgpspc){
    String cluster=dgc.getUrlConnection().getClusterName();
    String name=getNameForObjectClass(dgpspc);
    setStatus("Starting "+name+" for: "+cluster);
    dgc.setQuery(query);
    if (isNormalEnabled()) {
      logMessage(Logger.NORMAL,Logger.STATE_CHANGE,
		 "Requesting "+name+" for: "+cluster);
    }
    workQ.enque(dgpspc);
  }


  //Statics Members:
  //////////////////

  protected static String getNameForObjectClass(Object o){
    String name = o.getClass().getName();
    int loc = name.lastIndexOf('.');
    if(loc==-1)
      return name;
    return name.substring(loc+1);
  }

  //InnerClasses:
  ///////////////
}
