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
package org.cougaar.mlm.ui.grabber.derived;

import org.cougaar.mlm.ui.grabber.logger.Logger;
import org.cougaar.mlm.ui.grabber.config.DBConfig;
import org.cougaar.mlm.ui.grabber.config.DerivedTablesConfig;
import org.cougaar.mlm.ui.grabber.controller.Controller;
import org.cougaar.mlm.ui.grabber.connect.PrepareDBTables;

import org.cougaar.mlm.ui.grabber.connect.HierarchyConstants;
import org.cougaar.mlm.ui.grabber.connect.DGPSPConstants;

import java.sql.*;

/**
 * Creates derived tables used by TPFDD viewer to speed up queries.
 * Tables generated by this class should be considered supplementary.
 * Users of the DB should check for their presence before using, and should
 * NOT REQUIRE their presence.
 *
 * @author Benjamin Lubin; last modified by: $Author: gvidaver $
 *
 * @since 4/06/01
 **/
public class PrepareDerivedTables extends PrepareDBTables{

  //Constants:
  ////////////

  //Table names:
  /**This table contains assetid, legid, convid, for the FIRST leg for each asset.  
   **/
  public static final String FIRST_LEG="firstleg";

  public static final String ROLLUP="rollup";

  public static final String CARGO_TYPE="cargotype";
  public static final String CARGO_INSTANCE="cargoinstance";
  public static final String CARGO_LEG="cargoleg";

  public static final String CARRIER_TYPE="carriertype";
  public static final String CARRIER_INSTANCE="carrierinstance";

  public static final String[] DERIVED_TABLES = {FIRST_LEG,
						 ROLLUP,
						 CARGO_TYPE,
						 CARGO_INSTANCE,
						 CARGO_LEG,
						 CARRIER_TYPE,
						 CARRIER_INSTANCE};
  //Column names:

  //Supporting tables:  
  public static final String[] FIRST_LEG_COLS={DGPSPConstants.COL_ASSETID,
					       DGPSPConstants.COL_LEGID,
					       DGPSPConstants.COL_CONVEYANCEID,
					       DGPSPConstants.COL_STARTTIME};
  public static final String[] FIRST_LEG_INDICES={DGPSPConstants.COL_ASSETID,
						  DGPSPConstants.COL_LEGID,
						  DGPSPConstants.COL_CONVEYANCEID};

  //Hierarchy Queries:
  public static final String[] ROLLUP_COLS={HierarchyConstants.COL_RELID,
					    DGPSPConstants.COL_STARTTIME,
					    DGPSPConstants.COL_ENDTIME};
  public static final String[] ROLLUP_INDICES={HierarchyConstants.COL_RELID};

  //Cargo Queries:

  public static final String COL_START_GEOLOC = "start_"+DGPSPConstants.COL_GEOLOC;
  public static final String COL_START_PRETTYNAME = "start_"+DGPSPConstants.COL_PRETTYNAME;
  public static final String COL_END_GEOLOC = "end_"+DGPSPConstants.COL_GEOLOC;
  public static final String COL_END_PRETTYNAME = "end_"+DGPSPConstants.COL_PRETTYNAME;
  public static final String COL_CONV_PROTOTYPEID = "conv_"+DGPSPConstants.COL_PROTOTYPEID;
  public static final String COL_INST_PROTOTYPEID = "inst_"+DGPSPConstants.COL_PROTOTYPEID;

  public static final String[] CARGO_TYPE_COLS={DGPSPConstants.COL_OWNER,
						DGPSPConstants.COL_ALP_TYPEID,
						DGPSPConstants.COL_ALP_NOMENCLATURE,
						COL_INST_PROTOTYPEID,
						DGPSPConstants.COL_AGGREGATE,
						COL_CONV_PROTOTYPEID,
						DGPSPConstants.COL_CONVEYANCEID,
						DGPSPConstants.COL_ASSETID,
						DGPSPConstants.COL_WEIGHT,
						DGPSPConstants.COL_WIDTH,
						DGPSPConstants.COL_HEIGHT,
						DGPSPConstants.COL_DEPTH};

  public static final String[] CARGO_TYPE_INDICES={DGPSPConstants.COL_OWNER,
						   DGPSPConstants.COL_ALP_TYPEID,
						   DGPSPConstants.COL_ALP_NOMENCLATURE,
						   COL_INST_PROTOTYPEID,
						   COL_CONV_PROTOTYPEID,
						   DGPSPConstants.COL_CONVEYANCEID,
						   DGPSPConstants.COL_ASSETID};

  /**Only works for UnitQuery right now...
   * Also, this table is huge and slow to create.  
   * Probably won't have it turned on
   **/
  public static final String[] CARGO_INSTANCE_COLS={DGPSPConstants.COL_OWNER,
						    DGPSPConstants.COL_ASSETID,
						    DGPSPConstants.COL_PROTOTYPEID,
						    DGPSPConstants.COL_NAME,
						    DGPSPConstants.COL_AGGREGATE,
						    HierarchyConstants.COL_PRETTY_NAME,
						    DGPSPConstants.COL_ALP_NOMENCLATURE,
						    DGPSPConstants.COL_STARTTIME};
  /**For now, just make them all -- this should be changed to only those that are needed**/
  public static final String[] CARGO_INSTANCE_INDICES=CARGO_INSTANCE_COLS;
  
  public static final String[] CARGO_LEG_COLS={DGPSPConstants.COL_OWNER,
					       DGPSPConstants.COL_LEGID,
					       DGPSPConstants.COL_STARTTIME,
					       DGPSPConstants.COL_ENDTIME,
					       DGPSPConstants.COL_READYAT,
					       COL_START_GEOLOC,
					       COL_START_PRETTYNAME,
					       COL_END_GEOLOC,
					       COL_END_PRETTYNAME,
					       DGPSPConstants.COL_CONVEYANCE_TYPE,
					       DGPSPConstants.COL_LEGTYPE,
					       DGPSPConstants.COL_ASSETID,
					       DGPSPConstants.COL_CONVEYANCEID,
					       DGPSPConstants.COL_PROTOTYPEID,
					       DGPSPConstants.COL_ALP_NOMENCLATURE,
					       DGPSPConstants.COL_BUMPERNO,
					       DGPSPConstants.COL_AGGREGATE,
					       COL_CONV_PROTOTYPEID,
					       COL_INST_PROTOTYPEID};
  
