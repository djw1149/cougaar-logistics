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
package org.cougaar.mlm.ui.grabber.webserver;

import org.cougaar.mlm.ui.grabber.logger.Logger;
import org.cougaar.mlm.ui.grabber.logger.DBIDLogger;
import org.cougaar.mlm.ui.grabber.config.WebServerConfig;
import org.cougaar.mlm.ui.grabber.config.DBConfig;

import org.cougaar.mlm.ui.grabber.connect.DGPSPConstants;
import org.cougaar.mlm.ui.grabber.controller.*;
import org.cougaar.mlm.ui.grabber.validator.HTMLizer;
import org.cougaar.mlm.ui.grabber.validator.ResultTable;

import java.io.*;
import java.net.*;
import java.util.*;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;

import java.text.SimpleDateFormat;

/**
 * Handles requests for commands to the controller
 *
 * @since 2/8/01
 **/
public class ControllerRequestHandler extends DynamicRequestHandler{

  //Constants:
  ////////////

  public static final int COM_UNKNOWN=0;
  public static final int COM_NEWRUN=1;
  public static final int COM_LISTRUNS=2;
  public static final int COM_DELRUN=3;
  public static final int COM_LISTWORK=4;
  public static final int COM_LISTLOG=5;
  public static final int COM_DELLOG=6;
  public static final int COM_HALTRUN=7;
  public static final int COM_ANNORUN=8;

  private static String[]COMMANDS={"unknown",
				   "newrun",
				   "listruns",
				   "delrun",
				   "listwork",
				   "listlog",
				   "dellog",
				   "haltrun",
				   "annorun"};

  //Variables:
  ////////////

  protected int maxAnnotationLength=64;
  protected int run=-1;
  protected Controller controller;

  //Constructors:
  ///////////////

  public ControllerRequestHandler(DBConfig dbConfig, Connection connection,
				  WebServerConfig config, 
				  HttpRequest request,
				  Controller controller){
    super(dbConfig,connection,config,request);
    this.controller=controller;
  }

  //Members:
  //////////


  protected String getURLRun(int command){
    return getURLRun(command,run);
  }

  protected String getURLRun(int command, int run){
    return getURL(command,"?run="+run);
  }

  public int getModule(){
    return WebServerConfig.CONTROLLER;
  }

  /**Send the content for given command**/
  protected void sendCommandContent(HTMLizer h, 
				    Statement s, 
				    int command)
    throws SQLException,IOException{
    switch(command){
    case COM_NEWRUN:
      sendNewRun(h,s);break;
    case COM_ANNORUN:
      sendNewAnnoRun(h,s);break;
    case COM_LISTRUNS:
      sendListRuns(h,s);break;
    case COM_DELRUN:
      sendDelRun(h,s);break;
    case COM_LISTWORK:
      sendListWork(h,s);break;
    case COM_LISTLOG:
      sendListLog(h,s);break;
    case COM_DELLOG:
      sendDelLog(h,s);break;
    case COM_HALTRUN:
      sendHaltRun(h,s);break;
    }
  }

  protected int getCommand(String comStr){
    for(int i=0;i<COMMANDS.length;i++){
      if(comStr.endsWith(COMMANDS[i]))
	return i;
    }
    return COM_UNKNOWN;
  }

  public static String getCommandName(int command){
    if(command>=0&&command<COMMANDS.length)
      return COMMANDS[command];
    return "unknown";
  }

  protected void modifyTarget(){
    super.modifyTarget();
    run=getQueryWithPrefix("run=");
  }

  //Commands:

  protected void sendNewRun(HTMLizer h, Statement s) throws IOException{
    controller.startNewRun(new TopsRun() ,"");
    header(h,"New run started");
    footer(h);
  }
 
  /** 
    * User inputs a run annotation to give the context the run was done in -
    * what stage, what jars, etc.
    * This annotation gets stored as a column in the run table, so it will be visible
    * in the run list.
    */ 
  protected void sendNewAnnoRun(HTMLizer h, Statement s) throws IOException{
    String annotation = getStringQueryWithPrefix("annotation");
    if (annotation.startsWith("="))
	annotation = annotation.substring(1);
    annotation = URLDecoder.decode(annotation, "UTF-8");
    String replaced =annotation.replace('\"', '\''); // don't allow quotes that the sql will use

    controller.startNewRun(new TopsRun(),replaced);
    header(h,"New run started, annotation <b>\"" + replaced + "\"</b>");
    footer(h);
  }

