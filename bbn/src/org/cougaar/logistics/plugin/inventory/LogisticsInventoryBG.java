/*--------------------------------------------------------------------------
 *                         RESTRICTED RIGHTS LEGEND
 *
 *   Use, duplication, or disclosure by the Government is subject to
 *   restrictions as set forth in the Rights in Technical Data and Computer
 *   Software Clause at DFARS 52.227-7013.
 *
 *                             BBNT Solutions LLC,
 *                             10 Moulton Street
 *                            Cambridge, MA 02138
 *                              (617) 873-3000
 *
 *   Copyright 2000 by
 *             BBNT Solutions LLC,
 *             all rights reserved.
 *
 * --------------------------------------------------------------------------*/
package org.cougaar.logistics.plugin.inventory;

import java.util.*;

import org.cougaar.planning.ldm.asset.PGDelegate;
import org.cougaar.planning.ldm.asset.PropertyGroup;
import org.cougaar.planning.ldm.measure.Scalar;
import org.cougaar.planning.ldm.plan.Task;
import org.cougaar.planning.ldm.plan.AspectType;
import org.cougaar.planning.ldm.plan.PrepositionalPhrase;
import org.cougaar.glm.ldm.asset.Inventory;
import org.cougaar.glm.ldm.Constants;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.plugin.util.PluginHelper;
import org.cougaar.glm.plugins.TimeUtils;

import org.cougaar.logistics.plugin.inventory.InventoryPlugin;
import org.cougaar.logistics.plugin.inventory.LogisticsInventoryLogger;
import org.cougaar.logistics.plugin.inventory.TaskUtils;

public class LogisticsInventoryBG implements PGDelegate {

  // Beth, if you can come up with something better than 
  // 'bucket', I'd appreciate it.
  // Bucket will be a knob, this implementation temporary
  private long MSEC_PER_BUCKET = TimeUtils.MSEC_PER_DAY;
  protected LogisticsInventoryPG myPG;
  protected long startTime;
  protected int timeZero;
  private boolean initialized;
  private LoggingService logger;
  private LogisticsInventoryLogger csvLogger=null;
  // customerHash holds the time(long) the last actual is seen
  // for each customer
  private HashMap customerHash;
  private long earliestDemand;
  private TaskUtils taskUtils;


  protected ArrayList DueIn;
  protected ArrayList DueOut;

  public LogisticsInventoryBG(LogisticsInventoryPG pg) {
    myPG = pg;
    initialized = false;
    customerHash = new HashMap();
    DueOut = new ArrayList();
    DueIn = new ArrayList();
  }

  public void initialize(long today, InventoryPlugin parentPlugin) {
    // It would be nice to make this part of the constructor but I don't
    // know of a way.  If the BG is not initialized then we can't even
    // log an error so I added the initialized boolean but I don't care
    // for it.
    startTime = today;
    timeZero = (int)(startTime/MSEC_PER_BUCKET);
    logger = parentPlugin.getLoggingService(this);
    if(false) {
	csvLogger = new LogisticsInventoryLogger(myPG.getResource(),parentPlugin);
    }
    taskUtils = parentPlugin.getTaskUtils();
    logger.debug("Start day: "+TimeUtils.dateString(today));
    initialized = true;
  }

  public void addWithdrawTask(Task task) {
    if (task.getVerb().equals(Constants.Verb.WITHDRAW)) {
      addDueOut(task);
      earliestDemand = Math.min(earliestDemand, PluginHelper.getEndTime(task));
    } else if (task.getVerb().equals(Constants.Verb.PROJECTWITHDRAW)) {
      addDueOutProjection(task);
      earliestDemand = Math.min(earliestDemand, PluginHelper.getStartTime(task));
    }
  }

