/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class LunParam {
    private VNXeBase pool;
    private Boolean isThinEnabled;
    private Long size;
    private FastVPParam fastVPParameters;
    private int defaultNode;
    private List<BlockHostAccess> hostAccess;
    public VNXeBase getPool() {
        return pool;
    }
    public void setPool(VNXeBase pool) {
        this.pool = pool;
    }
    public Boolean getIsThinEnabled() {
        return isThinEnabled;
    }
    public void setIsThinEnabled(Boolean isThinEnabled) {
        this.isThinEnabled = isThinEnabled;
    }
    public Long getSize() {
        return size;
    }
    public void setSize(Long size) {
        this.size = size;
    }
    public FastVPParam getFastVPParameters() {
        return fastVPParameters;
    }
    public void setFastVPParameters(FastVPParam fastVPParameters) {
        this.fastVPParameters = fastVPParameters;
    }
    public int getDefaultNode() {
        return defaultNode;
    }
    public void setDefaultNode(int defaultNode) {
        this.defaultNode = defaultNode;
    }
    public List<BlockHostAccess> getHostAccess() {
        return hostAccess;
    }
    public void setHostAccess(List<BlockHostAccess> hostAccess) {
        this.hostAccess = hostAccess;
    }
    
}
