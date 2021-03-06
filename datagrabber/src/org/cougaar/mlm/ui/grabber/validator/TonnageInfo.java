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
package org.cougaar.mlm.ui.grabber.validator;

import  org.cougaar.mlm.ui.grabber.logger.Logger;
import  org.cougaar.mlm.ui.grabber.config.DBConfig;
import  org.cougaar.mlm.ui.grabber.connect.DGPSPConstants;
import  org.cougaar.mlm.ui.grabber.controller.Controller;

import java.sql.Statement;
import java.sql.SQLException;
import java.sql.ResultSet;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

import org.cougaar.mlm.ui.grabber.validator.Graphable;

/**
 * Looks for missing people
 *
 * @since 2/26/01
 **/
public class TonnageInfo extends AbstractTonnageInfo implements Graphable {
  
  /**
   * These constants are from :
   *	  http://www.wrsc.usace.army.mil/ndc/metric.htm
   */
  public static final double SHORT_TON_TO_METRIC = 0.90718;
  
  //Variables:
  ////////////

    public boolean debug = "true".equals (System.getProperty("TonnageInfo.debug", "false"));

  //Constructors:
  ///////////////

  public TonnageInfo(DBConfig dbConfig, boolean showUnit, boolean showClass,
				  boolean showType, boolean showLocation){
    super(dbConfig);
      init(showUnit, showClass, showType, showLocation);
  }

  public String getDescription(){
      return super.getDescription("Tonnage Information by");
  }

  /**Get header strings for the table**/
  public String[] getHeaders(){
    String[] possibleHeaders = new String[4];
    int count = 0;
    if (showUnit) possibleHeaders[count++] = "Unit";
    if (showClass) possibleHeaders[count++] = "Class";
    if (showType) possibleHeaders[count++] = "Type";
    if (showLocation) possibleHeaders[count++] = "Location";

    String[] headers= new String[count+2];
    for (int i = 0; i < count; i++) {
      headers[i] = possibleHeaders[i];
    }
    headers[count++] = "Quantity";
    headers[count] = "Tonnage (Short Tons)";
    return headers;
  }

  /**Get the types of the columns of the table**/
  public int[] getTypes(){
    int count = 0;
    if (showUnit) count++;
    if (showClass) count++;
    if (showType) count++;
    if (showLocation) count++;
    count +=2;

    int[] types= new int[count];
    for (int i = 0; i < count-2; i++) {
      types[i] = TYPE_STRING;
    }
    types[count-2] = TYPE_INT;
    types[count-1] = TYPE_TONNAGE;
    return types;
  }

  /** methods for interface Graphable */
  public int getXAxisColumn () { return ((showClass && showType) ? 2 : 1); }
  public int getYAxisColumn () { return getHeaders().length; }
  public int getZAxisColumn () { return -1; }
  public boolean hasThirdDimension () { return false; }

  /** 
   * if the third dimension is too wide, e.g. asset types, 
   * show one graph per X dimension entry 
   **/
  public boolean showMultipleGraphs () { return false; }

  protected void createTable(Statement s, int run) throws SQLException{
    s.executeUpdate("CREATE TABLE "+getTableName(run)+" ( "+
		    (showUnit?(COL_UNIT+" VARCHAR(255) NOT NULL,"):"")+
		    (showClass?(COL_CLASS+" VARCHAR(255) NOT NULL,"):"")+
		    (showType?(COL_TYPE+" VARCHAR(255) NOT NULL,"):"")+
		    (showLocation?(COL_LOCATION+" VARCHAR(255) NOT NULL,"):"")+
					DGPSPConstants.COL_AGGREGATE+" INTEGER NOT NULL,"+
					COL_TONNAGE+" DOUBLE NOT NULL"+
		    ")");
  }