  public static final String[] CARGO_LEG_INDICES={DGPSPConstants.COL_OWNER,
						  COL_CONV_PROTOTYPEID,
						  DGPSPConstants.COL_CONVEYANCEID,
						  COL_INST_PROTOTYPEID,
						  DGPSPConstants.COL_ASSETID,
						  DGPSPConstants.COL_STARTTIME};
  
  //Carrier Queries:

  public static final String[] CARRIER_TYPE_COLS={DGPSPConstants.COL_OWNER,
						  DGPSPConstants.COL_ALP_TYPEID,
						  DGPSPConstants.COL_ALP_NOMENCLATURE,
						  DGPSPConstants.COL_PROTOTYPEID,
						  DGPSPConstants.COL_SELFPROP};
  public static final String[] CARRIER_TYPE_INDICES={DGPSPConstants.COL_OWNER,
						     DGPSPConstants.COL_SELFPROP,
						     DGPSPConstants.COL_ALP_NOMENCLATURE};

  public static final String[] CARRIER_INSTANCE_COLS={DGPSPConstants.COL_OWNER,
						      DGPSPConstants.COL_CONVEYANCEID,
						      DGPSPConstants.COL_PROTOTYPEID,
						      DGPSPConstants.COL_BUMPERNO};
  public static final String[] CARRIER_INSTANCE_INDICES={DGPSPConstants.COL_OWNER,
							 DGPSPConstants.COL_BUMPERNO};

  public static final String[][] DERIVED_TABLE_COLS={ROLLUP_COLS,
						     CARGO_TYPE_COLS,
						     CARGO_INSTANCE_COLS,
						     CARGO_LEG_COLS,
						     CARRIER_TYPE_COLS,
						     CARRIER_INSTANCE_COLS};

  /** DON'T FORGET TO UPDATE THIS VARIABLE WHEN ADDING/REMOVING TABLES!**/
  public static final String[][] DERIVED_TABLE_INDICES={FIRST_LEG_INDICES,
							ROLLUP_INDICES,
							CARGO_TYPE_INDICES,
							CARGO_INSTANCE_INDICES,
							CARGO_LEG_INDICES,
							CARRIER_TYPE_INDICES,
							CARRIER_INSTANCE_INDICES};

  /**Used to describe the tables**/
  public static final String[] DERIVED_TABLE_DESCS={"Unused: contains first legid/convid for each asset",
						    "Holds rollup info for hierarchy",
						    "Cargo prototype info, by asset, conv and owner",
						    "Cargo instance info, currently not implemented for filter/carrier",
						    "Cargo leg info.  Our biggest win--you probably want this one",
						    "Carrier prototype info",
						    "Carrier instance info"};

  /**Debug level to print out queries at**/
  public static final int queryOutputLevel=Logger.MINOR;

  //Variables:
  ////////////

  private DerivedTablesConfig dtConfig;

  private boolean buildTable[]=new boolean[DERIVED_TABLES.length];
  
  //Constructors:
  ///////////////

  public PrepareDerivedTables(int id, int runID, 
			      DBConfig dbConfig,
			      Connection dbConnection,
			      DerivedTablesConfig dtConfig,
			      Logger logger){
    super(id,runID,dbConfig,dbConnection,logger);
    this.dtConfig=dtConfig;
    for(int i=0;i<buildTable.length;i++)
      buildTable[i]=false;
  }

  //Members:
  //////////

  public boolean isDerivedTablePresent(String tableName){
    return isTablePresent(getDBConnection(), getDerivedTableName(tableName));
  }

  /**Determine if the database needs preparation**/
  protected boolean needPrepareDB(Connection c){
    boolean ret=false;
    for(int i=0;i<DERIVED_TABLES.length;i++){
      buildTable[i]=dtConfig.getDoTable(DERIVED_TABLES[i])&&
	!isDerivedTablePresent(DERIVED_TABLES[i]);
      ret|=buildTable[i];
    }
    return ret;
  }

  /**Prepare it, since we need preparation**/
  protected void prepareDB(Connection c){
    Statement s=null;
    try{
      s = c.createStatement();
    }catch(SQLException e){
      haltForError(Logger.DB_STRUCTURE,"Could not create Statement",e);
      return;
    }

    long startTime=System.currentTimeMillis();

    long time;

    for(int i=0;i<DERIVED_TABLES.length;i++){
      if(buildTable[i]){
	createTable(s,DERIVED_TABLES[i]);
      }else{
	logMessage(Logger.NORMAL,Logger.GENERIC,"Skipping creation of table: "+
	       getDerivedTableName(DERIVED_TABLES[i]));
      }
    }

    logMessage(Logger.NORMAL, Logger.DB_WRITE,"Preparation of derived tables took "+
	       (System.currentTimeMillis()-startTime) + " total millis");
    try{
      if(s!=null)
	s.close();
    }catch(SQLException e){
      logMessage(Logger.ERROR,Logger.DB_WRITE,"Could not close Statement",e);
    }
  }
  
  //Actions:

