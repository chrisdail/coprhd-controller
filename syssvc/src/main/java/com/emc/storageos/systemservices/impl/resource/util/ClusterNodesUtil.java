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
package com.emc.storageos.systemservices.impl.resource.util;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Class used to get cluster nodes information.
 */
public class ClusterNodesUtil {
    // Logger reference.
    private static final Logger _log = LoggerFactory.getLogger(ClusterNodesUtil.class);

    // A reference to the coordinator client for retrieving the registered
    // Bourne nodes.
    private static CoordinatorClient _coordinator;

    // A reference to the service for getting Bourne cluster information.
    private static Service _service;

    private static CoordinatorClientExt _coordinatorExt;
    
    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public void setCoordinatorExt(CoordinatorClientExt coordinatorExt) {
        _coordinatorExt = coordinatorExt;
    }

    public void setService(Service service) {
        _service = service;
    }

    /**
     * Gets a reference to the node connection info for the nodes requested.
     *
     * @param nodeNames List of node names whose information is returned
     * @return A list containing the connection info for all nodes in the Bourne
     *         cluster.
     * @throws IllegalStateException When an exception occurs trying to get the
     *                               cluster nodes.
     */
    public static List<NodeInfo> getClusterNodeInfo(List<String> nodeNames) {
        List<NodeInfo> nodeInfoList = new ArrayList<NodeInfo>();
        List<String> validNodeIds = new ArrayList<String>();
        try {
            if( nodeNames != null && nodeNames.size() > 0 ){
                _log.info("Getting cluster node info for ids: {}", nodeNames);
            }
            else{
                _log.info("Getting cluster node info for all nodes");
            }

            // We get all instances of the "syssvc" services registered with the
            // cluster coordinator. There will be one on each Bourne cluster
            // node.
            List<Service> svcList = _coordinator.locateAllServices(_service.getName()
                    , _service.getVersion(), null, null);
            for (Service svc : svcList) {
                _log.debug("Got service with node id " + svc.getNodeName());
                // if there are node ids requested
                if (nodeNames != null && nodeNames.size() > 0 &&
                        !nodeNames.contains(svc.getNodeName())) {
                    continue;
                }
                // The service identifier specifies the connection information
                // for the node on which the service executes.
                URI nodeEndPoint = svc.getEndpoint(null);
                if (nodeEndPoint != null) {
                    nodeInfoList.add(new NodeInfo(svc.getNodeName(), nodeEndPoint));
                    validNodeIds.add(svc.getNodeName());
                }
            }
            _log.debug("Valid node ids: {}", validNodeIds);
        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError("cluster nodes info", "coordinator", e);
        }

        //validate if all requested node ids information is retrieved
        if(nodeNames != null && nodeNames.size() > 0 &&
                !validNodeIds.containsAll(nodeNames)){
            nodeNames.removeAll(validNodeIds);
            throw APIException.badRequests.parameterIsNotValid("node id(s): "+nodeNames);
        }
        return nodeInfoList;
    }

    /**
     * Gets a reference to the node connection info for all nodes in the Bourne
     * cluster.
     */
    public static List<NodeInfo> getClusterNodeInfo() {
        return getClusterNodeInfo(null);
    }
    
    public static ArrayList<String> getUnavailableControllerNodes() {
    	return _coordinatorExt.getUnavailableControllerNodes();
    }
 }
