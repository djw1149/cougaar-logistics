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
package org.cougaar.mlm.ui.grabber.config;

import org.cougaar.planning.servlet.data.xml.DeXMLable;
import org.cougaar.planning.servlet.data.xml.XMLable;
import org.cougaar.planning.servlet.data.xml.UnexpectedXMLException;
import org.cougaar.planning.servlet.data.xml.XMLWriter;

import org.cougaar.mlm.ui.grabber.logger.Logger;
import org.cougaar.mlm.ui.grabber.logger.StdLogger;

import java.io.IOException;
import org.xml.sax.Attributes;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Wraps all configuration for the data gatherer
 * @author Benjamin Lubin; last modified by: $Author: gvidaver $
 *
 * @since 2/8/01
 **/
public class DataGrabberConfig implements XMLable, DeXMLable{

  //Constants:
  ////////////

  public static final String NAME_TAG = "DataGrabber";

  public static final String VERBOSITY_TAG = "Verbosity";
  public static final String CONTROLLER_MAX_THREADS_TAG="ControllerMaxThreads";

  //Variables:
  ////////////

  protected int verbosityLevel;
  protected int controllerMaxThreads;

  protected DBConfig dbConfig;
  protected WebServerConfig webServerConfig;

  protected Map hierarchyConfigs;

  protected DataGathererPSPConfig DGPSPConfig;

  protected DerivedTablesConfig dtConfig;

  //Constructors:
  ///////////////

  public DataGrabberConfig(){
    hierarchyConfigs=new HashMap(11);
  }

  //Members:
  //////////

  public int getVerbosity(){
    return verbosityLevel;
  }

  public int getControllerMaxThreads(){
    return controllerMaxThreads;
  }

  public DBConfig getDBConfig(){
    return dbConfig;
  }

  public WebServerConfig getWebServerConfig(){
    return webServerConfig;
  }

  public HierarchyPSPConfig getHierarchyConfig(int society){
    return (HierarchyPSPConfig)hierarchyConfigs.get(new Integer(society));
  }

  public void addHierarchyConfig(HierarchyPSPConfig hc){
    hierarchyConfigs.put(new Integer(hc.getSociety()),hc);
  }

  public DataGathererPSPConfig getDGPSPConfig(){
    return DGPSPConfig;
  }

  public DerivedTablesConfig getDerivedTablesConfig(){
    if(dtConfig==null)
      return new DerivedTablesConfig();
    else
      return dtConfig;
  }

  //XMLable:

  /**
   * Write this class out to the Writer in XML format
   * @param w output Writer
   **/
  public void toXML(XMLWriter w) throws IOException{
    w.optagln(NAME_TAG);
    w.tagln(VERBOSITY_TAG, Logger.SEVERITIES[verbosityLevel]);
    w.tagln(CONTROLLER_MAX_THREADS_TAG, 
	    Integer.toString(controllerMaxThreads));
    dbConfig.toXML(w);
    webServerConfig.toXML(w);
    Iterator iter=hierarchyConfigs.values().iterator();
    while(iter.hasNext()){
      HierarchyPSPConfig hc=(HierarchyPSPConfig)iter.next();
      hc.toXML(w);
    }
    DGPSPConfig.toXML(w);
    w.cltagln(NAME_TAG);
  }

  //DeXMLable:

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
    if(name.equals(NAME_TAG)){
    }else if(name.equals(VERBOSITY_TAG)){
      verbosityLevel=StdLogger.getSeverity(data);
    }else if(name.equals(CONTROLLER_MAX_THREADS_TAG)){
      try{
	controllerMaxThreads=Integer.parseInt(data);
      }catch(NumberFormatException nfe){
	throw new UnexpectedXMLException("Could not parse as number: "+data);
      }
    }else
      throw new UnexpectedXMLException("Unexpected open tag:"+name);
  }

  /**
   * Report an endElement.
   * @param name endElement tag
   * @return true iff the object is DONE being deXMLized
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
    if(obj instanceof DBConfig)
      dbConfig=(DBConfig)obj;
    else if(obj instanceof WebServerConfig)
      webServerConfig=(WebServerConfig)obj;
    else if(obj instanceof HierarchyPSPConfig)
      addHierarchyConfig((HierarchyPSPConfig)obj);
    else if(obj instanceof DataGathererPSPConfig)
      DGPSPConfig = (DataGathererPSPConfig)obj;
    else if(obj instanceof DerivedTablesConfig)
      dtConfig = (DerivedTablesConfig)obj;
    else 
      throw new UnexpectedXMLException("Unexpected subobject:"+obj);
  }

  //InnerClasses:
  ///////////////
}
