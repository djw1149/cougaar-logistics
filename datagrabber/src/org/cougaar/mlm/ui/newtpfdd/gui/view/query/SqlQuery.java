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
package org.cougaar.mlm.ui.newtpfdd.gui.view.query;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;

import java.text.SimpleDateFormat;
import java.text.ParseException;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import org.cougaar.mlm.ui.grabber.connect.HierarchyConstants;
import org.cougaar.mlm.ui.grabber.controller.Controller;
import org.cougaar.mlm.ui.grabber.controller.Run;
import org.cougaar.mlm.ui.newtpfdd.gui.view.DatabaseConfig;
import org.cougaar.mlm.ui.grabber.derived.PrepareDerivedTables;
import org.cougaar.mlm.ui.grabber.logger.Logger;
import org.cougaar.mlm.ui.grabber.logger.TPFDDLoggerFactory;

public class SqlQuery implements Query {
  private static final String VISHNU = "vishnu";
  private static final String MYSQL = "mysql";
  private static final int NO_DISK_SPACE = 28; // mysql error code for out of memory
  
  boolean debug = 
    "true".equals (System.getProperty ("org.cougaar.mlm.ui.newtpfdd.gui.view.SqlQuery.debug", 
				       "false"));
  boolean showSqlTime = 
    "true".equals (System.getProperty ("org.cougaar.mlm.ui.newtpfdd.gui.view.SqlQuery.showSqlTime", 
				       "false"));
  boolean warningRunsOK = 
    "true".equals (System.getProperty ("org.cougaar.mlm.ui.newtpfdd.gui.view.SqlQuery.warningRunsOK", 
				       "true"));
  boolean showCondition = 
    "true".equals (System.getProperty ("org.cougaar.mlm.ui.newtpfdd.gui.view.SqlQuery.showCondition", 
				       "false"));

  public QueryResponse getResponse (Connection connection) {
    QueryResponse response = new QueryResponse ();
    String sqlQuery = getSqlQuery ();
	
    ResultSet rs = null;
    try{
      rs = getResultSet(connection, sqlQuery);
      handleResult (rs, response);
    }finally{
      if(rs!=null) {
	if (debug) TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getResponse closing result set.");
	try { 
	  rs.close();
	  rs.getStatement().close();
	} catch (SQLException e){
	  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getResponse - closing result set, got sql error : (" + e.getErrorCode() + ") " +
			      e); 
	}
      }
    }

