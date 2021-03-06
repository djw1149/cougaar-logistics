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
package org.cougaar.mlm.ui.newtpfdd.gui.view.statistics;

import org.cougaar.mlm.ui.newtpfdd.gui.view.NewTPFDDShell;
import org.cougaar.mlm.ui.newtpfdd.gui.view.DatabaseConfig;
import org.cougaar.mlm.ui.grabber.config.DBConfig;
import org.cougaar.mlm.ui.grabber.validator.Validator;
import org.cougaar.mlm.ui.grabber.validator.Test;
import org.cougaar.mlm.ui.grabber.validator.HTMLizer;
import org.cougaar.mlm.ui.grabber.logger.Logger;
import org.cougaar.mlm.ui.grabber.logger.StdLogger;
import org.cougaar.mlm.ui.grabber.logger.TPFDDLoggerFactory;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.text.EditorKit;
import javax.swing.text.Document;
import javax.swing.JLabel;

import java.sql.Statement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import java.io.*;

import com.ibm.eou.swingchart.UniversalChart; // from chart.jar - thanks AlphaWorks!

import org.cougaar.mlm.ui.grabber.validator.Graphable;

/**
 * Displays statistics information based on grabber Validation tests
 *
 * @since 5/3/01
 **/
public class GraphPane extends StatisticsPane {

  boolean debug = true;
  public String getName () { return "Run Charts"; }

  //Variables:
  ////////////

  protected UniversalChart ucChart;
  protected JLabel statusLabel;

  protected Color[] possibleColors = new Color [] { 
	Color.blue,    Color.cyan,     Color.green,
	Color.magenta, Color.red,      Color.yellow, 
	Color.black,   Color.orange,   Color.gray, 
	Color.pink,    Color.darkGray, Color.lightGray 
  };
  
  //Constructors:
  ///////////////

  public GraphPane(NewTPFDDShell shell){
	super (shell);
  }

  //Members:
  //////////

  protected void setupGUI(){
	super.setupGUI();
	statusLabel = new JLabel ();
    add(statusLabel,BorderLayout.SOUTH);
  }

  protected boolean addTest (Test test) {	
	return test instanceof Graphable;  
  }
  
  protected JComponent createContent () {
	//	ucChart = new UniversalChart();
	ucChart = new MyChart();
	((MyChart)ucChart).setDim(new Dimension(400,400));
    ucChart.setShowGridLines( false );
	// alphaworks Chart variables
	Color[] cuc = new Color [] { 
	  Color.blue
	};
	ucChart.setColors( cuc );
	
	//    ucChart.setFont(new Font("SansSerif", Font.ITALIC, 20));//iFonts[style]);
	//	ucChart.setForeground(Color.black);//iForegroundColors[style]);
	//    ucChart.setBackground(new Color (255,255,200));//iBackgroundColors[style]);
	return ucChart;
  }