  protected void sendListRuns(HTMLizer h, Statement s) 
    throws IOException,SQLException{
    header(h,"Currently available runs",
	   config.getViewRefresh(),
	   getURL(COM_LISTRUNS));
    h.sCenter();
    boolean showSpace=getStringQueryWithPrefix("showSpace=").equals("true");
    if(showSpace){
      h.p("["+h.aStr(getURL(COM_LISTRUNS),
		     "Hide Disk Space")+"]");
    }else{
      h.p("["+h.aStr(getURL(COM_LISTRUNS)+"?showSpace=true",
		     "Show Disk Space")+"]");
    }
    printRunTable(h,s, showSpace);
    h.p(h.aStr(getURL(WebServerConfig.COMPLETIONASSESSOR,
		      CompletionAssessorRequestHandler.COM_HIT_PSP),
	       "Show society completion status"));
    h.p(h.aStr(getURL(COM_NEWRUN),
	       "Start a new run"));
    
	// make form for inputing annotation and kicking off run
    
    h.p("<table>" +
	"<tr>" +
	"<td>With Annotation</td>" + 
	"<td>" +
	"<form action=\"" + getURL(COM_ANNORUN) + "\" method=\"get\" name=\"myform\">" +
   	"<input type=\"text\" name=\"annotation\" align=\"top\" maxlength=\""+maxAnnotationLength+"\""+
	" size=\""+(maxAnnotationLength+10)+"\">" + 
	"</td>"+
	"</tr>" +
	"<tr>" +
	"<td colspan='2' align='center'>"+
   	"<input type=\"submit\" value=\"Start New Run\" align=\"middle\">"+
   	"</form>" + 
	"</td>" +
	"</tr>" +
	"</table>");
 
    h.eCenter();
    footer(h);
  }

  protected static String runTableSQLBase = "SELECT "+Controller.COL_RUNID+","+
      Controller.COL_STARTTIME+","+
      Controller.COL_ENDTIME+","+
      Controller.COL_CONDITION+","+
      Controller.COL_ANNOTATION+" FROM ";

  protected void printRunTable(HTMLizer h, Statement s, boolean showSpace)
    throws IOException,SQLException{
    long total = 0;
    Map runToSize=null;
    if(showSpace)
      runToSize=getDiskSpacePerRun (s);
	
    String sql = runTableSQLBase +
      controller.getRunTableName() +
      " ORDER BY "+Controller.COL_RUNID;

    //System.err.println ("sql was <" + sql + ">");

    Map runToOwners = new HashMap ();
    Map runToAssets = new HashMap ();

    getSizes(s, sql, runToOwners, runToAssets);

    ResultSet rs=s.executeQuery(sql);

    h.sTable();
    h.sRow();
    h.tHead("ID");
    h.tHead("Start Time");
    h.tHead("End Time");
    if(showSpace)
      h.tHead("Disk Space");
    h.tHead("Units");
    h.tHead("Assets");
    h.tHead("Status");
    h.tHead("Action");
    h.tHead("Annotation");
    h.eRow();

    h.openBuffer();

    while(rs.next()){
      int runID=rs.getInt(1);
      h.sRowBuffer();
      h.tDataBuffer("" +runID);
      h.tDataBuffer(rs.getDate(2)+" "+rs.getTime(2));
      h.tDataBuffer(rs.getDate(3)+" "+rs.getTime(3));
      
      if(showSpace){
	long size = ((Long) runToSize.get(new Integer(runID))).longValue();
	total += size;
	h.tDataBuffer("" + size);
      }
      
      Object numOwners = runToOwners.get(new Integer(runID));
      Object numAssets = runToAssets.get(new Integer(runID));
      h.tDataBuffer((numOwners == null) ? "N/A" : numOwners.toString());
      h.tDataBuffer((numAssets == null) ? "N/A" : numAssets.toString());

      h.tDataBuffer(h.aStr(getURLRun(COM_LISTLOG,runID)+
			   "?displayLevel="+Logger.SEVERITIES[Logger.IMPORTANT],
			   Run.CONDITIONS[rs.getInt(4)])+
		    ": "+controller.getRunStatus(runID));
      if(controller.isRunActive(runID)){
	h.tDataBuffer(h.aStr(getURLRun(COM_HALTRUN,runID),
			     "Halt"));
      }else{
	h.tDataBuffer(h.aStr(getURL(WebServerConfig.VALIDATOR,
				    ValidatorRequestHandler.COM_MAIN_MENU,
				    "?run="+runID),
			     "Validation")+
		      ", "+
		      h.aStr(getURLRun(COM_DELRUN,runID),
			     "Delete"));
      }
      // Annotation string - 
      h.tDataBuffer(rs.getString(5));
      h.eRowBuffer();
    }

    h.closeBuffer();

    if(showSpace){
      h.sRow();
      h.tData("Total");
      h.tData("");
      h.tData("");
      // add size of other tables
      total += ((Long) runToSize.get(new Integer(-1))).longValue(); 
      h.tData("" + total + " K");
      h.tData("");
      h.tData("");
      h.eRow();
    }

    h.eTable();
  }


