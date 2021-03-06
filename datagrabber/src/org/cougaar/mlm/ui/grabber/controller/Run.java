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
import org.cougaar.mlm.ui.grabber.logger.IDLogger;
import org.cougaar.mlm.ui.grabber.workqueue.ResultHandler;
import org.cougaar.mlm.ui.grabber.workqueue.Result;
import org.cougaar.mlm.ui.grabber.workqueue.ResultQueue;
import org.cougaar.mlm.ui.grabber.workqueue.WorkQueue;
import org.cougaar.mlm.ui.grabber.config.DataGrabberConfig;
import org.cougaar.mlm.ui.grabber.config.DBConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single gatherer run
 *
 * @since 2/12/01
 **/
public abstract class Run implements ResultHandler, Logger, DBConnectionProvider{

  //Constants:
  ////////////

  //Epochs (pieces of the the STARTED Condition)

  public static final int EPOCH_INITIALIZING = 0;
  public static final int EPOCH_PREP_DB = 1;
  public static final int EPOCH_OBTAIN_HIERARCHY = 2;
  public static final int EPOCH_HIERARCHY_POST_PASS = 3;
  public static final int EPOCH_INIT_SESSIONS = 4;
  public static final int EPOCH_OBTAIN_LEGS = 5;
  public static final int EPOCH_OBTAIN_INSTANCES = 6;
  public static final int EPOCH_OBTAIN_PROTOTYPES = 7;
  public static final int EPOCH_OBTAIN_LOCATIONS = 8;
  public static final int EPOCH_OBTAIN_POPULATIONS = 9;
  public static final int EPOCH_OBTAIN_ROUTES = 10;
  public static final int EPOCH_OBTAIN_CONVOYS = 11;
  public static final int EPOCH_CLOSE_SESSIONS = 12;
  public static final int EPOCH_DGPSP_POST_PASS = 13;
  public static final int EPOCH_PREPARE_DERIVED_TABLES = 14;
  public static final int EPOCH_COMPLETED = 15;
  public static final int EPOCH_OBTAIN_LEGS_INSTANCES_POPULATIONS = 16;

  public static final String[] EPOCHS={"Initializing",
				       "Preparing DB",
				       "Getting Hierarchy",
				       "Processing Hierarchy",
				       "Init Sessions",
				       "Getting Legs",
				       "Getting Instances",
				       "Getting Prototypes",
				       "Getting Locations",
				       "Getting Populations",
				       "Getting Routes",
				       "Getting Convoys",
				       "Close Sessions",
				       "Processing DGPSP",
				       "Prepare Derived Tables",
				       "Completed",
				       "Getting Legs, Instances, and Populations"};

  //Conditions:

  public static final int COND_STARTED=0;
  public static final int COND_COMPLETED=1;
  public static final int COND_WARNING=2;
  public static final int COND_HALTED=3;
  public static final int COND_ERROR=4;
  public static final int COND_TIMEDOUT=5;//unused for now

  public static final String[] CONDITIONS={"Started",
					   "Completed",
					   "Warning",
					   "Halted",
					   "Error",
					   "Timed out"};
  
  //Variables:
  ////////////

  protected int id;
  protected Controller controller;
  protected WorkQueue workQ;
  protected DataGrabberConfig dgConfig;

  private String status="Initialized";
  protected ResultQueue resultQ;

  protected Connection dbConnection;
  protected DBConnectionProvider connectionProvider;
  private IDLogger logger;
  private int epoch=EPOCH_INITIALIZING;
  /**This is temporary, condition only becomes warning upon completion**/
  private int condition=COND_STARTED;
  private boolean warning=false;

  //Constructors:
  ///////////////

  public Run(){
    resultQ=new ResultQueue(this);
  }

  //Members:
  //////////


  //From ResultHandler:
  public void handleResult(Result r){
    resultQ.handleResult(r);
  }

  //Gets:

  public DataGrabberConfig getDGConfig(){
    return dgConfig;
  }

  public DBConfig getDBConfig(){
    return dgConfig.getDBConfig();
  }

  public int getID(){
    return id;
  }

