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

package org.cougaar.logistics.plugin.demand;

import org.cougaar.planning.ldm.plan.Task;
import org.cougaar.planning.ldm.plan.Schedule;
import org.cougaar.planning.ldm.asset.Asset;
import org.cougaar.util.TimeSpan;

import java.util.Collection;


/**
 * The GenProjExpanderIfc dictates the functionality needed by the
 * {@link DemandForecastPlugin}.  The Expander should extend the
 * DemandForecastModule for convenience and call super(DemandForcastPlugin).
 * The constructor of the Expander will take a single argument, a
 * reference to the plugin.
 **/

public interface GenProjExpanderIfc {

  /** 
   * Expand the passed in GenerateProjectins task into the requisite ProjectSupply
   * tasks - one for each resource need of this MEI/Asset determined by the 
   * BG associated with the passed in supplyPGClass.
   **/
  void expandGenerateProjections(Task gpTask, Schedule schedule, Asset asset, TimeSpan timespan);

  /** 
   * Reflect new received results in the estimated results slot so that
   * AllocationResult notifications flow back to the root.
   * @param planElements - Collection of PlanElements to check for updates.
   **/
  void updateAllocationResults(Collection planElements);
}