    return response;
  }

  public ResultSet getResultSet (Connection connection, String sqlQuery) {
    Date then = new Date();
	
    Statement s=null;
    try{
      s=connection.createStatement();
    }catch(SQLException e){
      TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getResultSet - got sql error : (" + e.getErrorCode() + ") " +
			  e +"\n" +
			  "sql was : \n" + sqlQuery); 

      String hostURL = "<could not get database URL>";
      try { hostURL = connection.getMetaData().getURL(); }
      catch (Exception f) {e.printStackTrace();}
	  
      if (e.getErrorCode () == NO_DISK_SPACE)
	TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getResultSet - mysqld at " + hostURL +
			    " is out of disk space!  Check with : df / ");
    }
    if(s==null) {
      TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getResultSet - ERROR, statement null.");
      return null;
    }

    /*
     * some sql here, probably built using a string buffer. ... 
     * don't forget to handle dates and doubles in a DB safe way and to use ' quotes.  
     * See /tops/src/org/cougaar/domain/mlm/ui/grabber/config/DBConfig 
     * for examples of functions for doing oracle/my sql syntax)
     */	

    ResultSet rs = null;
    try{
      rs = s.executeQuery(sqlQuery);
    }catch(SQLException e){
      TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getResultSet - got sql error : (" + e.getErrorCode() + ") " +
			  e +"\n" +
			  "sql was : \n" + sqlQuery); 

      String hostURL = "<could not get database URL>";
      try { hostURL = connection.getMetaData().getURL(); }
      catch (Exception f) {f.printStackTrace();}
	  
      if (e.getErrorCode () == NO_DISK_SPACE)
	TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getResultSet - mysqld at " + hostURL +
			    " is out of disk space!  Check with : df / ");
    }

    if (showSqlTime) {
      Date now = new Date();
      long diff = now.getTime()-then.getTime();
      TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getResultSet - query took " + diff+" msecs.");
    }
	
    return rs;
  }


  /**Get a set of the table names in the database**/
  protected Set getTableNames(Connection c){
    Set ret=new HashSet(39);
    ResultSet rs=null;
    String tTypes[]={"TABLE"};
    try{
      DatabaseMetaData meta = c.getMetaData();
      try{
	rs = meta.getTables(null,null,"%",tTypes);
	while(rs.next()){
	  ret.add(rs.getString(3));
	}
      }finally{
	if(rs!=null)
      	  rs.close();
      }
    }catch(SQLException e){
      System.err.println("SqlQuery.getTableNames - Could not get list of table names.");
      return ret;
    }
    return ret;
  }

  /** 
   * By default, just the latest run with condition =1. 
   * If warningRunsOK is set, will allow runs that had condition warning too.
   */
  protected int getRecentRun (Connection connection) {
    ResultSet rs = null;
    int runToUse = 1;
    try{
      // first figure out which run to use
      String sqlQuery = getRunSql ();
	  
      if (debug)
	TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRecentRun - sql is\n" + sqlQuery);
	  
      rs = getResultSet(connection, sqlQuery);
      while(rs.next()){
	int runid = rs.getInt (Controller.COL_RUNID); 
	if (runid > runToUse)
	  runToUse = runid;
		
	if (debug) TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRecentRun - Run id " + rs.getInt ("runid"));
      }
    } catch (SQLException e) {
      TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRecentRun - SQLError : " + e);
    }finally{
      if(rs!=null) {
	if (debug) TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRecentRun closing result set.");
	try { 
	  rs.close(); 
	  rs.getStatement().close();
	} catch (SQLException e){
	  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRecentRun - closing result set, got sql error : " + e);
	}
      }
    }

    return runToUse;
  }

  protected String getRunSql () {
    String sqlQuery = "select * from " + Controller.RUN_TABLE_NAME + " where " + 
      Controller.COL_CONDITION + " = " + Run.COND_COMPLETED + 
      ((warningRunsOK) ? 
       (" or " + Controller.COL_CONDITION + " = " + Run.COND_WARNING) : "") +
      " order by " + Controller.COL_RUNID;
	  
    return sqlQuery;
  }

  /** 
   * By default, just the latest run with condition =1. 
   * If warningRunsOK is set, will allow runs that had condition warning too.
   */
  protected List getRuns (String database, Connection connection) {
    ResultSet rs = null;
    List runs = new ArrayList ();
    Set tableNames=getTableNames(connection);

    try{
      // if the database doesn't have a runtable, there are no runs in it.
      if (!tableNames.contains (Controller.RUN_TABLE_NAME))
	return runs;
	  
      // first figure out which run to use
      String sqlQuery = getRunSql ();
	  
      if (debug)
	TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRuns - sql is\n" + sqlQuery);
	  
      rs = getResultSet(connection, sqlQuery);

      if (debug) TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRuns - got results set.");

      while (rs != null && rs.next()) {
	int runid = rs.getInt (Controller.COL_RUNID); 
	if (debug) TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRuns - got run #" + runid + " from row " + rs.getRow());
	int condition = rs.getInt (Controller.COL_CONDITION);
	Date timestamp = rs.getTimestamp (Controller.COL_ENDTIME);
	DatabaseRun dr=new DatabaseRun (database, runid, condition, timestamp);
	runs.add (dr);
	dr.setHasRollupTable(tableNames.contains
			     (PrepareDerivedTables.getDerivedTableName
			      (PrepareDerivedTables.ROLLUP,runid)));

	dr.setHasCargoTypeTable(tableNames.contains
				(PrepareDerivedTables.getDerivedTableName
				 (PrepareDerivedTables.CARGO_TYPE,runid)));
	dr.setHasCargoInstanceTable(tableNames.contains
				    (PrepareDerivedTables.getDerivedTableName
				     (PrepareDerivedTables.CARGO_INSTANCE,runid)));
	dr.setHasCargoLegTable(tableNames.contains
			       (PrepareDerivedTables.getDerivedTableName
				(PrepareDerivedTables.CARGO_LEG,runid)));

	dr.setHasCarrierTypeTable(tableNames.contains
				  (PrepareDerivedTables.getDerivedTableName
				   (PrepareDerivedTables.CARRIER_TYPE,runid)));
	dr.setHasCarrierInstanceTable(tableNames.contains
				      (PrepareDerivedTables.getDerivedTableName
				       (PrepareDerivedTables.CARRIER_INSTANCE,runid)));
      }
    }
    catch (SQLException e) {
      TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRuns - SQLError : " + e);
      e.printStackTrace ();
    }finally{
      if(rs!=null) {
	if (debug) TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRuns closing result set.");
	try { 
	  rs.close();
	  rs.getStatement().close();
	} catch (SQLException e){
	  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRuns - closing result set, got sql error : " + e);
	}
      }
    }
    return runs;
  }

  protected List getDatabases (DatabaseConfig dbConfig) {
    ResultSet rs = null;
    List databases = new ArrayList ();
	  
    try{
      // first figure out which run to use
      String sqlQuery = "show databases";
	  
      if (debug)
	TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getRunDescrip - sql is\n" + sqlQuery);

      Connection newConnection = dbConfig.createDBConnection (dbConfig.getHost(), "");
	  
      rs = getResultSet(newConnection, sqlQuery);

      while (rs.next()) {
	String database = rs.getString (1); 
	if (!database.startsWith(VISHNU) && !database.startsWith(MYSQL))
	  databases.add(database);
	// 		if (database.startsWith (GATHERER))
	// 		  databases.add (database);
      }
		
      if (debug) 
	TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getDatabases - " + databases);
    }
    catch (SQLException e) {
      TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getDatabases - SQLError : " + e);
    }finally{
      if(rs!=null) {
	if (debug) TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getDatabases closing result set.");
	try { 
	  rs.close(); 		  
	  rs.getStatement().close();
	} catch (SQLException e){
	  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getResponse - closing result set, got sql error : " + e);
	}
      }
    }
    return databases;
  }

  protected String [] getDemandRoot (Connection connection, int runID) {
    return getOrgRoot (connection, runID, HierarchyConstants.SOC_DEMAND);
  }
  
  protected String [] getTOPSRoot (Connection connection, int runID) {
    return getOrgRoot (connection, runID, HierarchyConstants.SOC_TOPS);
  }
  
  /** 
   * Get the org for run <code>runID</code> that is either the TOPS or demand 
   * root (<code>societyCode</code>).
   * 
   * @see org.cougaar.mlm.ui.grabber.connect.HierarchyConstants#SOC_TOPS
   * @see org.cougaar.mlm.ui.grabber.connect.HierarchyConstants#SOC_DEMAND
   */
  protected String [] getOrgRoot (Connection connection, int runID, int societyCode) {
    ResultSet rs = null;
    String rootOrgName = null;
    String prettyName = null;
	
    try{
      // first figure out which run to use
      String orgRootsTable = HierarchyConstants.ORGROOTS_TABLE_NAME + "_" + runID;
      String orgRootsIDCol = orgRootsTable + "." + HierarchyConstants.COL_ORGID;
      String orgNamesTable = HierarchyConstants.ORGNAMES_TABLE_NAME + "_" + runID;
      String orgNamesNameCol = orgNamesTable + "." + HierarchyConstants.COL_PRETTY_NAME;  
      String orgRootsSocietyCol = orgRootsTable + "." + HierarchyConstants.COL_SOCIETY;
	  
      String sqlQuery = 
	"select " + orgRootsIDCol + 
	" from " + orgRootsTable + 
	" where " + orgRootsSocietyCol + " = " + societyCode;
      if (debug) TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "sql query (1) " + sqlQuery);
	  
      rs = getResultSet(connection, sqlQuery);
      while(rs.next()){
	// should only be one
	rootOrgName = rs.getString (HierarchyConstants.COL_ORGID);
      }

      sqlQuery = 
	"select " + orgNamesNameCol +
	" from " + orgNamesTable +
	" where " + orgNamesTable + "." + HierarchyConstants.COL_ORGID + " = '" + rootOrgName + "'";

      if (debug) TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "sql query (2) " + sqlQuery);

      rs = getResultSet(connection, sqlQuery);
      while(rs.next()){
	// should only be one
	prettyName = rs.getString (HierarchyConstants.COL_PRETTY_NAME);
      }
      if (debug) TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery - root org " + rootOrgName + " pretty " + prettyName);

    } catch (SQLException e) {
      TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getOrgRoot - SQLError : " + e);
    }finally{
      if(rs!=null) {
	if (debug) TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getOrgRoot closing result set.");
	try { 
	  rs.close();
	  rs.getStatement().close();
	} catch (SQLException e){
	  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery.getResponse - closing result set, got sql error : " + e);
	}
      }
    }

    return new String [] {rootOrgName, prettyName};
  }

  public static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  
  protected Date getDate (String dateString) {
    synchronized (formatter) {
      try {
	return formatter.parse (dateString);
      }
      catch (ParseException pe) {
	return new Date(0);
      }
    }
  }
  
  protected String trim (String str) {
    int index = str.indexOf ('(');
    return (index != -1) ? str.substring (0, index) : str;
  }

  protected String getSqlQuery () { return null; }
 
  protected void handleResult (ResultSet rs, QueryResponse response) {}

  public static void main (String [] arg)  {
    SqlQuery sqlQuery = new SqlQuery ();
    DatabaseConfig dbConfig = new DatabaseConfig ("blubin");
	
    List databases = sqlQuery.getDatabases (dbConfig);
    TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "SqlQuery - databases " + databases);
  
    for (int i = 0; i < databases.size (); i++) {
      String db = (String) databases.get (i);
      Connection conn = dbConfig.createDBConnection (dbConfig.getHost(), db);
      TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "Database " + db + " has runs : ");
      List runs = sqlQuery.getRuns (db, conn);
	  
      for (int j = 0; j<runs.size(); j++)
	TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "\t" + runs.get(j));
    }
  }
}