  public boolean createTable(Statement s, String tableName){
    int idx=getIndexForTableName(tableName);
    if(idx==-1)
      return false;

    long time=System.currentTimeMillis();
    
    //Build appropriate table:
    if(tableName.equals(FIRST_LEG)){
      if(!isDerivedTablePresent(FIRST_LEG)){
	createFirstLegTable(s);
	selectIntoTable(s, getDerivedTableName(FIRST_LEG), getColumnString(FIRST_LEG_COLS),getFirstLegSql());
      }
    }else if(tableName.equals(ROLLUP)){
      createRollupTable(s);
      selectIntoTable(s, getDerivedTableName(ROLLUP), getColumnString(ROLLUP_COLS),getRollupSql());
    }else if(tableName.equals(CARGO_TYPE)){
      createCargoTypeTable(s, getDerivedTableName(CARGO_TYPE));
      selectIntoTable(s, getDerivedTableName(CARGO_TYPE), getColumnString(CARGO_TYPE_COLS), 
		      getCargoTypeSql(false));
      selectIntoTable(s, getDerivedTableName(CARGO_TYPE), getColumnString(CARGO_TYPE_COLS), 
		      getCargoTypeSql(true));
    }else if(tableName.equals(CARGO_INSTANCE)){
      createTableSelect(s, getDerivedTableName(CARGO_INSTANCE), getCargoInstanceSql1());
      selectIntoTable(s, getDerivedTableName(CARGO_INSTANCE), getColumnString(CARGO_INSTANCE_COLS), 
		      getCargoInstanceSql2());
    }else if(tableName.equals(CARGO_LEG)){
      createCargoLegTable(s);
      selectIntoTable(s, getDerivedTableName(CARGO_LEG), getColumnString(CARGO_LEG_COLS), 
		      getCargoLegSql(false));
      selectIntoTable(s, getDerivedTableName(CARGO_LEG), getColumnString(CARGO_LEG_COLS), 
		      getCargoLegSql(true));
    }else if(tableName.equals(CARRIER_TYPE)){
      createTableSelect(s, getDerivedTableName(CARRIER_TYPE), getCarrierTypeSql());
    }else if(tableName.equals(CARRIER_INSTANCE)){
      createTableSelect(s, getDerivedTableName(CARRIER_INSTANCE), getCarrierInstanceSql());
    }
    
    time=System.currentTimeMillis()-time;
    logMessage(Logger.NORMAL,Logger.DB_WRITE,"Created table "+getDerivedTableName(tableName)+
	       " in "+time+" millis.");
    //Create the tables indices:
    createIndexForAllColumns(s,getDerivedTableName(tableName),DERIVED_TABLE_INDICES[idx]);

    return true;
  }

  public void dropTable (Statement s, String tableName) {
    if(isDerivedTablePresent(tableName)){
      try{
	logMessage(Logger.NORMAL, Logger.DB_WRITE,
		   "Dropping table " + getDerivedTableName(tableName));
	s.executeQuery("drop table "+getDerivedTableName(tableName));
      }catch(SQLException e){
	logMessage(Logger.ERROR,Logger.DB_WRITE,
		   "Could not drop table "+getDerivedTableName(tableName),
		   e);
      }
    }
  }

  public void createIndexForAllColumns(Statement s, String tableName, String[] cols){
    for(int i=0;i<cols.length;i++){
      createIndex(s,tableName,cols[i]);
    }
  }

  //Gets:

  public int getIndexForTableName(String tableName){
    for(int i=0;i<DERIVED_TABLES.length;i++){
      if(tableName.equals(DERIVED_TABLES[i]))
	return i;
    }
    return -1;
  }

  public String getDerivedTableName(String baseName){
    return Controller.getTableName("derived_"+baseName,getRunID());
  }

  public String getTableName(String baseName){
    return Controller.getTableName(baseName,getRunID());
  }

  public String getColumnString(String[] cols){
    StringBuffer sb=new StringBuffer();
    for(int i=0;i<cols.length;i++){
      sb.append(cols[i]);
      if(i<cols.length-1)
	sb.append(", ");
    }
    return sb.toString();
  }

  //SQL Generation:

  public void eqClause(StringBuffer sb, String arg1, String arg2){
    sb.append(arg1);
    sb.append(" = ");
    sb.append(arg2);
  }

  public void and(StringBuffer sb){
    sb.append(" AND ");
  }

  public void comClause(StringBuffer sb, String arg1){
    sb.append(", ");
    sb.append(arg1);
  }

  //FIRST_LEG:
  protected void createFirstLegTable(Statement s){
    String tableName=getDerivedTableName(FIRST_LEG);
    StringBuffer sb=new StringBuffer();
    sb.append("create table ");
    sb.append(tableName);
    sb.append(" ( ");
    sb.append(DGPSPConstants.COL_ASSETID);
    sb.append(" VARCHAR(255)");
    sb.append(", ");
    sb.append(DGPSPConstants.COL_LEGID);
    sb.append(" VARCHAR(255)");
    sb.append(", ");
    sb.append(DGPSPConstants.COL_CONVEYANCEID);
    sb.append(" VARCHAR(255)");
    sb.append(", ");
    sb.append(DGPSPConstants.COL_STARTTIME);
    sb.append(" "+dbConfig.getDateTimeType());
    sb.append(")");
    prepareTable(s, tableName, sb.toString());
  }

  protected String getFirstLegSql(){
    StringBuffer sb=new StringBuffer();
    String conveyedLegTable = getTableName(DGPSPConstants.CONVEYED_LEG_TABLE);
    String assetItineraryTable = getTableName(DGPSPConstants.ASSET_ITINERARY_TABLE);

    String itineraryAsset    = assetItineraryTable + "." + DGPSPConstants.COL_ASSETID;
    String itineraryLeg      = assetItineraryTable + "." + DGPSPConstants.COL_LEGID;
    String conveyedLegLeg    = conveyedLegTable + "." + DGPSPConstants.COL_LEGID;
    String conveyedLegConvID = conveyedLegTable + "." + DGPSPConstants.COL_CONVEYANCEID;
    String conveyedLegStart  = conveyedLegTable + "." + DGPSPConstants.COL_STARTTIME;

    sb.append("select ");
    sb.append(itineraryAsset);sb.append(", ");
    sb.append(conveyedLegLeg);sb.append(", ");
    sb.append(conveyedLegConvID);sb.append(", ");
    sb.append("min(");sb.append(conveyedLegStart);sb.append(")");
    sb.append(" from ");
    sb.append(conveyedLegTable);sb.append(", ");
    sb.append(assetItineraryTable);
    sb.append(" where ");
    sb.append(itineraryLeg);sb.append(" = ");
    sb.append(conveyedLegLeg);
    sb.append(" group by ");
    sb.append(itineraryAsset);

    logMessage(queryOutputLevel, Logger.DB_WRITE,"First Leg Query: "+sb.toString());	
    return sb.toString();
  }

