/*
 * <copyright>
 *  Copyright 2001 BBNT Solutions, LLC
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
package org.cougaar.mlm.ui.grabber.controller;

import org.cougaar.mlm.ui.grabber.logger.*;
import org.cougaar.mlm.ui.grabber.workqueue.*;
import org.cougaar.mlm.ui.grabber.config.*;
import org.cougaar.mlm.ui.grabber.connect.*;
import org.cougaar.mlm.ui.grabber.config.*;

import java.util.Set;
import java.util.Iterator;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.ConcurrentModificationException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

//For debugging config info...
//import org.cougaar.planning.servlet.data.xml.*;
//import java.io.*;
//try{
//  XMLWriter w=new XMLWriter(new OutputStreamWriter(System.out));
//  dgConfig.get***().toXML(w);
//  w.flush();
//}catch(Exception e){}

/**
 * Controls the data grabber and orchestrates its workflow
 * @author Benjamin Lubin; last modified by: $Author: gvidaver $
 *
 * @since 2/01/01
 **/
public class Controller extends Thread implements ResultHandler{

  //Constants:
  ////////////

  //Whenever possible, don't use this static--use the member function!!!!
  public static final String RUN_TABLE_NAME="runtable";

  public static final String COL_RUNID="runid";
  public static final String COL_STARTTIME="starttime";
  public static final String COL_ENDTIME="endtime";
  public static final String COL_CONDITION="condition";

  //Variables:
  ////////////

  protected IDLogger logger;
  protected WorkQueue workQ;
  protected DataGrabberConfig dgConfig;
  protected Connection dbConnection;
  
  protected Map runMap;

  protected boolean halt=false;
  protected boolean debug = false;
  
  //Constructors:
  ///////////////

  public Controller(IDLogger l, DataGrabberConfig dgConfig){
    logger=l;
    workQ = new TimedWorkQueue(logger, this);
    workQ.setMaxThreads(dgConfig.getControllerMaxThreads());
    this.dgConfig=dgConfig;
    runMap=new HashMap(31);
  }

  //Members:
  //////////

  //From ResultHandler:
  public synchronized void handleResult(Result r){
    RunResult rr=(RunResult)r;
    int resultRunID =rr.getRunID();
    Run run=getRunForID(resultRunID);
    if(run!=null)
      run.handleResult(r);
    else
      logger.logMessage(Logger.ERROR,Logger.STATE_CHANGE,
			"Could not find expected run: "+resultRunID);
  }

  //From thread:

  public void start(){
    if(!initDB())
      return;
    try{
      DBIDLogger dbLogger=new DBIDLogger(dgConfig.getDBConfig(),
					 dgConfig.getVerbosity());
      dbLogger.start(getDBConnection());
      logger=new PairIDLogger(logger,dbLogger);
    }catch(SQLException e){
      logger.logMessage(Logger.ERROR,Logger.DB_CONNECT,
			"Could not create DBLogger",e);
    }
    prepareRunTable();
    super.start();
  }

  public void run(){
    processResults();
  }

  //Initialization"

  protected Connection getNewDBConnection(DBConfig dbConfig){
    Connection c = null;
    try{
      c=DriverManager.getConnection
	(dbConfig.getConnectionURL(),
	 dbConfig.getUser(),
	 dbConfig.getPassword());
    }catch(SQLException e){
	  logger.logMessage(Logger.MINOR,Logger.DB_CONNECT,
						"SQL Error code was " + e.getErrorCode());
	  logger.logMessage(Logger.IMPORTANT,Logger.DB_CONNECT,
						"Could not establish connection, trying to create database.");
	  c=createDatabase (dbConfig);
    }
	if (c != null)
	  logger.logMessage(Logger.IMPORTANT,Logger.DB_CONNECT,
						"Connection established");
    return c;
  }

  protected Connection createDatabase (DBConfig dbConfig) {
    Connection c = null;
    try{
	  System.out.println ("URL " + dbConfig.getConnectionURLNoDatabase());
	  
      c=DriverManager.getConnection
	(dbConfig.getConnectionURLNoDatabase(),
	 dbConfig.getUser(),
	 dbConfig.getPassword());
    }catch(SQLException e){
      logger.logMessage(Logger.FATAL,Logger.DB_CONNECT,
		 "Could not establish DB connection",e);
      return null;
    }
	try {
      Statement s=c.createStatement();
      s.execute("CREATE DATABASE "+dbConfig.getDatabaseName ());
    }catch(SQLException e){
      logger.logMessage(Logger.FATAL,Logger.DB_CONNECT,
		 "Could not create database " + dbConfig.getDatabaseName () + ".",e);
      return null;
    }
	
    logger.logMessage(Logger.IMPORTANT,Logger.DB_CONNECT,
		      "Database " + dbConfig.getDatabaseName () + " created.");
    try{
      c.close();
    }catch(SQLException e){
      logger.logMessage(Logger.WARNING,Logger.DB_CONNECT,
		 "Could not close connection",e);
    }

    try{
      c=DriverManager.getConnection
	(dbConfig.getConnectionURL(),
	 dbConfig.getUser(),
	 dbConfig.getPassword());
    }catch(SQLException e){
	  logger.logMessage(Logger.FATAL,Logger.DB_CONNECT,
	  		 "Could not establish DB connection",e);
	}
	
	return c;
  }
  
