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

import org.cougaar.mlm.ui.grabber.connect.DGPSPConstants;

/**
 * Contains the details on an Asset
 * NOTE: we aren't using Asset Type right now, as it isn't being filled
 * by the DB, and we aren't showing contents yet...
 * @author Benjamin Lubin; last modified by: $Author: gvidaver $
 *
 * @since 4/27/01
 **/
public class AssetDetails{

  //Constants:
  ////////////
  public static final int OWNER = 0;
  public static final int TYPE = 1;
  public static final int TYPENAME = 2;
  public static final int ASSETNAME = 3;
  public static final int NUMBER = 4;
  public static final int ASSETCLASS = 5;
  /*public static final int ASSETTYPE = ;*/
  public static final int WEIGHT = 6;
  public static final int WIDTH = 7;
  public static final int HEIGHT = 8;
  public static final int DEPTH = 9;
  public static final int AREA = 10;
  public static final int VOLUME = 11;

  private static double KGS_TO_STONS = 0.0011022927689594355d;
  private static double METERS_TO_FEET = 3.28084d;
  private static double SQUARE_METERS_TO_SQUARE_FEET = 10.763867d;
  private static double CUBIC_METERS_TO_CUBIC_FEET = 35.314667d;

  protected static final String[] columnNames={"Owner",
					       "Type",
					       "Type Name",
					       "Asset Name",
					       "Number",
					       "Asset Class",
					       /*"Asset Type",*/
					       "Weight (stons)",
					       "Width (ft)",
					       "Height (ft)",
					       "Depth (ft)",
					       "Area (ft^2)",
					       "Vol (ft^3)"};
  protected static final Class[] columnClasses={String.class,
						String.class,
						String.class,
						String.class,
						Integer.class,
						String.class,
						/*Integer.class,*/
						Double.class,
						Double.class,
						Double.class,
						Double.class,
						Double.class,
						Double.class};
  
  //Variables:
  ////////////
  private String owner;
  private String type;
  private String typeName;
  private String assetName;
  private int number;
  private int assetClass;
  /*  private String assetType;*/
  private double weight;
  private double width;
  private double height;
  private double depth;
  private double area;
  private double volume;
  
  //Constructors:
  ///////////////

  public AssetDetails(){
  }

  //Members:
  //////////

  public void setValueAt(Object o, int column){
    switch(column){
    case OWNER:
      owner=(String)o;break;
    case TYPE:
      type=(String)o;break;
    case TYPENAME:
      typeName=(String)o;break;
    case ASSETNAME:
      assetName=(String)o;break;
    case NUMBER:
      number=((Integer)o).intValue();break;
    case ASSETCLASS:
      assetClass=((Integer)o).intValue();break;
      /*    case ASSETTYPE:
      assetType=((Integer)o).intValue;break;
      */
    case WEIGHT:
      weight=(((Double)o).doubleValue()/1000)*KGS_TO_STONS;break;
    case WIDTH:
      width=((Double)o).doubleValue()*METERS_TO_FEET;break;
    case HEIGHT:
      height=((Double)o).doubleValue()*METERS_TO_FEET;break;
    case DEPTH:
      depth=((Double)o).doubleValue()*METERS_TO_FEET;break;     
    case AREA:
      area=((Double)o).doubleValue()*SQUARE_METERS_TO_SQUARE_FEET;break;     
    case VOLUME:
      volume=((Double)o).doubleValue()*CUBIC_METERS_TO_CUBIC_FEET;break;     
    }
  }

  public Object getValueAt(int column){
    switch(column){
    case OWNER:
      return owner;
    case TYPE:
      return type;
    case TYPENAME:
      return typeName;
    case ASSETNAME:
      return assetName;
    case NUMBER:
      return new Integer(number);
    case ASSETCLASS:
      return getStringForClass(assetClass);
      /*
    case ASSETTYPE:
      return assetType;
      */
    case WEIGHT:
      return new Double(weight);
    case WIDTH:
      return new Double(width);
    case HEIGHT:
      return new Double(height);
    case DEPTH:
      return new Double(depth);     
    case AREA:
      return new Double(area);     
    case VOLUME:
      return new Double(volume);     
    }
    return null;
  }

  protected String getStringForClass(int aclass){
    switch(aclass){
    case DGPSPConstants.ASSET_CLASS_1:
      return "I";
    case DGPSPConstants.ASSET_CLASS_2:
      return "II";
    case DGPSPConstants.ASSET_CLASS_3:
      return "III";
    case DGPSPConstants.ASSET_CLASS_4:
      return "IV";
    case DGPSPConstants.ASSET_CLASS_5:
      return "V";
    case DGPSPConstants.ASSET_CLASS_6:
      return "VI";
    case DGPSPConstants.ASSET_CLASS_7:
      return "VII";
    case DGPSPConstants.ASSET_CLASS_8:
      return "VII";
    case DGPSPConstants.ASSET_CLASS_9:
      return "IX";
    case DGPSPConstants.ASSET_CLASS_10:
      return "X";
    case DGPSPConstants.ASSET_CLASS_CONTAINER:
      return "Container";
    case DGPSPConstants.ASSET_CLASS_PERSON:
      return "Person";
    case DGPSPConstants.ASSET_CLASS_UNKNOWN:
    default:
      return "Unknown";
    }
  }

  //Statics:

  public static int getColumnCount(){
    return columnClasses.length;
  }

  public static String getColumnName(int column){
    return columnNames[column];
  }

  public static Class getColumnClass(int column){
    return columnClasses[column];
  }
  //InnerClasses:
  ///////////////
}