  //ROLLUP:

  protected void createRollupTable(Statement s){
    String tableName=getDerivedTableName(ROLLUP);
    StringBuffer sb=new StringBuffer();
    sb.append("create table ");
    sb.append(tableName);
    sb.append(" ( ");
    sb.append(HierarchyConstants.COL_RELID);
    sb.append(" VARCHAR(255)");
    sb.append(", ");
    sb.append(DGPSPConstants.COL_STARTTIME);
    sb.append(" "+dbConfig.getDateTimeType());
    sb.append(", ");
    sb.append(DGPSPConstants.COL_ENDTIME);
    sb.append(" "+dbConfig.getDateTimeType());
    sb.append(")");
    prepareTable(s, tableName, sb.toString());
  }


  protected String getRollupSql(){
    String orgTable = getTableName(HierarchyConstants.ORG_TABLE_NAME);
    String conveyedLegTable = getTableName(DGPSPConstants.CONVEYED_LEG_TABLE);
    String assetInstanceTable = getTableName(DGPSPConstants.ASSET_INSTANCE_TABLE);
    String assetItineraryTable = getTableName(DGPSPConstants.ASSET_ITINERARY_TABLE);
    
    // result columns
    String conveyedLegStart  = conveyedLegTable + "." + DGPSPConstants.COL_STARTTIME;
    String conveyedLegEnd    = conveyedLegTable + "." + DGPSPConstants.COL_ENDTIME;
    String orgTableRel       = orgTable + "." + HierarchyConstants.COL_RELID;
    
    String instanceOwner     = assetInstanceTable + "." + DGPSPConstants.COL_OWNER;
    String instanceAsset     = assetInstanceTable + "." + DGPSPConstants.COL_ASSETID;
    String itineraryAsset    = assetItineraryTable + "." + DGPSPConstants.COL_ASSETID;
    String itineraryLeg      = assetItineraryTable + "." + DGPSPConstants.COL_LEGID;
    String conveyedLegLeg    = conveyedLegTable + "." + DGPSPConstants.COL_LEGID;
    
    String sqlQuery = 
      "select " + orgTableRel + ", min(" + conveyedLegStart + "), max(" + conveyedLegEnd + ")" +
      " from "  + orgTable + ", " + assetInstanceTable + ", " + assetItineraryTable + ", " + conveyedLegTable +
      " where " + orgTableRel + " = " + instanceOwner + " and " + 
      instanceAsset + " = " + itineraryAsset + " and " + 
      itineraryLeg + " = " + conveyedLegLeg +
      " group by " + orgTableRel;

    logMessage(queryOutputLevel, Logger.DB_WRITE,"Rollup Query: "+sqlQuery);	
    return sqlQuery;
  }

  //CARGO_TYPE:

  public void createCargoTypeTable(Statement s, String tableName){
    StringBuffer sb=new StringBuffer();
    sb.append("create table ");
    sb.append(tableName);
    sb.append(" ( ");
    sb.append(DGPSPConstants.COL_OWNER);
    sb.append(" VARCHAR(255), ");
    sb.append(DGPSPConstants.COL_ALP_TYPEID);
    sb.append(" VARCHAR(255), ");
    sb.append(DGPSPConstants.COL_ALP_NOMENCLATURE);
    sb.append(" VARCHAR(255), ");
    sb.append(COL_INST_PROTOTYPEID);
    sb.append(" VARCHAR(255), ");
    sb.append(DGPSPConstants.COL_AGGREGATE);
    sb.append(" INTEGER, ");
    sb.append(COL_CONV_PROTOTYPEID);
    sb.append(" VARCHAR(255), ");
    sb.append(DGPSPConstants.COL_CONVEYANCEID);
    sb.append(" VARCHAR(255), ");
    sb.append(DGPSPConstants.COL_ASSETID);
    sb.append(" VARCHAR(255), ");
    sb.append(DGPSPConstants.COL_WEIGHT);
    sb.append(" DOUBLE, ");
    sb.append(DGPSPConstants.COL_WIDTH);
    sb.append(" DOUBLE, ");
    sb.append(DGPSPConstants.COL_HEIGHT);
    sb.append(" DOUBLE, ");
    sb.append(DGPSPConstants.COL_DEPTH);
    sb.append(" DOUBLE ");
    sb.append(")");
    prepareTable(s, tableName, sb.toString());
  }

