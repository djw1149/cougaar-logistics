/*  */

/*
  Copyright (C) 1999-2000 Ascent Technology Inc. (Program).  All rights
  Reserved.
  
  This material has been developed pursuant to the BBN/RTI "ALPINE"
  Joint Venture contract number MDA972-97-C-0800, by Ascent Technology,
  Inc. 64 Sidney Street, Suite 380, Cambridge, MA 02139.

*/


package org.cougaar.mlm.ui.newtpfdd.producer;


import org.cougaar.mlm.ui.newtpfdd.util.OutputHandler;


public class DateKey implements Comparable
{
    long dateSeconds;

    DateKey(long dateSeconds)
    {
	this.dateSeconds = dateSeconds / (60 * 10 * 1000); // consider 10 minutes apart to be equivalent
    }

    public int compareTo(Object object)
    {
	if ( !(object instanceof DateKey) )
	    OutputHandler.out("DK:cT Error: " + getClass() + " cannot compare to " + object.getClass());
	DateKey otherKey = (DateKey)object;
	if ( dateSeconds < otherKey.dateSeconds )
	    return -1;
	if ( dateSeconds == otherKey.dateSeconds )
	    return 0;
	return 1;
    }

    public String toString()
    {
	return dateSeconds + "";
    }
}
