/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResources;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addRollback;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;
import static com.emc.sa.util.ResourceType.BLOCK_SNAPSHOT;
import static com.emc.sa.util.ResourceType.VOLUME;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.block.tasks.AddVolumesToExport;
import com.emc.sa.service.vipr.block.tasks.CreateBlockVolume;
import com.emc.sa.service.vipr.block.tasks.CreateBlockVolumeByName;
import com.emc.sa.service.vipr.block.tasks.CreateContinuousCopy;
import com.emc.sa.service.vipr.block.tasks.CreateExport;
import com.emc.sa.service.vipr.block.tasks.CreateExportNoWait;
import com.emc.sa.service.vipr.block.tasks.CreateFullCopy;
import com.emc.sa.service.vipr.block.tasks.CreateSnapshotFullCopy;
import com.emc.sa.service.vipr.block.tasks.DeactivateBlockExport;
import com.emc.sa.service.vipr.block.tasks.DeactivateBlockSnapshot;
import com.emc.sa.service.vipr.block.tasks.DeactivateContinuousCopy;
import com.emc.sa.service.vipr.block.tasks.DeactivateVolume;
import com.emc.sa.service.vipr.block.tasks.DeactivateVolumes;
import com.emc.sa.service.vipr.block.tasks.DetachFullCopy;
import com.emc.sa.service.vipr.block.tasks.ExpandVolume;
import com.emc.sa.service.vipr.block.tasks.FindExportByCluster;
import com.emc.sa.service.vipr.block.tasks.FindExportByHost;
import com.emc.sa.service.vipr.block.tasks.FindExportsContainingHost;
import com.emc.sa.service.vipr.block.tasks.FindVirtualArrayInitiators;
import com.emc.sa.service.vipr.block.tasks.GetActiveContinuousCopiesForVolume;
import com.emc.sa.service.vipr.block.tasks.GetActiveFullCopiesForVolume;
import com.emc.sa.service.vipr.block.tasks.GetActiveSnapshotsForVolume;
import com.emc.sa.service.vipr.block.tasks.GetBlockExport;
import com.emc.sa.service.vipr.block.tasks.GetBlockExports;
import com.emc.sa.service.vipr.block.tasks.GetBlockResource;
import com.emc.sa.service.vipr.block.tasks.GetBlockSnapshot;
import com.emc.sa.service.vipr.block.tasks.GetBlockVolumeByWWN;
import com.emc.sa.service.vipr.block.tasks.GetExportsForBlockObject;
import com.emc.sa.service.vipr.block.tasks.GetVolumeByName;
import com.emc.sa.service.vipr.block.tasks.RemoveBlockResourcesFromExport;
import com.emc.sa.service.vipr.block.tasks.RestoreFromFullCopy;
import com.emc.sa.service.vipr.block.tasks.ResynchronizeFullCopy;
import com.emc.sa.service.vipr.block.tasks.SwapContinuousCopies;
import com.emc.sa.service.vipr.tasks.GetCluster;
import com.emc.sa.service.vipr.tasks.GetHost;
import com.emc.sa.util.DiskSizeConversionUtils;
import com.emc.sa.util.ResourceType;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.VirtualArrayRelatedResourceRep;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeRestRep.FullCopyRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class BlockStorageUtils {
    public static final String COPY_NATIVE = "native";
    public static final String COPY_RP = "rp";
    public static final String COPY_SRDF = "srdf";
    
    public static boolean isHost(URI id) {
        return StringUtils.startsWith(id.toString(), "urn:storageos:Host");
    }

    public static boolean isCluster(URI id) {
        return StringUtils.startsWith(id.toString(), "urn:storageos:Cluster");
    }

    public static Host getHost(URI hostId) {
        if (NullColumnValueGetter.isNullURI(hostId)) {
            return null;
        }
        return execute(new GetHost(hostId));
    }

    public static Cluster getCluster(URI clusterId) {
        if (NullColumnValueGetter.isNullURI(clusterId)) {
            return null;
        }
        return execute(new GetCluster(clusterId));
    }
    
    public static String getHostOrClusterId(URI hostOrClusterId) {
        String id = null;
        if (hostOrClusterId != null) {
            id = hostOrClusterId.toString();
            if (BlockStorageUtils.isHost(hostOrClusterId)) {   
                Host host = BlockStorageUtils.getHost(hostOrClusterId);
                if (host.getCluster() != null) {
                    Cluster cluster = BlockStorageUtils.getCluster(host.getCluster());
                    if (cluster != null) {
                        id = cluster.getId().toString();
                    }
                }
            }
        }
        return id;
    }

    public static BlockObjectRestRep getVolume(URI volumeId) {
        return getBlockResource(volumeId);
    }
    
    public static List<BlockObjectRestRep> getVolumes(List<URI> volumeIds) {
        List<BlockObjectRestRep> volumes = Lists.newArrayList();
        for (URI volumeId : volumeIds) {
            volumes.add(getVolume(volumeId));
        }
        return volumes;
    }

    public static BlockSnapshotRestRep getSnapshot(URI snapshotId) {
        return execute(new GetBlockSnapshot(snapshotId));
    }

    public static BlockObjectRestRep getBlockResource(URI resourceId) {
        return execute(new GetBlockResource(resourceId));
    }
    
    public static List<BlockObjectRestRep> getBlockResources(List<URI> resourceIds) {
        List<BlockObjectRestRep> blockResources = Lists.newArrayList();
        for (URI resourceId : resourceIds) {
            blockResources.add(getBlockResource(resourceId));
        }
        return blockResources;
    }

    public static VolumeRestRep getVolumeByWWN(String volumeWWN) {
        return execute(new GetBlockVolumeByWWN(volumeWWN));
    }

    public static List<VolumeRestRep> getVolumeByName(String name) {
        return execute(new GetVolumeByName(name));
    }
    
    public static ExportGroupRestRep getExport(URI exportId) {
        return execute(new GetBlockExport(exportId));
    }

    public static List<ExportGroupRestRep> getExports(List<URI> exportIds) {
        return execute(new GetBlockExports(exportIds));
    }

    public static ExportGroupRestRep findExportByHost(Host host, URI projectId, URI varrayId, URI volume) {
        return execute(new FindExportByHost(host.getId(), projectId, varrayId, volume));
    }

    public static ExportGroupRestRep findExportByCluster(Cluster cluster, URI projectId, URI varrayId, URI volume) {
        return execute(new FindExportByCluster(cluster.getId(), projectId, varrayId, volume));
    }

    public static List<ExportGroupRestRep> findExportsContainingHost(URI host, URI projectId, URI varrayId) {
        return execute(new FindExportsContainingHost(host, projectId, varrayId));
    }

    public static List<URI> createVolumes(URI projectId, URI virtualArrayId, URI virtualPoolId,
            String baseVolumeName, double sizeInGb, Integer count, URI consistencyGroupId) {
        String volumeSize = gbToVolumeSize(sizeInGb);
        Tasks<VolumeRestRep> tasks = execute(new CreateBlockVolume(virtualPoolId, virtualArrayId, projectId, volumeSize,
                count, baseVolumeName, consistencyGroupId));
        List<URI> volumeIds = Lists.newArrayList();
        for (Task<VolumeRestRep> task : tasks.getTasks()) {
            URI volumeId = task.getResourceId();
            addRollback(new DeactivateVolume(volumeId, VolumeDeleteTypeEnum.FULL));
            addAffectedResource(volumeId);
            volumeIds.add(volumeId);
        }
        return volumeIds;
    }

    public static Task<VolumeRestRep> createVolumesByName(URI projectId, URI virtualArrayId, URI virtualPoolId, 
            double sizeInGb , URI consistencyGroupId, String volumeName) {
        String volumeSize = gbToVolumeSize(sizeInGb);
        return execute(new CreateBlockVolumeByName(projectId, virtualArrayId, 
        		virtualPoolId, volumeSize, consistencyGroupId, volumeName));

    }
    public static void expandVolumes(Collection<URI> volumeIds, double newSizeInGB) {
        for (URI volumeId : volumeIds) {
            expandVolume(volumeId, newSizeInGB);
        }
    }

    public static void expandVolume(URI volumeId, double newSizeInGB) {
        String newSize = gbToVolumeSize(newSizeInGB);
        Task<VolumeRestRep> task = execute(new ExpandVolume(volumeId, newSize));
        addAffectedResource(task);
    }

    public static URI createHostExport(URI projectId, URI virtualArrayId, List<URI> volumeIds, Integer hlu, Host host) {
        String exportName = host.getHostName();
        Task<ExportGroupRestRep> task = execute(new CreateExport(exportName, virtualArrayId, projectId, volumeIds, hlu,
            host.getHostName(), host.getId(), null));
        URI exportId = task.getResourceId();
        addRollback(new DeactivateBlockExport(exportId));
        addAffectedResource(exportId);
        return exportId;
    }

    public static Task<ExportGroupRestRep> createHostExportNoWait(URI projectId, URI virtualArrayId, 
            List<URI> volumeIds, Integer hlu, Host host) {
        String exportName = host.getHostName();
        return execute(new CreateExportNoWait(exportName, virtualArrayId, projectId, 
                volumeIds, hlu, host.getHostName(), host.getId(), null));
    }

    public static URI createClusterExport(URI projectId, URI virtualArrayId, List<URI> volumeIds, Integer hlu, Cluster cluster ) {
        String exportName = cluster.getLabel();
        Task<ExportGroupRestRep> task = execute(new CreateExport(exportName, virtualArrayId, projectId, volumeIds, hlu,
                cluster.getLabel(), null, cluster.getId()));
        URI exportId = task.getResourceId();
        addRollback(new DeactivateBlockExport(exportId));
        addAffectedResource(exportId);
        return exportId;
    }

    public static void addVolumesToExport(Collection<URI> volumeIds, Integer hlu, URI exportId) {
        Task<ExportGroupRestRep> task = execute(new AddVolumesToExport(exportId, volumeIds, hlu));
        addRollback(new RemoveBlockResourcesFromExport(exportId, volumeIds));
        addAffectedResource(task);
    }

    public static List<ITLRestRep> getExportsForBlockObject(URI blockObjectId) {
        return execute(new GetExportsForBlockObject(blockObjectId));
    }

    /** build map of export id to set of volumes in that export */
    protected static Map<URI, Set<URI>> getExportToVolumesMap(List<URI> volumeIds) {
        Map<URI, Set<URI>> exportToVolumesMap = Maps.newHashMap();
        for (URI volumeId : volumeIds) {
            for (ITLRestRep export : getExportsForBlockObject(volumeId)) {
                Set<URI> volumesInExport = exportToVolumesMap.get(export.getExport().getId());
                if (volumesInExport == null) {
                    volumesInExport = Sets.newHashSet(volumeId);
                } 
                else {
                    volumesInExport.add(volumeId);
                }
                exportToVolumesMap.put(export.getExport().getId(), volumesInExport);
            }
        }
        return exportToVolumesMap;
    }
    
    public static void removeBlockResourcesFromExports(Map<URI, Set<URI>> exportToVolumesMap) {
        for (Map.Entry<URI, Set<URI>> entry : exportToVolumesMap.entrySet()) {
            // Check to see if the export returned is an internal export; one used by internal orchestrations only.
            ExportGroupRestRep export = getExport(entry.getKey());
            if (ResourceUtils.isNotInternal(export)) {
                removeBlockResourcesFromExport(entry.getValue(), entry.getKey());
            }
        }
    }
    
    public static void removeBlockResourcesFromExports(Collection<URI> blockResourceIds) {
        Map<URI, Set<URI>> resourcesInExport = Maps.newHashMap();
        for (URI blockResourceId : blockResourceIds) {
            List<ITLRestRep> exports = getExportsForBlockObject(blockResourceId);
            for (ITLRestRep export : exports) {
                URI exportId = export.getExport().getId();
                if (resourcesInExport.containsKey(exportId)) {
                    resourcesInExport.get(exportId).add(blockResourceId);
                }
                else {
                    resourcesInExport.put(exportId,Sets.newHashSet(blockResourceId));
                }
            }
        }
        
        removeBlockResourcesFromExports(resourcesInExport);
    }
    
    public static void removeBlockResourceFromExport(URI resourceId, URI exportId) {
        removeBlockResourcesFromExport(Collections.singletonList(resourceId), exportId);
    }
    
    public static void removeBlockResourcesFromExport(Collection<URI> resourceId, URI exportId) {
        Task<ExportGroupRestRep> task = execute(new RemoveBlockResourcesFromExport(exportId, resourceId));
        addAffectedResource(task);

        removeExportIfEmpty(exportId);
    }

    public static void removeExportIfEmpty(URI exportId) {
        ExportGroupRestRep export = getExport(exportId);
        if (ResourceUtils.isActive(export) && export.getVolumes().isEmpty()) {
            removeExport(export.getId() );
        }
    }

    public static void removeExport(URI exportId) {
        Task<ExportGroupRestRep> response = execute(new DeactivateBlockExport(exportId));
        addAffectedResource(response);
    }

    public static List<URI> getActiveSnapshots(URI volumeId) {
        if (ResourceType.isType(BLOCK_SNAPSHOT, volumeId)) {
            return Collections.emptyList();
        }
        return ResourceUtils.ids(execute(new GetActiveSnapshotsForVolume(volumeId)));
    }

    public static void removeSnapshotsForVolume(URI volumeId) {
        List<URI> snapshotIds = getActiveSnapshots(volumeId);
        removeBlockResourcesFromExports(snapshotIds);
        removeSnapshots(snapshotIds);
    }

    public static void removeSnapshots(Collection<URI> snapshotIds) {
        for (URI snapshotId : snapshotIds) {
            removeSnapshot(snapshotId);
        }
    }

    public static void removeSnapshot(URI snapshotId) {
        Tasks<BlockSnapshotRestRep> task = execute(new DeactivateBlockSnapshot(snapshotId));
        addAffectedResources(task);
    }

    public static List<URI> getActiveContinuousCopies(URI volumeId) {
        return ResourceUtils.ids(execute(new GetActiveContinuousCopiesForVolume(volumeId)));
    }

    public static void removeContinuousCopiesForVolume(URI volumeId) {
        if (!ResourceType.isType(BLOCK_SNAPSHOT, volumeId)) {
            Collection<URI> continuousCopyIds = getActiveContinuousCopies(volumeId);
            removeContinuousCopiesForVolume(volumeId, continuousCopyIds);
        }
    }

    public static void removeContinuousCopiesForVolume(URI volumeId, Collection<URI> continuousCopyIds) {
        for (URI continuousCopyId : continuousCopyIds) {
            removeContinuousCopy(volumeId, continuousCopyId);
        }
    }

    private static void removeContinuousCopy(URI volumeId, URI continuousCopyId) {
        Tasks<VolumeRestRep> tasks = execute(new DeactivateContinuousCopy(volumeId, continuousCopyId, COPY_NATIVE));
        addAffectedResources(tasks);
    }
    
    public static List<URI> getActiveFullCopies(URI volumeId) {
        return ResourceUtils.ids(execute(new GetActiveFullCopiesForVolume(volumeId)));
    }

    public static void removeFullCopiesForVolume(URI volumeId, Collection<URI> vols) {
        List<URI> fullCopiesIds = getActiveFullCopies(volumeId);
        vols.removeAll(fullCopiesIds);
        removeFullCopies(fullCopiesIds);
    }
    
    public static void removeFullCopies(Collection<URI> fullCopyIds) {
        for (URI fullCopyId : fullCopyIds) {
            removeFullCopy(fullCopyId);
        }
    }

    public static void removeFullCopy(URI fullCopyId) {
        detachFullCopy(fullCopyId);
        removeBlockResources(Collections.singletonList(fullCopyId), VolumeDeleteTypeEnum.FULL);
    }

    public static void detachFullCopies(Collection<URI> fullCopyIds) {
        for (URI fullCopyId : fullCopyIds) {
            detachFullCopy(fullCopyId);
        }
    }
    
    public static void detachFullCopy(URI fullCopyId) {
        execute(new DetachFullCopy(fullCopyId));
    }
    
    public static void restoreFromFullCopy(URI fullCopyId) {
        execute(new RestoreFromFullCopy(fullCopyId));
    }

    public static void resynchronizeFullCopies(Collection<URI> fullCopyIds) {
        for (URI fullCopyId : fullCopyIds) {
            resynchronizeFullCopy(fullCopyId);
        }
    }
    
    public static void resynchronizeFullCopy(URI fullCopyId) {
        execute(new ResynchronizeFullCopy(fullCopyId));
    }

    public static void removeBlockResources(Collection<URI> blockResourceIds, VolumeDeleteTypeEnum type) {

        List<URI> allBlockResources = Lists.newArrayList(blockResourceIds);
        for (URI volumeId : blockResourceIds) {
            BlockObjectRestRep volume = getVolume(volumeId);
            allBlockResources.addAll(getSrdfTargetVolumes(volume));
        }

        removeBlockResourcesFromExports(allBlockResources);
        for (URI volumeId : allBlockResources) {
            removeSnapshotsForVolume(volumeId);
            removeContinuousCopiesForVolume(volumeId);
            removeFullCopiesForVolume(volumeId, blockResourceIds);
        }
        deactivateBlockResources(blockResourceIds, type);
    }
    

    private static void deactivateBlockResources(Collection<URI> blockResourceIds, VolumeDeleteTypeEnum type) {
        List<URI> volumes = Lists.newArrayList();
        List<URI> fullCopies = Lists.newArrayList();
        for (URI blockResourceId : blockResourceIds) {
            if (ResourceType.isType(VOLUME,  blockResourceId)) {
                if (isFullCopyAttached(blockResourceId)) {
                    fullCopies.add(blockResourceId);
                }
                volumes.add(blockResourceId);
            }
            else if (ResourceType.isType(BLOCK_SNAPSHOT, blockResourceId)) {
                deactivateSnapshot(blockResourceId);
            }
        }
        detachFullCopies(fullCopies);
        deactivateVolumes(volumes, type);
    }
    
    public static boolean isFullCopyAttached(URI id) {
        BlockObjectRestRep obj = getVolume(id);
        if (obj instanceof VolumeRestRep) {
            VolumeRestRep volume = (VolumeRestRep)obj;
            if (volume.getProtection() != null) {
                FullCopyRestRep fullCopy = volume.getProtection().getFullCopyRep();
                if (fullCopy != null && 
                        fullCopy.getAssociatedSourceVolume() != null &&
                        fullCopy.getReplicaState() != null &&
                        !fullCopy.getReplicaState().equals(ReplicationState.DETACHED.name())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public static void deactivateVolumes(List<URI> volumeIds, VolumeDeleteTypeEnum type) {
        if (CollectionUtils.isNotEmpty(volumeIds)) {
            Tasks<VolumeRestRep> tasks = execute(new DeactivateVolumes(volumeIds, type));
            addAffectedResources(tasks);
        }
    }

    private static void deactivateSnapshot(URI snapshotId) {
        Tasks<BlockSnapshotRestRep> tasks = execute(new DeactivateBlockSnapshot(snapshotId));
        addAffectedResources(tasks);
    }

    public static List<URI> getSrdfTargetVolumes(BlockObjectRestRep blockObject) {
        List<URI> targetVolumes = Lists.newArrayList();
        if (blockObject instanceof VolumeRestRep) {            
            VolumeRestRep volume = (VolumeRestRep)blockObject;
            if (volume.getProtection() != null && volume.getProtection().getSrdfRep() != null) {
                for (VirtualArrayRelatedResourceRep targetVolume : volume.getProtection().getSrdfRep().getSRDFTargetVolumes()) {
                    targetVolumes.add(targetVolume.getId());
                }
            }
        }

        return targetVolumes;
    }

    public static void removeVolumes(List<URI> volumeIds) {
        removeBlockResources(volumeIds, VolumeDeleteTypeEnum.FULL);
    }

    public static void unexportVolumes(List<URI> volumeIds) {
        removeBlockResourcesFromExports(volumeIds);
    }
    
    public static Set<Initiator> findInitiatorsInVirtualArray(URI virtualArray, Collection<Initiator> initiators,
            Protocol protocol) {
        return findInitiatorsInVirtualArrays(Arrays.asList(virtualArray), initiators, protocol);
    }

    public static Set<Initiator> findInitiatorsInVirtualArrays(Collection<URI> virtualArrays,
            Collection<Initiator> initiators, Protocol protocol) {
        Set<Initiator> results = Sets.newHashSet();
        Collection<Initiator> filteredInitiators = filterInitiatorsByType(initiators, protocol);
        if (filteredInitiators.size() > 0) {
            for (URI virtualArray : virtualArrays) {
                results.addAll(execute(new FindVirtualArrayInitiators(virtualArray, filteredInitiators)));
            }
        }
        return results;
    }

    public static Set<URI> getVolumeVirtualArrays(Collection<? extends BlockObjectRestRep> volumes) {
        Set<URI> virtualArrays = Sets.newHashSet();
        virtualArrays.addAll(Collections2.transform(volumes, new Function<BlockObjectRestRep, URI>() {
            @Override
            public URI apply(BlockObjectRestRep input) {
                return input.getVirtualArray().getId();
            }
        }));
        return virtualArrays;
    }

    public static Collection<Initiator> filterInitiatorsByType(Collection<Initiator> initiators, final Protocol protocol) {
        return Collections2.filter(initiators, new Predicate<Initiator>() {
            @Override
            public boolean apply(Initiator input) {
                return StringUtils.equals(protocol.name(), input.getProtocol());
            }
        });
    }

    public static Collection<String> getPortNames(Collection<Initiator> initiators) {
        return Collections2.transform(initiators, new Function<Initiator, String>() {
            @Override
            public String apply(Initiator input) {
                return input.getInitiatorPort();
            }
        });
    }

    public static String gbToVolumeSize(double sizeInGB) {
        // Always use size in bytes, VMAX does not like size in GB
        return String.valueOf(DiskSizeConversionUtils.gbToBytes(sizeInGB));
    }

    public static Tasks<VolumeRestRep> createFullCopy(URI volumeId, String name, Integer count) {
        int countValue = (count != null) ? count : 1;
        Tasks<VolumeRestRep> copies = execute(new CreateFullCopy(volumeId, name, countValue));
        addAffectedResources(copies);
        return copies;
    }
    
    public static Tasks<BlockSnapshotRestRep> createSnapshotFullCopy(URI snapshotId, String name, Integer count) {
        int countValue = (count != null) ? count : 1;
        Tasks<BlockSnapshotRestRep> copyTasks = ViPRExecutionUtils.execute(new CreateSnapshotFullCopy(snapshotId, name, countValue));
        addAffectedResources(copyTasks);
        return copyTasks;
    }
    
    public static Tasks<VolumeRestRep> createContinuousCopy(URI volumeId, String name, Integer count) {
        int countValue = (count != null) ? count : 1;
        Tasks<VolumeRestRep> copies = execute(new CreateContinuousCopy(volumeId, name, countValue, COPY_NATIVE));
        addAffectedResources(copies);
        return copies;
    }

    public static Tasks<VolumeRestRep> swapContinuousCopy(URI targetVolumeId, String type) {
        Tasks<VolumeRestRep> copies = execute(new SwapContinuousCopies(targetVolumeId, type));
        addAffectedResources(copies);
        return copies;
    }

    /**
     * Finds the exports (itl) for the given initiators.
     * 
     * @param exports
     *        the list of all exports (itl)
     * @param initiators
     *        the initiators.
     * @return the exports for the initiators.
     */
    public static List<ITLRestRep> getExportsForInitiators(Collection<ITLRestRep> exports,
            Collection<Initiator> initiators) {
        Set<String> initiatorPorts = Sets.newHashSet(getPortNames(initiators));
        List<ITLRestRep> results = Lists.newArrayList();
        for (ITLRestRep export : exports) {
            if ((export.getInitiator() != null) && initiatorPorts.contains(export.getInitiator().getPort())) {
                results.add(export);
            }
        }
        return results;
    }

    public static Set<String> getTargetPortsForExports(Collection<ITLRestRep> exports) {
        Set<String> targetPorts = Sets.newTreeSet();
        for (ITLRestRep export : exports) {
            if (export.getStoragePort() != null && StringUtils.isNotBlank(export.getStoragePort().getPort())) {
                targetPorts.add(export.getStoragePort().getPort());
            }
        }
        return targetPorts;
    }

    public static boolean isVolumeInExportGroup(ExportGroupRestRep exportGroup, URI volumeId) {
        if (volumeId == null) {
            return false;
        }

        for (ExportBlockParam param : exportGroup.getVolumes()) {
            if (param.getId().equals(volumeId)) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * Get the project id off a {@link BlockObjectRestRep}
     */
    public static <T extends BlockObjectRestRep> URI getProjectId(T resource) {
        if (resource instanceof BlockSnapshotRestRep) {
            return ((BlockSnapshotRestRep)resource).getProject().getId();
        }
        else if (resource instanceof VolumeRestRep) {
            return ((VolumeRestRep)resource).getProject().getId();
        }
        else if (resource instanceof BlockMirrorRestRep) {
            return ((BlockMirrorRestRep)resource).getProject().getId();
        }
        throw new IllegalStateException(ExecutionUtils.getMessage("illegalState.projectNotFound", resource.getId()));
    }

    /**
     * Get the virtual array id off a {@link BlockObjectRestRep}
     */
    public static URI getVirtualArrayId(BlockObjectRestRep resource) {
        if (resource instanceof VolumeRestRep) {
            return ((VolumeRestRep)resource).getVirtualArray().getId();
        }
        else if (resource instanceof BlockSnapshotRestRep) {
            return ((BlockSnapshotRestRep)resource).getVirtualArray().getId();
        }
        else if (resource instanceof BlockMirrorRestRep) {
            return ((BlockMirrorRestRep)resource).getVirtualArray().getId();
        }
        throw new IllegalStateException(ExecutionUtils.getMessage("illegalState.varrayNotFound", resource.getId()));
    }

    public static String getFailoverType(BlockObjectRestRep blockObject) {
        if (blockObject instanceof VolumeRestRep) {
            VolumeRestRep volume = (VolumeRestRep)blockObject;
            if (volume.getProtection() != null && volume.getProtection().getRpRep() != null) {
                VolumeRestRep.RecoverPointRestRep rp = volume.getProtection().getRpRep();
                if (StringUtils.equals("TARGET", rp.getPersonality())) {
                    return "rp";
                }
            }
            else if (volume.getProtection() != null && volume.getProtection().getSrdfRep() != null) {
                VolumeRestRep.SRDFRestRep srdf = volume.getProtection().getSrdfRep();
                if (srdf.getAssociatedSourceVolume() != null ||
                        (srdf.getSRDFTargetVolumes() != null && !srdf.getSRDFTargetVolumes().isEmpty())) {
                    return "srdf";
                }
            }
        }

        return null;
    }

}