  public String getCargoTypeSql(boolean protoParentNull){
    String assetInstanceTable = getTableName(DGPSPConstants.ASSET_INSTANCE_TABLE);
    String assetProtoTable = getTableName(DGPSPConstants.ASSET_PROTOTYPE_TABLE);
    String conveyanceInstanceTable = getTableName(DGPSPConstants.CONV_INSTANCE_TABLE);
    String conveyancePrototypeTable = getTableName(DGPSPConstants.CONV_PROTOTYPE_TABLE);
    String itinTable = getTableName(DGPSPConstants.ASSET_ITINERARY_TABLE);
    String cLegTable = getTableName(DGPSPConstants.CONVEYED_LEG_TABLE);
    
    String instanceOwner      = assetInstanceTable + "." + DGPSPConstants.COL_OWNER;
    String instanceProto      = assetInstanceTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    String instanceID         = assetInstanceTable + "." + DGPSPConstants.COL_ASSETID;
    String instanceAggNumber  = assetInstanceTable + "." + DGPSPConstants.COL_AGGREGATE;

    String self1TypeID       = "self1" + "." + DGPSPConstants.COL_ALP_TYPEID;
    String self1Nomen        = "self1" + "." + DGPSPConstants.COL_ALP_NOMENCLATURE;
    String self1Proto        = "self1" + "." + DGPSPConstants.COL_PROTOTYPEID;
    String self1ParentProto  = "self1" + "." + DGPSPConstants.COL_PARENT_PROTOTYPEID;

    String self2TypeID       = "self2" + "." + DGPSPConstants.COL_ALP_TYPEID;
    String self2Nomen        = "self2" + "." + DGPSPConstants.COL_ALP_NOMENCLATURE;
    String self2Proto        = "self2" + "." + DGPSPConstants.COL_PROTOTYPEID;
    String self2ParentProto  = "self2" + "." + DGPSPConstants.COL_PARENT_PROTOTYPEID;

    String prototypeWeight = "self1." + DGPSPConstants.COL_WEIGHT;
    String prototypeWidth = "self1." + DGPSPConstants.COL_WIDTH;
    String prototypeHeight = "self1." + DGPSPConstants.COL_HEIGHT;
    String prototypeDepth = "self1." + DGPSPConstants.COL_DEPTH;

    String ciConvID      = conveyanceInstanceTable + "." + DGPSPConstants.COL_CONVEYANCEID;
    String ciPrototypeID = conveyanceInstanceTable + "." + DGPSPConstants.COL_PROTOTYPEID;

    String itinID  = itinTable + "." + DGPSPConstants.COL_ASSETID;
    String itinLeg = itinTable + "." + DGPSPConstants.COL_LEGID;

    String cLegID      = cLegTable + "." + DGPSPConstants.COL_LEGID;
    String cLegConvID  = cLegTable + "." + DGPSPConstants.COL_CONVEYANCEID;

    String typeID = (protoParentNull)?self1TypeID:self2TypeID;
    String nomen =(protoParentNull)?self1Nomen:self2Nomen;
    String proto =(protoParentNull)?self1Proto:self2Proto;
    String protocolTest = (protoParentNull)?(self1ParentProto+" is null "):
      (self1ParentProto+" is not null AND "+self1ParentProto+" = "+self2Proto+" ");
    String protoTables=(protoParentNull)?(assetProtoTable+" self1"):
      (assetProtoTable+" self1, "+assetProtoTable+" self2");

    String sqlQuery = 
      "select distinct " + 
      instanceOwner + ", " +
      typeID + ", " + nomen + ", " + proto + 
      ", " + instanceAggNumber + ", " +
      ciPrototypeID + ", " + ciConvID + ", " + instanceID + 
      ",\n " + prototypeWeight +", " + prototypeWidth +", " + prototypeHeight +", " + prototypeDepth +      
      "\nfrom "  + assetInstanceTable + ", " + protoTables + ", " + 
      conveyanceInstanceTable + ", " +
      itinTable + ", " + cLegTable +
      
      "\nwhere "+
      instanceID + " = " + itinID +
      "\nand " + itinLeg + " = " + cLegID +
      "\nand " + cLegConvID + " = " + ciConvID +
      "\nand " + instanceProto + " = " + self1Proto + 
      "\nand " + protocolTest ;//+
      //      " group by " + typeID + ((aggregateByUnit) ? ", " + instanceOwner : "");

    logMessage(queryOutputLevel, Logger.DB_WRITE,"CargoType "+((protoParentNull)?"Direct":"Parent")+
	       " Query: "+sqlQuery);
    return sqlQuery;
  }

  //CARGO_INSTANCE:

  protected String getCargoInstanceSql1(){
    StringBuffer sb=new StringBuffer();

    String assetInstanceTable = getTableName(DGPSPConstants.ASSET_INSTANCE_TABLE);
    String assetProtoTable = getTableName(DGPSPConstants.ASSET_PROTOTYPE_TABLE);

    String prototypeProto = assetProtoTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    String prototypeParentProto = assetProtoTable + "." + DGPSPConstants.COL_PARENT_PROTOTYPEID;
    String prototypeNomen = assetProtoTable + "." + DGPSPConstants.COL_ALP_NOMENCLATURE;
    
    String instanceID        = assetInstanceTable + "." + DGPSPConstants.COL_ASSETID;
    String instanceProto     = assetInstanceTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    String instanceName      = assetInstanceTable + "." + DGPSPConstants.COL_NAME;
    String instanceAggNumber = assetInstanceTable + "." + DGPSPConstants.COL_AGGREGATE;
    String instanceOwner     = assetInstanceTable + "." + DGPSPConstants.COL_OWNER;
    
    String orgNames = getTableName(HierarchyConstants.ORGNAMES_TABLE_NAME);
    String orgNamesName = orgNames + "." + HierarchyConstants.COL_PRETTY_NAME;
    String orgNamesOrg  = orgNames + "." + HierarchyConstants.COL_ORGID;

    String assetItineraryTable = getTableName(DGPSPConstants.ASSET_ITINERARY_TABLE);
    String itinLeg     = assetItineraryTable + "." + DGPSPConstants.COL_LEGID;
    String itinID      = assetItineraryTable + "." + DGPSPConstants.COL_ASSETID;
    String conveyedLegTable = getTableName(DGPSPConstants.CONVEYED_LEG_TABLE);
    String cLegID      = conveyedLegTable + "." + DGPSPConstants.COL_LEGID;
    String cLegStart   = conveyedLegTable + "." + DGPSPConstants.COL_STARTTIME;
    
    sb.append("select distinct ");
    sb.append(instanceOwner);
    comClause(sb, instanceID);
    comClause(sb, instanceProto);
    comClause(sb, instanceName);
    comClause(sb, instanceAggNumber);
    comClause(sb, orgNamesName);
    comClause(sb, prototypeNomen);
    comClause(sb, cLegStart);
    sb.append(" from ");
    sb.append(assetInstanceTable);
    comClause(sb, assetProtoTable);
    comClause(sb, orgNames);
    comClause(sb, assetItineraryTable);
    comClause(sb, conveyedLegTable);
    sb.append(" where ");
    eqClause(sb,instanceProto, prototypeProto);
    and(sb);
    eqClause(sb,orgNamesOrg, instanceOwner);
    and(sb);
    eqClause(sb,instanceID, itinID);
    and(sb);
    eqClause(sb,itinLeg,cLegID); 
    and(sb);
    sb.append(prototypeParentProto);
    sb.append(" is null ");
    
    //"\norder by " + orgNamesOrg + ", " + ((sortByName) ? instanceName + ", " : "") + 
    //prototypeNomen + ", " + cLegStart;
    
    logMessage(queryOutputLevel, Logger.DB_WRITE,"CargoInstance 1 Query: "+sb.toString());
    return sb.toString();
  }

