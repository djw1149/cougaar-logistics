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
package org.cougaar.mlm.ui.grabber.validator;

import org.cougaar.mlm.ui.grabber.logger.Logger;
import java.io.PrintStream;
import java.util.Date;

import java.text.NumberFormat;

/**
 * HTMLization
 * @author Benjamin Lubin; last modified by: $Author: gvidaver $
 *
 * @since 2/26/01
 **/
public class HTMLizer implements Logger{

  //Constants:
  ////////////

  public static final int NO_FRACTION_FORMAT = 0; // doubles appear as 1234 instead of 1234.567
  public static final int THREE_DIGIT_FORMAT = 1; // doubles appear as 1234.567 instead of 1234.567890

  //Variables:
  ////////////
  private PrintStream ps;
  private int indent;

  private int verbosityLevel=Logger.TRIVIAL;
  protected NumberFormat noExponentDoubleFormat;
  protected NumberFormat noExponentNoFractionDoubleFormat;
  protected NumberFormat noExponentThreeDigits;
   
  //Constructors:
  ///////////////

  public HTMLizer(PrintStream ps){
    this.ps=ps;
    indent=0;

    noExponentDoubleFormat=NumberFormat.getNumberInstance();
    noExponentDoubleFormat.setMaximumIntegerDigits(20);

    noExponentNoFractionDoubleFormat=NumberFormat.getNumberInstance();
    noExponentNoFractionDoubleFormat.setMaximumIntegerDigits(20);
    noExponentNoFractionDoubleFormat.setMaximumFractionDigits(0);

    noExponentThreeDigits=NumberFormat.getNumberInstance();
    noExponentThreeDigits.setMaximumIntegerDigits(20);
    noExponentThreeDigits.setMaximumFractionDigits(3);
  }

  //Members:
  //////////

  public void setVerbosity(int verbosity){
    verbosityLevel=verbosity;
  }

  public PrintStream getStream(){
    return ps;
  }

  public void print(String s){
    ps.print(s);
  }

  public void p(String text){
    indent();
    ps.print("<p>");
    ps.print(text);
    ps.print("</p>\n");
  }

  public void h1(String text){
    indent();
    ps.print("<H1>");
    ps.print(text);
    ps.print("</H1>\n");
  }

  public void h2(String text){
    indent();
    ps.print("<H2>");
    ps.print(text);
    ps.print("</H2>\n");
  }

  public void h3(String text){
    indent();
    ps.print("<H3>");
    ps.print(text);
    ps.print("</H3>\n");
  }

  public void a(String ref, String text){
    indent();
    ps.print("<A HREF=\"");
    ps.print(ref);
    ps.print("\">");
    ps.print(text);
    ps.print("</A>\n");
  }

  public String aStr(String ref, String text){
    StringBuffer sb=new StringBuffer();
    sb.append("<A HREF=\"");
    sb.append(ref);
    sb.append("\">");
    sb.append(text);
    sb.append("</A>");
    return sb.toString();
  }

  /** Create a link to that also opens up a new popup window
   * @param linkRef follow this link in the current window
   * @param winRef url for new popup window
   * @param winName name for the new window
   * @param text text for link
   **/
  public String popupStr(String linkRef, String winRef, 
			 String winName,String text){
    StringBuffer sb=new StringBuffer();
    sb.append("<A HREF=\"");
    sb.append(linkRef);
    sb.append("\" onClick=\"window.open('");
    sb.append(winRef);
    sb.append("','");
    sb.append(winName);
    sb.append("','toolbar=no,location=no,directories=no,"+
	      "menubar=no,scrollbars=yes,width=400,height=400');\">");
    sb.append(text);
    sb.append("</A>");
    return sb.toString();
  }

  public void dismissLink(){
    p("<B><A HREF=\"#\" onClick=\"self.close();\">Dismiss</A></B>");
  }

  public void dismissLink(String ref, String text){
    p("<B><A HREF=\""+ref+"\" onClick=\"self.close();\">"+text+"</A></B>");
  }

