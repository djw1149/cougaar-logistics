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
package org.cougaar.mlm.ui.newtpfdd.gui.view.details;

import org.cougaar.mlm.ui.newtpfdd.gui.view.DatabaseConfig;
import org.cougaar.mlm.ui.grabber.controller.Controller;
import org.cougaar.mlm.ui.grabber.connect.DGPSPConstants;

import java.util.List;
import java.util.ArrayList;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

/**
 * Gets data about carrier details
 * @author Benjamin Lubin; last modified by: $Author: gvidaver $
 *
 * @since 5/3/01
 **/
public class CarrierDetailRequest{

  //Constants:
  ////////////

  //Variables:
  ////////////

  private String convID;

  //Constructors:
  ///////////////
  public CarrierDetailRequest(String convID){
    this.convID=convID;
  }

  //Members:
  //////////

  protected String getTableName(String baseName, int runID){
    return Controller.getTableName(baseName,runID);
  }

  public CarrierDetails getCarrierDetails(DatabaseConfig dbConfig, int runID){
    CarrierDetails ret = new CarrierDetails();
    try{
      Statement s = dbConfig.getConnection().createStatement();
      ResultSet rs= s.executeQuery(getSql(dbConfig,runID));
      if(rs.next()){
	addCarrierFields(ret,rs);
      }
      if(s!=null)
	s.close();
    }catch(SQLException e){
      System.err.println("SQL Exception getting Carrier Details: "+e);
    }
    return ret;
  }

  protected void addCarrierFields(CarrierDetails cd, 
				  ResultSet rs)
    throws SQLException{
    cd.setValueAt(new String(rs.getString(1)),CarrierDetails.OWNER);
    //cd.setValueAt(new String(rs.getString(2)),CarrierDetails.HOMEBASE);
    cd.setValueAt(new Integer(rs.getInt(3)),CarrierDetails.CONVTYPE);
    cd.setValueAt(new String(rs.getString(4)),CarrierDetails.PROTOTYPE);
    cd.setValueAt(new String(rs.getString(5)),CarrierDetails.PROTONAME);
    cd.setValueAt(new String(rs.getString(6)),CarrierDetails.BUMPERNO);
    cd.setValueAt(new Integer(rs.getInt(7)),CarrierDetails.SELFPROP);
    cd.setValueAt(new Double(rs.getDouble(8)),CarrierDetails.AVESPEED);
    cd.setValueAt(new Double(rs.getDouble(9)),CarrierDetails.WEIGHTCAP);
    cd.setValueAt(new Double(rs.getDouble(10)),CarrierDetails.AREACAP);
    cd.setValueAt(new Double(rs.getDouble(11)),CarrierDetails.VOLCAP);
  }

  protected String getSql(DatabaseConfig dbConfig, int runID){
    String ciTable=getTableName(DGPSPConstants.CONV_INSTANCE_TABLE,runID);
    String cpTable=getTableName(DGPSPConstants.CONV_PROTOTYPE_TABLE,runID);

    //For select:
    String ciOwner="ci."+DGPSPConstants.COL_OWNER;
    String ciHomeBase="ci."+DGPSPConstants.COL_BASELOC;
    String cpConvType="cp."+DGPSPConstants.COL_CONVEYANCE_TYPE;
    String cpAlpType="cp."+DGPSPConstants.COL_ALP_TYPEID;
    String cpAlpNomen="cp."+DGPSPConstants.COL_ALP_NOMENCLATURE;
    String ciBumperno="ci."+DGPSPConstants.COL_BUMPERNO;
    String ciSelfProp="ci."+DGPSPConstants.COL_SELFPROP;
    String cpAveSpeed="cp."+DGPSPConstants.COL_AVE_SPEED;
    String cpWeightCap="cp."+DGPSPConstants.COL_WEIGHT_CAP;
    String cpVolCap="cp."+DGPSPConstants.COL_VOL_CAP;
    String cpAreaCap="cp."+DGPSPConstants.COL_AREA_CAP;

    //For Where:
    String ciConvID="ci."+DGPSPConstants.COL_CONVEYANCEID;
    String ciProtoID="ci."+DGPSPConstants.COL_PROTOTYPEID;
    String cpProtoID="cp."+DGPSPConstants.COL_PROTOTYPEID;

    String sql="select "+
      ciOwner+", "+
      ciHomeBase+", "+
      cpConvType+", "+
      cpAlpType+", "+
      cpAlpNomen+", "+
      ciBumperno+", "+
      ciSelfProp+", "+
      cpAveSpeed+", "+
      cpWeightCap+", "+
      cpAreaCap+", "+
      cpVolCap+
      " from "+
      ciTable+" ci, "+   
      cpTable+" cp "+
      " where "+
      ciConvID+"='"+convID+"'\nand "+
      ciProtoID+"="+cpProtoID;

    return sql;
  }

  //InnerClasses:
  ///////////////
}