  protected void closeDBConnection(){
    try{
      dbConnection.close();
    }catch(SQLException e){
      logger.logMessage(Logger.WARNING,Logger.DB_CONNECT,
		 "Could not close connection",e);
    }
  }

  /**Return true on success**/
  protected boolean initDB(){
    dbConnection=getNewDBConnection(dgConfig.getDBConfig());
    return dbConnection!=null;
  }

  //Gets:

  public Connection getDBConnection(){
    return dbConnection;
  }

  public synchronized Run getRunForID(int id){
    return (Run)runMap.get(new Integer(id));
  }

  public synchronized boolean isRunActive(int id){
    return getRunForID(id)!=null;
  }

  //Sets:

  protected synchronized void addRun(Run r){
    runMap.put(new Integer(r.getID()),r);
  }

  protected synchronized void removeRun(int id){
    runMap.remove(new Integer(id));
  }

  //Actions:

  public synchronized boolean startNewRun(Run run){
    try{
      int id=registerNewRun();
      run.setID(id);
    }catch(SQLException e){
      logger.logMessage(Logger.ERROR,Logger.DB_WRITE,
			"Could not obtain id for run",e);
      return false;
    }
    run.setController(this);
    run.setWorkQueue(workQ);
    run.setDGConfig(dgConfig);
    run.setDBConnection(dbConnection);
    run.setLogger(logger);
    addRun(run);
    logger.logMessage(Logger.IMPORTANT,Logger.STATE_CHANGE,
		      "Started new run: "+run.getID());
    run.start();
    return true;
  }

  public synchronized boolean haltRun(int id){
    logger.logMessage(Logger.IMPORTANT,Logger.STATE_CHANGE,
		      "Halting run: " +id);
    Run r=getRunForID(id);
    if(r!=null)
      return r.halt();
    return false;
  }

  public synchronized boolean halt(){
    logger.logMessage(Logger.IMPORTANT,Logger.STATE_CHANGE,
		      "Controller Halting");
    boolean checkedAll=false;
    while(!checkedAll){
      try{
	Iterator iter=runMap.keySet().iterator();
	while(iter.hasNext()){
	  int id=((Integer)iter.next()).intValue();
	  haltRun(id);
	}
	checkedAll=true;
      }catch(ConcurrentModificationException cme){}
    }
    halt=true;
    interrupt();
    return true;
  }

  public String getRunStatus(int id){
    Run run=getRunForID(id);
    return run==null?("Run inactive"):run.getStatus();
  }

  //Actions for Run:

  void runCompleted(Run r, int condition){
    updateRunTable(r.getID(), new Date().getTime(), condition);
    removeRun(r.getID());
    logger.logMessage(Logger.IMPORTANT,Logger.STATE_CHANGE,
		      "Run ("+r.getID()+") completed with condition: "+
		      Run.CONDITIONS[condition]);
  }

  //Internals:

  protected void processResults(){
    while(!halt){
      synchronized(this){
	boolean checkedAll=false;
	while(!checkedAll){
	  try{
	    Iterator iter=runMap.values().iterator();
	    while(iter.hasNext()){
	      Run r=(Run)iter.next();
	      try{
		if(r.hasResult())
		  r.processResults();	  
	      }catch(Exception e){
		r.logMessage(Logger.ERROR,Logger.GENERIC,
		"UNEXPECTED Exception while processing results in run",e);
		e.printStackTrace();
	      }
	    }
	    checkedAll=true;
	  }catch(ConcurrentModificationException cme){
	  }
	}
	try{
	  wait();
	}catch(InterruptedException e){
	}
      }
    }
    closeDBConnection();
  }

  //Run table:

  protected void updateRunTable(int id, long date, int condition){
    try{
      Statement s=dbConnection.createStatement();
      if(s.executeUpdate("UPDATE "+getRunTableName()+ " SET "+
			 COL_ENDTIME+"="+dateToSQL(date)+","+
			 COL_CONDITION+"="+Integer.toString(condition)+
			 " WHERE "+
			 COL_RUNID+"="+Integer.toString(id))==1){
	logger.logMessage(Logger.MINOR,Logger.DB_WRITE,
			  "Updated run("+id+")");
      }else{
	logger.logMessage(Logger.ERROR,Logger.DB_WRITE,
			  "Could not updated run("+id+")");
      }
      s.close();
    }catch(SQLException e){
      logger.logMessage(Logger.ERROR,Logger.DB_WRITE,
			"Could not update run("+id+")",e);
    }
  }

