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
package org.cougaar.mlm.ui.grabber.connect;

import org.cougaar.planning.servlet.data.xml.DeXMLable;
import org.cougaar.planning.servlet.data.xml.DeXMLableFactory;
import org.cougaar.mlm.ui.psp.transit.data.instances.Instance;
import org.cougaar.mlm.ui.psp.transit.data.instances.InstancesData;
import org.cougaar.mlm.ui.psp.transit.data.instances.InstancesDataFactory;

import org.cougaar.mlm.ui.grabber.logger.Logger;
import org.cougaar.mlm.ui.grabber.workqueue.Result;
import org.cougaar.mlm.ui.grabber.config.DataGathererPSPConfig;
import org.cougaar.mlm.ui.grabber.config.URLConnectionData;
import org.cougaar.mlm.ui.grabber.config.DBConfig;
import org.cougaar.mlm.ui.grabber.controller.RunResult;
import org.cougaar.mlm.ui.grabber.controller.SuccessRunResult;

import java.sql.*;

import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.text.DecimalFormat;

import org.cougaar.planning.ldm.measure.Mass;

/**
 * Handles getting instance data from DataGatherer PSP
 * @author Benjamin Lubin; last modified by: $Author: gvidaver $
 *
 * @since 2/19/01
 **/
public class DGPSPInstanceConnection extends DGPSPConnection 
  implements DGPSPConstants{

  //Constants:
  ////////////

  //Variables:
  ////////////

  //Constructors:
  ///////////////

  public DGPSPInstanceConnection(int id, int runID,
				 DataGathererPSPConfig pspConfig, 
				 DBConfig dbConfig,
				 Connection c,
				 Logger l){
    super(id, runID, pspConfig, dbConfig, c,l);
  }

  //Members:
  //////////

  //Gets:

  /**return the DeXMLableFactory specific to this URL connection**/
  protected DeXMLableFactory getDeXMLableFactory(){
    return new InstancesDataFactory();
  }

  //Actions:

  protected boolean updateAssetInstance(Statement s, Instance part){
    boolean ret=false;
    try{
      StringBuffer sb=new StringBuffer();
      boolean hasNomen=!(part.itemNomen==null||part.itemNomen.equals(""));
      sb.append("INSERT INTO ");
      sb.append(getTableName(ASSET_INSTANCE_TABLE));
      sb.append(" (");
      sb.append(COL_ASSETID);sb.append(",");
      sb.append(COL_AGGREGATE);sb.append(",");
      sb.append(COL_OWNER);sb.append(",");
      sb.append(COL_PROTOTYPEID);sb.append(",");
      sb.append(COL_NAME);
      if(hasNomen){
	sb.append(",");
	sb.append(COL_ALP_ITEM_NOMEN);
      }
      sb.append(") VALUES('");
      sb.append(part.UID);sb.append("',");
      sb.append(part.aggregateNumber);sb.append(",'");
      sb.append(part.ownerID==null?"":part.ownerID);sb.append("','");
      sb.append(part.prototypeUID);sb.append("','");
      sb.append(part.name==null?"":part.name);
      sb.append("'");
      if(hasNomen){
	sb.append(",'");
	sb.append(part.itemNomen);
	sb.append("'");
      }
      sb.append(")");
      ret=(s.executeUpdate(sb.toString())==1);
    }catch(SQLException e){
      if(!dbConfig.getUniqueViolatedErrorCode().equals(e.getSQLState())){
	haltForError(Logger.DB_WRITE,"Could not update table("+
		     getTableName(ASSET_INSTANCE_TABLE)+")"+
		     "["+e.getSQLState()+"]",e);
	return false;
      }else
	return true;
    }
    return ret;
  }

  DecimalFormat noExponentNoFractionDoubleFormat=new DecimalFormat ("#");

  protected boolean updateManifest(Statement s, Instance part){
    boolean ret=false;
    StringBuffer sb = null;

    int i = 0;
    //      System.out.println ("instance manifest type ids " +  part.typeIdentifications);
    //      System.out.println ("instance manifest weights " +  part.weights);
    //      System.out.println ("instance manifest nomenclatures " +  part.nomenclatures);

    Iterator typeIter = part.typeIdentifications.iterator();
    Iterator weightsIter = part.weights.iterator();
    for (Iterator nomenIter = part.nomenclatures.iterator (); nomenIter.hasNext ();) {
      i++;
      sb=new StringBuffer();
      sb.append("INSERT INTO ");
      sb.append(getTableName(MANIFEST_TABLE));
      sb.append(" (");
      sb.append (COL_MANIFEST_ITEM_ID);sb.append(",");
      sb.append (COL_ASSETID);sb.append(",");
      sb.append (COL_NAME);sb.append(",");
      sb.append (COL_ALP_TYPEID);sb.append(",");
      sb.append (COL_ALP_NOMENCLATURE);sb.append(",");
      sb.append (COL_WEIGHT);

      String nomen = (String) nomenIter.next ();
      String type  = (String) typeIter.next ();
      double weight = ((Mass) weightsIter.next ()).getGrams();

      sb.append(")\nVALUES('");
      sb.append(part.UID + "-item-" + i);sb.append("','");
      sb.append(part.UID);sb.append("','");
      sb.append(part.name+"-item-"+nomen+"-"+i);sb.append("','");
      sb.append(type);sb.append("','");
      sb.append(nomen+" "+type+"");sb.append("','");
      sb.append(noExponentNoFractionDoubleFormat.format(weight));
      sb.append("')");
      try{
	int rowsInserted = s.executeUpdate(sb.toString());
	if (rowsInserted == 1) 
	  ret = true;
	else {
	  ret = false;
	  logMessage(Logger.WARNING,Logger.DB_WRITE,
		     getClusterName()+" could not insert manifest info into database.");
	  logMessage(Logger.WARNING,Logger.DB_WRITE,
		     getClusterName()+" inserted " + rowsInserted + " rows");
	  logMessage(Logger.WARNING,Logger.DB_WRITE,
		     getClusterName()+" sql was " + sb);
	  break;
	}
      }catch(SQLException e){
	if(!dbConfig.getUniqueViolatedErrorCode().equals(e.getSQLState())){
	  haltForError(Logger.DB_WRITE,"Could not update table("+
		       getTableName(MANIFEST_TABLE)+")"+
		       "["+e.getSQLState()+"]",e);
	  System.out.println ("*** Sql is\n" + sb);
	  return false;
	}else {
	  //	logMessage(Logger.WARNING,Logger.DB_WRITE,
	  //		   getClusterName()+" got an exception " + e);
	  //	System.err.println (getClusterName()+" got an exception! : " + e);
	  return true;
	}
      }
    }
    return ret;
  }

  protected void updateDB(Connection c, DeXMLable obj){
    setStatus("Starting");
    InstancesData data=(InstancesData)obj;
    Statement s;
    try{
      s = c.createStatement();
    }catch(SQLException e){
      haltForError(Logger.DB_WRITE,"Could not create Statement",e);
      return;
    }
    int num=0;
    int unsuccessful=0;
    Iterator iter=data.getInstancesIterator();
    while(iter.hasNext()){
      num++;
      setStatus("Updating instance "+num);
      Instance part=(Instance)iter.next();
      if(!updateAssetInstance(s,part))
	unsuccessful++;
      if(part.hasManifest) {
	if(!updateManifest(s,part))
	  unsuccessful++;
      }
      if(halt)return;
    }
    logMessage(Logger.TRIVIAL,Logger.DB_WRITE,
	       getClusterName()+" added "+num+" instance(s)");
    if(unsuccessful>0)
      logMessage(Logger.WARNING,Logger.DB_WRITE,
		 getClusterName()+" could not add "+unsuccessful+
		 " instances(s)");
    setStatus("Done");
    if(s!=null){
      try{
	s.close();
      }catch(Exception e){
      }
    }
  }

  protected RunResult prepResult(DeXMLable obj){
    setStatus("Starting");
    InstancesData data=(InstancesData)obj;
    InstanceRunResult irr = new InstanceRunResult(getID(),getRunID());
    Iterator iter=data.getInstancesIterator();
    while(iter.hasNext()){
      Instance part=(Instance)iter.next();
      String muid=part.manifestUID;
      /**if a Container, MilVan, or Pallet**/
      if(!(muid==null||muid.equals(""))){
	irr.addManifestUID(muid);
      }
    }
    setStatus("Done");
    logMessage(Logger.MINOR,Logger.RESULT,"Produced Result");
    return irr;
  }

  //Static functions:
  ///////////////////

  //InnerClasses:
  ///////////////
  public class InstanceRunResult extends SuccessRunResult{
    private Set manifestUIDs;
    public InstanceRunResult(int id, int runID){
      super(id,runID);
      this.manifestUIDs=new HashSet(89);
    }
    public void addManifestUID(String uid){
      manifestUIDs.add(uid);
    }
    public Iterator getManifestUIDIterator(){
      return manifestUIDs.iterator();
    }
  }
}