  public void displayResult(Statement s, int idx){
    final int fin_idx=idx;
    final int fin_runID=getRunID();
    final Statement fin_s=s;
	final Test fin_whichTest = getValidator().getTest(idx);    
	
    final SwingWorker worker = new SwingWorker(){
		int idx=fin_idx;
		int runID=fin_runID;
		Statement s=fin_s;
		Test whichTest = fin_whichTest;

		String [] legends;
		String [] stringLabels;
		double [][] valueMatrix;

        public Object construct() {
		  try{
			List legendList = new ArrayList ();
			int numThirdDim = 1;
			Graphable graphableTest = (Graphable) whichTest;
			boolean hasThirdDim = graphableTest.hasThirdDimension();
			
			if (!hasThirdDim) {
			  legends = new String [1];
			  String[] headers=whichTest.getHeaders();
			  legends[0]=headers[graphableTest.getYAxisColumn ()-1];
			  legendList.add (legends[0]);
			}

			List labels = new ArrayList ();
			ResultSet rs=s.executeQuery("SELECT * FROM "+whichTest.getTableName(runID));

			int zCol = graphableTest.getZAxisColumn ();
			
			int rows=0;
			Set uniqueLegendNames = new HashSet();
			while (rs.next()) {
			  if (hasThirdDim)
				uniqueLegendNames.add(rs.getString(zCol));
			  rows++;
			}
			if (debug)
			  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "displayResult.worker.construct - JDBC 1.0 - rows: "+rows);

			if (rows == 0) // nothing to show
			  return new MyChart();

			if (hasThirdDim) {
			  legendList.addAll (uniqueLegendNames);
			  Collections.sort (legendList);
			  legends = (String []) legendList.toArray (new String[] {});
			  numThirdDim = legends.length;
			}
			  
			// need to get a new copy of the result set
			rs=s.executeQuery("SELECT * FROM "+whichTest.getTableName(runID));

			valueMatrix = new double[numThirdDim][rows];

			int numColumns = populateValues (rs, graphableTest, legendList, labels, valueMatrix);
			if (numColumns == 0) 
			  numColumns = 1;
			
			rs.close();

			// chop off parts of matrix that are not filled in
			double [][] valueMatrix2  = new double[numThirdDim][numColumns];
			for (int i = 0; i < numThirdDim; i++)
			  for (int j = 0; j < numColumns; j++)
				valueMatrix2[i][j] = valueMatrix[i][j];
			valueMatrix = valueMatrix2;
			
			if (debug)
			  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "displayResult.worker.construct - total rows: "+rows);

			stringLabels = (String []) labels.toArray(new String []{});

			if (debug) {
			  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "displayResult.worker.construct - legends :");
			  for (int i = 0; i < legends.length; i++)
				TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, i + " " + legends[i]);
			  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "displayResult.worker.construct - labels :");
			  for (int i = 0; i < stringLabels.length; i++)
				TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, i + " " + stringLabels[i]);
			  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "displayResult.worker.construct - values :");
			  for (int i = 0; i < numThirdDim; i++) {
				TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC,"[");
				
				for (int j = 0; j < valueMatrix[i].length; j++)
				  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "" + valueMatrix[i][j] + ((j == valueMatrix[i].length-1) ? "" : ","));
				TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "]");
			  }
			}
		  } catch(SQLException e){
			logger.logMessage(Logger.ERROR,Logger.DB_WRITE,
							  "Could not produce HTML from validation table",e);
		  }
		  return ucChart;
		}

		protected int populateValues (ResultSet rs, Graphable graphableTest,
									   List legendList, 
									   List labels, double [][] valueMatrix) {
		  boolean hasThirdDim = graphableTest.hasThirdDimension();
		  int xCol = graphableTest.getXAxisColumn ();
		  int yCol = graphableTest.getYAxisColumn ();
		  int zCol = graphableTest.getZAxisColumn ();
		  int types[]=((Test)graphableTest).getTypes();

		  String lastX = "";
		  int currentColumn=-1; // values for each z axis of data
		  try {
			while(rs.next()){
			  // X axis labels
			  String label = "?";
			  if (types[xCol-1] == Test.TYPE_STRING) {
				label = rs.getString(xCol);
			  } else if (types[xCol-1] == Test.TYPE_DATETIME) {
				label = rs.getTimestamp(xCol).toString();
              }

			  if (!label.equals(lastX)) {
				labels.add (label);
				currentColumn++;
			  } 

			  lastX = label;
			  
			  // Z axis index
			  int zAxisIndex = (hasThirdDim) ?
				legendList.indexOf(rs.getString(zCol)) : 0;
			  
			  if (zAxisIndex == -1)
				TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "GraphPane.construct - huh? " +
									"zAxisIndex is undefined. legend List " + legendList + 
									" does not contain z col " + rs.getString(zCol));
			  // Y axis values
			  // if (debug)
			  //TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "[" + zAxisIndex + "]["+currentColumn+"] = " + rs.getInt(yCol));
			  
			  valueMatrix[zAxisIndex][currentColumn] = getDoubleValue(rs, yCol);
			}
		  } catch(SQLException e){
			logger.logMessage(Logger.ERROR,Logger.DB_WRITE,
							  "Could not produce HTML from validation table",e);
		  }
		  return currentColumn;
		}
		
		public double getDoubleValue (ResultSet rs, int yCol) {
		  double doubleValue;
			  
		  try {
			//				intValue = rs.getInt(yCol);
			doubleValue = rs.getDouble(yCol);
		  } catch (SQLException e) {
			try {
			  String strDoubleValue = rs.getString (yCol);
			  try {
				doubleValue = Double.parseDouble (strDoubleValue);
			  }
			  catch (NumberFormatException nfe) {
				doubleValue = 0;
			  }
			} catch (SQLException se) {
			  doubleValue = 0;
			}
		  }
		  return doubleValue;
		}
		
        //Runs on the event-dispatching thread.
        public void finished(){
		  if (stringLabels == null) return;
		  
		  ucChart.setLegends(legends);
		  ucChart.setLabels (stringLabels);

		  if (legends.length > 1) {
			if (debug)
			  TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "GraphPane.finished - num legends " + legends.length);
			
			Color [] colorTable = new Color [legends.length];
			
			for (int i = 0; i < legends.length; i++)
			  colorTable[i] = possibleColors[i%possibleColors.length];

			ucChart.setColors(colorTable);
		  }
		  else 
			ucChart.setColors(new Color [] {Color.blue});

		  Rectangle bounds = ucChart.getBounds ();
		  int height = getPixelHeight(ucChart.getFontMetrics(ucChart.getFont()),legends);
		  
		  ucChart.setBounds ((int)bounds.getX(), (int)bounds.getY(),
		  					 getPixelWidth(ucChart.getFontMetrics(ucChart.getFont()),stringLabels,legends),
		  					 height);

		  ((MyChart)ucChart).setDim(new Dimension((int)ucChart.getBounds().getWidth(),
												  height-10));

		  ucChart.setMinimumSize(ucChart.getPreferredSize());

		  ucChart.invalidate ();
		  scrollPane.invalidate();
		  scrollPane.repaint();
			
		  if (debug)
			TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "displayResult.worker.construct - bounds " + //before:\n"+bounds +
								" after\n" + ucChart.getBounds());
			
		  ucChart.setValues (valueMatrix);
		  //			ucChart.setChartType( UniversalChart.PLOT );
		  ucChart.setChartType( UniversalChart.PARALLEL );

		  if (!ucChart.isVisible())
			ucChart.setVisible(true);
		  setText("<HTML><CENTER><H1></H1></CENTER></HTML>");
        }

		protected int getPixelWidth (FontMetrics fontMetrics, String [] labels,
									 String [] legends) {
		  int width = 0;
		  
		  for (int i = 0; i < labels.length; i++)
			width += fontMetrics.stringWidth(labels[i]);
		  
		  if (legends.length > 1)
			width = Math.max (width, legends.length*15);
		  
		  return width;
		}

		protected int getPixelHeight (FontMetrics fontMetrics, String [] legends) {
		  return legends.length*(fontMetrics.getHeight()+2);
		}

      };
	worker.start();  
  }

  protected void setText(String text){
    statusLabel.setText(text);
  }

  //InnerClasses:
  ///////////////

  private static class MyChart extends UniversalChart 
  {
	Dimension d;
	
	public void setDim (Dimension d) 
	{
	  this.d = d;
	}
	
	public Dimension getPreferredSize () 
	{
	  //		TPFDDLoggerFactory.createLogger().logMessage(Logger.NORMAL, Logger.GENERIC, "super.getPreferredSize " + super.getPreferredSize () +
	  //							" min " + getMinimumSize() +
	  //							" max " + getMaximumSize());
	  return d;
	}
	
  }
}