  protected Map getDiskSpacePerRun (Statement s)
    throws IOException,SQLException {
    ResultSet rs=s.executeQuery("SHOW TABLE STATUS");
	Map runToSize = new HashMap ();

    while(rs.next()){
      String tableName = rs.getString(1);
	  String runNumber = tableName.substring (tableName.lastIndexOf('_')+1);
	  Integer intRun;

	  try {
		intRun = new Integer(Integer.parseInt (runNumber));
	  }
	  catch (NumberFormatException nfe) {
		intRun = new Integer (-1); // log table, run table
		// System.out.println ("ControllerRequestHandler.getDiskSpacePerRun - ignoring table " + tableName);
	  }

	  long tableSizeInBytes  = rs.getLong(6); // data  size
	  tableSizeInBytes      += rs.getLong(8); // index size

	  Long size = (Long) runToSize.get (intRun);
	  if (size == null)
		size = new Long (0);
	  
	  runToSize.put (intRun, new Long(tableSizeInBytes/1024l+size.longValue()));
	  // System.out.println (tableName + " " + intRun + " size " + tableSizeInBytes);
	}
		
	return runToSize;
  }
  
  protected void sendDelRun(HTMLizer h, Statement s) 
    throws IOException, SQLException{
    //check to see if the run is active:
    if(run<1){
      sendInvalidQuery(h);
      return;
    }
    if(getQueryWithPrefix("confirm=")!=1){
      header(h,"Confirm delete run("+run+")");
      h.sCenter();
      h.p("<B>"+h.popupStr(getURL(COM_LISTRUNS),
			   getURLRun(COM_DELRUN)+"?confirm=1",
			   "delWindow",
			   "Confirm delete run "+run)+"</B>");
      h.eCenter();
      footer(h);
    }else{
      if(controller.isRunActive(run)){
	header(h,"Could not delete active run "+run);
	h.sCenter();
	h.dismissLink();
	h.eCenter();
	footer(h);
      }else{
	header(h,"Delete run "+run);
	h.sCenter();
	h.dismissLink();
	h.eCenter();
	h.sOl();
	//Now remove from the run table:
	if(s.executeUpdate("DELETE FROM "+controller.getRunTableName()+
			   " WHERE "+Controller.COL_RUNID+"="+
			   run)==1){
	  h.li("Deleted row from run table");
	}else{
	  h.li("<FONT COLOR=RED>Could not delete row from run table</FONT>");
	}
	//Now delete the rows from the log:
	h.li(deleteLogForRun(s,run));
	//Delete the tables:
	DatabaseMetaData meta = dbConnection.getMetaData();
	String tTypes[]={"TABLE"}; 
	ResultSet rs= meta.getTables(null,null,"%",tTypes);
	while(rs.next()){
	  String tableName=rs.getString(3);
	  if(Controller.tableNamePartOfRun(tableName, run)){
	    s.executeUpdate("DROP TABLE "+tableName);
	    h.li("Dropped table: "+tableName);
	  }
	}
	ResultTable.remove(h,s,run);
	h.eOl();
	footer(h);
      }
    }
  }
  
  protected void sendListWork(HTMLizer h, Statement s) throws IOException{
    header(h,"Active Work",config.getViewRefresh(),
	   getURL(COM_LISTWORK));
    h.sCenter();
    printWorkTable(h);
    h.eCenter();
    footer(h);
  }
  
