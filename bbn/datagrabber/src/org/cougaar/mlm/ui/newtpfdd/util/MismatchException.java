/* $Header: /opt/rep/cougaar/logistics/bbn/datagrabber/src/org/cougaar/mlm/ui/newtpfdd/util/Attic/MismatchException.java,v 1.1 2002-05-14 20:41:08 gvidaver Exp $ */

/*
  Copyright (C) 1999-2000 Ascent Technology Inc. (Program).  All rights
  Reserved.
  
  This material has been developed pursuant to the BBN/RTI "ALPINE"
  Joint Venture contract number MDA972-97-C-0800, by Ascent Technology,
  Inc. 64 Sidney Street, Suite 380, Cambridge, MA 02139.

  @author Daniel Bromberg
*/


package org.cougaar.mlm.ui.newtpfdd.util;


// Exception type for attempting to copy, clone, or parse between
// incompatible pre-existing objects
public class MismatchException extends java.lang.Exception
{
    public MismatchException(String message)
    {
	super(message);
    }
}
