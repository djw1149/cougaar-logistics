/*
 * <copyright>
 *  Copyright 2001-2003 BBNT Solutions, LLC
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
package org.cougaar.mlm.ui.psp.transit.data.population;

import org.cougaar.planning.servlet.data.xml.*;

import java.io.Writer;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

import org.xml.sax.Attributes;

/**
 * Represents the data leaving the PSP
 * @author Benjamin Lubin; last modified by: $Author: mthome $
 *
 * @since 1/24/01
 **/
public class ConveyancePrototype implements XMLable, DeXMLable, Serializable{

  //Variables:
  ////////////

  //TAGS:
  public static final String NAME_TAG = "ConveyancePrototype";
  protected static final String UID_TAG = "UID";
  protected static final String TYPE_TAG = "Type";
  protected static final String VOL_TAG = "Vol";
  protected static final String AREA_TAG = "Area";
  protected static final String WEIGHT_TAG = "Weight";
  protected static final String AVE_SPEED_TAG = "Speed";
  protected static final String ALP_TYPEID_TAG = "ALPTypeID";
  protected static final String NOMENCLATURE_TAG = "Nomen";

  //Constants:
  //types:
  /**none of the below**/
  public static final int ASSET_TYPE_UNKNOWN = 0;
  /**specific moving conveyances (trucks, etc)**/
  public static final int ASSET_TYPE_TRUCK = 1;
  public static final int ASSET_TYPE_TRAIN = 2;
  public static final int ASSET_TYPE_PLANE = 3;
  public static final int ASSET_TYPE_SHIP = 4;
  /**catch-all for other moving conveyances (tanks, etc)**/
  public static final int ASSET_TYPE_SELF_PROPELLABLE = 5;
  /**non-moving "conveyances" that one can assign to**/
  public static final int ASSET_TYPE_DECK = 6;
  public static final int ASSET_TYPE_PERSON = 7;
  public static final int ASSET_TYPE_FACILITY = 8;

  //Variables:
  public String UID;
  /**one of the ASSET_TYPE_ constants**/
  public int conveyanceType;
  /**volume in liters**/
  public double volCap;
  /**area in square_meters**/
  public double areaCap;
  /**weight in grams**/
  public double weightCap;
  /**average speed in miles_per_hour**/
  public double aveSpeed;
  public String alpTypeID;
  /**type nomenclature, not to be confused with the instance's *item* nomen**/
  public String nomenclature;

  //Constructors:
  ///////////////

  public ConveyancePrototype(){
  }

  //Members:
  //////////

  //XMLable members:
  //----------------

  /**
   * Write this class out to the Writer in XML format
   * @param w output Writer
   **/
  public void toXML(XMLWriter w) throws IOException{
    w.optagln(NAME_TAG);

    w.tagln(UID_TAG, UID);
    w.tagln(TYPE_TAG, conveyanceType);
    w.tagln(VOL_TAG, volCap);
    w.tagln(AREA_TAG, areaCap);
    w.tagln(WEIGHT_TAG, weightCap);
    w.tagln(AVE_SPEED_TAG, aveSpeed);
    w.tagln(ALP_TYPEID_TAG, alpTypeID);
    w.tagln(NOMENCLATURE_TAG, nomenclature);

    w.cltagln(NAME_TAG);
  }

  //DeXMLable members:
  //------------------

  /**
   * Report a startElement that pertains to THIS object, not any
   * sub objects.  Call also provides the elements Attributes and data.  
   * Note, that  unlike in a SAX parser, data is guaranteed to contain 
   * ALL of this tag's data, not just a 'chunk' of it.
   * @param name startElement tag
   * @param attr attributes for this tag
   * @param data data for this tag
   **/
  public void openTag(String name, Attributes attr, String data)
    throws UnexpectedXMLException{

    try{
      if(name.equals(NAME_TAG)){
      }else if(name.equals(UID_TAG)){
	UID=data;
      }else if(name.equals(TYPE_TAG)){
	conveyanceType=Integer.parseInt(data);
      }else if(name.equals(VOL_TAG)){
	volCap=Double.parseDouble(data);
      }else if(name.equals(AREA_TAG)){
	areaCap=Double.parseDouble(data);
      }else if(name.equals(WEIGHT_TAG)){
	weightCap=Double.parseDouble(data);
      }else if(name.equals(AVE_SPEED_TAG)){
	aveSpeed=Double.parseDouble(data);
      }else if(name.equals(ALP_TYPEID_TAG)){
	alpTypeID=data;
      }else if(name.equals(NOMENCLATURE_TAG)){
	nomenclature=data;
      }else{
	throw new UnexpectedXMLException("Unexpected tag: "+name);    
      }
    }catch(NumberFormatException e){
      throw new UnexpectedXMLException("Malformed Number: " + 
				       name + " : " + data);
    }
  }

  /**
   * Report an endElement.
   * @param name endElement tag
   * @return true iff the object is DONE being DeXMLized
   **/
  public boolean closeTag(String name)
    throws UnexpectedXMLException{
    return name.equals(NAME_TAG);
  }

  /**
   * This function will be called whenever a subobject has
   * completed de-XMLizing and needs to be encorporated into
   * this object.
   * @param name the startElement tag that caused this subobject
   * to be created
   * @param obj the object itself
   **/
  public void completeSubObject(String name, DeXMLable obj)
    throws UnexpectedXMLException{
  }

  //Inner Classes:
  private static final long serialVersionUID = 102938475657483920L;
}
