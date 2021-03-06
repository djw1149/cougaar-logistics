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

import java.util.Date;
import java.util.HashSet;

import org.cougaar.planning.ldm.asset.AggregateAsset;
import org.cougaar.planning.ldm.asset.Asset;
import org.cougaar.glm.ldm.asset.GLMAsset;
import org.cougaar.glm.ldm.asset.MovabilityPG;
import org.cougaar.planning.ldm.plan.Task;
import org.cougaar.util.log.Logger;

/**
 * Create either an XML document in the Vishnu Data format or Vishnu objects from ALP objects. <p>
 *
 * Adds fields for physical dimensions to tasks and assets.
 * <p>
 */
public class GenericDataXMLize extends TranscomDataXMLize {
  boolean warnAboutMissingSpeed = 
    System.getProperty ("GenericDataXMLize.warnAboutMissingSpeed", "false").equals("true");

  public GenericDataXMLize (boolean direct, Logger logger) {
    super (direct, logger, new HashSet());
  }

  /** 
   * Create XML for asset, subclass to add fields
   * 
   * NOTE : field names should match those in .dff file
   *
   * @param object node representing asset
   * @param taskOrAsset asset being translated
   * @return true if should add object to list of new objects
   */
  protected boolean processAsset (Object object, Object taskOrAsset) {
    GLMAsset asset = (GLMAsset) taskOrAsset;
    if (!asset.hasContainPG())
      return false;
	
    setType (object, "Asset");
    String type = setName ("Asset", object, asset);

    dataHelper.createRoleScheduleListField(object, "roleSchedule", asset);
    dataHelper.createAvailableScheduleListField(object, "availableSchedule", asset);

    dataHelper.createFloatField(object, "speed",             (float) getSpeed (asset));
    dataHelper.createFloatField(object, "weightCapacity",    (float) getWeightCapacity (asset));
    dataHelper.createFloatField(object, "areaCapacity",      (float) getAreaCapacity   (asset));
    addPassengerCapacity (object, asset);

    if (logger.isDebugEnabled())
      logger.debug ("GenericDataXMLize.processAsset - type " + type);
    if (direct && logger.isDebugEnabled())
      logger.debug ("GenericDataXMLize.processAsset - created resource : " + object);
	
    return true;
  }

  protected void addPassengerCapacity (Object object, GLMAsset asset) {
    dataHelper.createFloatField(object, "passengerCapacity", (float) getPassengerCapacity   (asset));
  }
  
  /** 
   * Create XML for task, subclass to add fields
   * 
   * NOTE : field names should match those in .dff file
   *
   * @param object node representing task
   * @param taskOrAsset task being translated
   * @return true if should add object to list of new objects
   */
  protected boolean processTask (Object object, Object taskOrAsset) {
    super.processTask (object, taskOrAsset);
    Task task = (Task) taskOrAsset;
    Asset directObject = task.getDirectObject();
    GLMAsset baseAsset;

    if (directObject instanceof AggregateAsset) {
      baseAsset = (GLMAsset) ((AggregateAsset)directObject).getAsset ();
    } 
    else {
      baseAsset = (GLMAsset)directObject;
    }
	
    dataHelper.createFloatField(object, "weight",   (float) getWeight   (baseAsset));
    dataHelper.createFloatField(object, "area",     (float) getArea     (baseAsset));
    dataHelper.createFloatField(object, "quantity", (float) getQuantity (directObject));
    dataHelper.createBooleanField(object, "isVehicle", isVehicle (baseAsset));
    Date earliestArrival = new Date(glmPrefHelper.getEarlyDate(task).getTime());
    dataHelper.createDateField(object, "earliestArrival", earliestArrival);

    /*
    if (direct && logger.isDebugEnabled()) {
      logger.debug ("GenericDataXMLize.processTask - created task : " + object);
      Reusable.RInteger departureTime = 
	(Reusable.RInteger) ((SchObject)object).getField ("departure");
      Reusable.RInteger arrivalTime   = 
	(Reusable.RInteger) ((SchObject)object).getField ("arrival");
	  
      logger.debug ("\tdeparture " + timeOps.timeToString (departureTime.intValue()) + 
		    " arrival "    + timeOps.timeToString (arrivalTime.intValue()));
    }
    */

    return true;
  }

  protected double getSpeed (GLMAsset asset) {
    double speed = 55;
	
    try {
      speed = asset.getGroundSelfPropulsionPG().getCruiseSpeed().getMilesPerHour();
    } catch (Exception e) {
      try {
	speed = asset.getAirSelfPropulsionPG().getCruiseSpeed().getMilesPerHour();
      } catch (Exception ee) {
	try {
	  speed = asset.getWaterSelfPropulsionPG().getCruiseSpeed().getMilesPerHour();
	} catch (Exception eee) {
	  if (warnAboutMissingSpeed) {
	    logger.warn ("GenericDataXMLize.getSpeed - WARNING - Could not determine"+ 
			 " resource speed for " + asset.getUID());
	  }
	}
      }
    }
	
    return speed;
  }
  
  protected double getWeightCapacity (GLMAsset asset) {
    return (asset.hasContainPG() && 
	    asset.getContainPG().getMaximumWeight() != null) ?
      asset.getContainPG().getMaximumWeight().getShortTons() : 0.0d;
  }
  
  protected double getWeight (GLMAsset asset) {
    return (asset.hasPhysicalPG() && 
	    asset.getPhysicalPG().getMass () != null) ?
      asset.getPhysicalPG().getMass().getShortTons() : 0.0d;
  }

  protected double getAreaCapacity (GLMAsset asset) {
    return (asset.hasContainPG() && 
	    asset.getContainPG().getMaximumFootprintArea() != null) ?
      asset.getContainPG().getMaximumFootprintArea().getSquareFeet() : 0.0;
  }
  
  protected double getArea (GLMAsset asset) {
    return (asset.hasPhysicalPG() && 
	    asset.getPhysicalPG().getFootprintArea() != null) ? 
      asset.getPhysicalPG().getFootprintArea().getSquareFeet() : 0.0d;
  }

  /** returns 1 if asset isn't an aggregate */
  protected double getQuantity (Asset asset) {
    return 
      (asset instanceof AggregateAsset) ?
      ((AggregateAsset)asset).getQuantity() : 1;
  }

  protected double getPassengerCapacity (GLMAsset asset) {
    return (asset.hasContainPG()) ?
      asset.getContainPG().getMaximumPassengers() : 0.0d;
  }

  /** 
   * <pre>
   * Something is a vehicle if 
   * a) it has a ground vehicle PG OR
   * b) it has a movability PG with a cargo category code whose
   *    first character is either R or A
   *
   * </pre>
   * @return true if asset is a vehicle
   */
  protected boolean isVehicle (GLMAsset asset) {
    if (asset.hasGroundVehiclePG()) return true;

    if (!asset.hasMovabilityPG())
      return false;

    try {
      MovabilityPG move_prop = asset.getMovabilityPG();
      String cargocatcode = move_prop.getCargoCategoryCode();
      char first = cargocatcode.charAt(0);
      if (first == 'R' || first == 'A')
	return true;
    } catch (Exception e) {
      return false;
    } 

    return false;
  }
}