  protected String getCargoInstanceSql2 (){
    StringBuffer sb=new StringBuffer();

    String assetInstanceTable = getTableName(DGPSPConstants.ASSET_INSTANCE_TABLE);
    String assetProtoTable = getTableName(DGPSPConstants.ASSET_PROTOTYPE_TABLE);
    
    String prototypeProto = assetProtoTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    String prototypeParentProto = assetProtoTable + "." + DGPSPConstants.COL_PARENT_PROTOTYPEID;
    String prototypeNomen = assetProtoTable + "." + DGPSPConstants.COL_ALP_NOMENCLATURE;
    
    String instanceID        = assetInstanceTable + "." + DGPSPConstants.COL_ASSETID;
    String instanceProto     = assetInstanceTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    String instanceName      = assetInstanceTable + "." + DGPSPConstants.COL_NAME;
    String instanceAggNumber = assetInstanceTable + "." + DGPSPConstants.COL_AGGREGATE;
    String instanceOwner     = assetInstanceTable + "." + DGPSPConstants.COL_OWNER;
    
    String orgNames = getTableName(HierarchyConstants.ORGNAMES_TABLE_NAME);
    String orgNamesName = orgNames + "." + HierarchyConstants.COL_PRETTY_NAME;
    String orgNamesOrg  = orgNames + "." + HierarchyConstants.COL_ORGID;
    
    String assetItineraryTable = getTableName(DGPSPConstants.ASSET_ITINERARY_TABLE);
    String itinLeg     = assetItineraryTable + "." + DGPSPConstants.COL_LEGID;
    String itinID      = assetItineraryTable + "." + DGPSPConstants.COL_ASSETID;
    String conveyedLegTable = getTableName(DGPSPConstants.CONVEYED_LEG_TABLE);
    String cLegID      = conveyedLegTable + "." + DGPSPConstants.COL_LEGID;
    String cLegStart   = conveyedLegTable + "." + DGPSPConstants.COL_STARTTIME;

    sb.append("select distinct ");
    sb.append(instanceOwner);
    comClause(sb, instanceID);
    comClause(sb, prototypeParentProto);
    comClause(sb, instanceName);
    comClause(sb, instanceAggNumber);
    comClause(sb, orgNamesName);
    comClause(sb, prototypeNomen);
    comClause(sb, cLegStart);
    sb.append(" from ");
    sb.append(assetInstanceTable);
    comClause(sb, assetProtoTable);
    comClause(sb, orgNames);
    comClause(sb, assetItineraryTable);
    comClause(sb, conveyedLegTable);
    sb.append(" where ");
    eqClause(sb, instanceProto, prototypeProto);
    and(sb);
    eqClause(sb, orgNamesOrg, instanceOwner);
    and(sb);
    eqClause(sb, instanceID, itinID);
    and(sb);
    eqClause(sb, itinLeg, cLegID);
    and(sb);
    sb.append(prototypeParentProto);
    sb.append(" is not null ");
    //"\norder by " + orgNamesOrg + ", " + ((sortByName) ? instanceName + ", " : "") + prototypeNomen + ", " + cLegStart;

    logMessage(queryOutputLevel, Logger.DB_WRITE,"CargoInstance 2 Query: "+sb.toString());
    return sb.toString();
  }

  //CARGO_LEG:

  private String getCargoLegTableDefs(){
    StringBuffer sb=new StringBuffer();
    sb.append(DGPSPConstants.COL_OWNER);
    sb.append(" VARCHAR(255),");
    sb.append(DGPSPConstants.COL_LEGID);
    sb.append(" VARCHAR(255),");
    sb.append(DGPSPConstants.COL_STARTTIME);
    sb.append(" "+dbConfig.getDateTimeType()+",");
    sb.append(DGPSPConstants.COL_ENDTIME);
    sb.append(" "+dbConfig.getDateTimeType()+",");
    sb.append(DGPSPConstants.COL_READYAT);
    sb.append(" "+dbConfig.getDateTimeType()+",");
    sb.append(COL_START_GEOLOC);
    sb.append(" VARCHAR(255),");
    sb.append(COL_START_PRETTYNAME);
    sb.append(" VARCHAR(255),");
    sb.append(COL_END_GEOLOC);
    sb.append(" VARCHAR(255),");
    sb.append(COL_END_PRETTYNAME);
    sb.append(" VARCHAR(255),");
    sb.append(DGPSPConstants.COL_CONVEYANCE_TYPE);
    sb.append(" INTEGER,");
    sb.append(DGPSPConstants.COL_LEGTYPE);
    sb.append(" INTEGER,");
    sb.append(DGPSPConstants.COL_ASSETID);
    sb.append(" VARCHAR(255),");
    sb.append(DGPSPConstants.COL_CONVEYANCEID);
    sb.append(" VARCHAR(255),");
    sb.append(DGPSPConstants.COL_PROTOTYPEID);
    sb.append(" VARCHAR(255),");
    sb.append(DGPSPConstants.COL_ALP_NOMENCLATURE);
    sb.append(" VARCHAR(255),");
    sb.append(DGPSPConstants.COL_BUMPERNO);    
    sb.append(" VARCHAR(255),");
    sb.append(DGPSPConstants.COL_AGGREGATE);    
    sb.append(" VARCHAR(255)");

    return sb.toString();
  }