  protected void insertResults (Logger l, Statement s, int run) {
    ResultSet rs=null;
    String sql = null;
    
    try {
      sql = getQuery(run);
      rs=s.executeQuery(sql);
    } catch (SQLException sqle) {
      l.logMessage(Logger.ERROR,Logger.DB_WRITE,
		   "TonnageInfo.insertResults - Problem executing query : " + sql,
		   sqle);
    }
    try {
      while(rs.next()){
	String unit = showUnit ? rs.getString(DGPSPConstants.COL_OWNER) : null;
	int assetclass = showClass ? rs.getInt(DGPSPConstants.COL_ASSET_CLASS) : -1;
	String assettype = showType ? rs.getString(DGPSPConstants.COL_ALP_NOMENCLATURE) : null;
	String location = showLocation ? rs.getString(DGPSPConstants.COL_PRETTYNAME) : null;
	double weight = rs.getDouble("sum("+DGPSPConstants.COL_WEIGHT+")");
	int quantity = rs.getInt("quantity");
	if (assetclass != DGPSPConstants.ASSET_CLASS_PERSON)
	  insertRow(l,s,run,unit,getRomanClass(assetclass),assettype,location,weight,quantity);
      }    
    } catch (SQLException sqle) {
      l.logMessage(Logger.ERROR,Logger.DB_WRITE,
		   "TonnageInfo.insertResults - Problem walking results.",sqle);
    }
  }
  
  private String getQuery (int run) {
    String assetTable = Controller.getTableName(DGPSPConstants.ASSET_INSTANCE_TABLE,run);
    String protoTable = Controller.getTableName(DGPSPConstants.ASSET_PROTOTYPE_TABLE,run);
    String itinTable = Controller.getTableName(DGPSPConstants.ASSET_ITINERARY_TABLE,run);
    String legTable = Controller.getTableName(DGPSPConstants.CONVEYED_LEG_TABLE,run);
    String locTable = Controller.getTableName(DGPSPConstants.LOCATIONS_TABLE,run);
    String cccDimTable = Controller.getTableName(DGPSPConstants.CARGO_CAT_CODE_DIM_TABLE,run);

    String weight = DGPSPConstants.COL_WEIGHT;
    String unit = DGPSPConstants.COL_OWNER;
    String assetclass = DGPSPConstants.COL_ASSET_CLASS;
    String assettype = DGPSPConstants.COL_ALP_NOMENCLATURE;
    String location = DGPSPConstants.COL_PRETTYNAME;
    String instProtoid = assetTable+"."+DGPSPConstants.COL_PROTOTYPEID;
    String aggnumber   = assetTable+"."+DGPSPConstants.COL_AGGREGATE;
    String protoProtoid = protoTable+"."+DGPSPConstants.COL_PROTOTYPEID;
    String cccDimProtoid = cccDimTable+"."+DGPSPConstants.COL_PROTOTYPEID;
    String instAssetid = assetTable+"."+DGPSPConstants.COL_ASSETID;
    String itinAssetid = itinTable+"."+DGPSPConstants.COL_ASSETID;
    String itinLegid = itinTable+"."+DGPSPConstants.COL_LEGID;
    String legLegid = legTable+"."+DGPSPConstants.COL_LEGID;
    String legLegType = legTable+"."+DGPSPConstants.COL_LEGTYPE;
    String legLoc = legTable+"."+DGPSPConstants.COL_ENDLOC;
    String locLoc = locTable+"."+DGPSPConstants.COL_LOCID;

    String sqlQuery =
      "select "+
      (showUnit?(unit+", "):"")+
      (showClass?(assetclass+", "):"")+
      (showType?(assettype+", "):"")+
      (showLocation?(location+", "):"")+
      "sum("+aggnumber+") as quantity,"+
      "sum("+weight+")\n"+
      "from "+assetTable+", "+protoTable+", " + cccDimTable +
      (showLocation?(", "+itinTable+", "+legTable+", "+locTable):"")+"\n"+
      "where "+instProtoid+" = "+protoProtoid+" and\n"+
      cccDimProtoid+" = "+protoProtoid+"\n"+
      (showLocation?("and "+instAssetid+" = "+itinAssetid+"\n"+
		     "and "+itinLegid+" = "+legLegid+"\n"+
		     "and "+legLegType+" = "+DGPSPConstants.LEG_TYPE_TRANSPORTING+"\n"+
		     "and "+legLoc+" = "+locLoc+"\n"):"")+
      ((showUnit || showClass || showType || showLocation) ? 
       (
	"group by "+
	(showUnit?(unit+((showClass || showType || showLocation)?", ":"")):"")+
	(showClass?(assetclass+((showType || showLocation)?", ":"")):"")+
	(showType?(assettype+((showLocation)?", ":"")):"")+
	(showLocation?location:"")+"\n"+
	"order by "+
	(showUnit?(unit+((showClass || showType || showLocation)?", ":"")):"")+
	(showClass?(assetclass+((showType || showLocation)?", ":"")):"")+
	(showType?(assettype+((showLocation)?", ":"")):"")+
	(showLocation?location:"")+"\n"
	):"");
	
	if (debug)
	  System.out.println ("TonnageInfo.getQuery - sql was:\n" + 
						  sqlQuery);
	
    return sqlQuery;
  }
  
