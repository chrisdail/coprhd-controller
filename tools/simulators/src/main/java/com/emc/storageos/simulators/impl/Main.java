/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.simulators.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import com.emc.storageos.simulators.StorageCtlrSimulator;

public class Main {
    private static final String SERVICE_BEAN = "simserver";
    private static StorageCtlrSimulator simservice;
    private static final Logger _log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            SLF4JBridgeHandler.install();
            FileSystemXmlApplicationContext ctx = new FileSystemXmlApplicationContext(args);
            simservice = (StorageCtlrSimulator)ctx.getBean(SERVICE_BEAN);
            simservice.start();
        } catch(Exception e) {
            _log.error("failed to start {}:", SERVICE_BEAN, e);
        }
    }

    public static void stop() throws Exception {
        simservice.stop();
    }
}
