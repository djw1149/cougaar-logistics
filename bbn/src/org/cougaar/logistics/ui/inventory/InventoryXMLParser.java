/*
 * <copyright>
 *  Copyright 1997-2002 BBNT Solutions, LLC
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
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.q
 * </copyright>
 */
 
package org.cougaar.logistics.ui.inventory;

import java.util.Stack;
import java.util.ArrayList;

import org.cougaar.logistics.plugin.inventory.LogisticsInventoryFormatter;
import org.cougaar.logistics.ui.inventory.data.*;

/** 
 * <pre>
 * 
 * The InventoryXMLParser is the class that parses the input xml
 * string into data objects.
 * 
 * 
 *
 **/

public class InventoryXMLParser
{
    private int ctr;
    private String[] lines;
    private InventoryData inventory;
    private String currentString;
    private Stack tagStack;

    public InventoryXMLParser() {
	tagStack = new Stack();
    }

    public void parseString(String xmlInput){
	ctr=0;
	inventory=null;
	lines = xmlInput.split("$");
	parse();
    }

    private void parse() {
	while(ctr < lines.length) {
	    currentString = lines[ctr];
	    if(currentString.startsWith("</")) {
		popTag();
	    }
	    if(currentString.startsWith("<")) {
		tagStack.push(currentString);
		String name = getTagName(currentString);
		if(name.equals(LogisticsInventoryFormatter.INVENTORY_DUMP_TAG)) {
		    parseHeader();
		}
		else {
		    parseSchedule();
		}
	    }
	}	
    }

    private String stripTag(String tag) {
	int start=0;
	int end=tag.length()-1;
	if(tag.startsWith("</")) {
	    start=2;
	}
	else if(tag.startsWith("<")) {
	    start=1;
	}
	if(tag.endsWith(">")) {
	    end--;
	}
	return tag.substring(start,end);
    }

    private String getTagName(String tag) {
	tag = stripTag(tag);
	String words[] = tag.split("\\s");
	return words[0];
    }

    private void parseHeader() {
      String[] words = currentString.split("\\s");
      String org=null;
      String asset=null;
      if(words[1].startsWith("org=")){
	org=words[1].substring("org=".length());
      }
      if(words[2].startsWith("item=")){
	asset=words[2].substring("item=".length());
      }
      inventory = new InventoryData(org,asset);
      ctr++;
    }

    private String getScheduleType(String tag) {
	tag = stripTag(tag);
	String words[] = tag.split("type=");
	return words[1].trim();
    }

    private void parseSchedule() {
        String name = getTagName(currentString);
	String typeStr = getScheduleType(currentString);
	int type = InventoryScheduleHeader.getTypeInt(typeStr);
	currentString = lines[++ctr];
	ArrayList elements = new ArrayList();
	while(!(currentString.startsWith("<"))) {
	    elements.add(parseString(currentString,type));
	    currentString = lines[++ctr];
	}
	InventoryScheduleHeader header = new InventoryScheduleHeader(name,
								     type,
								     elements);

	inventory.addSchedule(header);
    }
	
    private InventoryScheduleElement parseString(String elementString,
						 int    type){
      switch(type) {
      case InventoryScheduleHeader.TASKS_TYPE:
	return InventoryTask.createFromCSV(elementString);
      case InventoryScheduleHeader.PROJ_TASKS_TYPE:
	return InventoryProjTask.createFromCSV(elementString);
      case InventoryScheduleHeader.ARS_TYPE:
	return InventoryAR.createFromCSV(elementString);
      case InventoryScheduleHeader.PROJ_ARS_TYPE:
	return InventoryProjAR.createProjFromCSV(elementString);
      case InventoryScheduleHeader.LEVELS_TYPE:
	return InventoryLevel.createFromCSV(elementString);
      default:
	throw new RuntimeException("Unparseable CSV " + elementString +
				   " Element Type " + type); 
      }
    }

	    
    private void popTag() {
	String lastTag = (String) tagStack.peek();
	if(getTagName(lastTag).equals(getTagName(currentString))) {
	    tagStack.pop();
	}
	else {
	    throw new RuntimeException("ERROR:Last pushed tag doesn't match this termination tag");
	}
	ctr++;
    }
}