  protected void printWorkTable(HTMLizer h) throws IOException{
    Map map=controller.getWorkIDToStatusMap();
    Map sorted = new TreeMap(map);
    Iterator iter=sorted.keySet().iterator();

    h.sTable();
    h.sRow();
    h.tHead("ID");
    h.tHead("Status");
    h.eRow();
    while(iter.hasNext()){
      Integer workID=(Integer)iter.next();
      String status=(String)map.get(workID);
      h.sRow();
      h.tData(workID.toString());
      h.tData(status);
      h.eRow();
    }
    h.eTable();
  }
  
  protected int getLogLevelForString(String s){
    for(int i=0;i<Logger.SEVERITIES.length;i++){
      if(s.equals(Logger.SEVERITIES[i]))
	return i;
    }
    return Logger.TRIVIAL;
  }

  protected void sendListLog(HTMLizer h, Statement s)
    throws IOException, SQLException{
    String levelString=getStringQueryWithPrefix("displayLevel=");
    int displayLevel=Logger.IMPORTANT;
    if(!levelString.equals(""))
      displayLevel=getLogLevelForString(levelString);
    if(run<1){
      header(h,"Complete Log",
	     config.getViewRefresh(),
	     getURL(COM_LISTLOG)+
	     (levelString.equals("")?"":"?displayLevel="+levelString));
      h.sCenter();
      printLogRefs(h,s,displayLevel);
      printLogTable(h,s, displayLevel);
      h.p(h.aStr(getURL(COM_DELLOG),"Delete all logs"));
      h.eCenter();
      footer(h);
    }else{
      header(h,"Log entries for run "+run,
	     config.getViewRefresh(),
	     getURLRun(COM_LISTLOG)+
	     (levelString.equals("")?"":"?displayLevel="+levelString));
      h.sCenter();
      printLogRefs(h,s,displayLevel);
      printLogTable(h,s,displayLevel);
      h.p(h.aStr(getURLRun(COM_DELLOG),"Delete log entries for run "+run));
      h.eCenter();
      footer(h);
    }
  }

  protected void printLogRefs(HTMLizer h, Statement s, int displayLevel)
    throws IOException{
    h.print("<p>");
    for(int i=0;i<Logger.SEVERITIES.length;i++){
      h.print("[");
      if(i==displayLevel){
	h.print(Logger.SEVERITIES[i]);
      }else{
	h.print(h.aStr(getURLRun(COM_LISTLOG)+"?displayLevel="+
		       Logger.SEVERITIES[i],
		       Logger.SEVERITIES[i]));
      }
      h.print("]\n");
    }
    h.print("</p>");
  }

