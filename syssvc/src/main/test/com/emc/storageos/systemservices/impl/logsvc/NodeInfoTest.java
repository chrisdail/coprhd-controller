/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.logsvc;

import java.net.URI;

import com.emc.storageos.systemservices.impl.resource.util.NodeInfo;
import org.junit.Assert;
import org.junit.Test;

/**
 * JUnit test class for {@link NodeInfo}.
 */
public class NodeInfoTest {

    // Test data constants.
    private static final String TEST_ID = "testId";
    private static final String TEST_HOST = "10.247.66.22";
    private static final String TEST_PORT = "9998";
    private static final URI TEST_ENDPOIT = URI.create("http://" + TEST_HOST + ":"
        + TEST_PORT);

    /**
     * Tests the getId method.
     */
    @Test
    public void testGetId() {
        boolean wasException = false;
        try {
            NodeInfo nodeInfo = new NodeInfo(TEST_ID, TEST_ENDPOIT);
            Assert.assertEquals(nodeInfo.getId(), TEST_ID);

        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the getIpAddress method.
     */
    @Test
    public void testGetIpAddress() {
        boolean wasException = false;
        try {
            NodeInfo nodeInfo = new NodeInfo(TEST_ID, TEST_ENDPOIT);
            Assert.assertEquals(nodeInfo.getIpAddress(), TEST_HOST);

        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }

    /**
     * Tests the getPort method.
     */
    @Test
    public void getPort() {
        boolean wasException = false;
        try {
            NodeInfo nodeInfo = new NodeInfo(TEST_ID, TEST_ENDPOIT);
            Assert.assertEquals(nodeInfo.getPort(), Integer.parseInt(TEST_PORT));

        } catch (Exception e) {
            wasException = true;
        }
        Assert.assertFalse(wasException);
    }
}
