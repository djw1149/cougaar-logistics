/*
 * <copyright>
 *  
 *  Copyright 2003-2004 BBNT Solutions, LLC
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
/*
 * User: tom
 * Date: Aug 9, 2002
 * Time: 9:57:15 AM
 */
package test.org.cougaar.mlm.ui.grabber.workqueue;

import junit.framework.TestCase;
import org.cougaar.mlm.ui.grabber.workqueue.ResultQueue;
import org.cougaar.mlm.ui.grabber.workqueue.Result;

public class ResultQueueTest extends TestCase {
    public ResultQueueTest(String name) {
        super(name);
    }

    public void testBasic() {
        ResultQueue rq = new ResultQueue();
        assertTrue(!rq.hasResult());
    }

    public void testAdd() {
        ResultQueue rq = new ResultQueue();
        rq.handleResult(new Result() {
            public int getID() {
                return 1;
            }
        });
        assertTrue(rq.hasResult());
        assertEquals(1, rq.getResult().getID());
    }
}
