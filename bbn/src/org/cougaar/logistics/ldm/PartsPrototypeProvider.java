/*
 * <copyright>
 *  
 *  Copyright 1997-2004 BBNT Solutions, LLC
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

package org.cougaar.logistics.ldm;

import org.cougaar.logistics.ldm.asset.Level2Ammunition;
import org.cougaar.logistics.ldm.asset.Level2AmmoConsumerBG;
import org.cougaar.logistics.ldm.asset.Level2FuelConsumerBG;

import org.cougaar.core.service.LoggingService;
import org.cougaar.planning.ldm.asset.Asset;
import org.cougaar.planning.ldm.asset.NewPropertyGroup;
import org.cougaar.planning.ldm.asset.PropertyGroup;
import org.cougaar.planning.ldm.measure.Area;
import org.cougaar.planning.ldm.measure.Distance;
import org.cougaar.planning.ldm.measure.Mass;
import org.cougaar.planning.ldm.measure.Volume;
import org.cougaar.glm.ldm.asset.Ammunition;
import org.cougaar.glm.ldm.asset.BulkPOL;
import org.cougaar.glm.ldm.asset.PackagedPOL;
import org.cougaar.glm.ldm.asset.Consumable;
import org.cougaar.glm.ldm.asset.NewCostPG;
import org.cougaar.glm.ldm.asset.NewMovabilityPG;
import org.cougaar.glm.ldm.asset.NewPackagePG;
import org.cougaar.glm.ldm.asset.NewPhysicalPG;
import org.cougaar.glm.ldm.asset.PropertyGroupFactory;
import org.cougaar.glm.ldm.asset.NewSupplyClassPG;
import org.cougaar.glm.ldm.QueryLDMPlugin;

import java.math.BigDecimal;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public class PartsPrototypeProvider extends QueryLDMPlugin {

  private static Hashtable fuelsNomenclature = initFuelsNomenclature();
  private Hashtable propertyGroupTable = new Hashtable();
  private LoggingService logger;

  public void setLoggingService(LoggingService logger) {
    this.logger = logger;
  }

  public boolean canHandle(String typeid, Class class_hint) {
    Boolean protoProvider = (Boolean) myParams_.get("PrototypeProvider");
    if ((protoProvider == null) || (protoProvider.booleanValue())) {
      if (class_hint == null) {
        if (typeid.startsWith("NSN/") || typeid.startsWith("DODIC/") ||
            typeid.startsWith(MEIPrototypeProvider.LEVEL2)) {
          return true;
        } // if
      } else {
        String class_name = class_hint.getName();
        if (typeid.startsWith("DODIC/") &&
            class_name.equals("Ammunition")) {
          return true;
        } else if (typeid.startsWith("NSN/") &&
            (class_name.equals("BulkPOL") ||
            class_name.equals("Consumable") ||
            class_name.equals("PackagedPOL"))) {
          return true;
        } else if (typeid.startsWith(MEIPrototypeProvider.LEVEL2) &&
            (class_name.equals(Level2AmmoConsumerBG.LEVEL2AMMUNITION) ||
            class_name.equals(Level2FuelConsumerBG.LEVEL2BULKPOL))) {
          return true;
        }
      }// if
    } // if
    if (logger.isDebugEnabled()) {
      logger.debug("CanHandle(), Unable to provider Prototype." +
                   " ProtoProvider = " + protoProvider +
                   ", typeid= " +
                   typeid);
    }
    return false;
  } // canHandle

  public Asset makePrototype(String type_name, Class class_hint) {
    if (logger.isDebugEnabled()) {
      logger.debug("makePrototype() for type " + type_name);
    }
    String class_name = null;
    if (class_hint != null) {
      class_name = class_hint.getName();
      if (class_name.equals(MEIPrototypeProvider.MEI_STRING)) {
        return null;
      }
      if (!(class_name.equals("Ammunition") ||
          class_name.equals(Level2AmmoConsumerBG.LEVEL2AMMUNITION) ||
          class_name.equals(Level2FuelConsumerBG.LEVEL2BULKPOL) ||
          class_name.equals("BulkPOL") ||
          class_name.equals("Consumable") ||
          class_name.equals("PackagedPOL"))) {
        if (logger.isErrorEnabled()) {
          logger.error("make prototype How did we get this far?? " +
                       class_hint);
        }
        return null;
      } // if
    } // if

    // Determine the type of the prototype
    if (class_name == null) {
      // Check for consumable -- most common case
      if (type_name.startsWith("NSN/")) {
        if (fuelsNomenclature.containsKey(type_name)) {
          class_name = "BulkPOL";
        } else {
          if (getPackagedPOLNSN(type_name) != null) {
            class_name = "PackagedPOL";
          } else {
            class_name = "Consumable";
            if (logger.isDebugEnabled()) {
              logger.debug("This is a consumable");
            }
          }  // if
        } // if
      } else if (type_name.startsWith("DODIC/")) {
        class_name = "Ammunition";
      } else if (type_name.startsWith(MEIPrototypeProvider.LEVEL2)) {
        class_name = type_name;
      } else {
        if (logger.isErrorEnabled()) {
          logger.error("make prototype How did we get this far?? " +
                       class_hint);
        }
        return null;
      } // if
    } // if

    String nomenclature = getNomenclature(type_name, class_name);
    if (nomenclature == null) {
      if (logger.isDebugEnabled()) {
        logger.debug("makePrototype() getNomenclature() FAILED to make nomenclature");
      }
      return null;
    } // if
    Asset theAsset = newAsset(type_name, class_name, nomenclature);
    if (logger.isDebugEnabled()) {
      logger.debug("makePrototype() made for " + theAsset);
    }
    return theAsset;
  } // makePrototype

  private static Hashtable initFuelsNomenclature() {
    Hashtable h = new Hashtable();
    h.put("NSN/9130001601818", "MUG");
    h.put("NSN/9130002732379", "JP5");
    h.put("NSN/9130010315816", "JP8");
    h.put("NSN/9140002732377", "DFM");
    h.put("NSN/9140002865294", "DF2");
    return h;
  } // initFuelsNomenclature

  /**
   * Check whether this NSN is a Packaged POL NSN,
   * @return  null  if it isn't
   **/
  protected String getPackagedPOLNSN(String type_id) {

    String query = (String) fileParameters_.get("packagedPOLQuery");
    String consumer_id = type_id.substring(type_id.indexOf("/") + 1);
    Vector result = null;
    String nomen = null;
    if (query != null) {
      int i = query.indexOf(":nsn");
      String q1 = query.substring(0, i) + "'" + consumer_id + "'";
      String q2 = query.substring(i + 4, query.length());
      query = q1 + q2;
      try {
        result = executeQuery(query);
        if (result.isEmpty()) {
          // this is fine - means the type_id is not a Packaged POL MEI
          return null;
        } else {
          Object row[] = (Object[]) result.firstElement();
          nomen = (String) row[0];
        } // if
      } catch (Exception ee) {
        if (logger.isErrorEnabled()) {
          logger.error(" retrieveFromDB(), DB query failed. query= " +
                       query +
                       "\n ERROR " +
                       ee);
        }
        return null;
      } // try
    } // if
    return nomen;
  } // getPackagedPOLNSN

  protected String getNomenclature(String type_id, String type) {
    String nomen = null;
    if (logger.isDebugEnabled()) {
      logger.debug("getNomenclature() for type " + type_id);
    }
    Vector pgs = null;
    // skip whole query process
    if (type.equals("BulkPOL")) {
      NewSupplyClassPG pg = PropertyGroupFactory.newSupplyClassPG();
      pg.setSupplyClass("ClassIIIPOL");
      pg.setSupplyType("BulkPOL");
      pgs = new Vector();
      pgs.add(pg);
      propertyGroupTable.put(type_id, pgs);
      return (String) fuelsNomenclature.get(type_id);
    }

    String consumer_id = type_id.substring(type_id.indexOf("/") + 1);
    // create query
    String query = null;
    if (type.equals("Ammunition")) {
      query =
          substituteNSN((String) fileParameters_.get("classVData"),
                        consumer_id);
      if (logger.isDebugEnabled()) {
        logger.debug("For ammo, use query " + query);
      }
    } else if (type.equals("Consumable")) {
      //			query = substituteNSN ((String) fileParameters_.get ("classIXData"), consumer_id);

      ////			query = substituteNSN ((String) fileParameters_.get ("ConsumableArmyNSN"), consumer_id);

      // CDW: NSN for Hydraulic Fluid
      //String my_consumer_id = "9150009857236";

      // CDW: Original query and substitute
      query =
          substituteNSN((String) fileParameters_.get("classIXData"),
                        consumer_id);

      // CDW: Uses NSN for Hydraulic Fluid
      //query = "select nomenclature, ui, price, cube, weight from header where nsn='"+my_consumer_id+"'";

      // CDW: Uses current NSN with the info for Hydraulic Fluid
      //query = "select " + consumer_id + ", nomenclature from header where nsn='"+my_consumer_id+"'";

      if (logger.isDebugEnabled()) {
        logger.debug("For consumable, use query " + query);
      }
    } else if (type.startsWith(MEIPrototypeProvider.LEVEL2)) {
      pgs = addLevel2Pgs(type);
      nomen = type + " asset";
      if (pgs != null) {
        propertyGroupTable.put(type_id, pgs);
      }
      return nomen;
    } else if (type.equals("PackagedPOL")) {
      query =
          substituteNSN((String) fileParameters_.get("classIIIPackagedData"),
                        consumer_id);
      if (logger.isDebugEnabled()) {
        logger.debug("For pack pol, use query " + query);
      }
    } else {
      if (logger.isErrorEnabled()) {
        logger.error("getNomenclature(), unrecognized type " +
                     type + " for " + consumer_id);
      }
      return null;
    } // if

    // if query not found, return null
    if (query == null) {
      if (logger.isErrorEnabled()) {
        logger.error("getConsumableNomenclature() Query not found for " +
                     consumer_id);
      }
      return null;
    } // if

    // execute query
    Vector result;
    try {
      result = executeQuery(query);
    } catch (Exception ee) {
      if (logger.isErrorEnabled()) {
        logger.error("getConsumableNomenclature(), DB query failed. query= " +
                     query +
                     "\n ERROR " +
                     ee);
      }
      return null;
    } // try
    if (result.isEmpty()) {
      // PAS not sure why, but this seems to be normal
      if (logger.isDebugEnabled()) {
        logger.debug("getConsumableNomenclature() no result for " +
                     type_id +
                     " using " +
                     query);
      }
      return null;
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("Got a result:" + result);
      }
    } // if

    // parse results
    Object row[] = (Object[]) result.firstElement();
    nomen = (String) row[0];

    if (type.equals("Ammunition")) {
      pgs = parseAmmunitionRow(row);
      if (logger.isDebugEnabled()) {
        logger.debug("adding pgs for ammo");
      }
    } else if (type.equals("Consumable")) {
      pgs = parseConsumableRow(row);
      if (logger.isDebugEnabled()) {
        logger.debug("adding pgs for consumable");
      }
    } else if (type.equals("PackagedPOL")) {
      pgs = parsePackagedPOLRow(row);
      if (logger.isDebugEnabled()) {
        logger.debug("adding pgs for pack pol");
      }
    } // if

    if (pgs != null) {
      propertyGroupTable.put(type_id, pgs);
    } // if

    if (logger.isDebugEnabled()) {
      logger.debug("returning nomen " + nomen);
    }
    return nomen;
  } // getNomenclature

  private Vector parseConsumableRow(Object row[]) {
    if (logger.isDebugEnabled()) {
      logger.debug("parseConsumableRow()");
    }
    String unit_of_issue = (String) row[1];
    double cost = ((BigDecimal) row[2]).doubleValue();
    double volume = ((BigDecimal) row[3]).doubleValue();
    double weight = ((BigDecimal) row[4]).doubleValue();

    Vector pgs = createPhysicalPGs(weight, volume, unit_of_issue);
    PropertyGroup costpg = createCostPG(cost);
    if (costpg != null) {
      pgs.add(costpg);
    } // if
    NewSupplyClassPG supply_pg = PropertyGroupFactory.newSupplyClassPG();
    supply_pg.setSupplyClass("ClassIXRepairPart");
    supply_pg.setSupplyType("Consumable");
    pgs.add(supply_pg);
    return pgs;
  } // parseConsumableRow

  private Vector parsePackagedPOLRow(Object row[]) {
    String unit_of_issue = (String) row[1];
    double cost = ((BigDecimal) row[2]).doubleValue();
    double volume = ((BigDecimal) row[3]).doubleValue();
    double weight = ((BigDecimal) row[4]).doubleValue();

    Vector pgs = createPhysicalPGs(weight, volume, unit_of_issue);
    PropertyGroup costpg = createCostPG(cost);
    if (costpg != null) {
      pgs.add(costpg);
    }
    NewSupplyClassPG supply_pg = PropertyGroupFactory.newSupplyClassPG();
    supply_pg.setSupplyClass("ClassIIIPOL");
    supply_pg.setSupplyType("PackagedPOL");
    pgs.add(supply_pg);
    return pgs;
  } // parsePackagedPOLRow

  private Vector addLevel2Pgs (String type) {
    Vector pgs = new Vector();
    NewSupplyClassPG supply_pg = PropertyGroupFactory.newSupplyClassPG();

    if (type.equals(Level2AmmoConsumerBG.LEVEL2AMMUNITION)) {
      supply_pg.setSupplyClass("ClassVAmmunition");
      supply_pg.setSupplyType("Ammunition");
    }
    else if (type.equals(Level2FuelConsumerBG.LEVEL2BULKPOL)) {
      supply_pg.setSupplyClass("ClassIIIPOL");
      supply_pg.setSupplyType("BulkPOL");
    }
    else throw new IllegalArgumentException("Don't know anything about this type :" + type);
    pgs.add(supply_pg);
    return pgs;
  }

  private Vector parseAmmunitionRow(Object row[]) {
    double weight;
    if ((row[1]) instanceof BigDecimal) {
      weight = ((BigDecimal) row[1]).doubleValue();
    }
    else {
      weight = ((Double) row[1]).doubleValue();
    }
    String cargo_cat_code = (String) row[2];
    Vector pgs = new Vector();

    NewPhysicalPG pg = PropertyGroupFactory.newPhysicalPG();
    pg.setMass(new Mass(weight, Mass.POUNDS));
    pgs.add(pg);

    NewPackagePG pg2 = PropertyGroupFactory.newPackagePG();
    pg2.setPackMass(new Mass(weight, Mass.POUNDS));
    pgs.add(pg2);

    if (cargo_cat_code != null) {
      NewMovabilityPG pg3 = PropertyGroupFactory.newMovabilityPG();
      pg3.setMoveable(true);
      pg3.setCargoCategoryCode(cargo_cat_code);
      pgs.add(pg3);
    } // if
    NewSupplyClassPG supply_pg = PropertyGroupFactory.newSupplyClassPG();
    supply_pg.setSupplyClass("ClassVAmmunition");
    supply_pg.setSupplyType("Ammunition");
    pgs.add(supply_pg);

    return pgs;
  } // parseAmmunitionRow

  private PropertyGroup createCostPG(double cost) {
    NewCostPG pg = PropertyGroupFactory.newCostPG();
    // Unit Price in the header table has 2 implied decimal places
    pg.setBreakOutCost(cost / 100.);

    return pg;
  } // createCostPG

  private Vector createPhysicalPGs(double weight, double volume, String unit_of_issue) {
    Vector pgs = new Vector();
    double length;
    // Temporary fix for TOPS so that all parts have a PhysicalPG
    if (volume == 0) {
      volume = (double) 1;
    } // if
    if (weight == 0) {
      weight = (double) 1;
    } // if

    // Weight in the header table has 3 implied decimal places
    weight = weight / 1000.;
    // Volume in the header table has 4 implied decimal places
    volume = volume / 10000.;
    // Assuming a perfect cube until we get better data
    length = Math.pow(volume, 1.0 / 3.0);
    NewPhysicalPG pg = PropertyGroupFactory.newPhysicalPG();
    pg.setFootprintArea(new Area((length * length), Area.SQUARE_FEET));
    pg.setHeight(new Distance(length, Distance.FEET));
    pg.setLength(new Distance(length, Distance.FEET));
    pg.setWidth(new Distance(length, Distance.FEET));
    pg.setVolume(new Volume(volume, Volume.CUBIC_FEET));
    pg.setMass(new Mass(weight, Mass.POUNDS));
    pgs.add(pg);

    if (unit_of_issue != null) {
      NewPackagePG pg2 = PropertyGroupFactory.newPackagePG();
      pg2.setPackFootprintArea(new Area((length * length), Area.SQUARE_FEET));
      pg2.setPackHeight(new Distance(length, Distance.FEET));
      pg2.setPackLength(new Distance(length, Distance.FEET));
      pg2.setPackWidth(new Distance(length, Distance.FEET));
      pg2.setPackVolume(new Volume(volume, Volume.CUBIC_FEET));
      pg2.setPackMass(new Mass(weight, Mass.POUNDS));
      pg2.setUnitOfIssue(new String(unit_of_issue));
      pgs.add(pg2);
    } // if

    return pgs;
  } // createPhysicalPGs

  public void fillProperties(Asset anAsset) {
    if (logger.isDebugEnabled()) {
      logger.debug("fillProperties() for " + anAsset);
    }
    Vector pgs = null;
    if ((anAsset instanceof Ammunition) ||
        (anAsset instanceof Level2Ammunition) ||
        (anAsset instanceof BulkPOL) ||
        (anAsset instanceof PackagedPOL) ||
        (anAsset instanceof Consumable)) {
      String typeID = anAsset.getTypeIdentificationPG()
          .getTypeIdentification();
      pgs = (Vector) propertyGroupTable.get(typeID);
      if (logger.isDebugEnabled()) {
        logger.debug("typeID " + typeID);
      }
    }  // if
    if ((pgs != null) && !pgs.isEmpty()) {
      if (logger.isDebugEnabled()) {
        logger.debug("ready");
      }
      Enumeration pgs_enum = pgs.elements();
      while (pgs_enum.hasMoreElements()) {
        NewPropertyGroup pg = (NewPropertyGroup) pgs_enum.nextElement();
        anAsset.setPropertyGroup(pg);
        if (logger.isDebugEnabled()) {
          logger.debug("setting PG " + pg);
        }
      } // while
    } // if
  } // fillProperties

  /** Replaces the ":nsn" in the query with the actual NSN.
   * @param q query string
   * @param nsn actual NSN
   * @return new query
   **/
  public String substituteNSN(String q, String nsn) {
    String query = null;
    if (q != null) {
      int indx = q.indexOf(":nsn");
      if (indx != -1) {
        query = q.substring(0, indx) + "'" + nsn + "'";
        if (q.length() > indx + 4) {
          query += q.substring(indx + 4);
        } // if
      } // if
    } // if
    return query;
  } // substituteNSN

} // PartsPrototypeProvider
