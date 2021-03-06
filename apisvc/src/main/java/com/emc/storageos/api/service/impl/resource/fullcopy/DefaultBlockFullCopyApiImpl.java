/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;


import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.impl.placement.Scheduler;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VolumeRecommendation;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * The default implementation for the block full copy API.
 */
public class DefaultBlockFullCopyApiImpl extends AbstractBlockFullCopyApiImpl {

    // A reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(DefaultBlockFullCopyApiImpl.class);

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param coordinator A reference to the coordinator client.
     * @param scheduler A reference to the scheduler.
     */
    public DefaultBlockFullCopyApiImpl(DbClient dbClient, CoordinatorClient coordinator,
        Scheduler scheduler) {
        super(dbClient, coordinator, scheduler);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockObject> getAllSourceObjectsForFullCopyRequest(BlockObject fcSourceObj) {
        return super.getAllSourceObjectsForFullCopyRequest(fcSourceObj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateFullCopyCreateRequest(List<BlockObject> fcSourceObjList, int count) {
        super.validateFullCopyCreateRequest(fcSourceObjList, count);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList create(List<BlockObject> fcSourceObjList, VirtualArray varray,
        String name, boolean createInactive, int count, String taskId) {       
        // Get the placement recommendations for the full copies and
        // prepare the ViPR volumes to represent the full copies.
        // TBD We are getting recommendations one at a time instead
        // of for all full copies at the same time as was done 
        // previously. However, now we are allowing for creating
        // full copies for multiple volume form a CG. These volumes
        // could have different vpools and sizes. Therefore, I don't
        // see how we can get them at the same time for all volumes
        // as the capabilities could be different. I guess the 
        // possible result is that if the volumes are the same, they
        // could be placed in the same storage pool and if the pool
        // is approaching capacity, there may not actually be enough
        // space in the recommended pool.
        int sourceCounter = 0;
        List<Volume> volumesList = new ArrayList<Volume>();
        BlockObject aFCSource = null;
        List<BlockObject> sortedSourceObjectList = sortFullCopySourceList(fcSourceObjList);
        for (BlockObject fcSourceObj : sortedSourceObjectList) {
            // Make sure when there are multiple source objects, 
            // each full copy has a unique name.
            aFCSource = fcSourceObj;
            String copyName = name + (sortedSourceObjectList.size() > 1 ? "-" + ++sourceCounter : "");
            VirtualPool vpool = BlockFullCopyUtils.queryFullCopySourceVPool(fcSourceObj, _dbClient);
            VirtualPoolCapabilityValuesWrapper capabilities = getCapabilitiesForFullCopyCreate(
                fcSourceObj, vpool, count);
            List<VolumeRecommendation> placementRecommendations = getPlacementRecommendations(
                fcSourceObj, capabilities, varray, vpool.getId());
            volumesList.addAll(prepareClonesForEachRecommendation(copyName,
            fcSourceObj, capabilities, createInactive, placementRecommendations));
        }

        // Invoke the controller.
        return invokeFullCopyCreate(aFCSource, volumesList, createInactive, taskId);
    }
    
    /**
     * Get the placement recommendations for the passed full copy source.
     * 
     * @param blockObject A reference to the full copy source.
     * @param capabilities Encapsulates the copy capabilities
     * @param varray A reference to the virtual array.
     * @param vpoolURI The URI of the virtual pool for the source.
     * 
     * @return A list of volume placement recommendations.
     */
    private List<VolumeRecommendation> getPlacementRecommendations(
        BlockObject blockObject, VirtualPoolCapabilityValuesWrapper capabilities,
        VirtualArray varray, URI vpoolURI) {

        // Find placement for block volume copies
        VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, vpoolURI);
        List<VolumeRecommendation> placementRecommendations = ((StorageScheduler)_scheduler)
            .getRecommendationsForVolumeClones(varray, vPool, blockObject, capabilities);
        if (placementRecommendations.isEmpty()) {
            throw APIException.badRequests.invalidParameterNoStorageFoundForVolume(
                varray.getId(), vPool.getId(), blockObject.getId());
        }
        return placementRecommendations;
    }
    
    /**
     * Prepares a ViPR volume instance for each full copy.
     * 
     * @param name The full copy name.
     * @param blockObject The full copy source.
     * @param capabilities The full copy capabilities.
     * @param createInactive true to create the full copies inactive, false otherwise.
     * @param placementRecommendations The placement recommendation for each full copy.
     * 
     * @return A list of volumes representing the full copies.
     */
    private List<Volume> prepareClonesForEachRecommendation(String name,
        BlockObject blockObject, VirtualPoolCapabilityValuesWrapper capabilities,
        Boolean createInactive, List<VolumeRecommendation> placementRecommendations) {

        // Prepare clones for each recommendation
        List<Volume> volumesList = new ArrayList<Volume>();
        int volumeCounter = (capabilities.getResourceCount() > 1) ? 1 : 0;
        for (VolumeRecommendation recommendation : placementRecommendations) {

            Volume volume = StorageScheduler.prepareFullCopyVolume(_dbClient, name,
                blockObject, recommendation, volumeCounter, capabilities, createInactive);
            volumesList.add(volume);
            // set volume Id in the recommendation
            recommendation.setId(volume.getId());
            volumeCounter++;
        }
        return volumesList;
    }
    
    /**
     * Invokes the controller to create the full copy volumes.
     * 
     * @param fcSourceObj A reference to a full copy source.
     * @param fullCopyVolumes A list of the prepared full copy volumes.
     * @param createInactive true to create the full copies inactive, false otherwise.
     * @param taskId The unique task identifier
     * 
     * @return TaskList
     */
    private TaskList invokeFullCopyCreate(BlockObject fcSourceObj, List<Volume> fullCopyVolumes,
        Boolean createInactive, String taskId) {

        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
            fcSourceObj.getStorageController());
        TaskList taskList = new TaskList();
       
        for (Volume volume : fullCopyVolumes) {
            Operation op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(),
                taskId, ResourceOperationTypeEnum.CREATE_VOLUME_FULL_COPY);
            volume.getOpStatus().put(taskId, op);
            TaskResourceRep volumeTask = TaskMapper.toTask(volume, taskId, op);
            taskList.getTaskList().add(volumeTask);
        }
        try {
            BlockController controller = getController(BlockController.class,
                  storageSystem.getSystemType());
            controller.createFullCopy(storageSystem.getId(),
                volumesToURIs(fullCopyVolumes), createInactive, taskId);
        } catch (InternalException ie) {
            handleFailedRequest(taskId, taskList, fullCopyVolumes, ie, true);
        }
        return taskList;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList activate(BlockObject fcSourceObj, Volume fullCopyVolume) {
        return super.activate(fcSourceObj, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList detach(BlockObject fcSourceObj, Volume fullCopyVolume) {
        return super.detach(fcSourceObj, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList restoreSource(Volume sourceVolume, Volume fullCopyVolume) {
        
        // Create the task list.
        TaskList taskList = new TaskList();
        
        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();
        
        // If the source is in a CG, then we will restore the corresponding
        // full copies for all the volumes in the CG. Since we did not allow
        // full copies for volumes or snaps in CGs prior to Jedi, there should
        // be a full copy for all volumes in the CG.
        Map<URI, Volume> fullCopyMap = getFullCopySetMap(sourceVolume, fullCopyVolume);
        Set<URI> fullCopyURIs = fullCopyMap.keySet();

        // Get the id of the source volume.
        URI sourceVolumeURI = sourceVolume.getId();
        
        // Get the storage system for the source volume.
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class,
            sourceVolume.getStorageController());
        URI sourceSystemURI = sourceSystem.getId();
        
        // Create the restore task on the full copy volumes.
        // The controller expects the task to be on the full
        // copy even though the source is being restored.
        // Not really sure why. Change this TBD
        for (URI fullCopyURI : fullCopyURIs) {
            Operation op = _dbClient.createTaskOpStatus(Volume.class, fullCopyURI,
                taskId, ResourceOperationTypeEnum.RESTORE_VOLUME_FULL_COPY);
            fullCopyMap.get(fullCopyURI).getOpStatus().put(taskId, op);
            TaskResourceRep fullCopyVolumeTask = TaskMapper.toTask(
                fullCopyMap.get(fullCopyURI), taskId, op);
            taskList.getTaskList().add(fullCopyVolumeTask);
        }
        
        // Invoke the controller.
        try {
            BlockController controller = getController(BlockController.class,
                sourceSystem.getSystemType());            
            controller.restoreFromFullCopy(sourceSystemURI, new ArrayList<URI>(
                fullCopyURIs), Boolean.TRUE, taskId);
        } catch (InternalException ie) {
            s_logger.error(String.format("Failed to restore source %s from full copy %s",
                sourceVolumeURI, fullCopyVolume.getId()), ie);
            handleFailedRequest(taskId, taskList,
                new ArrayList<Volume>(fullCopyMap.values()), ie, false);
        }
        return taskList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList resynchronizeCopy(Volume sourceVolume, Volume fullCopyVolume) {

        // Create the task list.
        TaskList taskList = new TaskList();
        
        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();
        
        // If the source is in a CG, then we will resynchronize the corresponding
        // full copies for all the volumes in the CG. Since we did not allow
        // full copies for volumes or snaps in CGs prior to Jedi, there should
        // be a full copy for all volumes in the CG.
        Map<URI, Volume> fullCopyMap = getFullCopySetMap(sourceVolume, fullCopyVolume);
        Set<URI> fullCopyURIs = fullCopyMap.keySet();

        // Get the id of the source volume.
        URI sourceVolumeURI = sourceVolume.getId();

        // Get the storage system for the source volume.
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class,
            sourceVolume.getStorageController());
        URI sourceSystemURI = sourceSystem.getId();
        
        // Create the resynchronize task on the full copy volumes.
        for (URI fullCopyURI : fullCopyURIs) {
            Operation op = _dbClient.createTaskOpStatus(Volume.class, fullCopyURI,
                taskId, ResourceOperationTypeEnum.RESYNCHRONIZE_VOLUME_FULL_COPY);
            fullCopyMap.get(fullCopyURI).getOpStatus().put(taskId, op);
            TaskResourceRep fullCopyVolumeTask = TaskMapper.toTask(
                fullCopyMap.get(fullCopyURI), taskId, op);
            taskList.getTaskList().add(fullCopyVolumeTask);
        }
        
        // Invoke the controller.
        try {
            BlockController controller = getController(BlockController.class,
                sourceSystem.getSystemType());            
            controller.resyncFullCopy(sourceSystemURI, new ArrayList<URI>(fullCopyURIs),
                Boolean.TRUE, taskId);
        } catch (InternalException ie) {
            s_logger.error(String.format("Failed to resynchronize full copy %s from source %s",
                fullCopyVolume.getId(), sourceVolumeURI), ie);
            handleFailedRequest(taskId, taskList,
                new ArrayList<Volume>(fullCopyMap.values()), ie, false);
        }

        return taskList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VolumeRestRep checkProgress(URI sourceURI, Volume fullCopyVolume) {
        return super.checkProgress(sourceURI, fullCopyVolume);
    }
    
    /**
     * Get the URI of the passed volumes.
     * 
     * @param volumes A list of volumes.
     * 
     * @return A list of the volume URIs.
     */
    private List<URI> volumesToURIs(List<Volume> volumes) {
        List<URI> uris = new ArrayList<URI>();
        for (Volume v : volumes) {
            uris.add(v.getId());
        }
        return uris;
    }

    /**
     * Updates passed volume and tasks on failure.
     * 
     * @param taskId The unique task id.
     * @param taskList A list of the task responses.
     * @param volumes The list of volumes for the operation.
     * @param sc A reference to the error.
     * @param markInactive true to mark the volumes inactive, false otherwise.
     */
    private void handleFailedRequest(String taskId, TaskList taskList,
        List<Volume> volumes, ServiceCoded sc, boolean markInactive) {
        for (TaskResourceRep volumeTask :taskList.getTaskList()) {
            volumeTask.setState(Operation.Status.error.name());
            volumeTask.setMessage(sc.getMessage());
            Volume volume = _dbClient.queryObject(Volume.class, volumeTask.getResource().getId());
            Operation op = volume.getOpStatus().get(taskId);
            if (op != null) {
                op.error(sc);
                volume.getOpStatus().updateTaskStatus(taskId, op);
                _dbClient.persistObject(volume);
            }
        }
        if (markInactive) {
            for (Volume volume: volumes) {
                volume.setInactive(true);
                _dbClient.persistObject(volume);
            }
        }
    }
}