  public void sCenter(){
    indent();
    ps.print("<CENTER>\n");
    indent++;
  }
  
  public void eCenter(){
    indent--;
    indent();
    ps.print("</CENTER>\n");
  }
  
  public void sFont(String color){
    indent();
    ps.print("<FONT COLOR=");
    ps.print(color);
    ps.print(">\n");
    indent++;
  }
  
  public void eFont(){
    indent--;
    indent();
    ps.print("</FONT>\n");
  }
  
  public void br(){
    indent();
    ps.print("<BR>\n");
  }
  
  public void hr(){
    indent();
    ps.print("<HR>\n");
  }

  public void sOl(){
    indent();
    ps.print("<OL>\n");
    indent++;
  }

  public void eOl(){
    indent--;
    indent();
    ps.print("</OL>\n");
  }

  public void li(String text){
    indent();
    ps.print("<LI>");
    ps.println(text);
  }

  public void sTable(){
    indent();
    ps.print("<TABLE BORDER=1 CELLPADDING=5>\n");
    indent++;
  }
  public void eTable(){
    indent--;
    indent();
    ps.print("</TABLE>\n");
  }
  public void sRow(){
    indent();
    ps.print("<TR>\n");
    indent++;
  }
  public void eRow(){
    indent--;
    indent();
    ps.print("</TR>\n");
  }

  public void tHead(String head){
    indent();
    ps.print("<TH>");
    ps.print(head);
    ps.print("</TH>\n");
  }

  public void tHead(String head, int colspan){
    indent();
    ps.print("<TH COLSPAN="+colspan+">");
    ps.print(head);
    ps.print("</TH>\n");
  }
  
  public void tData(String data){
    indent();
    ps.print("<TD>");
    ps.print(data);
    ps.print("</TD>\n");
  }
  
  public void tDataRightJustify(String data){
    indent();
    ps.print("<TD align=right>");
    ps.print(data);
    ps.print("</TD>\n");
  }
  
  public void tData(int i){
    tData(Integer.toString(i));
  }
  
  public void tData(double d){
    tDataRightJustify(noExponentDoubleFormat.format(d));
  }
  
  /** right justifies number */
  public void tData(double d, int format){
    if (format == NO_FRACTION_FORMAT)
      tDataRightJustify(noExponentNoFractionDoubleFormat.format(d));
    else
      tDataRightJustify(noExponentThreeDigits.format(d));
  }
  
  public void tData(Date d){
    tData(d.toString());
  }
  
  public void tData(String ref, String data){
    indent();
    ps.print("<TD>");
    a(ref,data);
    ps.print("</TD>\n");
  }

  public void tData(String data, int colspan){
    indent();
    ps.print("<TD COLSPAN="+colspan+">");
    ps.print(data);
    ps.print("</TD>\n");
  }

  public void tData(String data, int colspan, int rowspan){
    indent();
    ps.print("<TD COLSPAN="+colspan+" ROWSPAN="+rowspan+">");
    ps.print(data);
    ps.print("</TD>\n");
  }
  
  private void indent(){
    for(int i=0;i<indent;i++)
      ps.print(' ');
  }


  //Logger:


  public void logMessage(int severity, int type, String message){
    if(severity<=verbosityLevel){
      ps.print("[");
      ps.print(Logger.SEVERITIES[severity]);
      ps.print("][");
      ps.print(Logger.TYPES[type]);
      ps.print("][");
      ps.print(message);
      ps.print("]<BR>\n");
    }
  }

  public void logMessage(int severity, int type, String message, Exception e){
    if(severity<=verbosityLevel){
      ps.print("[");
      ps.print(Logger.SEVERITIES[severity]);
      ps.print("][");
      ps.print(Logger.TYPES[type]);
      ps.print("][");
      ps.print(message);
      ps.print("][");
      ps.print(e.toString());
      ps.print("]<BR>\n");
    }
  }

  //InnerClasses:
  ///////////////
}




