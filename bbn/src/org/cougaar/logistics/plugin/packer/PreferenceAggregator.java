/*
 * <copyright>
 *  
 *  Copyright 1999-2004 Honeywell Inc
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

package org.cougaar.logistics.plugin.packer;

import org.cougaar.planning.ldm.PlanningFactory;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Typically, when building an aggregator, one wants to be able to
 * specify how the Preferences on the MPTask of a Container will
 * depend on the Preferences of its parent Task(s).  This interface
 * encapsulates that behavior.
 */
public interface PreferenceAggregator {
  /**
   * @return The return value of this method should be an Enumeration of
   * Preferences, suitable to be the input value of the NewTask interface's
   * setPreferences method.
   * @see org.cougaar.planning.ldm.plan.NewTask#setPreferences
   */
  ArrayList aggregatePreferences(Iterator tasks, PlanningFactory rootFactory);
}




