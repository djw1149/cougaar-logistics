/*
 * <copyright>
 *  Copyright 1997-2001 BBNT Solutions, LLC
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

package org.cougaar.mlm.ui.psp.transit;

import java.util.*;

import org.cougaar.core.util.UID;
import org.cougaar.util.log.*;
import org.cougaar.lib.util.UTILAsset;

import org.cougaar.planning.ldm.asset.*;
import org.cougaar.planning.ldm.measure.Area;
import org.cougaar.planning.ldm.measure.Distance;
import org.cougaar.planning.ldm.measure.Latitude;
import org.cougaar.planning.ldm.measure.Longitude;
import org.cougaar.planning.ldm.measure.Mass;
import org.cougaar.planning.ldm.measure.Volume;

import org.cougaar.glm.ldm.Constants;
import org.cougaar.glm.ldm.asset.*;

import org.cougaar.glm.ldm.plan.GeolocLocation;
import org.cougaar.glm.util.AssetUtil;

import org.cougaar.mlm.ui.psp.transit.data.instances.*;
import org.cougaar.mlm.ui.psp.transit.data.legs.*;
import org.cougaar.mlm.ui.psp.transit.data.locations.*;
import org.cougaar.mlm.ui.psp.transit.data.population.*;
import org.cougaar.mlm.ui.psp.transit.data.prototypes.*;
import org.cougaar.mlm.ui.psp.transit.data.routes.*;

// use full
//import org.cougaar.mlm.ui.psp.transit.data.convoys.*;

/**
 * The <code>DataRegistry</code> is used by the 
 * <b><code>DataComputer</code></b> to take ALP/GLM objects, convert
 * them to "org.cougaar.mlm.ui.psp.transit.data.*" objects,
 * and add them to an existing <code>Registry</code>.
 * <pre>
 * There are just four public methods:
 * 1) <tt>
 *   <i>(register a leg, get a unique id)</i>
 *   public static String registerLeg(
 *      Registry toReg, 
 *      Leg leg) {..}</tt>
 * 2) <tt>
 *   <i>(register a location, get a unique id)</i>
 *   public static String registerLocation(
 *      Registry toReg, 
 *      GeolocLocation geoloc) {..}</tt>
 * 3) <tt>
 *   <i>(register a conveyance asset, get a unique id)</i>
 *   public static String registerCarrierInstance(
 *      Registry toReg, 
 *      Asset instAsset,
 *      boolean selfPropIfUnreg) {..}</tt>
 * 4) <tt>
 *   <i>(register a cargo asset, adds the ids to the List)</i>
 *   public static boolean registerCargoInstance(
 *      Registry toReg, 
 *      List toCargoIds,
 *      Asset instAsset) {..}</tt>
 * </pre>
 * Most of the <code>Leg</code> related code is kept in DataComputer,
 * since it does the LogPlan traversal.
 *
 * "DataRegistry" isn't such a great name, but at least it's no
 * worse than "PSP_DataGatherer".
 *
 * @see DataComputer
 */
public abstract class DataRegistry {

  public static final boolean DEBUG = 
    Boolean.getBoolean(
        "org.cougaar.mlm.ui.psp.transit.DataRegistry.debug");
  public static final boolean testing = 
    Boolean.getBoolean(
		       "org.cougaar.mlm.ui.psp.transit.DataRegistry.testing");

  private DataRegistry() {
    // just static functions!
  }

  //
  // BEGIN REGISTER
  //

  /**
   * Register a <code>Leg</code>, return it's globally unique identifier.
   */
  public static String registerLeg(
      Registry toReg, 
      Leg leg) {
    // get id
    String id = leg.UID;

    // check if leg is registered
    if (toReg.legs.getLeg(id) == null) {
      // register the leg
      toReg.legs.addLeg(leg);
    }

    // return id
    return id;
  }

  /**
   * Register a <code>GeolocLocation</code>, return a <code>String</code>
   * globally unique location identifier.
   */
  public static String registerLocation(
      Registry toReg, 
      GeolocLocation geoloc) {
    // get the unique identifier for this location
    String locId;
    String geolocCode = geoloc.getGeolocCode();
    if (geolocCode != null) {
      locId = geolocCode;
    } else {
      // FIXME: all locations should have a geoloc
      // code!
      //
      // for now we'll concatenate the lat+long:
      locId = 
        geoloc.getLatitude()+","+geoloc.getLongitude();
    }

    // check if location is registered
    if (toReg.locs.getLocation(locId) == null) {
      // must register this location
      //
      // create a new location
      org.cougaar.mlm.ui.psp.transit.data.locations.Location loc = 
        new org.cougaar.mlm.ui.psp.transit.data.locations.Location();
      loc.UID = locId;

      // geoloc code
      loc.geoLoc = geolocCode;
      // latitude
      loc.lat = geoloc.getLatitude().getDegrees();
      // longitude
      loc.lon = geoloc.getLongitude().getDegrees();
      // icao code
      loc.icao = geoloc.getIcaoCode();
      // readable name
      String prettyName = geoloc.getName();
      if (prettyName == null) {
        prettyName = geolocCode;
      }
      loc.prettyName = prettyName;

      if (DEBUG) {
        System.out.println(
            "DataRegistry.registerLocation - registering new loc " + loc.geoLoc + 
            " pretty " + loc.prettyName); 
      }

      // register the location
      toReg.locs.addLocation(loc);
    }

    // return the location code
    return locId;
  }