  protected void printLogTable(HTMLizer h, Statement s, int displayLevel)
    throws IOException, SQLException{
    String query="SELECT "+DBIDLogger.COL_TIME+","+
      DBIDLogger.COL_SEVERITY+","+
      DBIDLogger.COL_TYPE+","+
      DBIDLogger.COL_ID+","+
      DBIDLogger.COL_MESSAGE+","+
      DBIDLogger.COL_EXCEPTION+
      " FROM "+
      DBIDLogger.LOG_TABLE_NAME+
      " WHERE "+
      DBIDLogger.COL_SEVERITY+"<="+displayLevel;
    if(run>0){
      query+=" AND "+
	DBIDLogger.COL_ID+
	"="+run;
    }
    query+=" ORDER BY "+DBIDLogger.COL_SEQ;
    
    ResultSet rs=s.executeQuery(query);
    
    h.sTable();
    h.sRow();
    h.tHead("Time");
    h.tHead("Severity");
    h.tHead("Type");
    h.tHead("Run");
    h.tHead("Message");
    h.tHead("Exception");
    h.eRow();
    
    h.openBuffer();
    while(rs.next()){
      int severity=rs.getInt(2);
      int run = rs.getInt(4);
      String message = rs.getString(5);
      String exception = rs.getString(6);

      h.sRowBuffer();
      h.tDataBuffer(rs.getDate(1)+" "+rs.getTime(1));
      h.tDataBuffer(fontTagForSeverity(severity)+
		    Logger.SEVERITIES[severity]+"</FONT>");
      h.tDataBuffer(Logger.TYPES[rs.getInt(3)]);
      h.tDataBuffer(run==0?"":Integer.toString(run));
      h.tDataBuffer(fontTagForSeverity(severity)+
		    ((message==null)?"":message)+
		    "</FONT>");
      h.tDataBuffer((exception==null)?"":exception);
      h.eRowBuffer();
    }
    h.closeBuffer();

    h.eTable();
  }
  
  
  protected void sendDelLog(HTMLizer h, Statement s) 
    throws IOException, SQLException{
    if(getQueryWithPrefix("confirm=")!=1){
      if(run<1){
	header(h,"Confirm delete complete log");
	h.sCenter();
	h.p("<B>"+h.popupStr(getURL(COM_LISTLOG),
			     getURL(COM_DELLOG)+"?confirm=1",
			     "delWindow",
			     "Confirm delete complete log")+"</B>");
	h.eCenter();
	footer(h);
      }else{
	header(h,"Confirm delete log("+run+")");
	h.sCenter();
	h.p("<B>"+h.popupStr(getURLRun(COM_LISTLOG),
			     getURLRun(COM_DELLOG)+"?confirm=1",
			     "delWindow",
			     "Confirm delete log "+run)+"</B>");
	h.eCenter();
	footer(h);
      }
    }else{
      if(run<1){
	header(h,"Deleting all logs");
	h.sCenter();
	h.dismissLink();
	h.eCenter();
	h.print(deleteAllLogs(s));
	footer(h);
      }else if(run>0){
	header(h,"Delete logs for run "+run);
	h.sCenter();
	h.dismissLink();
	h.eCenter();
	h.print(deleteLogForRun(s,run));
	footer(h);
      }else{
	sendInvalidQuery(h);
      }
    }
  }
  
  protected String deleteAllLogs(Statement s) throws SQLException{
    //Now remove from the run table:
    s.executeUpdate("DELETE FROM "+DBIDLogger.LOG_TABLE_NAME);
    return"Deleted all row(s) from log table\n";
  }
  
  protected String deleteLogForRun(Statement s, int runID)
    throws SQLException{  
    //Now remove from the run table:
    int rows;
    if((rows=s.executeUpdate("DELETE FROM "+DBIDLogger.LOG_TABLE_NAME+
			     " WHERE "+DBIDLogger.COL_ID+"="+
			     runID))>0){
      return "Deleted "+rows+" row(s) from log table\n";
    }else{
      return "<FONT COLOR=RED>"+
	"Could not delete any rows from log table</FONT>\n";
    }
  }
  
  protected void sendHaltRun(HTMLizer h, Statement s) throws IOException{
    if(run>0){
      if(getQueryWithPrefix("confirm=")!=1){
	header(h,"Confirm halt run("+run+")");
	h.sCenter();
	h.p("<B>"+h.popupStr(getURL(COM_LISTRUNS),
			     getURLRun(COM_HALTRUN)+"?confirm=1",
			     "haltWindow",
			     "Confirm halt run "+run)+"</B>");
	h.eCenter();
	footer(h);
      }else{
	header(h,"Halting run "+run);
	if(controller.haltRun(run))
	  h.p("<B>Success</B>");
	else{
	  h.print(fontTagForSeverity(ERROR));
	  h.print("<B>Failure</B>");
	  h.print("</FONT>");
	}
	h.dismissLink();
	emptyFooter(h);
      }
    }else{
      sendInvalidQuery(h);
    }
  }

  //Helpers:

  //Static members:
  /////////////////

  protected static String fontTagForSeverity(int severity){
    StringBuffer sb=new StringBuffer();
    sb.append("<FONT COLOR=\"");
    sb.append(colorForSeverity(severity));
    sb.append("\">");
    return sb.toString();
  }

  protected static String colorForSeverity(int severity){
    switch(severity){
    case FATAL:
    case ERROR:
      return "#B00000";//RED
    case WARNING:
      return "#C05000";//RED-ORANGE
    case IMPORTANT:
      return "#A0A000";//DARK-YELLOW
    case NORMAL:
      return "#009000";//Green
    case MINOR:
      return "BLACK";
    case TRIVIAL:
      return "#a0a0a0";//GRAY
    default:
      return "BLACK";
    }
  }

  //InnerClasses:
  ///////////////
}
