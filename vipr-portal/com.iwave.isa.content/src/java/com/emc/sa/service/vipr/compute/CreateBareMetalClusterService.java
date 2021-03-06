/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.COMPUTE_VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.compute.ComputeUtils.FqdnTable;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;

@Service("CreateBareMetalCluster")
public class CreateBareMetalClusterService extends ViPRService {

    @Param(PROJECT)
    protected URI project;

    @Param(NAME)
    protected String name;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(COMPUTE_VIRTUAL_POOL)
    protected URI computeVirtualPool;

	@Param(SIZE_IN_GB)
    protected Double size;
    
    @Bindable(itemType = FqdnTable.class)
    protected FqdnTable[] fqdnValues;

    Cluster cluster = null;
    List<String> hostNames = null;

    @Override
    public void precheck() throws Exception {

        StringBuffer preCheckErrors = new StringBuffer();
        hostNames = ComputeUtils.getHostNamesFromFqdn(fqdnValues);
                
        List<String> existingHostNames = ComputeUtils.getHostNamesByName(getClient(), hostNames);
        cluster = ComputeUtils.getCluster(name);
        List<String> hostNamesInCluster = ComputeUtils.findHostNamesInCluster(cluster);
        
        if ((cluster != null) && hostNamesInCluster.isEmpty()) {
            preCheckErrors.append(ExecutionUtils.getMessage("compute.cluster.empty.cluster.exists"));
        }
        
        if((cluster != null) && !hostNames.containsAll(hostNamesInCluster)){
            preCheckErrors.append(ExecutionUtils.getMessage("compute.cluster.unknown.host"));
        }
        
        if (hostNames == null || hostNames.size() == 0) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.baremetal.hostname.required") + "  ");
        }
        
        // Check for validity of host names and host Ips
        for (String hostName : hostNames) {
            if (!ComputeUtils.isValidHostIdentifier(hostName)) {
                preCheckErrors.append(
                        ExecutionUtils.getMessage("compute.cluster.hostname.invalid",hostName) + "  ");
            }
        }
        
        if (hostNames != null && hostNames.size() > 0 && existingHostNames.size() > 0 && 
                hostNamesInCluster.containsAll(existingHostNames) && (hostNames.size() == hostNamesInCluster.size()) ) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.host.already.in.cluster") + "  ");
        }
        
        if (!ComputeUtils.isCapacityAvailable(getClient(), virtualPool,
                virtualArray, size, hostNames.size()-existingHostNames.size())) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.insufficient.storage.capacity") + "  ");
        }
        
        if (!ComputeUtils.isComputePoolCapacityAvailable(getClient(), computeVirtualPool,
                hostNames.size()-existingHostNames.size())) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.insufficient.compute.capacity") + "  ");
        }
        
        for (String existingHostName : existingHostNames) {
            if (!hostNamesInCluster.contains(existingHostName)) {
                preCheckErrors.append(
                        ExecutionUtils.getMessage("compute.cluster.hosts.exists.elsewhere",
                                existingHostName) + "  ");
            }
        }
        
        ComputeVirtualPoolRestRep cvp = ComputeUtils.getComputeVirtualPool(getClient(),computeVirtualPool);
        if (cvp.getServiceProfileTemplates().isEmpty()) {
        	preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.service.profile.templates.null", cvp.getName()) + "  ");
        } 
        
        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString());
        }
    }
    
    @Override
    public void execute() throws Exception {

        // Note: creates ordered lists of hosts, bootVolumes & exports
        // host[0] goes with bootVolume[0] and export[0], etc
        // elements are set to null if they fail
        
        if (cluster == null) {
            cluster = ComputeUtils.createCluster(name);
            logInfo("compute.cluster.created",name);
        }
        else {
            // If the hostName already exists, we remove it from the hostnames list.
            hostNames = ComputeUtils.removeExistingHosts(hostNames, cluster);
        }

        List<Host> hosts = ComputeUtils.createHosts(cluster.getId(), computeVirtualPool, hostNames, virtualArray);

        logInfo("compute.cluster.hosts.created", ComputeUtils.nonNull(hosts).size());
        
        List<URI> bootVolumeIds = ComputeUtils.makeBootVolumes(project,
                virtualArray, virtualPool, size, hosts, getClient());
        logInfo("compute.cluster.boot.volumes.created", 
                ComputeUtils.nonNull(bootVolumeIds).size());
        hosts = ComputeUtils.deactivateHostsWithNoBootVolume(hosts,
                bootVolumeIds);

        List<URI> exportIds = ComputeUtils.exportBootVols(bootVolumeIds, hosts,
                project, virtualArray,true);
        logInfo("compute.cluster.exports.created", ComputeUtils.nonNull(exportIds).size());
        hosts = ComputeUtils.deactivateHostsWithNoExport(hosts,exportIds);

        ComputeUtils.setHostBootVolumes(hosts,bootVolumeIds);
        
        if (ComputeUtils.findHostNamesInCluster(cluster).isEmpty()) {
            logInfo("compute.cluster.removing.empty.cluster");
            ComputeUtils.deactivateCluster(cluster);
        }

        String orderErrors = ComputeUtils.getOrderErrors(cluster, hostNames,null,null);
        if (orderErrors.length() > 0) { // fail order so user can resubmit
            if (ComputeUtils.nonNull(hosts).isEmpty()) {
                throw new IllegalStateException(
                        ExecutionUtils.getMessage("compute.cluster.order.incomplete",orderErrors));
            }
            else {
                logError("compute.cluster.order.incomplete",orderErrors);
                setPartialSuccess();
            }
        }
    }

    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public URI getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        this.virtualArray = virtualArray;
    }

    public URI getVirtualPool() {
        return virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        this.virtualPool = virtualPool;
    }

    public URI getVirtualComputePool() {
        return computeVirtualPool;
    }

    public void setVirtualComputePool(URI computeVirtualPool) {
        this.computeVirtualPool = computeVirtualPool;
    }

    public Double getSize() {
        return size;
    }

    public void setSize(Double size) {
        this.size = size;
    }

    public FqdnTable[] getFqdnValues() {
        return fqdnValues;
    }

    public void setFqdnValues(FqdnTable[] fqdnValues) {
        this.fqdnValues = fqdnValues;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    public List<String> getHostNames() {
        return hostNames;
    }

    public void setHostNames(List<String> hostNames) {
        this.hostNames = hostNames;
    }
    
}