  /**
   * @return true if the given convoy <code>Asset</code> has been 
   * registered.
   */
  public static boolean containsConvoy(
      Registry toReg,
      Asset convoyAsset) {
    // get id
    String convoyId = convoyAsset.getUID().toString();

    // check if convoy is registered
    return (toReg.convoys.getConvoy(convoyId) != null);
  }

  /**
   * Register a convoy <code>Asset</code>, return a <code>String</code> 
   * convoy identifier.
   *
   * @param toReg the registry
   * @param convoyAsset the convoy asset, which is either a
   *   Convoy or Train
   */
  public static String registerConvoy(
      Registry toReg, 
      Asset convoyAsset,
      long startTime,
      long endTime) {
    // get id
    String convoyId = convoyAsset.getUID().toString();

    // check if convoy is registered
    if (toReg.convoys.getConvoy(convoyId) == null) {
      // must register this convoy
      //
      // create a new convoy
      org.cougaar.mlm.ui.psp.transit.data.convoys.Convoy toConvoy =
        new org.cougaar.mlm.ui.psp.transit.data.convoys.Convoy();
      toConvoy.setUID(convoyId);

      // set the start and end times
      toConvoy.setStartTime(startTime);
      toConvoy.setEndTime(endTime);

      // set pretty name
      String name;
      ItemIdentificationPG itemPG;
      if (((itemPG = convoyAsset.getItemIdentificationPG()) == null) ||
          ((name = itemPG.getItemIdentification()) == null)) {
        // typically use itemId, but here use (classname+"-"+UID)
        name =
          (((convoyAsset instanceof Convoy) ? 
            "Convoy-" :
            (convoyAsset instanceof Train) ? 
            "Train-" :
            "Other-")+
           convoyId);
      }

      if (convoyAsset instanceof AssetGroup) {
        toConvoy.setPrettyName(name);
        // register the carrier assets in this convoy
        List groupL = assetHelper.expandAssetGroup((AssetGroup)convoyAsset);
        int n = groupL.size();
        for (int i = 0; i < n; i++) {
          Asset ai = (Asset)groupL.get(i);
          String aiId;
          try {
            aiId = registerCarrierInstance(toReg, ai, true);
          } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                "Unable to expand (convoy) assetGroup UID:("+
                convoyAsset.getUID()+
                ") element["+i+"]: "+
                ((ai != null) ? 
                 ("(UID: "+ai.getUID()+")") :
                 ("null")));
          }
          toConvoy.addConveyanceID(aiId);
        }
      } else {
        // should never happen.. Convoys and Trains are AssetGroups
        throw new IllegalArgumentException(
            "Unexpected Convoy type: "+
            ((convoyAsset != null) ? 
             convoyAsset.getClass().getName() : 
             "null"));
      }

