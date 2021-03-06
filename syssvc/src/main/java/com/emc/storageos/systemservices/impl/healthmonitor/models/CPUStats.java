/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.healthmonitor.models;

import com.google.common.primitives.UnsignedLong;

public class CPUStats {

    public CPUStats(UnsignedLong userMode, UnsignedLong systemMode,
                    UnsignedLong idle, UnsignedLong iowait) {
        this.userMode = userMode;
        this.systemMode = systemMode;
        this.idle = idle;
        this.iowait = iowait;
    }

    private UnsignedLong userMode;  //system spent in user mode
    private UnsignedLong systemMode; // system mode
    private UnsignedLong idle; // idle task
    private UnsignedLong iowait;

    public UnsignedLong getUserMode() {
        return userMode;
    }

    public UnsignedLong getSystemMode() {
        return systemMode;
    }

    public UnsignedLong getIdle() {
        return idle;
    }

    public UnsignedLong getIowait() {
        return iowait;
    }
}
