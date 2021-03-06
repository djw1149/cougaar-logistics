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

/**
 * Cargo, broken down by asset class
 *
 * @since 2/26/01
 **/
public class UnitCargoByClassTest extends UnitCargoAmountTest{
  
  //Constants:
  ////////////

  //Variables:
  ////////////

  boolean groupByUnit;
  boolean groupByType;

    public static final int UNIT  = 1;
    public static final int CLASS = 2;
    public static final int TYPE  = 3;
    public static final int MOVED = 4;

  protected boolean debug = 
	"true".equals (System.getProperty("org.cougaar.mlm.ui.grabber.validator.UnitCargoByClassTest.debug", "false"));
  
  //Constructors:
  ///////////////

  public UnitCargoByClassTest(DBConfig dbConfig, boolean groupByUnit, boolean groupByType){
    super(dbConfig, false);

	this.groupByUnit = groupByUnit;
	this.groupByType = groupByType;
  }

  //Members:
  //////////

  /**for gui**/
  public String getDescription(){
    return "Amount of asset class " + getSuffix ().replace('_',' ');
  }

  /**Base name**/
  protected String getRawTableName(){
    return "Unit"+getSuffix()+"ByClassAmount";
  }

  /**Get header strings for the table**/
  public String[] getHeaders(){
    String[] headers={"Unit","Class",((groupByType) ? "" : "Example ") + "Type","AssetsMoved"};
    return headers;
  }

  /**Get the types of the columns of the table**/
  public int[] getTypes(){
    int[] types={TYPE_STRING,TYPE_STRING,TYPE_STRING,TYPE_INT};
    return types;
  }

  public int getXAxisColumn () { 
	return (groupByUnit) ? 1 : 3;
  }
  public int getYAxisColumn () { return 4; }
  public int getZAxisColumn () { 
	return 2; //(groupByUnit) ? 3 : 2;
  }
  public boolean hasThirdDimension () { 
	return groupByUnit; //(groupByUnit || groupByType); 
  }

  public boolean showMultipleGraphs () {
	return groupByUnit && groupByType;
  }
  
  /** z labels */
  //  public String [] getZAxisLabels () { return new String [] {"sea","air"}; } 

  protected void createTable(Statement s, int run) throws SQLException{
    s.executeUpdate("CREATE TABLE "+getTableName(run)+" ( "+
		    DGPSPConstants.COL_OWNER+" VARCHAR(255) NOT NULL,"+
		    DGPSPConstants.COL_ASSET_CLASS+" VARCHAR(255) NOT NULL,"+
		    DGPSPConstants.COL_ALP_NOMENCLATURE+" VARCHAR(255) NOT NULL,"+
		    COL_NUMASSETS+" INTEGER NOT NULL"+
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
		   "UnitCargoByClassTest.insertResults - Problem executing query : " + sql,
		   sqle);
    }
    try {
      while(rs.next()){
	String owner = rs.getString(1);
	if (!groupByUnit)
	  owner = "All Units";
	
	int assetclass = rs.getInt(2);
	String nomen = rs.getString(3);
	int count = rs.getInt(4);
	insertRow(l,s,run,owner,getRomanClass(assetclass),nomen,count);
      }    
    } catch (SQLException sqle) {
      l.logMessage(Logger.ERROR,Logger.DB_WRITE,
		   "UnitCargoByClassTest.insertResults - Problem walking results.",sqle);
    }
  }
  
  private String getQuery (int run) {
    String assetTable = Controller.getTableName(DGPSPConstants.ASSET_INSTANCE_TABLE,run);
    String protoTable = Controller.getTableName(DGPSPConstants.ASSET_PROTOTYPE_TABLE,run);
    String owner = DGPSPConstants.COL_OWNER;
    String aggnum = DGPSPConstants.COL_AGGREGATE;
    String instProtoid = assetTable+"."+DGPSPConstants.COL_PROTOTYPEID;
    String protoProtoid = protoTable+"."+DGPSPConstants.COL_PROTOTYPEID;
    String assetClass = DGPSPConstants.COL_ASSET_CLASS;
    String type = DGPSPConstants.COL_ALP_TYPEID;
    String nomen = DGPSPConstants.COL_ALP_NOMENCLATURE;

    String sqlQuery =
      "select "+owner+", " + assetClass + ", " + nomen + ", " + "sum("+aggnum+")\n"+
      "from "+assetTable+", "+protoTable+"\n"+
      "where "+instProtoid+" = "+protoProtoid+"\n"+
      "group by "+(groupByUnit ? (owner + ", ") : "") +assetClass + 
	  ((groupByType) ? (", " + type) : "");

    if (debug)
	  System.out.println("\n"+sqlQuery);

    return sqlQuery;
  }
  
  private void insertRow(Logger l, Statement s, int run,
			 String owner, String assetClass, String nomen, int count) throws SQLException {
    String sql = null;
    try {
      StringBuffer sb=new StringBuffer();
      sb.append("INSERT INTO ");
      sb.append(getTableName(run));
      sb.append(" (");
      sb.append(DGPSPConstants.COL_OWNER);sb.append(",");
      sb.append(DGPSPConstants.COL_ASSET_CLASS);sb.append(",");
      sb.append(DGPSPConstants.COL_ALP_NOMENCLATURE);sb.append(",");
      sb.append(COL_NUMASSETS);
      sb.append(") VALUES('");
      sb.append(owner);sb.append("','");
      sb.append(assetClass);sb.append("',");
      sb.append("'");sb.append(nomen);sb.append("',");
      sb.append(count);
      sb.append(")");
      sql = sb.toString();
      s.executeUpdate(sql);
    } catch (SQLException sqle) {
      l.logMessage(Logger.ERROR,Logger.DB_WRITE,
		   "UnitCargoByClassTest.insertRow - Problem inserting rows in table. Sql was " + sql,
		   sqle);
    }
  }

  protected String getSuffix () { 
	return ((groupByUnit) ? "by_unit" : "") +
	  ((groupByType) ? "by_type" : ""); 
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

    int result = columnCompare(logger, rs1, rs2, UNIT);

    if (result != EQUALTO) {
	return result;
    }

    result = columnCompare(logger, rs1, rs2, MOVED);

    if (result != EQUALTO) {
	return CONFLICT;
    }

    return result;
  }
}
