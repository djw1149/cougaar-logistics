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
package org.cougaar.mlm.ui.newtpfdd.gui.view.details;

import org.cougaar.mlm.ui.newtpfdd.gui.view.DatabaseConfig;

import javax.swing.table.AbstractTableModel;

import java.util.List;
import java.util.ArrayList;

/**
 * Shows a popup of the details about an asset, or all the 
 * assets carried on a leg.
 * @author Benjamin Lubin; last modified by: $Author: mthome $
 *
 * @since 4/27/01
 **/
public class AssetDetailTableModel extends AbstractTableModel{

  //Constants:
  ////////////

  //Variables:
  ////////////

  private List rows;

  private DatabaseConfig dbConfig;
  private int runID;

  //Constructors:
  ///////////////

  public AssetDetailTableModel(DatabaseConfig dbConfig, int runID){
    rows=new ArrayList();
    this.dbConfig=dbConfig;
    this.runID=runID;
  }

  //Members:
  //////////
  public int getRowCount(){
    return rows.size();
  }
  public int getColumnCount(){
    return AssetDetails.getColumnCount();
  }
  public Object getValueAt(int row, int column){
    AssetDetails ad=(AssetDetails)rows.get(row);
    return ad.getValueAt(column);
  }

  public Class getColumnClass(int column){
    return AssetDetails.getColumnClass(column);
  }

  public String getColumnName(int column){
    return AssetDetails.getColumnName(column);
  }

  public void fillWithData(AssetDetailRequest adr){
    List newRows=adr.getAssetDetails(dbConfig, runID);
    int size=rows.size();
    rows.addAll(newRows);
    fireTableRowsInserted(size,rows.size()-1);
  }

  //InnerClasses:
  ///////////////
}