  /** 
   * DBConnectionProvider interface methods - delegates to connection provider
   */
  public Connection getDBConnection(){ return connectionProvider.getDBConnection(); }
  public Statement  createStatement() throws SQLException { 
    return connectionProvider.getDBConnection().createStatement(); 
  }
  public int  getNumDBConnections () { return connectionProvider.getNumDBConnections(); }
  public List getAllDBConnections () { return connectionProvider.getAllDBConnections(); }

  public int getEpoch(){
    return epoch;
  }

  public int getCondition(){
    return condition;
  }

  //Sets:

  private void setCondition(int condition){
    this.condition=condition;
  }

  protected void setEpoch(int epoch){
    logMessage(Logger.IMPORTANT,Logger.STATE_CHANGE,
	       "Epoch shift to: '"+EPOCHS[epoch]+"'");
    this.epoch=epoch;
  }
  
  protected void setStatus(String s){
    status=s;
  }

  /**This is temporary, condition only becomes warning upon completion**/
  protected void setWarning(boolean w){
    warning=w;
  }

  public void setID(int id){
    this.id=id;
  }

  public void setController(Controller c){
    this.controller=c;
    resultQ.setNotifyObject(c);
  }

  public void setWorkQueue(WorkQueue workQ){
    this.workQ=workQ;
  }

  public void setDGConfig(DataGrabberConfig dgConfig){
    this.dgConfig=dgConfig;
  }

  public void setDBConnectionProvider (DBConnectionProvider connectionProvider) {
    this.connectionProvider = connectionProvider;
  }

  public void setLogger(IDLogger l){
    this.logger=l;
  }

  //Actions:

  protected abstract void haltAllInWorkQ();

  public boolean halt(){
    haltAllInWorkQ();
    runComplete(COND_HALTED);
    return true;
  }

  public String getStatus(){
    return EPOCHS[epoch]+": "+status;
  }

  public boolean hasResult(){
    return resultQ.hasResult();
  }

  /**
   * Begin the run.  This will usually be enqueing the 
   * first piece(s) of work
   **/
  public abstract boolean start();

  /**
   * call back to process results waiting in the resultQ
   **/
  public abstract boolean processResults();

  //Internals:

  /**
   * useful when writing result handler to appropriately handle a
   * result that you know is an error if it is a Failure, regardless of the
   * error bit in the result itself
   **/
  protected boolean errorFailure(Result result){
    if(result instanceof FailureRunResult){
      logMessage(Logger.ERROR,Logger.STATE_CHANGE,
		 EPOCHS[epoch]+": Critical result was a failure aborting run");
      runComplete(COND_ERROR);
      return true;
    }
    return false;
  }

  /**
   * useful when writing result handler to appropriately handle a given
   * result that you know is a warning if it is a Failure, regardless of the
   * error bit in the result itself
   **/
  protected boolean warningFailure(Result result){
    if(result instanceof FailureRunResult){
      logMessage(Logger.WARNING,Logger.STATE_CHANGE,
		 EPOCHS[epoch]+": Result was a failure");
      setWarning(true);
      return true;
    }
    return false;
  }
  
  /**
   * call this when the run is complete, giving status from constants
   * in Controller.java
   **/
  protected void runComplete(int cond){
    if(cond==COND_COMPLETED&&warning)
      cond=COND_WARNING;
    setCondition(cond);
    setEpoch(EPOCH_COMPLETED);
    setStatus("");
    logMessage(Logger.IMPORTANT,Logger.STATE_CHANGE,
	       "Run completed with condition: "+
	       CONDITIONS[condition]);
    controller.runCompleted(this,condition);
  }

  //From Logger:

  public void logMessage(int severity, int type, String message){
    if(severity<=Logger.WARNING)
      warning=true;
    logger.logMessage(id,severity,type,message);
  }

  public void logMessage(int severity, int type, String message, Exception e){
    if(severity<=Logger.WARNING)
      warning=true;
    logger.logMessage(id,severity,type,message,e);
  }

  public boolean isWarningEnabled   () { return logger.isWarningEnabled(); }
  public boolean isImportantEnabled () { return logger.isImportantEnabled(); }
  public boolean isNormalEnabled    () { return logger.isNormalEnabled(); }
  public boolean isMinorEnabled     () { return logger.isMinorEnabled(); }
  public boolean isTrivialEnabled   () { return logger.isTrivialEnabled(); }

  //InnerClasses:
  ///////////////
}