  protected void insertRow(Logger l, Statement s, int run, String unit, String assetclass, 
			   String assettype, String location, double weight, int quantity) throws SQLException {
    String sql = null;
    try {
      StringBuffer sb=new StringBuffer();
      sb.append("INSERT INTO ");
      sb.append(getTableName(run));
      sb.append(" (");
      if (showUnit) {sb.append(COL_UNIT);sb.append(",");}
      if (showClass) {sb.append(COL_CLASS);sb.append(",");}
      if (showType) {sb.append(COL_TYPE);sb.append(",");}
      if (showLocation) {sb.append(COL_LOCATION);sb.append(",");}
      sb.append(DGPSPConstants.COL_AGGREGATE + ",");
      sb.append(COL_TONNAGE);
      sb.append(") VALUES(");
      if (showUnit)  {sb.append("'" + unit + "',");}
      if (showClass) {sb.append("'" + assetclass + "',");}
      if (showType)  {sb.append("'" + assettype + "',");}
	  if (showLocation) {sb.append("'" + location + "',");}

	  // weight is in grams, we want short tons = 2000 pounds
	  sb.append(quantity + ","); 
	  sb.append(dbConfig.getDBDouble((weight/1000000.0d)*CargoDimensionTest.METRIC_TO_SHORT_TON)); 
	  sb.append(")");
	  
      sql = sb.toString();
      if (debug)
		System.out.println(sql);
      s.executeUpdate(sql);
    } catch (SQLException sqle) {
      l.logMessage(Logger.ERROR,Logger.DB_WRITE,
		   "TonnageInfo.insertRow - Problem inserting rows in table. Sql was " + sql,
		   sqle);
    }
  }


  /**
   * <pre>
   * The crucial test to determine whether two rows are equal
   * in tests from two different runs.
   *
   * It does this by iterating over the columns in each row
   * and comparing them in each result set.
   * </pre>
   * @param logger to log errors to
   * @param hasLine1 is there a row in the first result set
   * @param hasLine2 is there a row in the second result set
   * @param columns many columns to compare
   */
  public int linesEqual(Logger logger,
			ResultSet rs1, boolean hasLine1, 
			ResultSet rs2, boolean hasLine2,
			int columns) {
    if (!hasLine1 && !hasLine2) return EQUALTO;
    if (!hasLine1 && hasLine2) return GREATERTHAN;
    if (hasLine1 && !hasLine2) return LESSTHAN;

    int [] types = getTypes ();
    int tonnageColumn = types.length-1;

    if (logger.isMinorEnabled()) {
      if (types[tonnageColumn] == TYPE_TONNAGE) {
	logger.logMessage(Logger.MINOR,Logger.DB_WRITE,
			  "TonnageInfo.linesEqual on column for tonnage");
      } else {
	logger.logMessage(Logger.ERROR,Logger.DB_WRITE,
			  "TonnageInfo.linesEqual on column of type " + types[tonnageColumn] + "?");
      }
    }
      
    if (showUnit) { // unit goes first
      int result = columnCompare(logger, rs1, rs2, 1);
      
      if (result != EQUALTO) {
	return result;
      }
    }

    int result = columnCompare(logger, rs1, rs2, tonnageColumn+1);

    if (result != EQUALTO) {
	if (logger.isMinorEnabled()) {
	    logger.logMessage(Logger.MINOR,Logger.DB_WRITE,
			      "TonnageInfo.linesEqual - not equal for compared rows : ");
	    logRow (logger,rs1);
	    logRow (logger,rs2);
	}
	result = CONFLICT;
    }

    return result;
  }

    
}





