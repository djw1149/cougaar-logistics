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
package org.cougaar.logistics.plugin.trans;

import org.cougaar.planning.ldm.asset.AggregateAsset;
import org.cougaar.planning.ldm.asset.Asset;
import org.cougaar.planning.ldm.plan.Task;
import org.cougaar.planning.ldm.plan.MPTask;

import org.cougaar.lib.vishnu.client.VishnuAggregatorPlugin;

import org.cougaar.logistics.ldm.Constants;
import org.cougaar.glm.ldm.asset.GLMAsset;
import org.cougaar.glm.ldm.asset.MovabilityPG;
import org.cougaar.glm.ldm.plan.GeolocLocation;
import org.cougaar.util.log.Logger;

import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.cougaar.planning.ldm.plan.AspectScorePoint;
import org.cougaar.planning.ldm.plan.AspectScoreRange;
import org.cougaar.planning.ldm.plan.AspectType;
import org.cougaar.planning.ldm.plan.AspectValue;
import org.cougaar.planning.ldm.plan.Preference;
import org.cougaar.planning.ldm.plan.ScoringFunction;

import org.cougaar.lib.callback.UTILExpandableTaskCallback;
import org.cougaar.lib.callback.UTILFilterCallback;
import org.cougaar.lib.callback.UTILGenericListener;

/**
 * Note that although this is an aggregator, an expansion is made and subtasks created. <p>
 *
 * This is because after the aggregation, the setup and wrapup times must be represented. <br>
 * They are represented as separate tasks with verb "Transit".  They represent the time   <br>
 * taken to travel from the supply point and to return.  They should not be figured into  <br>
 * the time that the task takes to perform, since they are only indirectly related to it. <p>
 * 
 * If the specs were altered, and no setup and wrapup duration were included, only aggregations <br>
 * would be made, and therefore only MPTask would be produced.  The allocator downstream        <br>
 * would have to be sensitive to this change.
 */
public class TransportAggregatorPlugin extends VishnuAggregatorPlugin {
  boolean debugPrefs = false;

  /** 
   * Only tasks with transport verbs are given to Vishnu   <p>
   * If a task has no FROM prep, it is not handled.         <p>
   * If a task has a FROM prep that is in CONUS, it is not handled. <p>
   * If a task is an MPTask (and possibly an output of the plugin), it is not handled.
   */
  public boolean interestingTask(Task t) {
    if (debugPrefs) {
      for (Enumeration prefs = t.getPreferences(); prefs.hasMoreElements (); ) {
	Preference pref = (Preference) prefs.nextElement ();
	int aspectType = pref.getAspectType ();

	AspectValue lower = AspectValue.newAspectValue (aspectType, 0.0d);

	Calendar cal = java.util.Calendar.getInstance();
	cal.set(2200, 0, 0, 0, 0, 0);
	cal.set(Calendar.MILLISECOND, 0);
	double endOfRange = (double) ((Date) cal.getTime()).getTime();
	AspectValue upper = AspectValue.newAspectValue (aspectType, endOfRange);

	print (pref, pref.getScoringFunction().getDefinedRange (),
	       pref.getScoringFunction().getValidRanges (lower, upper), logger);
      }
    }

    boolean hasTransport = t.getVerb().equals (Constants.Verb.TRANSPORT) ||
      t.getVerb().equals(Constants.Verb.TRANSIT);

    if (!hasTransport)
      return false;
    if (!prepHelper.hasPrepNamed (t, Constants.Preposition.FROM)) {
      if (isInfoEnabled())
	info (getName () + ".interestingTask - ignoring TRANSPORT task " + t.getUID () + " that doesn't have a FROM prep.");
      return false;
    }
    /*
      GeolocLocation geoloc = 
      (GeolocLocation) UTILPrepPhrase.getIndirectObject (t, Constants.Preposition.FROM);
      if (geoloc.getLongitude().getDegrees () < 0) {
      if (isInfoEnabled())
      info (getName () + ".interestingTask - ignoring task " + t.getUID() + " with FROM of " + geoloc + " - it's not in theater.");
      return false;
      }
      geoloc = 
      (GeolocLocation) UTILPrepPhrase.getIndirectObject (t, Constants.Preposition.TO);
      if (geoloc.getLongitude().getDegrees () < 0) {
      if (isInfoEnabled())
      info (getName () + ".interestingTask - ignoring task " + t.getUID() + " with TO of " + geoloc + " - it's not in theater.");
      return false;
      }
    */

    /*
      boolean hasPrepo = UTILPrepPhrase.hasPrepNamed (t, "PREPO");
      boolean hasStratTrans = false;

      if (UTILPrepPhrase.hasPrepNamed (t, "OFTYPE")) {
      Asset oftype = (Asset) UTILPrepPhrase.getIndirectObject (t, "OFTYPE");
      String typeid = oftype.getTypeIdentificationPG().getTypeIdentification ();
      hasStratTrans = typeid.equals ("StrategicTransportation");
      }
    */

    boolean val = 
      !(t instanceof MPTask) &&
      super.interestingTask(t);
    //  !hasPrepo && !hasStratTrans; 

    if (isDebugEnabled() && val)
      debug (getName () + ".interestingTask - interested in " + t.getUID());
	
    return val;
  }
  
  protected void print (Preference pref, 
			       AspectScoreRange definedRange, Enumeration validRanges, Logger logger) {
    double prefval = pref.getScoringFunction().getBest ().getValue();
    String prefstr = "" + prefval;
    String type = "" + pref.getAspectType ();
    boolean isDate = false;
    switch (pref.getAspectType ()) {
    case AspectType.START_TIME: 
      type = "START_TIME";
      prefstr = "" + new Date ((long) prefval);
      isDate = true;
      break;
    case AspectType.END_TIME: 
      type = "END_TIME";
      prefstr = "" + new Date ((long) prefval);
      isDate = true;
      break;
    case AspectType.COST: 
      type = "COST";
      prefstr = "$" + (long) prefval;
      break;
    }

    logger.info ("pref type " + type + " value " + prefstr + " sf " + pref.getScoringFunction());

    for (; validRanges.hasMoreElements (); ) {
      AspectScoreRange range = (AspectScoreRange) validRanges.nextElement();
      AspectScorePoint start = range.getRangeStartPoint ();
      AspectScorePoint end   = range.getRangeEndPoint ();
      double startValue = start.getValue ();
      double endValue   = end.getValue ();

      if (isDate) {
	logger.info ("<" + new Date ((long) (startValue)) + "-" + new Date ((long) (endValue)) + "> "); 
      }
      else {
	logger.info ("<" + startValue + "-" + endValue + "> "); 
      }
    }
    logger.info ("");
  }

  /** only trucks, which have contain pgs, are given to Vishnu */
  public boolean interestingAsset(Asset a) {
    if (!(a instanceof GLMAsset)) {
      if (isInfoEnabled())
	info (getName () + ".interestingAsset - ignoring asset " + a + " because it's not an GLMAsset.");
      return false;
    }
	
    boolean val = ((GLMAsset)a).hasContainPG ();
    if (isInfoEnabled() && !val)
      info (getName () + ".interestingAsset - ignoring GLMAsset " + a + " because it's missing a Contain PG.");
    return val;
  }
}