  protected void createCargoLegTable(Statement s){
    String tableName=getDerivedTableName(CARGO_LEG);
    StringBuffer sb=new StringBuffer();
    sb.append("create table ");
    sb.append(tableName);
    sb.append(" ( ");
    sb.append(getCargoLegTableDefs());
    sb.append(", ");
    sb.append(COL_CONV_PROTOTYPEID);
    sb.append(" VARCHAR(255),");
    sb.append(COL_INST_PROTOTYPEID);
    sb.append(" VARCHAR(255)");
    sb.append(")");
    prepareTable(s, tableName, sb.toString());
  }

  protected String getCargoLegSql(boolean protoParentNull) {
    StringBuffer sb = new StringBuffer();

    String locTable = getTableName(DGPSPConstants.LOCATIONS_TABLE);
    String conveyedLegTable = getTableName(DGPSPConstants.CONVEYED_LEG_TABLE);
    String assetInstanceTable = getTableName(DGPSPConstants.ASSET_INSTANCE_TABLE);
    String assetItineraryTable = getTableName(DGPSPConstants.ASSET_ITINERARY_TABLE);
    String conveyanceInstanceTable = getTableName(DGPSPConstants.CONV_INSTANCE_TABLE);
    String conveyancePrototypeTable = getTableName(DGPSPConstants.CONV_PROTOTYPE_TABLE);
    String assetProtoTable = getTableName(DGPSPConstants.ASSET_PROTOTYPE_TABLE);
    
    String assetInstanceOwner = assetInstanceTable + "." + DGPSPConstants.COL_OWNER;
    String assetInstanceID    = assetInstanceTable + "." + DGPSPConstants.COL_ASSETID;
    String instanceProto      = assetInstanceTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    String ciAggNumber        = assetInstanceTable + "." + DGPSPConstants.COL_AGGREGATE;
    String itinID             = assetItineraryTable + "." + DGPSPConstants.COL_ASSETID;
    String itinLeg            = assetItineraryTable + "." + DGPSPConstants.COL_LEGID;
    String cLegID      = conveyedLegTable + "." + DGPSPConstants.COL_LEGID;
    String cLegStartLoc= conveyedLegTable + "." + DGPSPConstants.COL_STARTLOC;
    String cLegEndLoc  = conveyedLegTable + "." + DGPSPConstants.COL_ENDLOC;
    String cLegStart   = conveyedLegTable + "." + DGPSPConstants.COL_STARTTIME;
    String cLegEnd     = conveyedLegTable + "." + DGPSPConstants.COL_ENDTIME;
    String cLegType    = conveyedLegTable + "." + DGPSPConstants.COL_LEGTYPE;
    String cLegConvID  = conveyedLegTable + "." + DGPSPConstants.COL_CONVEYANCEID;
    String cLegReadyAt = conveyedLegTable + "." + DGPSPConstants.COL_READYAT;
    
    String l1id     = "l1" + "." + DGPSPConstants.COL_LOCID;
    String l1geoloc = "l1" + "." + DGPSPConstants.COL_GEOLOC;
    String l1name   = "l1" + "." + DGPSPConstants.COL_PRETTYNAME;
    
    String l2id     = "l2" + "." + DGPSPConstants.COL_LOCID;
    String l2geoloc = "l2" + "." + DGPSPConstants.COL_GEOLOC;
    String l2name   = "l2" + "." + DGPSPConstants.COL_PRETTYNAME;
    
    String ciPrototypeID = conveyanceInstanceTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    String ciConvID      = conveyanceInstanceTable + "." + DGPSPConstants.COL_CONVEYANCEID;
    String ciBumper      = conveyanceInstanceTable + "." + DGPSPConstants.COL_BUMPERNO;
    String cpPrototypeID = conveyancePrototypeTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    String cpConvType    = conveyancePrototypeTable + "." + DGPSPConstants.COL_CONVEYANCE_TYPE;
    String cpNomen       = conveyancePrototypeTable + "." + DGPSPConstants.COL_ALP_NOMENCLATURE;
    
    String prototypeProto = assetProtoTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    String prototypeParentProto = assetProtoTable + "." + DGPSPConstants.COL_PARENT_PROTOTYPEID;
    
    String protoColToUse = (protoParentNull) ? instanceProto : prototypeParentProto;
    String protoTest =     (protoParentNull) ? " is null " : " is not null" ;
    
    sb.append("select ");
    sb.append(assetInstanceOwner + ", " +
	      cLegID + ", " + cLegStart + ", " + cLegEnd + ", " + cLegReadyAt + ", " + 
	      l1geoloc + ", " + l1name + ", " + 
	      l2geoloc + ", " + l2name + ", " + 
	      cpConvType + ", " + cLegType + ", " + assetInstanceID + ", " + 
	      ciConvID + ", " + instanceProto + ", " + cpNomen + ", " + 
	      ciBumper + ", " + ciAggNumber + ", " + 
	      cpPrototypeID + ", " + protoColToUse);
    sb.append(" from ");
    sb.append(locTable + " l1, " + locTable + " l2, " + assetInstanceTable + ", " + 
	      assetItineraryTable + ", " + conveyedLegTable + ", " + 
	      conveyanceInstanceTable + ", " + conveyancePrototypeTable + ", " + 
	      assetProtoTable);
    sb.append(" where ");
    sb.append(assetInstanceID + " = " + itinID + 
	      "\nand " + itinLeg + " = " + cLegID +
	      "\nand " + cLegStartLoc + " = " + l1id +
	      "\nand " + cLegEndLoc + " = " + l2id +
	      "\nand " + cLegConvID + " = " + ciConvID +
	      "\nand " + ciPrototypeID + " = " + cpPrototypeID +
	      "\nand " + instanceProto + " = " + prototypeProto +
	      "\nand " + prototypeParentProto + protoTest);

      //      "\norder by " + assetInstanceID + ", " + cLegStart;

      logMessage(queryOutputLevel, Logger.DB_WRITE,"Cargo Leg Query: "+sb.toString());
    
    return sb.toString();
  }