      // register the convoy
      toReg.convoys.addConvoy(toConvoy);
    }

    // return the convoy id
    return convoyId;
  }

  /**
   * Register an instance of a carrier <code>Asset</code>, return a 
   * <code>String</code> carrier instance identifier.
   *
   * @param toReg the registry
   * @param carrierAsset the carrier asset, which is either a
   *   PhysicalAsset or Person
   * @param selfPropIfUnreg this applies only to an unregistered carrier -- 
   *   if true then register the instance as self-propelled.
   */
  public static String registerCarrierInstance(
      Registry toReg, 
      Asset instAsset,
      boolean selfPropIfUnreg) {
    // get id
    String instId = instAsset.getUID().toString();

    // check if carrier instance is registered
    if (toReg.carriers.getInstance(instId) == null) {
      // must register this carrier
      //
      // create a new carrier
      ConveyanceInstance toCarrier = new ConveyanceInstance();
      toCarrier.UID = instId;

      // prototype id
      toCarrier.prototypeUID = 
        registerCarrierPrototype(toReg, instAsset);
      ItemIdentificationPG itemPG = 
        instAsset.getItemIdentificationPG();
      if (itemPG != null) {
        // item id == "bumper number"
        toCarrier.bumperNo = 
          itemPG.getItemIdentification();
        toCarrier.itemNomen =
          itemPG.getNomenclature();
      }
      // home location id
      // FIXME: home location needs to be a registered LocationID,
      // but this information is not recorded.
      toCarrier.homeLocID = null;
      // owner id
      // FIXME: for now use the UID's "clustername" owner field
      // better fix would be for assets to have a separate PG
      toCarrier.ownerID = instAsset.getUID().getOwner();
      // self propelled if specified by parameter
      toCarrier.selfPropelled = selfPropIfUnreg;

      // register the carrier
      toReg.carriers.addInstance(toCarrier);
    }

    // return the carrier id
    return instId;
  }

  /**
   * Register a prototype of a carrier <code>Asset</code>, return a 
   * <code>String</code> carrier prototype identifier.
   * <p>
   * This is more like a "template" than a "prototype", since the
   * registered information might refer to either the asset 
   * instance or it's <tt>GLMAsset.getPrototype()</tt>.  For carriers 
   * either the instance or it's GLMAsset.getPrototype() is registered
   * -- the GLMAsset.getPrototype() if the instance information exactly 
   * matches the GLMAsset.getPrototype().
   */
  private static String registerCarrierPrototype(
      Registry toReg, 
      Asset instAsset) {
    // given an "instance" of a carrier asset, register it with
    //   it's "template" (== "prototype").
    //
    // check for the typical case in TOPS, where an instance is
    //   equivalent to it's prototype.  This often happens for
    //   ships, planes, etc.
    Asset protAsset = instAsset.getPrototype();
typical_case:
    if (protAsset instanceof GLMAsset) {
      // instAsset must also be an GLMAsset
      GLMAsset alpInst = (GLMAsset)instAsset;
      GLMAsset alpProt = (GLMAsset)protAsset;
      // compare containPGs (volCap, areaCap, weightCap)
      ContainPG instContainPG = alpInst.getContainPG();
      ContainPG protContainPG = alpProt.getContainPG();
      if (instContainPG != protContainPG) {
        // break to the general case
        break typical_case;
      }
      // compare typeIdPGs (typeId, nomen)
      TypeIdentificationPG instTypeIdPG = 
        alpInst.getTypeIdentificationPG();
      TypeIdentificationPG protTypeIdPG = 
        alpProt.getTypeIdentificationPG();
      if (instTypeIdPG != protTypeIdPG) {
        break typical_case;
      }
      // compare asset types (e.g. SHIP, TRUCK, etc)
      int protConveyanceType = getAssetType(alpProt);
      if (alpInst.getClass() != alpProt.getClass()) {
        int instConveyanceType = getAssetType(alpInst);
        if (instConveyanceType != protConveyanceType) {
          break typical_case;
        }
      }
      // compare average speed (e.g. 55 mpg, etc)
      double instAveSpeed = getAverageSpeed(alpInst);
      double protAveSpeed = getAverageSpeed(alpProt);
      // FIXME double equality should probably use +/- epsilon
      if (instAveSpeed != protAveSpeed) {
        break typical_case;
      }

      // instance is equivalent to it's prototype!
      //
      // register the prototype
      String protId;
      if ((protTypeIdPG == null) ||
          ((protId = protTypeIdPG.getTypeIdentification()) == null)) {
        // typically use typeId, but here use UID
        protId = alpProt.getUID().toString();
      }
      if (toReg.carriers.getPrototype(protId) == null) {
        // must register this prototype
        //
        // create a new ConveyancePrototype
        ConveyancePrototype toConvProt = new ConveyancePrototype();
        toConvProt.UID = protId;
        toConvProt.conveyanceType = protConveyanceType;
        toConvProt.aveSpeed = protAveSpeed;
        if (protContainPG != null) {
          // volume in liters
          Volume maxVol = protContainPG.getMaximumVolume();
          if (maxVol != null) {
            toConvProt.volCap = maxVol.getLiters();
          }
          // area in square meters
          Area maxArea = protContainPG.getMaximumFootprintArea();
          if (maxArea != null) {
            toConvProt.areaCap = maxArea.getSquareMeters();
          }
          // weight in grams
          Mass maxWeight = protContainPG.getMaximumWeight();
          if (maxWeight != null) {
            toConvProt.weightCap = maxWeight.getGrams();
          }
        }
        if (protTypeIdPG != null) {
          // type id
          toConvProt.alpTypeID = 
            protTypeIdPG.getTypeIdentification();
          // nomen
          toConvProt.nomenclature = 
            protTypeIdPG.getNomenclature();
        }

        // register the ConveyancePrototype
        toReg.carriers.addPrototype(toConvProt);
      }

      // return the id
      return protId;
    }

    // general case.  must grab all "carrier" fields and compare
    //   values.
    //
    // FIXME this could be optimized, but we've already caught
    //   the "typical" case... 

    // get all "instance" values
    double instVolCap = 0.0;
    double instAreaCap = 0.0;
    double instWeightCap = 0.0;
    int instConveyanceType = 0;
    double instAveSpeed = 0.0;
    String instAlpTypeId = null;
    String instNomenclature = null;
    //
    TypeIdentificationPG instTypeIdPG = 
      instAsset.getTypeIdentificationPG();
    if (instTypeIdPG != null) {
      instAlpTypeId = 
        instTypeIdPG.getTypeIdentification();
      instNomenclature = 
        instTypeIdPG.getNomenclature();
    }
    if (instAsset instanceof GLMAsset) {
      // instAsset must also be an GLMAsset
      GLMAsset alpInst = (GLMAsset)instAsset;
      ContainPG instContainPG = alpInst.getContainPG();
      if (instContainPG != null) {
        Volume maxVol = instContainPG.getMaximumVolume();
        if (maxVol != null) {
          instVolCap = maxVol.getLiters();
        }
        Area maxArea = instContainPG.getMaximumFootprintArea();
        if (maxArea != null) {
          instAreaCap = maxArea.getSquareMeters();
        }
        Mass maxWeight = instContainPG.getMaximumWeight();
        if (maxWeight != null) {
          instWeightCap = maxWeight.getGrams();
        }
      }
      instConveyanceType = getAssetType(alpInst);
      instAveSpeed = getAverageSpeed(alpInst);
    }

    // get all "prototype" values
    double protVolCap = 0.0;
    double protAreaCap = 0.0;
    double protWeightCap = 0.0;
    int protConveyanceType = 0;
    double protAveSpeed = 0.0;
    String protAlpTypeId = null;
    String protNomenclature = null;
    //
    if (protAsset != null) {
      TypeIdentificationPG protTypeIdPG = 
        protAsset.getTypeIdentificationPG();
      if (protTypeIdPG != null) {
        protAlpTypeId = 
          protTypeIdPG.getTypeIdentification();
        protNomenclature = 
          protTypeIdPG.getNomenclature();
      }
    }
    if (protAsset instanceof GLMAsset) {
      // instAsset must also be an GLMAsset
      GLMAsset alpProt = (GLMAsset)protAsset;
      ContainPG protContainPG = alpProt.getContainPG();
      if (protContainPG != null) {
        Volume maxVol = protContainPG.getMaximumVolume();
        if (maxVol != null) {
          protVolCap = maxVol.getLiters();
        }
        Area maxArea = protContainPG.getMaximumFootprintArea();
        if (maxArea != null) {
          protAreaCap = maxArea.getSquareMeters();
        }
        Mass maxWeight = protContainPG.getMaximumWeight();
        if (maxWeight != null) {
          protWeightCap = maxWeight.getGrams();
        }
      }
      protConveyanceType = getAssetType(alpProt);
      protAveSpeed = getAverageSpeed(alpProt);
    }

    // compare the "instance" to the "prototype"
    //
    // FIXME double equality should probably use +/- epsilon
    if ((instVolCap == protVolCap) &&
        (instAreaCap == protAreaCap) &&
        (instWeightCap == protWeightCap) &&
        (instAveSpeed == protAveSpeed) &&
        ((protAlpTypeId == instAlpTypeId) ||
         ((protAlpTypeId != null) &&
          (protAlpTypeId.equals(instAlpTypeId)))) &&
        ((protNomenclature == instNomenclature) ||
         ((protNomenclature != null) &&
          (protNomenclature.equals(instNomenclature)))) &&
        (protAsset != null)) {
      // instance is equivalent to it's prototype!
      //
      // register the prototype
      String protId =
        ((protAlpTypeId != null) ?
         (protAlpTypeId) :
         (protAsset.getUID().toString()));
      if (toReg.carriers.getPrototype(protId) == null) {
        // must register this prototype
        //
        // create a new ConveyancePrototype
        ConveyancePrototype toConvProt = new ConveyancePrototype();
        toConvProt.UID = protId;
        toConvProt.conveyanceType = protConveyanceType;
        toConvProt.aveSpeed = protAveSpeed;
        toConvProt.volCap = protVolCap;
        toConvProt.areaCap = protAreaCap;
        toConvProt.weightCap = protWeightCap;
        toConvProt.alpTypeID = protAlpTypeId;
        toConvProt.nomenclature = protNomenclature;

        // register the ConveyancePrototype
        toReg.carriers.addPrototype(toConvProt);
      }

      // return the prototype id
      return protId;
    } else {
      // instAsset is different than it's prototype
      //
      // register the instance
      String instId =
        ((instAlpTypeId != null) ?
         (instAlpTypeId) :
         (instAsset.getUID().toString()));
      if (toReg.carriers.getPrototype(instId) == null) {
        // must register this instance
        //
        // create a new ConveyancePrototype
        ConveyancePrototype toConvProt = new ConveyancePrototype();
        toConvProt.UID = instId;
        toConvProt.conveyanceType = instConveyanceType;
        toConvProt.aveSpeed = instAveSpeed;
        toConvProt.volCap = instVolCap;
        toConvProt.areaCap = instAreaCap;
        toConvProt.weightCap = instWeightCap;
        toConvProt.alpTypeID = instAlpTypeId;
        toConvProt.nomenclature = instNomenclature;

        // register the ConveyancePrototype
        toReg.carriers.addPrototype(toConvProt);
      }

      // return the instance id
      return instId;
    }
  }

  /**
   * Register an instance of a cargo <code>Asset</code> (or group of 
   * Assets), adds the <code>String</code> cargo instance identifiers
   * to the given <code>List</code>.
   */
  public static boolean registerCargoInstance(
      Registry toReg, 
      List toCargoIds,
      Asset instAsset) {

    if (instAsset instanceof AssetGroup) {
      // divide group into separate assets
      List groupL = assetHelper.expandAssetGroup((AssetGroup)instAsset);
      int n = groupL.size();
      for (int i = 0; i < n; i++) {
        Asset ai = (Asset)groupL.get(i);
        String aiId;
        try {
          aiId = registerCargoInstance(toReg, ai);
        } catch (RuntimeException e) {
          throw new IllegalArgumentException(
              "Unable to expand assetGroup "+
              ((instAsset != null) ?
               ("(UID: "+instAsset.getUID()+")") :
               ("null")) + " element["+i+"]: "+
              ((ai != null) ? 
               ("(UID: "+ai.getUID()+")") :
               ("null")));
        }
        toCargoIds.add(aiId);
      }
    } else {
      // add the single element
      toCargoIds.add(
          registerCargoInstance(toReg, instAsset));
    }

    // return list of identifiers
    return true;
  }

  /**
   * Register a non-<code>AssetGroup</code> cargo asset instance, return
   * the instance identifier.
   *
   * Only for use by <tt>registerCargoInstance(Registry,List,Asset)</tt>!
   */
  private static String registerCargoInstance(
      Registry toReg, 
      Asset instAsset) {
    //
    // ASSERT(!(instAsset instanceof AssetGroup));
    //

    // a single asset

    // get the cargo id
    String instId = instAsset.getUID().toString();

    // check if cargo instance is registered
    if (toReg.cargoInstances.getInstance(instId) == null) {
      // must register this cargo instance
      //
      // create a new cargo instance
      Instance toCargo = new Instance();
      toCargo.UID = instId;

      // get owner id (unit)
      toCargo.ownerID = getAssetOwner(instAsset);

      // get asset "instance" for prototyping
      Asset singleAsset;
      if (instAsset instanceof AggregateAsset) {
        // aggregate
        AggregateAsset ag = (AggregateAsset)instAsset;
        toCargo.aggregateNumber = ag.getQuantity();
        singleAsset = ag.getAsset();
      } else {
        // single item
        toCargo.aggregateNumber = 1;
        if (glmAssetHelper.isPallet(instAsset)) {
          // manifest UID if container/milvan/pallet
          //   FIXME: where is the manifest id kept?
          toCargo.manifestUID = null;
        }
	else if (instAsset instanceof Container) {
	  if (testing) {
	    toCargo.hasManifest = true;
	    toCargo.nomenclatures = new ArrayList ();
	    toCargo.nomenclatures.add ("Tank Shell");
	    toCargo.typeIdentifications = new ArrayList ();
	    toCargo.typeIdentifications.add ("DODIC/XXX");
	    toCargo.weights = new ArrayList ();
	    toCargo.weights.add (Mass.newMass(0.01d, Mass.SHORT_TONS)); 
	    System.out.println ("Added stuff to container " + instAsset);
	  }
	  /*
	  else {
	    Class contentsPGClass = org.cougaar.domain.glm.ldm.asset.ContentsPG.class;
	    ContentsPG contentsPG = (ContentsPG) instAsset.searchForPropertyGroup (contentsPGClass);
	    if (contentsPG != null) {
	      toCargo.hasManifest = true;
	      toCargo.nomenclatures = (List) contentsPG.getNomenclatures ();
	      toCargo.typeIdentifications = (List) contentsPG.getTypeIdentifications ();
	      toCargo.weights = (List) contentsPG.getWeights ();
	    }
	  }
	  */
	}
        singleAsset = instAsset;
      }

      // set prototype id
      toCargo.prototypeUID = 
        registerCargoPrototype(toReg, singleAsset);

      // set the item id == "bumper number"
      ItemIdentificationPG itemPG = 
        singleAsset.getItemIdentificationPG();
      if (itemPG != null) {
        toCargo.name = 
          itemPG.getItemIdentification();
        toCargo.itemNomen =
          itemPG.getNomenclature();
      }

      // register the cargo
      toReg.cargoInstances.addInstance(toCargo);
    }

    // return the id
    return instId;
  }

  /**
   * Register a prototype of a cargo <code>Asset</code>, return
   * a <code>String</code> cargo prototype identifier.
   * <p>
   * This is more like a "template" than a "prototype", since the
   * registered information might refer to either the asset 
   * instance or it's <tt>GLMAsset.getPrototype()</tt>.  In the
   * same fashion as for carriers, for cargo either the instance 
   * or it's GLMAsset.getPrototype() is registered -- the 
   * GLMAsset.getPrototype() if the instance information exactly 
   * matches the GLMAsset.getPrototype().  Unlike carriers we
   * always register the prototype and reference it in an "instance"
   * <code>Prototype</code> as the <tt>parentUID</tt>.
   */
  private static String registerCargoPrototype(
      Registry toReg, 
      Asset instAsset) {

    // given an "instance" of a cargoAsset, register it with
    //   it's "template" (== "prototype").
    //
    // check for the typical case in TOPS, where an instance is
    //   equivalent to it's prototype.  This often happens for
    //   tanks, pallets, etc.
    Asset protAsset = instAsset.getPrototype();
typical_case:
    if (protAsset instanceof GLMAsset) {
      // instAsset must also be an GLMAsset
      GLMAsset alpInst = (GLMAsset)instAsset;
      GLMAsset alpProt = (GLMAsset)protAsset;
      // compare asset classes (CLASS_I, CLASS_IV, etc)
      //   and type (CONTAINER or !CONTAINER)
      int protAssetClass = getAssetClass(protAsset);
      if (alpInst.getClass() != alpProt.getClass()) {
        int instAssetClass = getAssetClass(instAsset);
        if (instAssetClass != protAssetClass) {
          // break to the general case
          break typical_case;
        }
      }
      // compare physicalPGs (weight, width, height, depth)
      PhysicalPG instPhysPG = alpInst.getPhysicalPG();
      PhysicalPG protPhysPG = alpProt.getPhysicalPG();
      if (instPhysPG != protPhysPG) {
        break typical_case;
      }
      // compare typeIdPGs (typeId, nomen)
      TypeIdentificationPG instTypeIdPG = 
        instAsset.getTypeIdentificationPG();
      TypeIdentificationPG protTypeIdPG = 
        protAsset.getTypeIdentificationPG();
      if (instTypeIdPG != protTypeIdPG) {
        break typical_case;
      }

      // instance is equivalent to it's prototype!
      //
      // register the prototype
      String protId;
      if ((protTypeIdPG == null) ||
          ((protId = protTypeIdPG.getTypeIdentification()) == null)) {
        // typically use typeId, but here use UID
        protId = alpProt.getUID().toString();
      }
      if (toReg.cargoPrototypes.getPrototype(protId) == null) {
        // must register this cargo prototype
        //
        // create a new cargo prototype
        Prototype toProt = new Prototype();
        toProt.UID = protId;

        // toProt.parentUID is null, since we're using the prototype
        //
        // asset class (CLASS_I, CLASS_IV, etc)
        toProt.assetClass = protAssetClass;
        // asset type (CONTAINER or !CONTAINER)
        toProt.assetType = 
          ((protAssetClass == Prototype.ASSET_CLASS_CONTAINER) ?
           (Prototype.ASSET_TYPE_CONTAINER) :
           (Prototype.ASSET_TYPE_ASSET));
        if (protPhysPG != null) {
          // weight in grams
          Mass protWeight = protPhysPG.getMass();
          if (protWeight != null) {
            toProt.weight = protWeight.getGrams();
          }
          // width in meters
          Distance protWidth = protPhysPG.getWidth();
          if (protWidth != null) {
            toProt.width = protWidth.getMeters();
          }
          // height in meters
          Distance protHeight = protPhysPG.getHeight();
          if (protHeight != null) {
            toProt.height = protHeight.getMeters();
          }
          // depth in meters
          Distance protDepth = protPhysPG.getLength();
          if (protDepth != null) {
            toProt.depth = protDepth.getMeters();
          }
        }
        if (protTypeIdPG != null) {
          // type id
          toProt.alpTypeID = 
            protTypeIdPG.getTypeIdentification();
          // nomen
          toProt.nomenclature = 
            protTypeIdPG.getNomenclature();
        }

        // register the cargo prototype
        toReg.cargoPrototypes.addPrototype(toProt);
      }

      // return the id
      return protId;
    }

    // general case.  must grab all "cargo" fields and compare
    //   values.
    //
    // FIXME this could be optimized, but we've already caught
    //   the "typical" case... 

    // always need the prototype for cargo
    String protId = null;
    //
    int protAssetClass = 0;
    int protAssetType = 0;
    String protAlpTypeId = null;
    String protNomenclature = null;
    double protWeight = 0.0;
    double protWidth = 0.0;
    double protHeight = 0.0;
    double protDepth = 0.0;
    //
    if (protAsset != null) {
      protAssetClass = getAssetClass(protAsset);
      protAssetType = 
        ((protAssetClass == Prototype.ASSET_CLASS_CONTAINER) ?
         (Prototype.ASSET_TYPE_CONTAINER) :
         (Prototype.ASSET_TYPE_ASSET));
      TypeIdentificationPG protTypePG = 
        protAsset.getTypeIdentificationPG();
      if (protTypePG != null) {
        protAlpTypeId = 
          protTypePG.getTypeIdentification();
        protNomenclature = 
          protTypePG.getNomenclature();
      }
      if (protAsset instanceof GLMAsset) {
        PhysicalPG protPhysPG = 
          ((GLMAsset)protAsset).getPhysicalPG();
        if (protPhysPG != null) {
          Mass weight = protPhysPG.getMass();
          if (weight != null) {
            protWeight = weight.getGrams();
          }
          Distance width = protPhysPG.getWidth();
          if (width != null) {
            protWidth = width.getMeters();
          }
          Distance height = protPhysPG.getHeight();
          if (height != null) {
            protHeight = height.getMeters();
          }
          Distance depth = protPhysPG.getLength();
          if (depth != null) {
            protDepth = depth.getMeters();
          }
        }
      }

      // register the prototype
      protId =
        ((protAlpTypeId != null) ?
         (protAlpTypeId) :
         (protAsset.getUID().toString()));
      if (toReg.cargoPrototypes.getPrototype(protId) == null) {
        // must register this prototype
        //
        // create a new cargo prototype
        Prototype toProt = new Prototype();
        toProt.UID = protId;
        toProt.assetClass = protAssetClass;
        toProt.assetType = protAssetType;
        toProt.alpTypeID = protAlpTypeId;
        toProt.nomenclature = protNomenclature;
        toProt.weight = protWeight;
        toProt.width = protWidth;
        toProt.height = protHeight;
        toProt.depth = protDepth;

        // register the cargo prototype
        toReg.cargoPrototypes.addPrototype(toProt);
      }

      // don't return the protId just yet!
    }

    // get all the instance values
    int instAssetClass = 0;
    int instAssetType = 0;
    String instAlpTypeId = null;
    String instNomenclature = null;
    double instWeight = 0.0;
    double instWidth = 0.0;
    double instHeight = 0.0;
    double instDepth = 0.0;
    //
    instAssetClass = getAssetClass(instAsset);
    instAssetType = 
      ((instAssetClass == Prototype.ASSET_CLASS_CONTAINER) ?
       (Prototype.ASSET_TYPE_CONTAINER) :
       (Prototype.ASSET_TYPE_ASSET));
    TypeIdentificationPG instTypeIdPG = 
      instAsset.getTypeIdentificationPG();
    if (instTypeIdPG != null) {
      instAlpTypeId = 
        instTypeIdPG.getTypeIdentification();
      instNomenclature = 
        instTypeIdPG.getNomenclature();
    }
    if (instAsset instanceof GLMAsset) {
      PhysicalPG instPhysPG = 
        ((GLMAsset)instAsset).getPhysicalPG();
      if (instPhysPG != null) {
        Mass weight = instPhysPG.getMass();
        if (weight != null) {
          instWeight = weight.getGrams();
        }
        Distance width = instPhysPG.getWidth();
        if (width != null) {
          instWidth = width.getMeters();
        }
        Distance height = instPhysPG.getHeight();
        if (height != null) {
          instHeight = height.getMeters();
        }
        Distance depth = instPhysPG.getLength();
        if (depth != null) {
          instDepth = depth.getMeters();
        }
      }
    }

    // compare the "instance" to the "prototype"
    //
    // FIXME double equality should probably use +/- epsilon
    if ((protId != null) &&
        (instAssetClass == protAssetClass) &&
        (instAssetType == protAssetType) &&
        ((instAlpTypeId == protAlpTypeId) ||
         ((protAlpTypeId != null) &&
          (protAlpTypeId.equals(instAlpTypeId)))) &&
        ((instNomenclature == protNomenclature) ||
         ((protNomenclature != null) &&
          (protNomenclature.equals(instNomenclature)))) &&
        (instWeight == protWeight) &&
        (instWidth == protWidth) &&
        (instHeight == protHeight) &&
        (instDepth == protDepth)) {
      // instance is equivalent to it's prototype!
      //
      // already registered the prototype, so just 
      //   return it's id
      return protId;
    } else {
      // instAsset is different than it's prototype
      //
      // register the instance
      String instId =
        ((instAlpTypeId != null) ?
         (instAlpTypeId) :
         (instAsset.getUID().toString()));
      if (toReg.cargoPrototypes.getPrototype(instId) == null) {
        // must register this "instance" prototype
        //
        // create a new cargo "instance" prototype
        Prototype toProt = new Prototype();
        toProt.UID = instId;
        // parentUID is the already registered prototype!
        toProt.parentUID = protId; 
        toProt.assetClass = instAssetClass;
        toProt.assetType = instAssetType;
        toProt.alpTypeID = instAlpTypeId;
        toProt.nomenclature = instNomenclature;
        toProt.weight = instWeight;
        toProt.width = instWidth;
        toProt.height = instHeight;
        toProt.depth = instDepth;

        // register the cargo "instance" prototype
        toReg.cargoPrototypes.addPrototype(toProt);
      }

      // return the instance id
      return instId;
    }
  }

  /**
   * Register a <code>TransportationRoute</code>, returns a 
   * <code>String</code> route identifier.
   */
  public static String registerRoute(
      Registry toReg,
      TransportationRoute transRoute) {

    String routeId = transRoute.getUID().toString();

    if (toReg.routes.getRoute(routeId) == null) {
      // must register this route
      //
      // create a new route
      Route toRoute = new Route();
      toRoute.setUID(routeId);

      // get links of route
      Vector routeLinks = transRoute.getLinks();
      int nRouteLinks = 
        ((routeLinks != null) ? routeLinks.size() : 0);
      if (nRouteLinks < 2) {
        // invalid route?
        return null;
      }

      // register the locations
      int i = 0;
      while (true) {
        // get start and stop locations
        TransportationLink transLink =
          (TransportationLink)routeLinks.elementAt(i);
        GeolocLocation startLoc =
          transLink.getOrigin().getGeolocLocation();
        toRoute.addSegmentLocID(
            registerLocation(toReg, startLoc));

        if (++i >= nRouteLinks) {
          // register the end location
          GeolocLocation endLoc =
            transLink.getDestination().getGeolocLocation();
          toRoute.addSegmentLocID(
              registerLocation(toReg, endLoc));
          break;
        }
      }

      // register the cargo "instance" prototype
      toReg.routes.addRoute(toRoute);
    }

    // return the route id
    return routeId;
  }

  //
  // END REGISTER
  //

  //
  // BEGIN UTILITIES
  //

  /**
   * Get the "Prototype.ASSET_CLASS_" for the given asset.
   */
  private static int getAssetClass(Asset a) {
    if (a instanceof AggregateAsset) {
      a = ((AggregateAsset)a).getAsset();
    }
    return
      (a instanceof ClassISubsistence) ? 
      Prototype.ASSET_CLASS_1 :
      (a instanceof ClassIIClothingAndEquipment) ?
      Prototype.ASSET_CLASS_2 :
      (a instanceof ClassIIIPOL) ?
      Prototype.ASSET_CLASS_3 :
      (a instanceof ClassIVConstructionMaterial) ?
      Prototype.ASSET_CLASS_4 :
      (a instanceof ClassVAmmunition) ?
      Prototype.ASSET_CLASS_5 :
      (a instanceof ClassVIPersonalDemandItem) ?
      Prototype.ASSET_CLASS_6 :
      (a instanceof ClassVIIMajorEndItem) ?
      Prototype.ASSET_CLASS_7 :
      (a instanceof ClassVIIIMedical) ?
      Prototype.ASSET_CLASS_8 :
      (a instanceof ClassIXRepairPart) ?
      Prototype.ASSET_CLASS_9 :
      (a instanceof ClassXNonMilitaryItem) ?
      Prototype.ASSET_CLASS_10 :
      ((a instanceof Container) ||
       (a instanceof org.cougaar.glm.ldm.asset.Package) ||
       (glmAssetHelper.isPallet(a))) ?
      Prototype.ASSET_CLASS_CONTAINER :
      ((a instanceof Person) ||
       ((a instanceof GLMAsset) &&
        (((GLMAsset)a).hasPersonPG()))) ?
      Prototype.ASSET_CLASS_PERSON :
      Prototype.ASSET_CLASS_UNKNOWN;
  }

  /**
   * Get the "ConveyancePrototype." type (TRUCK, SHIP, etc) for the 
   * given asset.
   */
  private static int getAssetType(Asset a) {
    // could modify to use "hasXPG()" methods
    final int retval;
    if ((a instanceof Truck) ||
        (a instanceof Trailer)) {
      // trucks
      retval = ConveyancePrototype.ASSET_TYPE_TRUCK;
    } else if ((a instanceof Train) ||
        (a instanceof RailCar)) {
      // trains
      retval = ConveyancePrototype.ASSET_TYPE_TRAIN;
    } else if ((a instanceof CargoFixedWingAircraft) ||
        (a instanceof CargoRotaryWingAircraft))  {
      // planes
      retval = ConveyancePrototype.ASSET_TYPE_PLANE;
    } else if ((a instanceof CargoShip) ||
        (a instanceof FightingShip)) {
      // ships
      retval = ConveyancePrototype.ASSET_TYPE_SHIP;
    } else if (a instanceof Deck) {
      // decks
      retval = ConveyancePrototype.ASSET_TYPE_DECK;
    } else if (a instanceof Facility) {
      // facility (e.g. airport)
      retval = ConveyancePrototype.ASSET_TYPE_FACILITY;
    } else if (a instanceof Person) {
      // people
      retval = ConveyancePrototype.ASSET_TYPE_PERSON;
    } else if (a instanceof GLMAsset) {
      GLMAsset alpA = (GLMAsset)a;
      if (alpA.hasPersonPG()) {
        // people
        retval = ConveyancePrototype.ASSET_TYPE_PERSON;
      } else if (glmAssetHelper.isVehicle(alpA)) {
        // misc self-propellable (e.g. tank)
        retval = ConveyancePrototype.ASSET_TYPE_SELF_PROPELLABLE;
      } else {
        // unknown
        retval = ConveyancePrototype.ASSET_TYPE_UNKNOWN;
      }
    } else {
      // unknown
      retval = ConveyancePrototype.ASSET_TYPE_UNKNOWN;
    }
    return retval;
  }

  /**
   * Get the owner (unit id) of the given <code>Asset</code>.
   */
  private static String getAssetOwner(Asset a) {
    // get "ForUnit" PG
    ForUnitPG unitPG;
    if (a instanceof GLMAsset) {
      // GLMAssets have a unit slot
      GLMAsset alpA = (GLMAsset)a;
      unitPG = 
        (alpA.hasForUnitPG() ?
         (alpA.getForUnitPG()) :
         (null));
    } else {
      // "forUnit" maybe attached (e.g. aggregate)
      unitPG = 
        (ForUnitPG)a.searchForPropertyGroup(ForUnitPG.class);
    }
    // take the unit info
    String ownerId;
    if (unitPG != null) {
      ownerId = unitPG.getUnit();
      if (ownerId != null) {
        int slashIdx = ownerId.indexOf('/');
        if (slashIdx > 0) {
          // remove occasional "UIC/" prefix
          ownerId = ownerId.substring(slashIdx+1);
        }
      }
    } else {
      // unknown
      ownerId = null;
    }
    // return the "owner"
    return ownerId;
  }

  /**
   * Get the average speed in miles_per_hour for an GLMAsset.
   *
   * Somewhat awkward.
   */
  private static double getAverageSpeed(GLMAsset a) {
    GroundSelfPropulsionPG groundPG;
    RailSelfPropulsionPG railPG;
    WaterSelfPropulsionPG waterPG;
    AirSelfPropulsionPG airPG;
    return
      // trucks
      ((groundPG = a.getGroundSelfPropulsionPG()) != null) ?
      groundPG.getCruiseSpeed().getMilesPerHour() :
      // trains
      ((railPG = a.getRailSelfPropulsionPG()) != null) ?
      railPG.getCruiseSpeed().getMilesPerHour() :
      // ships
      ((waterPG = a.getWaterSelfPropulsionPG()) != null) ?
      waterPG.getCruiseSpeed().getMilesPerHour() :
      // planes
      ((airPG = a.getAirSelfPropulsionPG()) != null) ?
      airPG.getCruiseSpeed().getMilesPerHour() :
      // unknown
      0.0;
  }

  /** 
   * Get the length in meters of the given transportation link.
   *
   * Should be in TransportationLink! 
   */
  private static double getLinkLength(TransportationLink transLink) {
    org.cougaar.planning.ldm.measure.Distance dist;
    if (transLink instanceof TransportationRoadLink) {
      dist = 
        ((TransportationRoadLink)transLink).getRoadLinkPG().getLinkLength();
    } else if (transLink instanceof TransportationRailLink) {
      dist =  
        ((TransportationRailLink)transLink).getRailLinkPG().getLinkLength();
    } else if (transLink instanceof TransportationSeaLink) {
      dist =  
        ((TransportationSeaLink)transLink).getSeaLinkPG().getLinkLength();
    } else if (transLink instanceof TransportationAirLink) {
      dist =  
        ((TransportationAirLink)transLink).getAirLinkPG().getLinkLength();
    } else {
      throw new RuntimeException("UNKNOWN LINK TYPE: "+transLink);
    }
    return dist.getMeters();
  }

  //
  // END UTILITIES
  //

  private static Logger logger=LoggerFactory.getInstance().createLogger("DataRegistry");
  protected static UTILAsset assetHelper    = new UTILAsset (logger);
  protected static AssetUtil glmAssetHelper = new AssetUtil (logger);
}