  private void addDueOut(Task task) {
    long endTime = PluginHelper.getEndTime(task);
    // The customer code should be broken out into a utility method.  
    // The new design needs the customer in more places than just the BG.
    // Do you think we could add getCustomer() to the PluginHelper?
    PrepositionalPhrase pp_for = task.getPrepositionalPhrase(Constants.Preposition.FOR);
    Object org;
    if (pp_for != null) {
      org = pp_for.getIndirectObject();
      // We know that SRA is using a String as FOR DO 
      // but can we assume that for all Supply tasks?
      Long lastActualSeen = (Long)customerHash.get(org);
      if ((lastActualSeen == null) || (endTime > lastActualSeen.longValue())) {
	customerHash.put(org, new Long(endTime));
      }
    }
    int bucket = convertTimeToBucket(endTime);
    addDueOut(task, bucket);
  }

  private void addDueOutProjection(Task task) {
    long start = (long)PluginHelper.getPreferenceBestValue(task, AspectType.START_TIME);
    long end = (long)PluginHelper.getPreferenceBestValue(task, AspectType.END_TIME);
    int bucket_start = convertTimeToBucket(start);
    int bucket_end = convertTimeToBucket(end);
    // There are more efficient ways of doing this but this is easy and clear
    for (int i=bucket_start; i < bucket_end; i++) {
      addDueOut(task, i);
    }
  }

  private void addDueOut(Task task, int bucket) {
    while (bucket >= DueOut.size()) {
      DueOut.add(new ArrayList());
    }
    ArrayList list = (ArrayList)DueOut.get(bucket);
    list.add(task);
  }

  public void removeWithdrawTask(Task task) {
  }

  public void updateRefillAllocation(Task task) {
    // need to update DueIn list
    // different updates obviously needed for Supply
    // and ProjectSupply because projections can span
    // serveral buckets.
    // This needs to be a well implemented method as it involves
    // shifting Refills and Projections in the dueIn list
  }

  public void logAllToCSVFile() {
      if(csvLogger != null) {
	  logDueOutsToCSVFile();
	  csvLogger.incrementCycleCtr();
      }
  }

  private void logDueOutsToCSVFile() {
      if(csvLogger != null) {
	  csvLogger.write("DUE_OUTS:START");
	  csvLogger.writeNoCtr("CYCLE,END TIME,VERB,FOR,QTY");
	  for(int i=0; i < DueOut.size(); i++) {
	      ArrayList bin = (ArrayList) DueOut.get(i);
	      csvLogger.write("Bin #" + i);
	      for(int j=0; j < bin.size(); j++) {
		  Task aDueOut = (Task) bin.get(j);
		  Date endDate = new Date(taskUtils.getEndTime(aDueOut));  
         	  String dueOutStr = endDate.toString() + "," + aDueOut.getVerb() + ",";
		  PrepositionalPhrase pp_for = aDueOut.getPrepositionalPhrase(Constants.Preposition.FOR);
		  Object org;
		  if (pp_for != null) {
		      org = pp_for.getIndirectObject();
		      dueOutStr = dueOutStr + org + ",";
		  }
		  if(taskUtils.isSupply(aDueOut)) {
		      dueOutStr = dueOutStr + taskUtils.getQuantity(aDueOut);
		  }
		  //We have to get the Rate if its a projection....MWD

		  csvLogger.write(dueOutStr);
	      }
	  }
	  csvLogger.write("DUE_OUTS:END");
      }
  }

  public void addRefillTask(Task task) {
    // I'm not sure how this is going to work in the new
    // design
  }

  public long getStartTime() {
    return startTime;
  }

  /**
   * Convert a time (long) into a bucket of this inventory that can be
   * used to index duein/out vectors, levels, etc.
   **/
  public int convertTimeToBucket(long time) {
    int thisDay = (int) (time / MSEC_PER_BUCKET);
    return thisDay - timeZero;
  }

  // Ask Beth about persistance.  Would like to make sure structures
  // are persisted when they are added to the code
  public PGDelegate copy(PropertyGroup pg) {
    return new LogisticsInventoryBG((LogisticsInventoryPG)pg);
  }

}