  /**Put a new run into the table, and return the number
   **/
  protected int registerNewRun()throws SQLException{
    int ret=getLargestRunNumber()+1;
    Statement s=dbConnection.createStatement();
    if(s.executeUpdate("INSERT INTO "+getRunTableName()+" ("+
		       COL_RUNID+","+COL_STARTTIME+","+COL_CONDITION+
		       ") VALUES("+
		       Integer.toString(ret)+","+
		       dateToSQL(new Date().getTime())+","+
		       Run.COND_STARTED+")")==1){
      logger.logMessage(Logger.MINOR,Logger.DB_WRITE,
			"Inserted new run("+ret+")");
    }else{
      logger.logMessage(Logger.ERROR,Logger.DB_WRITE,
			"Error inserting run("+ret+")");
    }
    s.close();
    return ret;
  }

  protected synchronized int getLargestRunNumber(){
    int ret=0;
    try{
      Statement s=dbConnection.createStatement();
      if(s==null){
	logger.logMessage(Logger.ERROR,Logger.DB_CONNECT,
			  "Could not create statement from connection");
	return ret;
      }
      ResultSet rs=s.executeQuery("SELECT MAX("+COL_RUNID+") FROM "+
				  getRunTableName());
      if(rs.next()){
	ret=rs.getInt(1);
      }else{
	logger.logMessage(Logger.WARNING,Logger.DB_QUERY,
			  "Empty set obtaining largest run number");
      }
      s.close();
    }catch(SQLException e){
      logger.logMessage(Logger.ERROR,Logger.DB_QUERY,
			"Could not obtain largest run number",e);
    }
    return ret;
  }

  public String getRunTableName(){
    return dgConfig.getDBConfig().getDBTableName(RUN_TABLE_NAME);
  }

  protected boolean prepareRunTable(){
    try{
      DatabaseMetaData meta = dbConnection.getMetaData();
      String tTypes[]={"TABLE"}; 
      ResultSet rs;
      rs = meta.getTables(null,null,getRunTableName(),tTypes);
      if(!rs.next()){ //If there is no row, then we need to make table
	Statement s=dbConnection.createStatement();
	prepareTable(s,getRunTableName(),
		     "CREATE TABLE "+getRunTableName()+" ( "+
		     COL_RUNID+" INTEGER NOT NULL, "+
		     COL_STARTTIME+" "+
		     dgConfig.getDBConfig().getDateTimeType()+
		     " NOT NULL, "+
		     COL_ENDTIME+" "+
		     dgConfig.getDBConfig().getDateTimeType()+
		     " NULL, "+
		     COL_CONDITION+" INTEGER NOT NULL)");
	s.close();
      }
      rs.close();
    }catch(SQLException e){
      logger.logMessage(Logger.ERROR,Logger.DB_CONNECT,
			"Could not determine table("+getRunTableName()+
			")presence",e);
      return false;
    }
    return true;
  }
  
  //General table functions:

  /**
   * Go from a java formated long to a SQLable string:
   **/
  protected String dateToSQL(long time){
    return dgConfig.getDBConfig().dateToSQL(time);
  }

  /**
   * Create a new table
   **/
  protected void prepareTable(Statement s, String tableName, String sql){
    try{
      s.executeUpdate(sql);
    }catch(SQLException e){
      logger.logMessage(Logger.ERROR,Logger.DB_STRUCTURE,
		 "Could not create table("+tableName+")",e);
      return;
    }
    logger.logMessage(Logger.MINOR,Logger.DB_STRUCTURE,"Created table("+
		      tableName+")"); 
  }

  //UI interface:

  public Map getWorkIDToStatusMap(){
    return workQ.getWorkIDToStatusMap();
  }
  
  //Static members:
  /////////////////

  /**provides a table name that includes the run number from the generic
   * name
   **/
  public static String getTableName(String baseTableName, int runID){
    //Truncate the string to 25 letters to ensure enough space, lowercase,
    //then include "_"+runID
    //Oracle tables can only be 30 long...
    String name=baseTableName;
    if(baseTableName.length()>25)
      name=baseTableName.substring(0,25);
    name=name+'_'+Integer.toString(runID);
    return name;
  }

  /**
   * Determine if a table is part of a given run
   **/
  public static boolean tableNamePartOfRun(String tableName, int runID){
    return tableName.endsWith("_"+Integer.toString(runID));
  }

  //InnerClasses:
  ///////////////

}