  // Carrier:

  protected String getCarrierTypeSql (){
    String assetInstanceTable  = getTableName(DGPSPConstants.ASSET_INSTANCE_TABLE);
    String assetItineraryTable = getTableName(DGPSPConstants.ASSET_ITINERARY_TABLE);
    String conveyedLegTable    = getTableName(DGPSPConstants.CONVEYED_LEG_TABLE);
    String conveyanceInstanceTable = getTableName(DGPSPConstants.CONV_INSTANCE_TABLE);
    String conveyancePrototypeTable = getTableName(DGPSPConstants.CONV_PROTOTYPE_TABLE);

    String instanceOwner  = assetInstanceTable + "." + DGPSPConstants.COL_OWNER;
    String instanceID     = assetInstanceTable + "." + DGPSPConstants.COL_ASSETID;
    String itineraryID    = assetItineraryTable + "." + DGPSPConstants.COL_ASSETID;
    String itineraryLeg   = assetItineraryTable + "." + DGPSPConstants.COL_LEGID;
    String conveyedLegLeg = conveyedLegTable + "." + DGPSPConstants.COL_LEGID;
    String conveyedLegID  = conveyedLegTable + "." + DGPSPConstants.COL_CONVEYANCEID;
    String conveyanceInstanceID = conveyanceInstanceTable + "." + DGPSPConstants.COL_CONVEYANCEID;
    String conveyanceInstanceProto = conveyanceInstanceTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    String conveyanceInstanceSelfProp = conveyanceInstanceTable + "." + DGPSPConstants.COL_SELFPROP;
    String conveyanceProtoProto = conveyancePrototypeTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    String conveyanceProtoType = conveyancePrototypeTable + "." + DGPSPConstants.COL_ALP_TYPEID;
    String conveyanceProtoNomen = conveyancePrototypeTable + "." + DGPSPConstants.COL_ALP_NOMENCLATURE;
    
    String sqlQuery = 
      "select distinct " + instanceOwner + ", " +
      conveyanceProtoType + ", " + conveyanceProtoNomen + ", " + conveyanceProtoProto + ", " + 
      conveyanceInstanceSelfProp +
      " from "  + assetInstanceTable + ", " + assetItineraryTable + ", " + conveyedLegTable + ", " + 
      conveyanceInstanceTable + ", " + conveyancePrototypeTable +
      " where " +
      instanceID + " = " + itineraryID + " and " + 
      itineraryLeg + " = " + conveyedLegLeg + " and " + 
      conveyedLegID + " = " + conveyanceInstanceID + " and " + 
      conveyanceInstanceProto + " = " + conveyanceProtoProto;

    logMessage(queryOutputLevel, Logger.DB_WRITE,"CarrierType Query: "+sqlQuery);
	
    return sqlQuery;
  }

  protected String getCarrierInstanceSql(){
    String conveyanceInstanceTable = getTableName(DGPSPConstants.CONV_INSTANCE_TABLE);
    String conveyedLegTable = getTableName(DGPSPConstants.CONVEYED_LEG_TABLE);
    String assetInstanceTable = getTableName(DGPSPConstants.ASSET_INSTANCE_TABLE);
    String assetItineraryTable = getTableName(DGPSPConstants.ASSET_ITINERARY_TABLE);

    String instanceID        = conveyanceInstanceTable + "." + DGPSPConstants.COL_CONVEYANCEID;
    String instanceName      = conveyanceInstanceTable + "." + DGPSPConstants.COL_BUMPERNO;
    String instanceProto     = conveyanceInstanceTable + "." + DGPSPConstants.COL_PROTOTYPEID;
    
    String assetInstanceOwner     = assetInstanceTable + "." + DGPSPConstants.COL_OWNER;
    String assetInstanceID   = assetInstanceTable + "." + DGPSPConstants.COL_ASSETID;
    String itinID             = assetItineraryTable + "." + DGPSPConstants.COL_ASSETID;
    String itinLeg            = assetItineraryTable + "." + DGPSPConstants.COL_LEGID;
    String conveyedLegLeg     = conveyedLegTable + "." + DGPSPConstants.COL_LEGID;
    String conveyedLegConvID  = conveyedLegTable + "." + DGPSPConstants.COL_CONVEYANCEID;
	
    String sqlQuery = 
      "select distinct " + assetInstanceOwner + ", " + 
      instanceID + ", " + instanceProto + ", " + instanceName +
      " from "  + conveyanceInstanceTable + ", " + assetInstanceTable + ", " + 
      assetItineraryTable + ", " + conveyedLegTable +
      " where " +
      assetInstanceID + " = " + itinID + 
      " and " + itinLeg + " = " + conveyedLegLeg +
      " and " + conveyedLegConvID + " = " + instanceID;
      //      " order by " + instanceName;

    logMessage(queryOutputLevel, Logger.DB_WRITE,"CarrierInstance Query: "+sqlQuery);    
	
    return sqlQuery;
  }

  //HELPERS:

  //Statics:
  public static String getDerivedTableName(String baseName, int runID){
    return Controller.getTableName("derived_"+baseName,runID);
  }
}
