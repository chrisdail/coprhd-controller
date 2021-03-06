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

package com.emc.storageos.volumecontroller.impl.block;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.Arrays.asList;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.DataBindingException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationInterface;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockMirror.SynchronizationState;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DecommissionedResource;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageProvider.InterfaceType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.model.factories.VolumeFactory;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.DistributedOwnerLockService;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorDeactivateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorDetachCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorFractureCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorResumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotActivateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockWaitForSynchronizedCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CleanupMetaVolumeMembersCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneActivateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneFractureCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneCreateWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneResyncCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MultiVolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SimpleTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeDetachCloneCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DiscoverTaskCompleter;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.ScanTaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.MetaVolumeUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Joiner;

/**
 * Generic Block Controller Implementation that does all of the database
 * operations and calls methods on the array specific implementations
 *
 *
 *
 */
public class BlockDeviceController implements BlockController, BlockOrchestrationInterface {
    // Constants for Event Types
    private static final String EVENT_SERVICE_TYPE = "block";
    private static final String EVENT_SERVICE_SOURCE = "BlockController";
    private DbClient _dbClient;
    private static final Logger _log = LoggerFactory.getLogger(BlockDeviceController.class);
    private static final int SCAN_LOCK_TIMEOUT  =  60;   // wait at most 60 seconds for scan lock
    private Map<String, BlockStorageDevice> _devices;

    private RecordableEventManager _eventManager;
    private BlockStorageScheduler _blockScheduler;
    private WorkflowService _workflowService;
    
    private static final String ATTACH_MIRRORS_WF_NAME = "ATTACH_MIRRORS_WORKFLOW";
    private static final String DETACH_MIRRORS_WF_NAME = "DETACH_MIRRORS_WORKFLOW";
    private static final String RESUME_MIRRORS_WF_NAME = "RESUME_MIRRORS_WORKFLOW";
    private static final String PAUSE_MIRRORS_WF_NAME  = "PAUSE_MIRRORS_WORKFLOW";
    private static final String RESTORE_VOLUME_WF_NAME = "RESTORE_VOLUME_WORKFLOW";
    private static final String EXPAND_VOLUME_WF_NAME = "expandVolume";
    private static final String ROLLBACK_METHOD_NULL = "rollbackMethodNull";
    private static final String TERMINATE_RESTORE_SESSIONS_METHOD = "terminateRestoreSessions";
    private static final String FRACTURE_CLONE_METHOD = "fractureClone";
    
    public static final String BLOCK_VOLUME_EXPAND_GROUP = "BlockDeviceExpandVolume";

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setBlockScheduler(BlockStorageScheduler blockScheduler) {
        _blockScheduler = blockScheduler;
    }

    public void setDevices(Map<String, BlockStorageDevice> deviceInterfaces) {
        _devices = deviceInterfaces;
    }

    public void setEventManager(RecordableEventManager eventManager) {
        _eventManager = eventManager;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        _workflowService = workflowService;
    }

    public BlockStorageDevice getDevice(String deviceType) {
        return _devices.get(deviceType);
    }

    /**
     * Creates a rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain.
     * 
     * @return A workflow method
     */
    private Workflow.Method rollbackMethodNullMethod() {
        return new Workflow.Method(ROLLBACK_METHOD_NULL);
    }
    
    /**
     * A rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain. Can be and is
     * used in workflows in other controllers that invoke operations on this 
     * block controller. If the block operation happens to fail, this no-op
     * rollback method is invoked. It says the rollback step succeeded,
     * which will then allow other rollback operations to execute for other
     * workflow steps executed by the other controller.
     * 
     * See the VPlexDeviceController restoreVolume method which creates a 
     * workflow step that invokes the BlockDeviceController restoreVolume 
     * method. The rollback method for this step is this no-op. If the
     * BlockDeviceController restoreVolume step fails, this rollback 
     * method is invoked, which simply says the rollback for the step
     * was successful. This in turn allows the other steps in the workflow
     * rollback.
     * 
     * @param stepId The id of the step being rolled back.
     * 
     * @throws WorkflowException
     */
    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    /**
     * Fail the task
     *
     * @param clazz
     * @param id
     * @param opId
     * @param msg
     */
    @Deprecated
    private void doFailTask(Class<? extends DataObject> clazz, URI id, String opId, String msg){
        try{
            _dbClient.updateTaskOpStatus(clazz, id, opId, new Operation(Operation.Status.error.name(), msg));
        }
        catch (DatabaseException ioe) {
           _log.error(ioe.getMessage());
        }
    }

    /**
     * Set the status of operation to 'ready'
     *
     * @param clazz The data object class.
     * @param ids The ids of the data objects for which the task completed.
     * @param opId The task id.
     */
    private void doSuccessTask(
            Class<? extends DataObject> clazz, List<URI> ids, String opId) {
        try {
            for (URI id : ids) {
            	 _dbClient.ready(clazz, id, opId);
            }
        } catch (DatabaseException ioe) {
            _log.error(ioe.getMessage());
        }
    }

    /**
     * Fail the task. Called when an exception occurs attempting to
     * execute a task on multiple data objects.
     *
     * @param clazz The data object class.
     * @param ids The ids of the data objects for which the task failed.
     * @param opId The task id.
     * @param serviceCoded Original exception.
     */
    private void doFailTask(
            Class<? extends DataObject> clazz, List<URI> ids, String opId, ServiceCoded serviceCoded) {
        try {
            for (URI id : ids) {
                _dbClient.error(clazz, id, opId, serviceCoded);
            }
        } catch (DatabaseException ioe) {
            _log.error(ioe.getMessage());
        }
    }

    /**
     * Fail the task. Called when an exception occurs attempting to
     * execute a task.
     *
     * @param clazz The data object class.
     * @param id The id of the data object for which the task failed.
     * @param opId The task id.
     * @param serviceCoded Original exception.
     */
    private void doFailTask(
            Class<? extends DataObject> clazz, URI id, String opId, ServiceCoded serviceCoded) {

        List<URI> ids = new ArrayList<URI>();
        ids.add(id);
        doFailTask(clazz, ids, opId, serviceCoded);
    }


    /**
     * Create a nice event based on the volume
     *
     * @param volume Volume for which the event is about
     * @param type Type of event such as VolumeCreated or VolumeDeleted
     * @param description Description for the event if needed
     * @param extensions The event extension data
     */
    public void recordVolumeEvent(Volume volume, String type, String description,
        String extensions) {
        // TODO fix the bogus user ID once we have AuthZ working

       RecordableBourneEvent event= ControllerUtils.convertToRecordableBourneEvent(volume, type, description,
                extensions,_dbClient,EVENT_SERVICE_TYPE, RecordType.Event.name(),EVENT_SERVICE_SOURCE);
        try {
            _eventManager.recordEvents(event);
        } catch(Throwable th ) {
            _log.error("Failed to record event. Event description: {}. Error: ",  description, th);
        }
    }

    /**
     * Workflow step associated with creating volumes.
     * TODO: RB3294, Do we really need these steps?  Can we just call the method directly if that's all its really doing?
     *
     * @param systemURI storage system
     * @param poolURI storage pool
     * @param volumeURIs list of volume uris that were pre-created in the db
     * @param stepId the task id from the workflow service
     * @return true if successful, false otherwise
     */
    public boolean createVolumesStep(URI systemURI, URI poolURI, List<URI> volumeURIs,
            VirtualPoolCapabilityValuesWrapper cosCapabilities, String stepId) throws WorkflowException {
        boolean status = true;      // no error initially
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            // Now call addZones to add all the required zones.
            createVolumes(systemURI, poolURI, volumeURIs, cosCapabilities, stepId);
        } catch (Exception ex) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
            status = false;
        }
        return status;
    }

    static final String CREATE_VOLUMES_STEP_GROUP = "BlockDeviceCreateVolumes";
    static final String MODIFY_VOLUMES_STEP_GROUP = "BlockDeviceModifyVolumes";
    static final String CREATE_MIRRORS_STEP_GROUP = "BlockDeviceCreateMirrors";
    static final String CREATE_CONSISTENCY_GROUP_STEP_GROUP = "BlockDeviceCreateGroup";
    /**
     * {@inheritDoc}}
     */
    @Override
    public String addStepsForCreateVolumes(Workflow workflow, String waitFor,
            List<VolumeDescriptor> origVolumes, String taskId) throws ControllerException {

        // Get the list of descriptors the BlockDeviceController needs to create volumes for.
        List<VolumeDescriptor> volumeDescriptors = VolumeDescriptor.filterByType(origVolumes,
                new VolumeDescriptor.Type[] {
                    VolumeDescriptor.Type.BLOCK_DATA,
                    VolumeDescriptor.Type.RP_SOURCE,
                    VolumeDescriptor.Type.RP_JOURNAL,
                    VolumeDescriptor.Type.RP_TARGET,
                    VolumeDescriptor.Type.SRDF_SOURCE,
                    VolumeDescriptor.Type.SRDF_TARGET
                }, null);

        // If no volumes to create, just return
        if (volumeDescriptors.isEmpty()) return waitFor;

        // Segregate by pool to list of volumes.
        Map<URI, Map<Long, List<VolumeDescriptor>>> poolMap = VolumeDescriptor.getPoolSizeMap(volumeDescriptors);

        // Add a Step to create the consistency group if needed
        waitFor = addStepsForCreateConsistencyGroup(workflow, waitFor, volumeDescriptors);

        // Add a Step for each Pool in each Device.
        // For meta volumes add  Step for each meta volume, except vmax thin meta volumes.
        for (URI poolURI : poolMap.keySet()) {
            for (Long volumeSize : poolMap.get(poolURI).keySet()) {
                List<VolumeDescriptor> descriptors = poolMap.get(poolURI).get(volumeSize);
                List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(descriptors);
                VolumeDescriptor first = descriptors.get(0);
                URI deviceURI = first.getDeviceURI();
                VirtualPoolCapabilityValuesWrapper capabilities = first.getCapabilitiesValues();

                // Check if volumes have to be created as meta volumes
                _log.debug(String.format("Capabilities : isMeta: %s, Meta Type: %s, Member size: %s, Count: %s",
                        capabilities.getIsMetaVolume(), capabilities.getMetaVolumeType(), capabilities.getMetaVolumeMemberSize(),
                        capabilities.getMetaVolumeMemberCount()));

                boolean createAsMetaVolume = capabilities.getIsMetaVolume() || MetaVolumeUtils.createAsMetaVolume(first.getVolumeURI(), _dbClient, capabilities);
                if (createAsMetaVolume) {
                    Volume volume = _dbClient.queryObject(Volume.class, first.getVolumeURI());
                    StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, volume.getStorageController());
                    // For vmax thin meta volumes we can create multiple meta volumes in one smis request
                    if (volume.getThinlyProvisioned() && storageSystem.getSystemType().equals(StorageSystem.Type.vmax.toString())) {
                        workflow.createStep(CREATE_VOLUMES_STEP_GROUP,
                                String.format("Creating meta volumes:\n%s", getVolumesMsg(_dbClient, volumeURIs)),
                                waitFor, deviceURI, getDeviceType(deviceURI),
                                this.getClass(),
                                createMetaVolumesMethod(deviceURI, poolURI, volumeURIs, capabilities),
                                rollbackCreateMetaVolumesMethod(deviceURI, volumeURIs), null);
                    } else {
                        // Add workflow step for each meta volume
                        for (URI metaVolumeURI : volumeURIs) {
                            List<URI> metaVolumeURIs = new ArrayList<URI>();
                            metaVolumeURIs.add(metaVolumeURI);
                            String stepId = workflow.createStepId();
                            workflow.createStep(CREATE_VOLUMES_STEP_GROUP,
                                    String.format("Creating meta volume:\n%s", getVolumesMsg(_dbClient, metaVolumeURIs)),
                                    waitFor, deviceURI, getDeviceType(deviceURI),
                                    this.getClass(),
                                    createMetaVolumeMethod(deviceURI, poolURI, metaVolumeURI, capabilities),
                                    rollbackCreateMetaVolumeMethod(deviceURI, metaVolumeURI, stepId), stepId);
                        }
                    }
                } else {
                    workflow.createStep(CREATE_VOLUMES_STEP_GROUP,
                            String.format("Creating volumes:\n%s", getVolumesMsg(_dbClient, volumeURIs)),
                            waitFor, deviceURI, getDeviceType(deviceURI),
                            this.getClass(),
                            createVolumesMethod(deviceURI, poolURI, volumeURIs, capabilities),
                            rollbackCreateVolumesMethod(deviceURI, volumeURIs), null);
                }
                // Following workflow step is only applicable to HDS Thin Volume modification.
                if (getDeviceType(deviceURI).equalsIgnoreCase(Type.hds.name())) {
                    boolean modifyHitachiVolumeToApplyTieringPolicy = HDSUtils.isVolumeModifyApplicable(
                            first.getVolumeURI(), _dbClient);
                    if (modifyHitachiVolumeToApplyTieringPolicy) {
                        workflow.createStep(MODIFY_VOLUMES_STEP_GROUP,
                                String.format("Modifying volumes:\n%s", getVolumesMsg(_dbClient, volumeURIs)),
                                CREATE_VOLUMES_STEP_GROUP, deviceURI, getDeviceType(deviceURI), this.getClass(),
                                moidfyVolumesMethod(deviceURI, poolURI, volumeURIs),
                                rollbackCreateVolumesMethod(deviceURI, volumeURIs), null);
                    }
                }
            }
        }
        waitFor = CREATE_VOLUMES_STEP_GROUP;
        
        return waitFor;
    }

    /**
     * Add Steps to create any BLOCK_MIRRORs specified in the VolumeDescriptor list.
     * @param workflow -- The Workflow being built
     * @param waitFor -- Previous steps to waitFor
     * @param volumes -- List<VolumeDescriptors> -- volumes of all types to be processed
     * @return last step added to waitFor
     * @throws ControllerException
     */
    public String addStepsForCreateMirrors(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes) throws ControllerException {
        // Filter any BLOCK_MIRRORs that need to be created.
        volumes = VolumeDescriptor.filterByType(volumes,
                 new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_MIRROR },
                 new VolumeDescriptor.Type[] { });
        // If no volumes to be created, just return.
        if (volumes.isEmpty()) return waitFor;

        for (VolumeDescriptor volume : volumes) {
            URI deviceURI = volume.getDeviceURI();
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, volume.getVolumeURI());
            workflow.createStep(CREATE_MIRRORS_STEP_GROUP,
                    String.format("Creating mirror for %s", volume), waitFor,
                    deviceURI, getDeviceType(deviceURI),
                    this.getClass(),
                    createMirrorMethod(volume.getDeviceURI(), mirror.getId(), false),
                    rollbackMirrorMethod(volume.getDeviceURI(), mirror.getId()), null);
        }
        return CREATE_MIRRORS_STEP_GROUP;
    }

    /**
     * Add Steps to create the required consistency group
     * @param workflow -- The Workflow being built
     * @param waitFor -- Previous steps to waitFor
     * @param volumesDescriptors -- List<VolumeDescriptors> -- volumes of all types to be processed
     * @return last step added to waitFor
     * @throws ControllerException
     */
    private String addStepsForCreateConsistencyGroup(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumesDescriptors) throws ControllerException {

    	// Filter any BLOCK_DATAs that need to be created.
    	List<VolumeDescriptor> volumes = VolumeDescriptor.filterByType(volumesDescriptors,
                 new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA },
                 new VolumeDescriptor.Type[] { });

    	// If no volumes to be created, just return.
        if (volumes.isEmpty()) {
            return waitFor;
        }
        
        if (VolumeDescriptor.Type.SRDF_SOURCE.toString().equalsIgnoreCase(volumes.get(0).getType().toString())
        		|| VolumeDescriptor.Type.SRDF_TARGET.toString().equalsIgnoreCase(volumes.get(0).getType().toString())
        		| VolumeDescriptor.Type.SRDF_EXISTING_SOURCE.toString().equalsIgnoreCase(volumes.get(0).getType().toString())) {
        	return waitFor;
        }
        
        // Get the consistency group. If no consistency group to be created,
        // just return. Get CG from any descriptor.
        final VolumeDescriptor first = volumes.get(0);
    	final URI consistencyGroupURI = first.getConsistencyGroupURI();  	
    	if (consistencyGroupURI == null) {
    	    return waitFor;
    	}
        final BlockConsistencyGroup consistencyGroup = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupURI);

        // Create the CG on each system it has yet to be created on. Note that
        // typically, for a volume creation request in a CG, the system will be 
        // the same for all volumes because all volumes will reside in the CG 
        // on that array. However, it is also called when creating VPLEX distributed 
        // volumes in CGs. In this case, the backend volumes will be on different
        // arrays and we will need the CG created on both backend arrays.
        List<URI> deviceURIs = new ArrayList<URI>();
        for (VolumeDescriptor descr : volumes) {
        	// If the descriptor's associated volume is the backing volume for a RP+VPlex
        	// journal/target volume, we want to ignore its storage system.  We do not want to
        	// create backing array consistency groups for RP+VPlex target volumes.  Only
        	// source volume.
        	Volume volume = _dbClient.queryObject(Volume.class, descr.getVolumeURI());
        	if (!RPHelper.isAssociatedToRpVplexType(volume, _dbClient, PersonalityTypes.TARGET, PersonalityTypes.METADATA)) {
                URI deviceURI = descr.getDeviceURI();
                if (!deviceURIs.contains(deviceURI)) {
                    deviceURIs.add(deviceURI);
                }
        	}
        }
        
        boolean createdCg = false;
        for (URI deviceURI : deviceURIs) {
            // If the consistency group has already been created in the array, just return
            if (!consistencyGroup.created(deviceURI)) {
                // Create step to create consistency group
                waitFor = workflow.createStep(CREATE_CONSISTENCY_GROUP_STEP_GROUP,
                        String.format("Creating consistency group  %s", consistencyGroupURI), waitFor,
                        deviceURI, getDeviceType(deviceURI),
                        this.getClass(),
                        new Workflow.Method("createConsistencyGroup", deviceURI, consistencyGroupURI),
                        new Workflow.Method("deleteConsistencyGroup", deviceURI, consistencyGroupURI, false), null);
                createdCg = true;
            }
        }
        
        if (createdCg) {
            waitFor = CREATE_CONSISTENCY_GROUP_STEP_GROUP;
        }

        return waitFor;
    }

    /**
     * Returns a message containing information about each volume.
     * @param volumeURIs
     * @return
     */
    static public String getVolumesMsg(DbClient dbClient, List<URI> volumeURIs) {
        StringBuilder builder = new StringBuilder();
        for (URI uri : volumeURIs) {
            BlockObject obj = BlockObject.fetch(dbClient, uri);
            if (obj == null) continue;
            builder.append("Volume: " + obj.getLabel() + " (" + obj.getId() + ")");
            if (obj.getWWN() != null) builder.append(" wwn: " + obj.getWWN());
            if (obj.getNativeId() != null) builder.append(" native id: " + obj.getNativeId());
            builder.append("\n");
        }
        return builder.toString();
    }
    
    /**
     * Return a Workflow.Method for createVolumes.
     * @param systemURI
     * @param poolURI
     * @param volumeURIs
     * @param capabilities
     * @return Workflow.Method
     */
    private Workflow.Method moidfyVolumesMethod(URI systemURI, URI poolURI, List<URI> volumeURIs) {
        return new Workflow.Method("modifyVolumes", systemURI, poolURI, volumeURIs);
    }
    
    /**
     * {@inheritDoc}}
     * NOTE NOTE: The signature here MUST match the Workflow.Method modifyVolumesMethod just above (except opId).
     * Currently this workflow step is used only for Hitachi Thin Volumes modification to update volume tieringPolicy.
     * Hitachi allows setting of tieringpolicy at LDEV level, hence We should have a LDEV id of a LogicalUnit. 
     * But LDEV is only created after we LogicalUnit is created. Hence createVolumes workflow includes creation of LU (i.e. LDEV)
     * And LDEV modification (to set tieringPolicy.)
     * 
     */
    @Override
    public void modifyVolumes(URI systemURI, URI poolURI, List<URI> volumeURIs, String opId) throws ControllerException {
        
        List<Volume> volumes = new ArrayList<Volume>();
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                    systemURI);
            List<VolumeTaskCompleter> volumeCompleters = new ArrayList<VolumeTaskCompleter>();
            Iterator<URI> volumeURIsIter = volumeURIs.iterator();
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "moidfyVolumes start - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            while (volumeURIsIter.hasNext()) {
                URI volumeURI = volumeURIsIter.next();
                logMsgBuilder.append(String.format("\nVolume:%s", volumeURI.toString()));
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                volumes.add(volume);
                VolumeUpdateCompleter volumeCompleter = new VolumeUpdateCompleter(
                        volumeURI, opId);
                volumeCompleters.add(volumeCompleter);
            }
            _log.info(logMsgBuilder.toString());
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, poolURI);
            MultiVolumeTaskCompleter completer = new MultiVolumeTaskCompleter(volumeURIs, volumeCompleters, opId);

            Volume volume = volumes.get(0);
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            WorkflowStepCompleter.stepExecuting(completer.getOpId());
            getDevice(storageSystem.getSystemType()).doModifyVolumes(storageSystem,
                    storagePool, opId, volumes, completer);
            logMsgBuilder = new StringBuilder(String.format(
                    "modifyVolumes end - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            volumeURIsIter = volumeURIs.iterator();
            while (volumeURIsIter.hasNext()) {
                logMsgBuilder.append(String.format("\nVolume:%s", volumeURIsIter.next()
                        .toString()));
            }
            _log.info(logMsgBuilder.toString());
        } catch (InternalException e) {
            _log.error(String.format("modifyVolumes Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            doFailTask(Volume.class, volumeURIs, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
            
        } catch (Exception e) {
            _log.error(String.format("modifyVolumes Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURIs, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }


    /**
     * Return a Workflow.Method for createVolumes.
     * @param systemURI
     * @param poolURI
     * @param volumeURIs
     * @param capabilities
     * @return Workflow.Method
     */
    private Workflow.Method createVolumesMethod(URI systemURI, URI poolURI, List<URI> volumeURIs,
                                                VirtualPoolCapabilityValuesWrapper capabilities) {
        return new Workflow.Method("createVolumes", systemURI, poolURI, volumeURIs, capabilities);
    }

    /**
     * {@inheritDoc}}
     * NOTE NOTE: The signature here MUST match the Workflow.Method createVolumesMethod just above (except opId).
     */
    @Override
    public void createVolumes(URI systemURI, URI poolURI, List<URI> volumeURIs,
                              VirtualPoolCapabilityValuesWrapper capabilities, String opId) throws ControllerException {
        
        boolean opCreateFailed = false; 
        List<Volume> volumes = new ArrayList<Volume>();
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                    systemURI);
            List<VolumeTaskCompleter> volumeCompleters = new ArrayList<VolumeTaskCompleter>();
            Iterator<URI> volumeURIsIter = volumeURIs.iterator();
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "createVolumes start - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            while (volumeURIsIter.hasNext()) {
                URI volumeURI = volumeURIsIter.next();
                logMsgBuilder.append(String.format("\nVolume:%s", volumeURI.toString()));
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                volumes.add(volume);
                VolumeCreateCompleter volumeCompleter = new VolumeCreateCompleter(
                        volumeURI, opId);
                volumeCompleters.add(volumeCompleter);
            }
            _log.info(logMsgBuilder.toString());
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, poolURI);
            MultiVolumeTaskCompleter completer = new MultiVolumeTaskCompleter(volumeURIs, volumeCompleters, opId);

            Volume volume= volumes.get(0);
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            WorkflowStepCompleter.stepExecuting(completer.getOpId());
            getDevice(storageSystem.getSystemType()).doCreateVolumes(storageSystem,
                    storagePool, opId, volumes, capabilities, completer);
            logMsgBuilder = new StringBuilder(String.format(
                    "createVolumes end - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            volumeURIsIter = volumeURIs.iterator();
            while (volumeURIsIter.hasNext()) {
                logMsgBuilder.append(String.format("\nVolume:%s", volumeURIsIter.next()
                        .toString()));
            }
            _log.info(logMsgBuilder.toString());
        } catch (InternalException e) {
            _log.error(String.format("createVolume Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            doFailTask(Volume.class, volumeURIs, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
            opCreateFailed = true;
        } catch (Exception e) {
            _log.error(String.format("createVolume Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURIs, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            opCreateFailed = true;
        }
        if(opCreateFailed){
            for( Volume volume : volumes ){
                volume.setInactive(true);
                _dbClient.persistObject(volume); 
            }
        }
    }

    /**
     * Return a Workflow.Method for rollbackCreateVolumes
     * @param systemURI
     * @param volumeURI
     * @return Workflow.Method
     */
    public static Workflow.Method rollbackCreateVolumesMethod(URI systemURI, List<URI> volumeURIs) {
        return new Workflow.Method("rollBackCreateVolumes", systemURI, volumeURIs);
    }

    /**
     * {@inheritDoc}}
     * NOTE: The signature here MUST match the Workflow.Method rollbackCreateVolumesMethod just above (except opId).
     */
    @Override
    public void rollBackCreateVolumes(URI systemURI, List<URI> volumeURIs, String opId) throws ControllerException {
    	try {
    		String logMsg = String.format(
    				"rollbackCreateVolume start - Array:%s, Volume:%s", systemURI.toString(), Joiner.on(',').join(volumeURIs));
    		_log.info(logMsg.toString());

    		WorkflowStepCompleter.stepExecuting(opId);

    		for (URI volumeId : volumeURIs) {
    			Volume volume = _dbClient.queryObject(Volume.class, volumeId);
    			
                // CTRL-5597 clean volumes which have failed only in a multi-volume request
                if (null != volume.getNativeGuid()) {
                    StorageSystem system = _dbClient.queryObject(StorageSystem.class,
                            volume.getStorageController());
                    if (Type.xtremio.toString().equalsIgnoreCase(system.getSystemType())) {
                        continue;
                    }
                }
    			// For quicker garbage collection, remove any reference to journal volumes if it exists
    			if (volume.getRpJournalVolume() != null) {
    				volume.setRpJournalVolume(NullColumnValueGetter.getNullURI());
    				_dbClient.persistObject(volume);
    			}
    			
    			// For quicker garbage collection, remove any reference to target volumes if it exists
    			if (volume.getRpTargets() != null && !volume.getRpTargets().isEmpty()) {
    				StringSet ss = volume.getRpTargets();
    				ss.clear();
    				volume.setRpTargets(ss);
    				_dbClient.persistObject(volume);
    			}
    			//clearing targets explicitly, during vpool change if target volume creation failed for same reason,
    			//then we need to clear srdfTargets field for source
    			if (null != volume.getSrdfTargets()) {
    				_log.info("Clearing targets for existing source");
    			     volume.getSrdfTargets().clear();
    			     _dbClient.persistObject(volume);
    			}
    			//for change Virtual Pool, if failed, clear targets for source
    			if (!NullColumnValueGetter.isNullNamedURI(volume.getSrdfParent())) {
    			    URI sourceUri = volume.getSrdfParent().getURI();
    			    Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceUri);
    			    if (null != sourceVolume.getSrdfTargets()) {
    			        sourceVolume.getSrdfTargets().clear();
    			        _dbClient.persistObject(sourceVolume);
    			    }
    			    
    			    //Clearing target CG
    			    URI cgUri = volume.getConsistencyGroup();
					if (null != cgUri) {
						BlockConsistencyGroup targetCG = _dbClient.queryObject(
								BlockConsistencyGroup.class, cgUri);
						if (null != targetCG && (null == targetCG.getTypes()
								|| null == targetCG.getStorageController())) {
							_log.info("Set target CG {} inactive",targetCG.getLabel());
							targetCG.setInactive(true);
							_dbClient.persistObject(targetCG);
						}

						// clear association between target volume and target cg
						volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
						_dbClient.updateAndReindexObject(volume);
					}
    			}
    			
    			// Check for loose export groups associated with this rolled-back volume
    			URIQueryResultList exportGroupURIs = new URIQueryResultList();
    			_dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeExportGroupConstraint(
    					volume.getId()), exportGroupURIs);
    			while (exportGroupURIs.iterator().hasNext()) {
    				URI exportGroupURI = exportGroupURIs.iterator().next();
    				ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
    				if (!exportGroup.getInactive()) {
    					if (exportGroup.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
    						// Make sure the volume is not in an export mask
    						boolean foundInMask = false;
    						if (exportGroup.getExportMasks() != null) {
    							for (String exportMaskId : exportGroup.getExportMasks()) {
    								ExportMask mask = _dbClient.queryObject(ExportMask.class, URI.create(exportMaskId));
    								if (mask.hasVolume(volume.getId())) {
    									foundInMask = true;
    									break;
    								}
    							}
    						}

    						// If we didn't find that volume in a mask, it's OK to remove it.
    						if (!foundInMask) {
    							exportGroup.removeVolume(volume.getId());
    							if (exportGroup.getVolumes().isEmpty()) {
    								_dbClient.removeObject(exportGroup);
    							} else {
    								_dbClient.updateAndReindexObject(exportGroup);
    							}
    						}
    					}
    				}
    			}
    		}

    		// Call regular delete volumes
    		deleteVolumes(systemURI, volumeURIs, opId);

    		logMsg = String.format(
    				"rollbackCreateVolume end - Array:%s, Volume:%s", systemURI.toString(), Joiner.on(',').join(volumeURIs));
    		_log.info(logMsg.toString());
    	} catch (InternalException e) {
    		_log.error(String.format("rollbackCreateVolume Failed - Array:%s, Volume:%s", systemURI.toString(),
    				Joiner.on(',').join(volumeURIs)));
    		doFailTask(Volume.class, volumeURIs, opId, e);
    		WorkflowStepCompleter.stepFailed(opId, e);
    	} catch (Exception e) {
    		_log.error(String.format("rollbackCreateVolume Failed - Array:%s, Volume:%s", systemURI.toString(),
    				Joiner.on(',').join(volumeURIs)));
    		ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
    		doFailTask(Volume.class, volumeURIs, opId, serviceError);
    		WorkflowStepCompleter.stepFailed(opId, serviceError);
    	}
    }

    /**
     * Return a Workflow.Method for createMetaVolumes.
     * @param systemURI
     * @param poolURI
     * @param volumeURIs
     * @param capabilities
     * @return Workflow.Method
     */
    private Workflow.Method createMetaVolumesMethod(URI systemURI, URI poolURI, List<URI> volumeURIs,
                                                   VirtualPoolCapabilityValuesWrapper capabilities) {
        return new Workflow.Method("createMetaVolumes", systemURI, poolURI, volumeURIs, capabilities);
    }

    /**
     * Return a Workflow.Method for rollbackCreateMetaVolumes.
     * @param systemURI
     * @param volumeURIs
     * @return Workflow.Method
     */
    public static Workflow.Method rollbackCreateMetaVolumesMethod(URI systemURI, List<URI> volumeURIs) {
        return rollbackCreateVolumesMethod(systemURI, volumeURIs);
    }

    /**
     * Return a Workflow.Method for createVolumes.
     * @param systemURI
     * @param poolURI
     * @param volumeURI
     * @param capabilities
     * @return Workflow.Method
     */
    private Workflow.Method createMetaVolumeMethod(URI systemURI, URI poolURI, URI volumeURI,
                                                   VirtualPoolCapabilityValuesWrapper capabilities) {
        return new Workflow.Method("createMetaVolume", systemURI, poolURI, volumeURI, capabilities);
    }

    /**
     * Return a Workflow.Method for rollbackCreateMetaVolume.
     * @param systemURI
     * @param volumeURI
     * @return Workflow.Method
     */
    public static Workflow.Method rollbackCreateMetaVolumeMethod(URI systemURI, URI volumeURI, String createMetaVolumeStepId) {
        return new Workflow.Method("rollBackCreateMetaVolume", systemURI, volumeURI, createMetaVolumeStepId);
    }

    
    /**
     * {@inheritDoc}}
     * NOTE: The signature here MUST match the Workflow.Method rollbackCreateMetaVolumeMethod just above (except opId).
     */
    @Override
    public void rollBackCreateMetaVolume(URI systemURI,URI volumeURI, String createStepId, String opId) throws ControllerException {

        try {
            String logMsg = String.format(
                    "rollbackCreateMetaVolume start - Array:%s, Volume:%s", systemURI.toString(), volumeURI.toString());
            _log.info(logMsg.toString());

            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);

            CleanupMetaVolumeMembersCompleter cleanupCompleter = null;
            WorkflowStepCompleter.stepExecuting(opId);
            // Check if we need to cleanup dangling meta members volumes on array.
            // Meta members are temporary array volumes. They only exist until they are added to a meta volume.
            // We store these volumes in WF create step data.
            List<String> metaMembers = (ArrayList<String>)_workflowService.loadStepData(createStepId);
            if (metaMembers != null && !metaMembers.isEmpty() )  {
                boolean isWFStep = false;
                cleanupCompleter = new CleanupMetaVolumeMembersCompleter(volumeURI, isWFStep,  createStepId, opId);
                getDevice(storageSystem.getSystemType()).doCleanupMetaMembers(storageSystem, volume, cleanupCompleter);
            }
            // TEMPER  Used for negative testing.
            // Comment out call to doCleanupMetaMembers above
            // cleanupCompleter.setSuccess(false);
            //// TEMPER
            // Delete meta volume.
            // Delete only if meta members cleanup was successful (in case it was executed).
            if (cleanupCompleter == null || cleanupCompleter.isSuccess() ) {
                List<URI> volumeURIs = new ArrayList<URI>();
                volumeURIs.add(volumeURI);
                deleteVolumeStep(systemURI, volumeURIs, opId);
            } else {
                ServiceError serviceError;
                if (cleanupCompleter.getError() != null) {
                    serviceError = cleanupCompleter.getError();
                }  else {
                    serviceError = DeviceControllerException.errors.jobFailedOp("CleanupMetaVolumeMembers");
                }
                doFailTask(Volume.class, volumeURI, opId, serviceError);
                WorkflowStepCompleter.stepFailed(opId, serviceError);
            }
            logMsg = String.format(
                    "rollbackCreateMetaVolume end - Array:%s, Volume:%s", systemURI.toString(), volumeURI.toString());
            _log.info(logMsg.toString());
        } catch (InternalException e) {
            _log.error(String.format("rollbackCreateMetaVolume Failed - Array:%s, Volume:%s", systemURI.toString(),
                    volumeURI.toString()));
            doFailTask(Volume.class, volumeURI, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
        } catch (Exception e) {
            _log.error(String.format("rollbackCreateMetaVolume Failed - Array:%s, Volume:%s", systemURI.toString(),
                    volumeURI.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURI, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }


    /**
     * Return a Workflow.Method for rollbackExpandVolume.
     * @param systemURI
     * @param volumeURI
     * @return Workflow.Method
     */
    public static Workflow.Method rollbackExpandVolumeMethod(URI systemURI, URI volumeURI, String expandStepId) {
        return new Workflow.Method("rollBackExpandVolume", systemURI, volumeURI, expandStepId);
    }

    /**
     * {@inheritDoc}}
     * NOTE: The signature here MUST match the Workflow.Method rollbackExpandVolume just above (except opId).
     */
    @Override
    public  void rollBackExpandVolume(URI systemURI, URI volumeURI, String expandStepId, String opId) throws ControllerException {

        try {
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "rollbackExpandVolume start - Array:%s, Volume:%s", systemURI.toString(), volumeURI.toString()));
            _log.info(logMsgBuilder.toString());

            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);

            WorkflowStepCompleter.stepExecuting(opId);

            // Check if we need to cleanup dangling meta members volumes on array.
            // Meta members are temporary array volumes. They only exist until they are added to a meta volume.
            // We store these volumes in WF expand step data.
            List<String> metaMembers = (ArrayList<String>)_workflowService.loadStepData(expandStepId);
            if (metaMembers != null && !metaMembers.isEmpty() ) {
                CleanupMetaVolumeMembersCompleter cleanupCompleter = null;
                boolean isWFStep = true;
                cleanupCompleter = new CleanupMetaVolumeMembersCompleter(volumeURI, isWFStep, expandStepId, opId);
                getDevice(storageSystem.getSystemType()).doCleanupMetaMembers(storageSystem, volume, cleanupCompleter);
                // TEMPER  Used for negative testing.
                // Comment out call to doCleanupMetaMembers above
                // cleanupCompleter.setSuccess(false);
                //// TEMPER
                if (!cleanupCompleter.isSuccess()) {
                    ServiceError serviceError;
                    if (cleanupCompleter.getError() != null) {
                        serviceError = cleanupCompleter.getError();
                    }  else {
                        serviceError = DeviceControllerException.errors.jobFailedOp("CleanupMetaVolumeMembers");
                    }
                    doFailTask(Volume.class, volumeURI, opId, serviceError);
                    WorkflowStepCompleter.stepFailed(opId, serviceError);
                }
            }
            else {
                // We came here if: a. Volume was expanded as a regular volume. or b. Volume was expanded as a meta volume,
                // but there are no dangling meta members left.
                _log.info("rollbackExpandVolume: nothing to cleanup in rollback.");
                WorkflowStepCompleter.stepSucceded(opId);
            }
            logMsgBuilder = new StringBuilder(String.format(
                    "rollbackExpandVolume end - Array:%s, Volume:%s", systemURI.toString(), volumeURI.toString()));
            _log.info(logMsgBuilder.toString());
        } catch (InternalException e) {
            _log.error(String.format("rollbackExpandVolume Failed - Array:%s,  Volume:%s", systemURI.toString(),
                    volumeURI.toString()));
            doFailTask(Volume.class, volumeURI, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
        } catch (Exception e) {
            _log.error(String.format("rollbackExpandVolume Failed - Array:%s, Volume:%s", systemURI.toString(),
                    volumeURI.toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURI, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    /**
     * {@inheritDoc}}
     * NOTE NOTE: The signature here MUST match the Workflow.Method createMetaVolumesMethod just above (except opId).
     */
    @Override
    public void createMetaVolumes(URI systemURI, URI poolURI, List<URI> volumeURIs,
                                 VirtualPoolCapabilityValuesWrapper capabilities, String opId) throws ControllerException {
        boolean opCreateFailed = false;
        List<Volume> volumes = new ArrayList<Volume>();
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                    systemURI);

            List<VolumeTaskCompleter> volumeCompleters = new ArrayList<VolumeTaskCompleter>();
            Iterator<URI> volumeURIsIter = volumeURIs.iterator();
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "createMetaVolumes start - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            while (volumeURIsIter.hasNext()) {
                URI volumeURI = volumeURIsIter.next();
                logMsgBuilder.append(String.format("\nVolume:%s", volumeURI.toString()));
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                volumes.add(volume);
                VolumeCreateCompleter volumeCompleter = new VolumeCreateCompleter(
                        volumeURI, opId);
                volumeCompleters.add(volumeCompleter);
            }
            _log.info(logMsgBuilder.toString());
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, poolURI);
            MultiVolumeTaskCompleter completer = new MultiVolumeTaskCompleter(volumeURIs, volumeCompleters, opId);

            Volume volume= volumes.get(0);
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());

            // All volumes are in the same storage pool with the same capacity. Get recommendation for the first volume.
            MetaVolumeRecommendation recommendation = MetaVolumeUtils.getCreateRecommendation(storageSystem, storagePool,
                    volume.getCapacity(), volume.getThinlyProvisioned(), vpool.getFastExpansion(), capabilities);

            for (Volume metaVolume: volumes) {
                MetaVolumeUtils.prepareMetaVolume(metaVolume, recommendation.getMetaMemberSize(), recommendation.getMetaMemberCount(),
                        recommendation.getMetaVolumeType().toString(), _dbClient);
            }

            WorkflowStepCompleter.stepExecuting(completer.getOpId());
            getDevice(storageSystem.getSystemType()).doCreateMetaVolumes(storageSystem,
                    storagePool, volumes, capabilities, recommendation, completer);

            logMsgBuilder = new StringBuilder(String.format(
                    "createMetaVolumes end - Array:%s Pool:%s", systemURI.toString(),
                    poolURI.toString()));
            volumeURIsIter = volumeURIs.iterator();
            while (volumeURIsIter.hasNext()) {
                logMsgBuilder.append(String.format("\nVolume:%s", volumeURIsIter.next()
                        .toString()));
            }
            _log.info(logMsgBuilder.toString());
        } catch (InternalException e) {
            _log.error(String.format("createMetaVolumes Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            doFailTask(Volume.class, volumeURIs, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
            opCreateFailed = true;
        } catch (Exception e) {
            _log.error(String.format("createMetaVolumes Failed - Array: %s Pool:%s Volume:%s",
                    systemURI.toString(), poolURI.toString(), Joiner.on("\t").join(volumeURIs)));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURIs, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            opCreateFailed = true;
        }
        if(opCreateFailed){
            for( Volume volume : volumes ){
                volume.setInactive(true);
                _dbClient.persistObject(volume);
            }
        }
    }


    /**
     * {@inheritDoc}}
     * NOTE NOTE: The signature here MUST match the Workflow.Method createMetaVolumeMethod just above (except opId).
     */
    @Override
    public void createMetaVolume(URI systemURI, URI poolURI, URI volumeURI,
                                 VirtualPoolCapabilityValuesWrapper capabilities, String opId) throws ControllerException {

        try {
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "createMetaVolume start - Array:%s Pool:%s, Volume:%s", systemURI.toString(),
                    poolURI.toString(),volumeURI.toString()));
            _log.info(logMsgBuilder.toString());

            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, poolURI);
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());

            MetaVolumeRecommendation recommendation = MetaVolumeUtils.getCreateRecommendation(storageSystem, storagePool,
                    volume.getCapacity(), volume.getThinlyProvisioned(), vpool.getFastExpansion(), capabilities);
            MetaVolumeUtils.prepareMetaVolume(volume, recommendation.getMetaMemberSize(), recommendation.getMetaMemberCount(),
                    recommendation.getMetaVolumeType().toString(), _dbClient);

            VolumeCreateCompleter completer = new VolumeCreateCompleter(volumeURI, opId);
            WorkflowStepCompleter.stepExecuting(completer.getOpId());
            getDevice(storageSystem.getSystemType()).doCreateMetaVolume(storageSystem,
                    storagePool, volume, capabilities, recommendation, completer);

            logMsgBuilder = new StringBuilder(String.format(
                    "createMetaVolume end - Array:%s Pool:%s, Volume:%s", systemURI.toString(),
                    poolURI.toString(),volumeURI.toString()));
            _log.info(logMsgBuilder.toString());
        } catch (InternalException e) {
            _log.error(String.format("createMetaVolume Failed - Array:%s Pool:%s, Volume:%s", systemURI.toString(),
                    poolURI.toString(),volumeURI.toString()));
            doFailTask(Volume.class, volumeURI, opId, e);
            WorkflowStepCompleter.stepFailed(opId, e);
        } catch (Exception e) {
            _log.error(String.format("createMetaVolume Failed - Array:%s Pool:%s, Volume:%s", systemURI.toString(),
                    poolURI.toString(),volumeURI.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURI, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    /**
     * Return a Workflow.Method for expandVolume.
     * @param storage storage system
     * @param pool storage pool
     * @param volume volume to expand
     * @param size size to expand to
     * @return Workflow.Method
     */
	public static Workflow.Method expandVolumesMethod(URI storage, URI pool, URI volume, Long size) {
        return new Workflow.Method("expandVolume", storage, pool, volume, size);
    }

    /*
     * {@inheritDoc}
     * <p>
     * Single step workflow to expand volume with rollback.
     *
     */
    @Override
    public void expandBlockVolume(URI storage, URI pool, URI volume, Long size, String opId)
            throws ControllerException {
        SimpleTaskCompleter completer = new SimpleTaskCompleter(Volume.class, volume, opId);

        try {
            // Get a new workflow to execute volume expand
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    EXPAND_VOLUME_WF_NAME, false, opId);
            _log.info("Created new expansion workflow with operation id {}", opId);

            String stepId = workflow.createStepId();
            workflow.createStep(BLOCK_VOLUME_EXPAND_GROUP, String.format(
                    "Expand volume %s", volume), null,
                    storage, getDeviceType(storage),
                    BlockDeviceController.class,
                    expandVolumesMethod(storage, pool, volume, size),
                    rollbackExpandVolumeMethod(storage, volume, stepId),
                    stepId);
            _log.info("Executing workflow plan {}", BLOCK_VOLUME_EXPAND_GROUP);

            workflow.executePlan(completer, String.format(
                    "Expansion of volume %s completed successfully", volume));
        } catch (Exception ex) {
            _log.error("Could not expand volume: " + volume, ex);
            String opName = ResourceOperationTypeEnum.EXPAND_BLOCK_VOLUME.getName();
            ServiceError serviceError = DeviceControllerException.errors.expandVolumeFailed(
                    volume.toString(), opName, ex);
            completer.error(_dbClient, serviceError);
        }
    }

    /*
     * Add workflow steps for volume expand.
     */
    @Override    
    public String addStepsForExpandVolume(Workflow workflow, String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) 
                                            throws InternalException {
    	
        // The the list of Volumes that the BlockDeviceController needs to process.
        volumeDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] {
                    VolumeDescriptor.Type.BLOCK_DATA,
                    VolumeDescriptor.Type.RP_SOURCE,
                    VolumeDescriptor.Type.RP_TARGET, 
                    VolumeDescriptor.Type.RP_EXISTING_SOURCE,
                    VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE,
                    VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET                    
                }, null );
    	if (volumeDescriptors == null || volumeDescriptors.isEmpty()) {
    	    return waitFor;
    	}
        
    	Map <URI, Long> volumesToExpand = new HashMap<URI, Long>();
    	    	
    	// Check to see if there are any migrations
    	List<Migration> migrations = null; 
    	if (volumeDescriptors != null) {        
    	    List<VolumeDescriptor> migrateDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
    	            new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_MIGRATE_VOLUME }, null );
          
    	    if (migrateDescriptors != null && !migrateDescriptors.isEmpty()) {    	       
    	        // Load the migration objects for use later
    	        migrations = new ArrayList<Migration>();            
    	        Iterator<VolumeDescriptor> migrationIter = migrateDescriptors.iterator();                
    	        while (migrationIter.hasNext()) {
    	            Migration migration = _dbClient.queryObject(Migration.class, migrationIter.next().getMigrationId());
    	            migrations.add(migration);
    	        }
    	    }
    	}     
        
    	for (VolumeDescriptor descriptor : volumeDescriptors) {
    	    // Grab the volume, let's see if an expand is really needed
    	    Volume volume = _dbClient.queryObject(Volume.class, descriptor.getVolumeURI());
    	        	    
    	    //If this volume is a VPLEX volume, check to see if we need to expand its backend volume. 
    	    if (volume.getAssociatedVolumes() != null && !volume.getAssociatedVolumes().isEmpty()) {
    	        for (String volStr : volume.getAssociatedVolumes()) {		        	
    	            URI volStrURI = URI.create(volStr);
    	            Volume associatedVolume = _dbClient.queryObject(Volume.class, volStrURI);
    	            
    	            boolean migrationExists = false;
    	            // If there are any volumes that are tagged for migration, ignore them.
    	            if (migrations != null && !migrations.isEmpty()) {
    	                for (Migration migration : migrations) {                                
    	                    if (migration.getTarget().equals(volume.getId())) {
    	                        _log.info("Volume [{}] has a migration, ignore this volume for expand.", volume.getLabel());
    	                        migrationExists = true;
    	                        break;
    	                    }
    	                }
    	            }
    	            
    	            // Only expand backend volume if there is no existing migration and 
    	            // the new size > existing backend volume's provisioned capacity, otherwise we can ignore.    	                	            
    	            if (!migrationExists 
    	                    && associatedVolume.getProvisionedCapacity() != null 
    	                    && descriptor.getVolumeSize() > associatedVolume.getProvisionedCapacity().longValue()) {
    	                volumesToExpand.put(volStrURI, descriptor.getVolumeSize());
    	            }
    	        } 
    	    } 
    	    else {
    	        // Only expand the volume if it's an existing volume (provisoned capacity is not null and not 0) and 
    	        // new size > existing volume's provisioned capacity, otherwise we can ignore.
    	        if (volume.getProvisionedCapacity() != null 
    	                && volume.getProvisionedCapacity().longValue() != 0
    	                && descriptor.getVolumeSize() > volume.getProvisionedCapacity().longValue()) {
    	            volumesToExpand.put(volume.getId(), descriptor.getVolumeSize());
    	        }
    	    }		        	
    	}
      
    	String nextStep = (volumesToExpand.size() > 0) ? BLOCK_VOLUME_EXPAND_GROUP : waitFor;
    	
    	for (Map.Entry<URI, Long> entry : volumesToExpand.entrySet()) {
    	    _log.info("Creating WF step for Expand Volume for  {}", entry.getKey().toString());
    	    Volume volumeToExpand = _dbClient.queryObject(Volume.class, entry.getKey());      	        
    	    StorageSystem storage =  _dbClient.queryObject(StorageSystem.class, volumeToExpand.getStorageController());           
    	    String stepId = workflow.createStepId();
    	    workflow.createStep(BLOCK_VOLUME_EXPAND_GROUP, String.format(
    	            "Expand Block volume %s", volumeToExpand), waitFor,
    	            storage.getId(), getDeviceType(storage.getId()), 
    	            BlockDeviceController.class,
    	            expandVolumesMethod(volumeToExpand.getStorageController(), volumeToExpand.getPool(), volumeToExpand.getId(), entry.getValue()),
    	            rollbackExpandVolumeMethod(volumeToExpand.getStorageController(), volumeToExpand.getId(), stepId),
    	            stepId);
    	    _log.info("Creating workflow step {}", BLOCK_VOLUME_EXPAND_GROUP);    
    	}
  
    	return nextStep;
    }

    @Override
    public void expandVolume(URI storage, URI pool, URI volume, Long size, String opId)
            throws ControllerException {
        try {
            StorageSystem storageObj = _dbClient
                    .queryObject(StorageSystem.class, storage);
            Volume volumeObj = _dbClient.queryObject(Volume.class, volume);
            _log.info(String.format(
                    "expandVolume start - Array: %s Pool:%s Volume:%s, IsMetaVolume: %s, OldSize: %s, NewSize: %s",
                    storage.toString(), pool.toString(), volume.toString(), volumeObj.getIsComposite(), volumeObj.getCapacity(), size));
            StoragePool poolObj = _dbClient.queryObject(StoragePool.class, pool);
            VolumeExpandCompleter completer = new VolumeExpandCompleter(volume, size, opId);

            long metaMemberSize = volumeObj.getIsComposite() ? volumeObj.getMetaMemberSize() : volumeObj.getCapacity();
            long metaCapacity = volumeObj.getIsComposite() ? volumeObj.getTotalMetaMemberCapacity() : volumeObj.getCapacity();

            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volumeObj.getVirtualPool());
            boolean isThinlyProvisioned = volumeObj.getThinlyProvisioned();
            MetaVolumeRecommendation recommendation = MetaVolumeUtils.getExpandRecommendation(storageObj, poolObj,
                    metaCapacity, size, metaMemberSize, isThinlyProvisioned, vpool.getFastExpansion());
            if (recommendation.isCreateMetaVolumes()) {
                // check if we are required to create any members.
                // When expansion size fits into total meta member size, no new members should be created.
                // Also check that this is not recovery to clean dangling meta volumes with zero-capacity expansion.
                if (recommendation.getMetaMemberCount() == 0 && (volumeObj.getMetaVolumeMembers() == null ||
                        volumeObj.getMetaVolumeMembers().isEmpty())) {
                    volumeObj.setCapacity(size);
                    _dbClient.persistObject(volumeObj);
                    _log.info(String.format(
                            "Expanded volume within its total meta volume capacity (simple case) - Array: %s Pool:%s Volume:%s, IsMetaVolume: %s, Total meta volume capacity: %s, NewSize: %s",
                            storage.toString(), pool.toString(), volume.toString(), volumeObj.getIsComposite(), volumeObj.getTotalMetaMemberCapacity(), volumeObj.getCapacity()));
                    completer.ready(_dbClient);
                }  else {
                    // set meta related data in task completer
                    long metaMemberCount = volumeObj.getIsComposite() ? recommendation.getMetaMemberCount()+volumeObj.getMetaMemberCount() :
                            recommendation.getMetaMemberCount()+1;
                    completer.setMetaMemberSize(recommendation.getMetaMemberSize());
                    completer.setMetaMemberCount((int)metaMemberCount);
                    completer.setTotalMetaMembersSize(metaMemberCount*recommendation.getMetaMemberSize());
                    completer.setComposite(true);
                    completer.setMetaVolumeType(recommendation.getMetaVolumeType().toString());

                    getDevice(storageObj.getSystemType()).doExpandAsMetaVolume(storageObj, poolObj,
                            volumeObj, size, recommendation, completer);
                }
            }  else {
                // expand as regular volume
                getDevice(storageObj.getSystemType()).doExpandVolume(storageObj, poolObj,
                        volumeObj, size, completer);
            }
            _log.info(String.format("expandVolume end - Array: %s Pool:%s Volume:%s",
                    storage.toString(), pool.toString(), volume.toString()));
        } catch (Exception e) {
            _log.error(String.format("expandVolume Failed - Array: %s Pool:%s Volume:%s",
                    storage.toString(), pool.toString(), volume.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            List<URI> volumes = Arrays.asList(volume);
            doFailTask(Volume.class, volumes, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    static final String DELETE_VOLUMES_STEP_GROUP = "BlockDeviceDeleteVolumes";
    /**
     * {@inheritDoc}}
     */
    @Override
    public String addStepsForDeleteVolumes(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId) throws ControllerException {

        // Add steps for deleting any local mirrors that may be present.
        waitFor = addStepsForDeleteMirrors(workflow, waitFor, volumes);

        // The the list of Volumes that the BlockDeviceController needs to process.
        volumes = VolumeDescriptor.filterByType(volumes,
                new VolumeDescriptor.Type[] {
                    VolumeDescriptor.Type.BLOCK_DATA,
                    VolumeDescriptor.Type.RP_JOURNAL,
                    VolumeDescriptor.Type.RP_TARGET, 
                    VolumeDescriptor.Type.RP_VPLEX_VIRT_JOURNAL,
                    VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET
                }, null );
        if (volumes.isEmpty()) return waitFor;

        // Segregate by device.
        Map<URI, List<VolumeDescriptor>> deviceMap = VolumeDescriptor.getDeviceMap(volumes);

        // Add a step to delete the volumes in each device.
        for (URI deviceURI : deviceMap.keySet()) {
            volumes = deviceMap.get(deviceURI);
            List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(volumes);

            workflow.createStep(DELETE_VOLUMES_STEP_GROUP,
                    String.format("Deleting volumes:\n%s", getVolumesMsg(_dbClient, volumeURIs)),
                    waitFor, deviceURI, getDeviceType(deviceURI),
                    this.getClass(),
                    deleteVolumesMethod(deviceURI, volumeURIs),
                    null, null);
        }
        return DELETE_VOLUMES_STEP_GROUP;
    }

    /**
     * Return a Workflow.Method for deleteVolumes.
     * @param systemURI
     * @param volumeURIs
     * @return
     */
    private Workflow.Method deleteVolumesMethod(URI systemURI, List<URI> volumeURIs) {
        return new Workflow.Method("deleteVolumes", systemURI, volumeURIs);
    }

    /**
     * {@inheritDoc}}
     * NOTE NOTE: The arguments here must match deleteVolumesMethod defined above (except opId).
     */
    @Override
    public void deleteVolumes(URI systemURI, List<URI> volumeURIs, String opId)
        throws ControllerException {
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                systemURI);
            List<Volume> volumes = new ArrayList<Volume>();
            List<VolumeTaskCompleter> volumeCompleters = new ArrayList<VolumeTaskCompleter>();
            Iterator<URI> volumeURIsIter = volumeURIs.iterator();
            String arrayName = systemURI.toString();
            StringBuilder entryLogMsgBuilder = new StringBuilder(String.format(
                "deleteVolume start - Array:%s", arrayName));
            StringBuilder exitLogMsgBuilder = new StringBuilder(String.format(
                    "deleteVolume end - Array:%s", arrayName));
            while (volumeURIsIter.hasNext()) {
                URI volumeURI = volumeURIsIter.next();
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                entryLogMsgBuilder.append(String.format("\nPool:%s Volume:%s", volume
                    .getPool().toString(), volumeURI.toString()));
                exitLogMsgBuilder.append(String.format("\nPool:%s Volume:%s", volume
                        .getPool().toString(), volumeURI.toString()));
                VolumeDeleteCompleter volumeCompleter = new VolumeDeleteCompleter(volumeURI, opId);
                if (volume.getInactive() == false) {
                	// Add the volume to the list to delete
                    volumes.add(volume);
                } else {
                	// Add the proper status, since we won't be deleting this volume
                    String opName = ResourceOperationTypeEnum.DELETE_BLOCK_VOLUME.getName();
                    ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
                    serviceError.setMessage("Volume does not exist or is already deleted");
                    _log.info("Volume does not exist or is already deleted");
                    volumeCompleter.error(_dbClient, serviceError);
                }
                volumeCompleters.add(volumeCompleter);
            }
            _log.info(entryLogMsgBuilder.toString());
            if (!volumes.isEmpty()) {
            	WorkflowStepCompleter.stepExecuting(opId);
                TaskCompleter completer = new MultiVolumeTaskCompleter(volumeURIs,
                        volumeCompleters, opId);
                getDevice(storageSystem.getSystemType()).doDeleteVolumes(storageSystem, opId,
                        volumes, completer);
            } else {
                doSuccessTask(Volume.class, volumeURIs, opId);
                WorkflowStepCompleter.stepSucceded(opId);
            }
            _log.info(exitLogMsgBuilder.toString());
        } catch (InternalException e) {
            doFailTask(Volume.class, volumeURIs, opId, e);
        } catch (Exception e) {
        	ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, volumeURIs, opId, serviceError);
        }
    }

    /**
     * Workflow step to delete a volume
     *
     * @param storageURI the storage system ID
     * @param volumes the volume IDs
     * @param token the task ID from the workflow
     * @return true if the step was fired off properly.
     * @throws WorkflowException
     */
    public boolean deleteVolumeStep(URI storageURI, List<URI> volumes, String token) throws WorkflowException {
        boolean status = true;
        String volumeList = Joiner.on(',').join(volumes);
        try {
            WorkflowStepCompleter.stepExecuting(token);
            _log.info("Delete Volume Step Started. " + volumeList);
            deleteVolumes(storageURI, volumes, token);
            _log.info("Delete Volume Step Dispatched: " + volumeList);
        } catch (Exception ex) {
            _log.error("Delete Volume Step Failed." + volumeList);
            String opName = ResourceOperationTypeEnum.DELETE_VOLUME_WORKFLOW_STEP.getName();
            ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
            WorkflowStepCompleter.stepFailed(token, serviceError);
            status = false;
        }
        return status;
    }

    private void exportMaskUpdate(ExportMask exportMask, Map<URI, Integer> volumeMap, List<Initiator> initiators,
                                     List<URI> targets) throws Exception {
        if (volumeMap != null) {
            for (URI volume: volumeMap.keySet()) {
                exportMask.addVolume(volume, volumeMap.get(volume));
            }
        }

        if (initiators != null) {
            for (Initiator initiator: initiators) {
                exportMask.addInitiator(initiator);
            }
        }

        if (targets != null) {
            for (URI target: targets) {
                exportMask.addTarget(target);
            }
        }
    }

    /**
     * Select volumes from an export group that resides on a given storage array
     * @param exportGroup
     * @param exportMask
     * @return
     * @throws IOException
     */
    private Map<URI, Integer> selectExportMaskVolumes(ExportGroup exportGroup, ExportMask exportMask)
            throws IOException {
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();

        for (String uri: exportGroup.getVolumes().keySet()) {
            URI blockURI;
            try {
                blockURI = new URI(uri);
                BlockObject block = BlockObject.fetch(_dbClient, blockURI);
                if (!block.getStorageController().equals(exportMask.getStorageDevice())) {
                   continue;
                }
                volumeMap.put(blockURI, Integer.valueOf(exportGroup.getVolumes().get(blockURI.toString())));
            } catch (URISyntaxException e) {
                _log.error(e.getMessage(), e);
            }
        }
        return volumeMap;
    }

    @Override
    public void createSnapshot(URI storage, List<URI> snapshotList, Boolean createInactive, String opId) throws ControllerException {
    	TaskCompleter completer = null;
    	try {
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockSnapshotCreateCompleter(snapshotList, opId);            
            getDevice(storageObj.getSystemType()).doCreateSnapshot(storageObj, snapshotList, createInactive, completer);
        } catch (Exception e) {
        	if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                completer.error(_dbClient, serviceError);
            } else {
               throw DeviceControllerException.exceptions.createVolumeSnapshotFailed(e);
            }        	
        }
    }

    @Override
    public void activateSnapshot(URI storage, List<URI> snapshotList, String opId)
            throws ControllerException {
    	TaskCompleter completer = null;
    	try {
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockSnapshotActivateCompleter(snapshotList, opId);
            getDevice(storageObj.getSystemType()).doActivateSnapshot(storageObj, snapshotList, completer);
        } catch (Exception e) {
        	if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                completer.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.activateVolumeSnapshotFailed(e);
            }
        }
    }

    @Override
    public void deleteSnapshot(URI storage, URI snapshot, String opId) throws ControllerException {
    	TaskCompleter completer = null;
        try {
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            BlockSnapshot snapObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            completer = BlockSnapshotDeleteCompleter.createCompleter(_dbClient, snapObj, opId);
            getDevice(storageObj.getSystemType()).doDeleteSnapshot(storageObj, snapshot, completer);
        } catch (Exception e) {
        	if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                completer.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.deleteVolumeSnapshotFailed(e);
            }
        }
    }

    private static final String BLOCK_VOLUME_RESTORE_GROUP = "BlockDeviceRestoreVolume";
    private static final String POST_BLOCK_VOLUME_RESTORE_GROUP = "PostBlockDeviceRestoreVolume";

    @Override
    public void restoreVolume(URI storage, URI pool, URI volume, URI snapshot, Boolean updateOpStatus, String opId)
        throws ControllerException {

        SimpleTaskCompleter completer = new SimpleTaskCompleter(BlockSnapshot.class, snapshot, opId);

        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, RESTORE_VOLUME_WF_NAME, false, opId);
            _log.info("Created new restore workflow with operation id {}", opId);

            Volume sourceVolume = _dbClient.queryObject(Volume.class, volume);
            BlockSnapshot blockSnapshot = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, storage);

            String description = String.format("Restore volume %s from snapshot %s", volume, snapshot);
            workflow.createStep(BLOCK_VOLUME_RESTORE_GROUP, description, null,
                    storage, getDeviceType(storage), BlockDeviceController.class,
                    restoreVolumeMethod(storage, pool, volume, snapshot, updateOpStatus),
                    rollbackMethodNullMethod(), null);

            // Skip the step for VMAX3, as restore operation may still be in progress (OPT#476325)
            // Regardless, termination of restore session should be call before restore
            // Note this is not needed for VNX
            if (!system.checkIfVmax3()) {
                addPostRestoreVolumeSteps(workflow, system, sourceVolume, blockSnapshot);
            }

            _log.info("Executing workflow {}", BLOCK_VOLUME_RESTORE_GROUP);

            String msg = String.format("Restore of volume %s from %s completed successfully", volume, snapshot);
            workflow.executePlan(completer, msg);
        } catch(Exception e) {
            String msg = String.format("Could not restore volume %s from snapshot %s", volume, snapshot);
            _log.error(msg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }
    }
    

    /**
     * Return a Workflow.Method for restoreVolume
     * @param storage storage system
     * @param pool storage pool
     * @param volume target of restore operation
     * @param snapshot snapshot to restore from
     * @param updateOpStatus update operation status flag
     * @return Workflow.Method
     */
    public static Workflow.Method restoreVolumeMethod(URI storage, URI pool, URI volume, URI snapshot,
                                                      Boolean updateOpStatus) {
        return new Workflow.Method("restoreVolumeStep", storage, pool, volume, snapshot, updateOpStatus);
    }

    public boolean restoreVolumeStep(URI storage, URI pool, URI volume, URI snapshot, Boolean updateOpStatus, String opId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, storage);
            BlockSnapshot snapObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            completer = new BlockSnapshotRestoreCompleter(snapObj, opId, updateOpStatus);
            getDevice(storageDevice.getSystemType()).doRestoreFromSnapshot(storageDevice, volume, snapshot, completer);
        } catch (Exception e) {
            _log.error(String.format("restoreVolume failed - storage: %s, pool: %s, volume: %s, snapshot: %s",
                    storage.toString(), pool.toString(), volume.toString(), snapshot.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
            doFailTask(BlockSnapshot.class, snapshot, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }
        return true;
    }

    private void addPostRestoreVolumeSteps(Workflow workflow, StorageSystem system, Volume sourceVolume,
            BlockSnapshot blockSnapshot) {
        _log.info("Creating post restore volume steps");
        if (Type.vmax.toString().equalsIgnoreCase(system.getSystemType())) {
            _log.info("Adding terminate restore session post-step for VMAX snapshot {}", blockSnapshot.getId());
            String description = String.format("Terminating VMAX restore session from %s to %s", blockSnapshot.getId(),
                    sourceVolume.getId());
            workflow.createStep(POST_BLOCK_VOLUME_RESTORE_GROUP, description, BLOCK_VOLUME_RESTORE_GROUP,
            system.getId(), system.getSystemType(), BlockDeviceController.class,
            terminateRestoreSessionsMethod(system.getId(), sourceVolume.getId(), blockSnapshot.getId()),
            null, null);
        }
    }
    
    public static Workflow.Method terminateRestoreSessionsMethod(URI storage, URI source, URI snapshot) {
        return new Workflow.Method(TERMINATE_RESTORE_SESSIONS_METHOD, storage, source, snapshot);
    }

    public boolean terminateRestoreSessions(URI storage, URI source, URI snapshot, String opId) {
        _log.info("Terminating restore sessions for snapshot: {}", snapshot);
        TaskCompleter completer = null;
        try {
            StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, storage);
            BlockSnapshot snapObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            completer = new SimpleTaskCompleter(BlockSnapshot.class, snapshot, opId);
            WorkflowStepCompleter.stepExecuting(opId);
            // Synchronous operation
            getDevice(storageDevice.getSystemType()).doTerminateAnyRestoreSessions(storageDevice, source, snapObj,
                    completer);
            completer.ready(_dbClient);
        } catch (Exception e) {
            _log.error(
                    String.format("Terminate restore sessions step failed - storage: %s, volume: %s, snapshot: %s",
                            storage.toString(), source.toString(), snapshot.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
            doFailTask(BlockSnapshot.class, snapshot, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }

        return true;
    }
    

    public Workflow.Method createMirrorMethod(URI storage, URI mirror, Boolean createInactive) {
        return new Workflow.Method("createMirror", storage, mirror, createInactive);
    }

    /**
     * {@inheritDoc}}
     * NOTE NOTE: The signature here MUST match the Workflow.Method createMirrorMethod just above (except opId).
     */
    @Override
    public void createMirror(URI storage, URI mirror, Boolean createInactive, String opId) throws ControllerException {
    	TaskCompleter completer = null;
    	try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockMirrorCreateCompleter(mirror, opId);
            getDevice(storageObj.getSystemType()).doCreateMirror(storageObj, mirror, createInactive, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            if (completer != null) {
                completer.error(_dbClient, serviceError);
            }
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }
    
    public Workflow.Method rollbackMirrorMethod(URI storage, URI mirror) {
        return new Workflow.Method("rollbackMirror", storage, mirror);
    }
    /**
     * {@inheritDoc}}
     * NOTE NOTE: The signature here MUST match the Workflow.Method rollbackMirrorMethod just above (except opId).
     */
    public void rollbackMirror(URI storage, URI mirror, String taskId) {
        WorkflowStepCompleter.stepExecuting(taskId);
        BlockMirror mirrorObj = _dbClient.queryObject(BlockMirror.class, mirror);
        try {
            if (!isNullOrEmpty(mirrorObj.getNativeId())) {
                _log.info("Attempting to detach {} for rollback", mirrorObj.getLabel());
                detachMirror(storage, mirror, taskId);
                _log.info("Attempting to delete {} for rollback", mirrorObj.getLabel());
                deleteMirror(storage, mirror, taskId);
            } else {
                mirrorObj.setInactive(true);
                _dbClient.persistObject(mirrorObj);
            }
            WorkflowStepCompleter.stepSucceded(taskId);
        } catch (InternalException ie) {
            _log.error(String.format("rollbackMirror Failed - Array:%s, Mirror:%s", storage, mirror));
            doFailTask(Volume.class, mirror, taskId, ie);
            WorkflowStepCompleter.stepFailed(taskId, ie);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, asList(mirror), taskId, serviceError);
        }
    }
    
    

    @Override
    public void attachNativeContinuousCopies(URI storage, URI sourceVolume, String opId) throws ControllerException {
        _log.info("START attach continuous copies workflow");

        Workflow workflow = _workflowService.getNewWorkflow(this, ATTACH_MIRRORS_WF_NAME, true, opId);
        TaskCompleter taskCompleter = null;

        Volume sourceVolumeObj = _dbClient.queryObject(Volume.class, sourceVolume);
        StringSet mirrorSet = sourceVolumeObj.getMirrors();
        if (mirrorSet == null || mirrorSet.isEmpty()) {
            _log.info("Volume {} has no mirrors to attach", sourceVolume);
            throw DeviceControllerException.exceptions.attachVolumeMirrorFailed("Volume has no continuous copies defined");
        }
        List<URI> pendingMirrorsURI = new ArrayList<>();
        List<BlockMirror> pendingMirrors = getPendingMirrors(mirrorSet);
        List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();
        for (BlockMirror pendingMirror : pendingMirrors) {
            VolumeDescriptor desc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_MIRROR,
                    pendingMirror.getStorageController(), pendingMirror.getId(), pendingMirror.getPool(), null);
            descriptors.add(desc);
            pendingMirrorsURI.add(pendingMirror.getId());
        }
        try {
            addStepsForCreateMirrors(workflow, null, descriptors);
            taskCompleter = new BlockMirrorTaskCompleter(BlockMirror.class, pendingMirrorsURI, opId);
            workflow.executePlan(taskCompleter, "Successfully attached continuous copies");
        } catch (Exception e) {
            String msg = String.format("Failed to execute attach continuous copies workflow for volume %s",
                    sourceVolume);

            _log.error(msg, e);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    @Override
    public void detachNativeContinuousCopies(URI storage, List<URI> mirrors, List<URI> promotees,
                                             String opId) throws ControllerException {
        _log.info("START detach continuous copies workflow");

        Workflow workflow = _workflowService.getNewWorkflow(this, DETACH_MIRRORS_WF_NAME, false, opId);
        TaskCompleter taskCompleter = null;
        List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();
        NamedURI sourceVolumeURI = null;

        for (URI mirror : mirrors) {
            BlockMirror mirrorObj = _dbClient.queryObject(BlockMirror.class, mirror);
            if (mirrorObj != null && !mirrorObj.getInactive()) {
                sourceVolumeURI = mirrorObj.getSource();
                VolumeDescriptor desc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_MIRROR,
                        mirrorObj.getStorageController(), mirror, mirrorObj.getPool(), null);
                descriptors.add(desc);
            }
        }

        try {
            addStepsForPromoteMirrors(workflow, null, descriptors, promotees);

            // There is a task for the source volume, as well as for each newly promoted volume
            List<URI> volumesWithTasks = new ArrayList<URI>(promotees);
            volumesWithTasks.add(sourceVolumeURI.getURI());
            taskCompleter = new BlockMirrorTaskCompleter(Volume.class, volumesWithTasks, opId);

            workflow.executePlan(taskCompleter, "Successfully detached continuous copies");
        } catch (Exception e) {
            List<Volume> promotedVolumes = _dbClient.queryObject(Volume.class, promotees);
            for (Volume promotedVolume : promotedVolumes) {
                promotedVolume.setInactive(true);
            }
            _dbClient.persistObject(promotedVolumes);

            String msg = String.format("Failed to execute detach continuous copies workflow for mirrors: %s", mirrors);
            _log.error(msg, e);
        }
    }

    public Workflow.Method fractureMirrorMethod(URI storage, URI mirror, Boolean sync) {
        return new Workflow.Method("fractureMirror", storage, mirror, sync);
    }
    /**
     * {@inheritDoc}}
     * NOTE NOTE: The signature here MUST match the Workflow.Method fractureMirrorMethod just above (except opId).
     */
    public void fractureMirror(URI storage, URI mirror, Boolean sync, String opId) throws ControllerException {
    	TaskCompleter completer = null;
        try {
        	WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockMirrorFractureCompleter(mirror, opId);
            getDevice(storageObj.getSystemType()).doFractureMirror(storageObj, mirror, sync, completer);
        } catch (Exception e) {
        	if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                completer.error(_dbClient, serviceError);
            }
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    @Override
    public void pauseNativeContinuousCopies(URI storage, List<URI> mirrors, Boolean sync,
                                            String opId) throws ControllerException {
        _log.info("START pause continuous copies workflow");

        if (mirrors.size() == 1) {
            fractureMirror(storage, mirrors.get(0), sync, opId);
            return;
        }

        Workflow workflow = _workflowService.getNewWorkflow(this, PAUSE_MIRRORS_WF_NAME, false, opId);
        TaskCompleter taskCompleter = null;
        BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, mirrors.get(0));
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);

        try {
            for (URI mirrorUri : mirrors) {
                BlockMirror blockMirror = _dbClient.queryObject(BlockMirror.class, mirrorUri);
                if (!mirrorIsPausable(blockMirror)) {
                    String errorMsg = format("Can not pause continuous copy %s with synchronization state %s for volume %s",
                            blockMirror.getId(), blockMirror.getSyncState(), blockMirror.getSource().getURI());
                    _log.error(errorMsg);
                    String opName = ResourceOperationTypeEnum.PAUSE_NATIVE_CONTINUOUS_COPIES.getName();
                    ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
                    WorkflowStepCompleter.stepFailed(opId, serviceError);
                    throw new IllegalStateException(errorMsg);
                }
                workflow.createStep("pauseMirror", "pause mirror", null, storage, storageObj.getSystemType(),
                        this.getClass(), fractureMirrorMethod(storage, mirrorUri, sync), null, null);
            }

            taskCompleter = new BlockMirrorTaskCompleter(Volume.class, asList(mirror.getSource().getURI()), opId);
            workflow.executePlan(taskCompleter, "Successfully paused continuous copies");
        } catch (Exception e) {
            String msg = String.format("Failed to execute pause continuous copies workflow for volume %s",
                    mirror.getSource().getURI());

            _log.error(msg, e);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    @Override
    public void resumeNativeContinuousCopies(URI storage, List<URI> mirrors, String opId) throws ControllerException {
        _log.info("START resume continuous copies workflow");

        Workflow workflow = _workflowService.getNewWorkflow(this, RESUME_MIRRORS_WF_NAME, false, opId);
        TaskCompleter taskCompleter = null;
        BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, mirrors.get(0));
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);

        try {
            for (URI mirrorUri : mirrors) {
                BlockMirror blockMirror = _dbClient.queryObject(BlockMirror.class, mirrorUri);
                if (SynchronizationState.FRACTURED.toString().equals(blockMirror.getSyncState())) {
                    workflow.createStep("resumeStep", "resume", null, storage, storageObj.getSystemType(),
                            this.getClass(), resumeNativeContinuousCopyMethod(storage, mirrorUri), null, null);
                }
            }
            taskCompleter = new BlockMirrorTaskCompleter(Volume.class, asList(mirror.getSource().getURI()), opId);
            workflow.executePlan(taskCompleter, "Successfully resumed continuous copies");
        } catch (Exception e) {
            String msg = String.format("Failed to execute resume continuous copies workflow for volume %s",
                    mirror.getSource().getURI());

            _log.error(msg, e);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    private Workflow.Method resumeNativeContinuousCopyMethod(URI storage, URI mirror) {
        return new Workflow.Method("resumeNativeContinuousCopy", storage, mirror);
    }

    public void resumeNativeContinuousCopy(URI storage, URI mirror, String opId) throws ControllerException {
        try {
        	WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter completer = new BlockMirrorResumeCompleter(mirror, opId);
            getDevice(storageObj.getSystemType()).doResumeNativeContinuousCopy(storageObj, mirror, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    public Workflow.Method detachMirrorMethod(URI storage, URI mirror) {
        return new Workflow.Method("detachMirror", storage, mirror);
    }

    /**
     * {@inheritDoc}}
     * NOTE NOTE: The signature here MUST match the Workflow.Method detachMirrorMethod just above (except opId).
     */
    @Override
    public void detachMirror(URI storage, URI mirror, String opId) throws ControllerException {
    	TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockMirrorDetachCompleter(mirror, opId);
            getDevice(storageObj.getSystemType()).doDetachMirror(storageObj, mirror, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            if (completer != null) {
                completer.error(_dbClient, serviceError);
            }
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private String addStepsForDetachMirror(Workflow workflow, String waitFor,
                                           String stepGroup, BlockMirror mirror) throws ControllerException {
        URI controller = mirror.getStorageController();
        String stepId = null;

        // Optionally create a step to pause (fracture) the mirror
        if (mirrorIsPausable(mirror)) {

            stepId = workflow.createStep(stepGroup,
                    String.format("Fracture mirror: %s", mirror.getId()),
                    waitFor, controller, getDeviceType(controller),
                    this.getClass(),
                    fractureMirrorMethod(controller, mirror.getId(), false),
                    null, null);
        }

        // Create a step to detach the mirror
        stepId = workflow.createStep(stepGroup,
                String.format("Detach mirror: %s", mirror.getId()),
                stepId == null ? waitFor : stepId,
                controller, getDeviceType(controller),
                this.getClass(),
                detachMirrorMethod(controller, mirror.getId()),
                null, null);

        return stepId;
    }

    public static final String PROMOTE_MIRROR_STEP_GROUP = "BlockDevicePromoteMirror";

    /**
     * Adds the additional steps necessary to promote mirrors to regular block volumes
     *
     * @param workflow
     * @param waitFor
     * @param descriptors
     * @param promotees
     * @return
     * @throws ControllerException
     */
    public String addStepsForPromoteMirrors(Workflow workflow, String waitFor,
                                            List<VolumeDescriptor> descriptors, List<URI> promotees)
            throws ControllerException {
        // Get only the BLOCK_MIRROR descriptors.
        descriptors = VolumeDescriptor.filterByType(descriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_MIRROR }, null);
        if (descriptors.isEmpty()) {
            return waitFor;
        }

        List<URI> uris = VolumeDescriptor.getVolumeURIs(descriptors);
        List<Volume> promotedVolumes = _dbClient.queryObject(Volume.class, promotees);
        Iterator<BlockMirror> mirrorIterator = _dbClient.queryIterativeObjects(BlockMirror.class, uris);
        while (mirrorIterator.hasNext()) {
            BlockMirror mirror = mirrorIterator.next();
            URI controller = mirror.getStorageController();
            String stepId = null;

            // Add steps for detaching the mirror
            stepId = addStepsForDetachMirror(workflow, waitFor, PROMOTE_MIRROR_STEP_GROUP, mirror);

            // Find the volume this mirror will be promoted to
            Volume promotedVolumeForMirror = findPromotedVolumeForMirror(mirror, promotedVolumes);

            // Create a step for promoting the mirror.
            stepId = workflow.createStep(PROMOTE_MIRROR_STEP_GROUP,
                    String.format("Promote mirror: %s", mirror.getId()),
                    stepId, controller, getDeviceType(controller),
                    this.getClass(),
                    promoteMirrorMethod(controller, mirror.getId(), promotedVolumeForMirror.getId()),
                    null, null);
        }

        return PROMOTE_MIRROR_STEP_GROUP;
    }

    private Volume findPromotedVolumeForMirror(BlockMirror mirror, List<Volume> promotedVolumes) {
        for (Volume promotee : promotedVolumes) {
            OpStatusMap statusMap = promotee.getOpStatus();
            for (Map.Entry<String, Operation> entry : statusMap.entrySet()) {
                Operation operation = entry.getValue();
                if (operation.getAssociatedResourcesField().contains(mirror.getId().toString())) {
                    return promotee;
                }
            }
        }
        throw new IllegalStateException("No volume available for the promotion of mirror " + mirror.getId());
    }

    public Workflow.Method promoteMirrorMethod(URI controller, URI id, URI promotedVolumeForMirror) {
        return new Workflow.Method("promoteMirror", controller, id, promotedVolumeForMirror);
    }

    public void promoteMirror(URI storage, URI id, URI promotedVolumeForMirror, String opId) {
        _log.info("START promoteMirror");
        Volume promoted = null;
        try {
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, id);
            Volume source = _dbClient.queryObject(Volume.class, mirror.getSource().getURI());
            String promotedLabel = String.format("%s-%s", source.getLabel(), mirror.getLabel());

            promoted = VolumeFactory.newInstance(mirror);
            promoted.setId(promotedVolumeForMirror);
            promoted.setLabel(promotedLabel);
            _log.info("Promoted mirror {} to volume {}", id, promoted.getId());
            //If there are exports masks/export groups associated, then 
            //remove the mirror from them and add the promoted volume.
            ExportUtils.updatePromotedMirrorExports(mirror, promoted, _dbClient);
            
            mirror.setInactive(true);
            _dbClient.persistObject(mirror);
            _dbClient.persistObject(promoted);
            WorkflowStepCompleter.stepSucceded(opId);
        } catch (Exception e) {
            String msg = String.format("Failed to promote mirror %s", id);
            _log.error(msg, e);
            WorkflowStepCompleter.stepFailed(opId, DeviceControllerException.exceptions.stopVolumeMirrorFailed(id));
        }
    }

    public static final String DELETE_MIRROR_STEP_GROUP = "BlockDeviceDeleteMirror";

    /**
     * Adds the additional steps necessary to delete local mirrors.
     * @param workflow
     * @param waitFor
     * @param descriptors List<VolumeDescriptor> volumes to be processed
     * @return
     */
    public String addStepsForDeleteMirrors(Workflow workflow, String waitFor,
            List<VolumeDescriptor> descriptors)
                    throws ControllerException {
        // Get only the BLOCK_MIRROR descriptors.
        descriptors = VolumeDescriptor.filterByType(descriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_MIRROR }, null);
        if (descriptors.isEmpty()) {
            return waitFor;
        }

        List<URI> uris = VolumeDescriptor.getVolumeURIs(descriptors);
        Iterator<BlockMirror> mirrorIterator = _dbClient.queryIterativeObjects(BlockMirror.class, uris);
        while (mirrorIterator.hasNext()) {
            BlockMirror mirror = mirrorIterator.next();
            URI controller = mirror.getStorageController();
            String stepId = null;

            // Add steps for detaching the mirror
            stepId = addStepsForDetachMirror(workflow, waitFor, DELETE_MIRROR_STEP_GROUP, mirror);
            // Create a step for deleting the mirror.
            stepId = workflow.createStep(DELETE_MIRROR_STEP_GROUP,
                    String.format("Delete mirror: %s", mirror.getId()),
                    stepId, controller, getDeviceType(controller),
                    this.getClass(),
                    deleteMirrorMethod(controller, mirror.getId()),
                    null, null);
        }

        return DELETE_MIRROR_STEP_GROUP;
    }


    public Workflow.Method deleteMirrorMethod(URI storage, URI mirror) {
        return new Workflow.Method("deleteMirror", storage, mirror);
    }

    /**
     * {@inheritDoc}}
     * NOTE NOTE: The signature here MUST match the Workflow.Method deleteMirrorMethod just above (except opId).
     */
    @Override
    public void deleteMirror(URI storage, URI mirror, String opId) throws ControllerException {
    	TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockMirrorDeleteCompleter(mirror, opId);
            getDevice(storageObj.getSystemType()).doDeleteMirror(storageObj, mirror, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            if (completer != null) {
                completer.error(_dbClient, serviceError);
            }
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    @Override
    public void createConsistencyGroup(URI storage, URI consistencyGroup, String opId) throws ControllerException {
    	try {
    		WorkflowStepCompleter.stepExecuting(opId);

    		// Lock the CG for the step duration.
    		List<String> lockKeys = new ArrayList<String>();
    		lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(consistencyGroup, storage));
    		_workflowService.acquireWorkflowStepLocks(opId, lockKeys, LockTimeoutValue.get(LockType.ARRAY_CG));

    		StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
    		TaskCompleter completer = new BlockConsistencyGroupCreateCompleter(consistencyGroup, opId);

    		// Check if already created, if not create, if so just complete.
    		BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroup);
    		if (!cg.created(storage)) {
    			getDevice(storageObj.getSystemType()).doCreateConsistencyGroup(storageObj, consistencyGroup, completer);
    		} else {
    			_log.info(String.format("Consistency group %s (%s) already created", cg.getLabel(), cg.getId()));
    			completer.ready(_dbClient);
    		}
    	} catch (Exception e) {
    		throw DeviceControllerException.exceptions.createConsistencyGroupFailed(e);
    	}
	}

    @Override
	public void deleteConsistencyGroup(URI storage, URI consistencyGroup, Boolean markInactive, String opId) throws ControllerException {
    	TaskCompleter completer = null;
		try {
			WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockConsistencyGroupDeleteCompleter(consistencyGroup, opId);
            getDevice(storageObj.getSystemType()).doDeleteConsistencyGroup(storageObj, consistencyGroup, markInactive, completer);
        } catch (Exception e) {
        	if (completer != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                completer.error(_dbClient, serviceError);
            }
            throw DeviceControllerException.exceptions.deleteConsistencyGroupFailed(e);
        }
	}

    /**
     * An orchestration controller method for detaching and deleting a mirror
     *
     * @param storage       URI of storage controller.
     * @param mirror        URI of block mirror
     * @param opId          Operation ID
     * @throws ControllerException
     */
    @Override
    public void deactivateMirror(URI storage, URI mirror, String opId) throws ControllerException {
        _log.info("deactivateMirror: START");
        TaskCompleter taskCompleter = null;

        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            Workflow workflow = _workflowService.getNewWorkflow(this, "deactivateMirror", true, opId);
            taskCompleter = new BlockMirrorDeactivateCompleter(mirror, opId);

            String detachStep = workflow.createStepId();
            // TODO - check if addStepsForDetachMirror is necessary for other arrays as well
            if (storageSystem.checkIfVmax3()) {
                BlockMirror mirrorObj = _dbClient.queryObject(BlockMirror.class, mirror);
                detachStep = addStepsForDetachMirror(workflow, null, "deactivate", mirrorObj);
            }
            else {
                Workflow.Method detach = new Workflow.Method("detachMirror", storage, mirror);
                workflow.createStep("deactivate", "detaching mirror volume: " + mirror, null, storage,
                        storageSystem.getSystemType(),getClass(), detach, null, detachStep);
            }

            String deleteStep = workflow.createStepId();
            Workflow.Method delete = new Workflow.Method("deleteMirror", storage, mirror);
            workflow.createStep("deactivate", "deleting mirror volume: " + mirror, detachStep, storage,
                    storageSystem.getSystemType(),getClass(), delete, null, deleteStep);

            String successMessage = String.format("Successfully deactivated mirror %s on StorageArray %s",
                    mirror, storage);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            if (_log.isErrorEnabled()) {
            	String msg = String.format("Deactivate mirror failed for mirror %s", mirror);
                _log.error(msg);
            }
            if (taskCompleter != null) {
                String opName = ResourceOperationTypeEnum.DEACTIVATE_VOLUME_MIRROR.getName();
                ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.deactivateMirrorFailed(e);
            }
        }
    }

    static final String FULL_COPY_WORKFLOW = "fullCopyVolumes";
    static final String FULL_COPY_CREATE_STEP_GROUP = "createFullCopiesStepGroup";
    static final String FULL_COPY_WFS_STEP_GROUP = "waitForSyncStepGroup";
    static final String FULL_COPY_DETACH_STEP_GROUP = "detachFullCopyStepGroup";
    static final String FULL_COPY_FRACTURE_STEP_GROUP = "fractureFullCopyStepGroup";

    @Override
    public void createFullCopy(URI storage, List<URI> fullCopyVolumes, Boolean createInactive,
                                String taskId)
            throws ControllerException {
        _log.info("START fullCopyVolumes");
        TaskCompleter taskCompleter = null;
        Volume clone = _dbClient.queryObject(Volume.class, fullCopyVolumes.get(0));
        URI sourceVolume = clone.getAssociatedSourceVolume();
        
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            Workflow workflow = _workflowService.getNewWorkflow(this, FULL_COPY_WORKFLOW, true, taskId);
            boolean isCG = false;
            //check if the clone is in a CG
            if (isCloneInConsistencyGroup(fullCopyVolumes.get(0), _dbClient)) {
                isCG = true;
                _log.info("Creating group full copy");
                Workflow.Method createMethod = createFullCopyVolumeMethod(storage, sourceVolume, fullCopyVolumes, createInactive, isCG);
                workflow.createStep(FULL_COPY_CREATE_STEP_GROUP, "Creating full copy", null, storage,
                        storageSystem.getSystemType(), getClass(), createMethod,
                        null, null);
                
                if (!createInactive) {
                    // After all full copies have been created, wait for synchronization to complete
                    if (!storageSystem.deviceIsType(Type.vnxblock)) {
                        Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, fullCopyVolumes, isCG);
                        String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                                "Waiting for synchronization", FULL_COPY_CREATE_STEP_GROUP, storage,
                                storageSystem.getSystemType(), getClass(), waitForSyncMethod, null, null);
                        setCloneReplicaStateStep(workflow, storageSystem, fullCopyVolumes, waitForSyncStep, ReplicationState.SYNCHRONIZED);
                    } else {
                        String previousStep = FULL_COPY_CREATE_STEP_GROUP;
                        for (URI cloneUri : fullCopyVolumes) {
                            Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage,Arrays.asList(cloneUri), false);
                            String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                                    "Waiting for synchronization", previousStep, storage,
                                    storageSystem.getSystemType(), getClass(), waitForSyncMethod, null, null);
                            previousStep = waitForSyncStep;
                        }    
                            
                        workflow.createStep(FULL_COPY_FRACTURE_STEP_GROUP, "fracture full copy", previousStep,
                                storage, storageSystem.getSystemType(), BlockDeviceController.class,
                                fractureCloneMethod(storage, fullCopyVolumes, isCG), null, null);
                        
                    }
                } 
                
            } else {
                for (URI uri : fullCopyVolumes) {
                    Workflow.Method createMethod = createFullCopyVolumeMethod(storage, sourceVolume, Arrays.asList(uri), createInactive, isCG);
                    Workflow.Method rollbackMethod = rollbackFullCopyVolumeMethod(storage, uri);
                    workflow.createStep(FULL_COPY_CREATE_STEP_GROUP, "Creating full copy", null, storage,
                            storageSystem.getSystemType(), getClass(), createMethod,
                            rollbackMethod, null);
                     if (!createInactive) {
                        // After all full copies have been created, wait for synchronization to complete
                        Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, Arrays.asList(uri), isCG);
                        String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                                "Waiting for synchronization", FULL_COPY_CREATE_STEP_GROUP, storage,
                                storageSystem.getSystemType(), getClass(), waitForSyncMethod, null, null);
                        Volume cloneVol = _dbClient.queryObject(Volume.class, uri);
                        BlockObject sourceObj = BlockObject.fetch(_dbClient, cloneVol.getAssociatedSourceVolume());
                        //detach if source is snapshot, or storage system is not vmax/vnx/hds
                        if (sourceObj instanceof BlockSnapshot 
                                || !(storageSystem.deviceIsType(Type.vmax) || storageSystem.deviceIsType(Type.hds)
                                        ||storageSystem.deviceIsType(Type.vnxblock))) {
                            Workflow.Method detachMethod = detachFullCopyMethod(storage, uri);
                            workflow.createStep(FULL_COPY_DETACH_STEP_GROUP, "Detaching full copy", waitForSyncStep,
                                    storage, storageSystem.getSystemType(), getClass(), detachMethod, null, null);
                        } else if (storageSystem.deviceIsType(Type.vnxblock)) {
                            workflow.createStep(FULL_COPY_FRACTURE_STEP_GROUP, "fracture full copy", waitForSyncStep,
                                    storage, storageSystem.getSystemType(), BlockDeviceController.class,
                                    fractureCloneMethod(storage,Arrays.asList(uri), isCG), null, null);
                        } else {
                            setCloneReplicaStateStep(workflow, storageSystem, asList(uri), waitForSyncStep, ReplicationState.SYNCHRONIZED);
                        }
                    } 
                }
    
            }
            //List<URI> uris = new ArrayList<URI>(fullCopyVolumes);
            taskCompleter = new CloneCreateWorkflowCompleter(fullCopyVolumes, taskId);
            String successMsg = String.format("Full copy of %s to %s successful", sourceVolume, fullCopyVolumes);
            workflow.executePlan(taskCompleter, successMsg);
        } catch (InternalException e) {
            _log.error("Failed to create full copy of volume", e);
            doFailTask(Volume.class, sourceVolume, taskId, e);
            WorkflowStepCompleter.stepFailed(taskId, e);
        } catch (Exception e) {
            _log.error("Failed to create full copy of volume", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            doFailTask(Volume.class, sourceVolume, taskId, serviceError);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
        }
    }

    public Workflow.Method createFullCopyVolumeMethod(URI storage, URI sourceVolume, List<URI> fullCopyVolumes,
                                                      Boolean createInactive, boolean isCG) {
        return new Workflow.Method("createFullCopyVolume", storage, sourceVolume, fullCopyVolumes, createInactive, isCG);
    }

    public void createFullCopyVolume(URI storage, URI sourceVolume, List<URI> fullCopyVolumes, Boolean createInactive, boolean isCG,
                                     String taskId) {
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter taskCompleter = new CloneCreateCompleter(fullCopyVolumes, taskId);
            WorkflowStepCompleter.stepExecuting(taskId);
            if (isCG) {
                getDevice(storageSystem.getSystemType()).doCreateGroupClone(storageSystem, fullCopyVolumes,
                        createInactive, taskCompleter);
            } else {
                getDevice(storageSystem.getSystemType()).doCreateClone(storageSystem, sourceVolume, fullCopyVolumes.get(0),
                        createInactive, taskCompleter);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, fullCopyVolumes, taskId, serviceError);
        }
    }

    public Workflow.Method rollbackFullCopyVolumeMethod(URI storage, URI fullCopy) {
        return new Workflow.Method("rollbackFullCopyVolume", storage, fullCopy);
    }

    public void rollbackFullCopyVolume(URI storage, URI fullCopy, String taskId) {
        WorkflowStepCompleter.stepExecuting(taskId);

        Volume volume = _dbClient.queryObject(Volume.class, fullCopy);
        try {
            if (!isNullOrEmpty(volume.getNativeId())) {
                _log.info("Attempting to detach {} for rollback", volume.getLabel());
                detachFullCopy(storage, asList(fullCopy), taskId);
                _log.info("Attempting to delete {} for rollback", volume.getLabel());
                deleteVolumes(storage, asList(fullCopy), taskId);
            } else {
                volume.setInactive(true);
                _dbClient.persistObject(volume);
            }
            WorkflowStepCompleter.stepSucceded(taskId);
        } catch (InternalException ie) {
            _log.error(String.format("rollbackFullCopyVolume Failed - Array:%s, Volume:%s", storage, fullCopy));
            doFailTask(Volume.class, fullCopy, taskId, ie);
            WorkflowStepCompleter.stepFailed(taskId, ie);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, asList(fullCopy), taskId, serviceError);
        }
    }

    private static final String ACTIVATE_CLONE_WF_NAME = "ACTIVATE_CLONE_WORKFLOW";
    private static final String ACTIVATE_CLONE_GROUP = "BlockDeviceActivateClone";
    @Override
    public void activateFullCopy(URI storage, List<URI> fullCopy, String opId) {
        TaskCompleter completer = new CloneActivateCompleter(fullCopy, opId);
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
           
            if (storageSystem.deviceIsType(Type.vnxblock)) {
                //need to create a workflow to wait sync finish, then do fracture/activate
                Workflow workflow = _workflowService.getNewWorkflow(this, RESYNC_CLONE_WF_NAME, false, opId);
                _log.info("Created new activate workflow with operation id {}", opId);
                boolean isCG = isCloneInConsistencyGroup(fullCopy.get(0), _dbClient);
                String previousStep = null;
                if (isCG ) {
                    for (URI cloneUri : fullCopy) {
                        Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage,Arrays.asList(cloneUri), false);
                        String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                                "Waiting for synchronization", previousStep, storage,
                                storageSystem.getSystemType(), getClass(), waitForSyncMethod, null, null);
                        previousStep = waitForSyncStep;
                    }    
                    
                } else {
                    Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, fullCopy, isCG);
                    String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                            "Waiting for synchronization", previousStep, storage,
                            storageSystem.getSystemType(), getClass(), waitForSyncMethod, null, null);
                    previousStep = waitForSyncStep;
                }
                workflow.createStep(ACTIVATE_CLONE_GROUP, "Activating clone", previousStep,
                        storage, getDeviceType(storage), BlockDeviceController.class,
                        activateCloneMethod(storage, fullCopy), rollbackMethodNullMethod(), null);
                _log.info("Executing workflow");
                String msg = String.format("Actitvate %s completed successfully", fullCopy.get(0));
                    
                completer = new CloneCreateWorkflowCompleter(fullCopy, opId);
       
                workflow.executePlan(completer, msg);
                
            } else {
                activateFullCopyStep(storage, fullCopy, opId);
            }
        } catch(Exception e) {
            String msg = String.format("Could not activate the clone %s", fullCopy.get(0));
            _log.error(msg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }
            
    }
    
    public static Workflow.Method activateCloneMethod(URI storage, List<URI> clone) {
        return new Workflow.Method("activateFullCopyStep", storage, clone);
    }
    
    public void activateFullCopyStep(URI storage, List<URI>fullCopy, String opId) {
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            TaskCompleter taskCompleter = new CloneActivateCompleter(fullCopy, opId);
            if (isCloneInConsistencyGroup(fullCopy.get(0), _dbClient)) {
                getDevice(storageSystem.getSystemType()).doActivateGroupFullCopy(storageSystem, fullCopy, taskCompleter);
               
            } else {
                getDevice(storageSystem.getSystemType()).doActivateFullCopy(storageSystem, fullCopy.get(0), taskCompleter);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            doFailTask(Volume.class, fullCopy, opId, serviceError);
        }
    }

    public Workflow.Method detachFullCopyMethod(URI storage, URI fullCopyVolume) {
        return new Workflow.Method("detachFullCopy", storage, Arrays.asList(fullCopyVolume));
    }

    @Override
    public void detachFullCopy(URI storage, List<URI> fullCopyVolume, String taskId)
            throws ControllerException {
        _log.info("START detachFullCopy: {}", fullCopyVolume);
        
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            if (isCloneInConsistencyGroup(fullCopyVolume.get(0), _dbClient)) {
                _log.info("detach group full copy");
                TaskCompleter taskCompleter = new VolumeDetachCloneCompleter(fullCopyVolume, taskId);
                getDevice(storageSystem.getSystemType()).doDetachGroupClone(storageSystem, fullCopyVolume, taskCompleter);
               
            } else {   
                TaskCompleter taskCompleter = new VolumeDetachCloneCompleter(fullCopyVolume, taskId);
                getDevice(storageSystem.getSystemType()).doDetachClone(storageSystem, fullCopyVolume.get(0),
                        taskCompleter);
            }
        } catch (Exception e) {
        	ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(taskId, serviceError);
            doFailTask(Volume.class, fullCopyVolume, taskId, serviceError);
        }
    }

    @Override
    public Integer checkSyncProgress(URI storage, URI source, URI target, String task) {
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            return getDevice(storageSystem.getSystemType()).checkSyncProgress(storage, source, target);
        } catch (Exception e) {
            String msg = String.format("Failed to check synchronization progress for %s", target);
            _log.error(msg, e);
        }
        return null;
    }

    /**
     * Creates a connection to monitor events generated by the storage
     * identified by the passed URI.
     *
     * @param storage A database client URI that identifies the storage to be
     *        monitored.
     *
     * @throws ControllerException When errors occur connecting the storage for
     *         event monitoring.
     */
    @Override
    public void connectStorage(URI storage) throws ControllerException {
        // Retrieve the storage device info from the database.
        StorageSystem storageObj = null;
        try {
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        } catch (Exception e) {
            throw DeviceControllerException.exceptions.connectStorageFailedDb(e);
        }
        // Verify non-null storage device returned from the database client.
        if (storageObj == null) {
            throw DeviceControllerException.exceptions.connectStorageFailedNull();
        }
        // Get the block device reference for the type of block device managed
        // by the controller.
        BlockStorageDevice storageDevice = getDevice(storageObj.getSystemType());
        if (storageDevice == null) {
            throw DeviceControllerException.exceptions.connectStorageFailedNoDevice(
                    storageObj.getSystemType());
        }
        storageDevice.doConnect(storageObj);
        _log.info("Adding to storage device to work pool: {}", storageObj.getId());

    }

    /**
     * Removes a connection that was previously established for monitoring
     * events from the storage identified by the passed URI.
     *
     * @param storage A database client URI that identifies the storage to be
     *        disconnected.
     *
     * @throws ControllerException When errors occur disconnecting the storage
     *         for event monitoring.
     */
    @Override
    public void disconnectStorage(URI storage) throws ControllerException {
        // Retrieve the storage device info from the database.
        StorageSystem storageObj = null;
        try {
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        } catch (Exception e) {
            throw DeviceControllerException.exceptions.disconnectStorageFailedDb(e);
        }

        // Verify non-null storage device returned from the database client.
        if (storageObj == null) {
            throw DeviceControllerException.exceptions.disconnectStorageFailedNull();
        }

        // Get the block device reference for the type of block device managed
        // by the controller.
        BlockStorageDevice storageDevice = getDevice(storageObj.getSystemType());
        if (storageDevice == null) {
            throw DeviceControllerException.exceptions.disconnectStorageFailedNull();
        }

        storageDevice.doDisconnect(storageObj);
        _log.info("Removing storage device from work pool: {}", storageObj.getId());
    }

    /**
     * {@inheritDoc}}
     */
    @Override
    public void discoverStorageSystem(AsyncTask[] tasks)
            throws ControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    /**
     * {@inheritDoc}}
     */
    @Override
    public void scanStorageProviders(AsyncTask[] tasks)
            throws ControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    private String addStorageToSMIS(StorageSystem storageSystem, StorageProvider provider)
               throws DataBindingException, ControllerException {
        String system = "";
        if( provider != null)    {
            //Populate provider info. This information normally corresponds to the active provider.
            //Do not persist system information in the
            storageSystem.setSmisPassword(provider.getPassword());
            storageSystem.setSmisUserName(provider.getUserName());
            storageSystem.setSmisPortNumber(provider.getPortNumber());
            storageSystem.setSmisProviderIP(provider.getIPAddress());
            storageSystem.setSmisUseSSL(provider.getUseSSL());
            system = getDevice(storageSystem.getSystemType()).doAddStorageSystem(storageSystem);
            _log.info("Storage is added to the SMI-S provider : " + provider.getProviderID());
        }
        return system;
    }


    private boolean scanProvider(StorageProvider provider, StorageSystem storageSystem,
                                  boolean activeProvider, String opId) throws DatabaseException,
                                                                                           BaseCollectionException,
                                                                                           ControllerException{

        Map<String, StorageSystemViewObject>  storageCache = new HashMap<String, StorageSystemViewObject>();
        _dbClient.createTaskOpStatus(StorageProvider.class, provider.getId(), opId,
                                                    ResourceOperationTypeEnum.SCAN_SMISPROVIDER);
        ScanTaskCompleter scanCompleter =  new ScanTaskCompleter(StorageProvider.class, provider.getId(), opId);
        try {
            scanCompleter.statusPending(_dbClient, "Scan for storage system is Initiated");
            provider.setLastScanStatusMessage("");
            _dbClient.persistObject(provider);
            ControllerServiceImpl.performScan(provider.getId(), scanCompleter, storageCache);
            scanCompleter.statusReady(_dbClient, "Scan for storage system has completed");
        }
        catch (Exception ex) {
            _log.error("Scan failed for {}--->", provider, ex);
            scanCompleter.statusError(_dbClient, DeviceControllerErrors.dataCollectionErrors.scanFailed(ex));
            throw DeviceControllerException.exceptions.scanProviderFailed(storageSystem.getNativeGuid(),
                    provider.getId().toString());
        }
        if (!storageCache.containsKey(storageSystem.getNativeGuid())) {
            return false;            
        }
        else {            
            StorageSystemViewObject vo = storageCache.get(storageSystem.getNativeGuid());

            String model = vo.getProperty(StorageSystemViewObject.MODEL);
            if (StringUtils.isNotBlank(model)) {
                storageSystem.setModel(model);
            }
            String serialNo = vo.getProperty(StorageSystemViewObject.SERIAL_NUMBER);
            if (StringUtils.isNotBlank(serialNo)) {
                storageSystem.setSerialNumber(serialNo);
            }
            String version = vo.getProperty(StorageSystemViewObject.VERSION);
            if (StringUtils.isNotBlank(version)) {
                storageSystem.setMajorVersion(version);
            }
            String name = vo.getProperty(StorageSystemViewObject.STORAGE_NAME);
            if (StringUtils.isNotBlank(name)) {
                storageSystem.setLabel(name);
            }
            provider.addStorageSystem(_dbClient, storageSystem, activeProvider);
            return true;
        }
    }


    /**
     * {@inheritDoc}}
     * @throws WorkflowException
     */
    @Override
    public void addStorageSystem(URI storage, URI[] providers, boolean activeProvider, String opId) throws ControllerException {

        if(providers == null)
            return;
        String allProviders = Joiner.on("\t").join(providers);

        DiscoverTaskCompleter completer =  new DiscoverTaskCompleter(StorageSystem.class,storage,opId, ControllerServiceImpl.DISCOVERY);
        StringBuilder failedProviders = new StringBuilder();
        boolean exceptionIntercepted = false;
        boolean needDiscovery = false;

        boolean acquiredLock = false;
        try {
            acquiredLock = ControllerServiceImpl.Lock.SCAN_COLLECTION_LOCK.acquire(SCAN_LOCK_TIMEOUT);
        }
        catch (Exception ex) {
            _log .error("Exception while acquiring a lock ", ex);
            acquiredLock = false;
        }

        if (acquiredLock) {
            try {
                StorageSystem storageSystem  = _dbClient.queryObject(StorageSystem.class,storage);

                for (int ii = 0; ii < providers.length;  ii++ ) {
                    try {
                        StorageProvider providerSMIS   = _dbClient.queryObject(StorageProvider.class, providers[ii]);

                        if (providerSMIS==null) {
                            throw DeviceControllerException.exceptions.entityNullOrEmpty(null);
                        }
                        if (providerSMIS.getInactive()) {
                            throw DeviceControllerException.exceptions.entityInactive(providerSMIS.getId());
                        }
                        boolean found = scanProvider(providerSMIS, storageSystem, activeProvider && ii==0, opId);
                        if (!found) {
                            if (storageSystem.getSystemType().equals(Type.vnxblock.toString()) &&
                               StringUtils.isNotBlank(storageSystem.getIpAddress())) {
                                String system = addStorageToSMIS(storageSystem, providerSMIS);
                                if (!system.equalsIgnoreCase(storageSystem.getNativeGuid())) {
                                    throw DeviceControllerException.exceptions.addStorageSystemFailed(storageSystem.getNativeGuid(),
                                            providerSMIS.getId().toString());
                                }
                                providerSMIS.addStorageSystem(_dbClient, storageSystem, activeProvider && ii==0);
                                if (activeProvider && ii==0) {
                                    providerSMIS.removeDecommissionedSystem(_dbClient, storageSystem.getNativeGuid());
                                }
                                storageSystem.setReachableStatus(true);
                                _dbClient.persistObject(storageSystem);
                            }
                            else {
                                throw DeviceControllerException.exceptions.scanFailedToFindSystem(providerSMIS.getId().toString(),
                                        storageSystem.getNativeGuid());
                            }
                        }
                        else {
                            storageSystem.setReachableStatus(true);
                            _dbClient.persistObject(storageSystem);
                        }
                        if (providers.length > 1) {
                            completer.statusPending(_dbClient,
                                    "Adding storage to SMIS Providers : completed " + (ii + 1) + " providers out of " + providers.length);
                        }
                    }
                    catch (Exception ex) {// any type of exceptions for a particular provider
                        _log .error("Failed to add storage from the following provider: " + providers[ii], ex);
                        failedProviders.append(providers[ii]).append(' ');
                        exceptionIntercepted = true;
                    }
                }
                if (activeProvider) {
                    updateActiveProvider(storageSystem);
                    _dbClient.persistObject(storageSystem);
                }

                DecommissionedResource.removeDecommissionedFlag(_dbClient, storageSystem.getNativeGuid(), StorageSystem.class);

                if (exceptionIntercepted) {
                    String opName = ResourceOperationTypeEnum.ADD_STORAGE_SYSTEM.getName();
                    ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
                    completer.error(_dbClient, serviceError);
                }
                else {
                    recordBourneStorageEvent( RecordableEventManager.EventType.StorageDeviceAdded,
                                                  storageSystem, "Added SMI-S Storage");
                    _log.info("Storage is added to the SMI-S providers: ");
                    if (activeProvider) {
                        needDiscovery = true;

                        storageSystem.setLastDiscoveryRunTime(new Long(0));
                        completer.statusPending(_dbClient,
                                "Storage is added to the specified SMI-S providers : " + allProviders);
                        // We need to set timer back to 0 to indicate that it is a new system ready for discovery.
                        _dbClient.persistObject(storageSystem);
                    }
                    else {
                        completer.ready(_dbClient);
                    }
                }
            } catch (Exception outEx) {
                exceptionIntercepted = true;
                _log.error("Failed to add SMIS providers", outEx);
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(outEx);
                completer.error(_dbClient, serviceError);
            }
            finally {
                try {
                    ControllerServiceImpl.Lock.SCAN_COLLECTION_LOCK.release();
                }
                catch (Exception ex) {
                    _log.error("Failed to release SCAN lock; scanning might become disabled", ex);
                }
                if (needDiscovery && !exceptionIntercepted) {
                    try {
                        ControllerServiceImpl.scheduleDiscoverJobs(
                                new AsyncTask[]{new AsyncTask(StorageSystem.class,storage,opId)},
                                Lock.DISCOVER_COLLECTION_LOCK, ControllerServiceImpl.DISCOVERY);
                    }
                    catch (Exception ex) {
                        _log.error("Failed to start discovery : " + storage, ex);
                    }
                }

            }
        }
        else {
            String opName = ResourceOperationTypeEnum.ADD_STORAGE_SYSTEM.getName();
            ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
            completer.error(_dbClient, serviceError);
            _log.debug("Not able to Acquire Scanning lock-->{}", Thread.currentThread().getId());
        }
    }

    private void recordBourneStorageEvent(RecordableEventManager.EventType evtType, StorageSystem storage, String desc) {

        RecordableBourneEvent event = ControllerUtils
                .convertToRecordableBourneEvent(storage, evtType.toString(),
                        desc, "", _dbClient,
                        ControllerUtils.BLOCK_EVENT_SERVICE,
                        RecordType.Event.name(),
                        ControllerUtils.BLOCK_EVENT_SOURCE);

        try {
            _eventManager.recordEvents(event);
            _log.info("Bourne {} event recorded", evtType.name());
        } catch (Throwable th) {
            _log.error(
                    "Failed to record event. Event description: {}. Error: ",
                    evtType.name(), th);
        }
    }

    private void updateActiveProvider(StorageSystem storage) throws DatabaseException {
        if (storage.getActiveProviderURI() == null)
           return;
        StorageProvider mainProvider = _dbClient.queryObject(StorageProvider.class, storage.getActiveProviderURI());
        if (mainProvider != null) {
            storage.setSmisPassword(mainProvider.getPassword());
            storage.setSmisUserName(mainProvider.getUserName());
            storage.setSmisPortNumber(mainProvider.getPortNumber());
            storage.setSmisProviderIP(mainProvider.getIPAddress());
            storage.setSmisUseSSL(mainProvider.getUseSSL());
            _dbClient.persistObject(storage);
        }
    }

    @Override
    public void startMonitoring(AsyncTask task, Type deviceType)
            throws ControllerException {
        // TODO Auto-generated method stub
    }

    public <T extends BlockObject> Workflow.Method waitForSynchronizedMethod(Class clazz, URI storage, List<URI> target, boolean isCG) {
        return new Workflow.Method("waitForSynchronized", clazz, storage, target, isCG);
    }

    public void waitForSynchronized(Class<? extends BlockObject> clazz, URI storage, List<URI> target, boolean isCG, String opId)
            throws ControllerException {
        _log.info("START waitForSynchronized for {}", target);
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            completer = new BlockWaitForSynchronizedCompleter(clazz, target, opId);
            if (!isCG) {
                getDevice(storageObj.getSystemType()).doWaitForSynchronized(clazz, storageObj, target.get(0), completer);
            } else {
                getDevice(storageObj.getSystemType()).doWaitForGroupSynchronized(storageObj, target, completer);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            if (completer != null) {
                completer.error(_dbClient, serviceError);
            }
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    /**
     * Get the deviceType for a StorageSystem.
     * @param deviceURI -- StorageSystem URI
     * @return deviceType String
     */
    private String getDeviceType(URI deviceURI) throws ControllerException {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, deviceURI);
        if (storageSystem == null)
            throw DeviceControllerException.exceptions.getDeviceTypeFailed(deviceURI.toString());
        return storageSystem.getSystemType();
    }

    /**
     * Checks if a given BlockMirror is applicable for pausing.
     * A mirror is valid for pausing if it is
     * 1) Not null
     * 2) Has valid sync state integer value
     * 3) Sync state is not already fractured (paused) and not resynchronizing
     *
     * @param mirror The BlockMirror to test
     * @return true, if mirror is applicable for a pause operation
     */
    private boolean mirrorIsPausable(BlockMirror mirror) {
        try {
            return mirror != null &&
                    mirror.getInactive() == false &&
                    !SynchronizationState.FRACTURED.toString().equals(mirror.getSyncState()) &&
                    !SynchronizationState.RESYNCHRONIZING.toString().equals(mirror.getSyncState());
        } catch (NumberFormatException nfe) {
            _log.warn("Failed to parse sync state ({}) for mirror {}", mirror.getSyncState(), mirror.getId());
        }
        return false;
    }

    /**
     * Pending mirrors are mirrors that are pending creation on the physical array
     * @param mirrorURIs
     * @return list of active mirrors, waiting to be created
     */
    private List<BlockMirror> getPendingMirrors(StringSet mirrorURIs) {
        List<BlockMirror> result = new ArrayList<BlockMirror>();

        for (String uri : mirrorURIs) {
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, URI.create(uri));
            if (isPending(mirror)) {
                result.add(mirror);
            }
        }
        return result;
    }

    /**
     * Check if a mirror exists in ViPR as an active model and is pending creation on the
     * storage array.
     *
     * @param mirror
     * @return true if the mirror is pending creation
     */
    private boolean isPending(BlockMirror mirror) {
        return !isInactive(mirror) && isNullOrEmpty(mirror.getSynchronizedInstance());
    }

    private boolean isInactive(BlockMirror mirror) {
        return mirror == null || (mirror.getInactive() != null && mirror.getInactive());
    }

	@Override
	public void noActionRollBackStep(URI deviceURI, String opID) {
		_log.info("Running empty Roll back step for storage system {}", deviceURI);
		WorkflowStepCompleter.stepSucceded(opID);

	}
	
    
    @Override
    public void updateConsistencyGroup(URI storage, URI consistencyGroup,
                                       List<URI> addVolumesList,
                                       List<URI> removeVolumesList, String task) {
        TaskCompleter completer = new BlockConsistencyGroupUpdateCompleter(consistencyGroup, task);
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            BlockStorageDevice device = getDevice(storageSystem.getSystemType());
            if (addVolumesList != null && !addVolumesList.isEmpty()) {
                device.doAddToConsistencyGroup(storageSystem, consistencyGroup,addVolumesList, completer);
            }
            if (removeVolumesList != null && !removeVolumesList.isEmpty()) {
                device.doRemoveFromConsistencyGroup(storageSystem, consistencyGroup,removeVolumesList, completer);
            }
        } catch (Exception e) {
            completer.error(_dbClient, DeviceControllerException.exceptions.failedToUpdateConsistencyGroup(e.getMessage()));
        }
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress,
            Integer portNumber, String interfaceType) {
        List<String> systemType = InterfaceType
                .getSystemTypesForInterfaceType(InterfaceType
                        .valueOf(interfaceType));
        return getDevice(systemType.get(0)).validateStorageProviderConnection(
                ipAddress, portNumber);
    }

    @Override
    public String addStepsForPostDeleteVolumes(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumes, String taskId,
            VolumeWorkflowCompleter completer) {
        // Nothing to do, no steps to add
        return waitFor;
    }

    @Override
    public String addStepsForChangeVirtualPool(Workflow workflow,
            String waitFor, List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }
    
    @Override
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor,
        List<VolumeDescriptor> volumes, String taskId) throws InternalException {
        // Nothing to do, no steps to add
        return waitFor;
    }
    
    public static boolean isCloneInConsistencyGroup(URI cloneUri, DbClient dbClient) {
        boolean isCG = false;
        
        Volume clone = dbClient.queryObject(Volume.class, cloneUri);
        if (clone != null) {
            URI source = clone.getAssociatedSourceVolume();
            BlockObject sourceObj = BlockObject.fetch(dbClient, source);
            if (sourceObj instanceof BlockSnapshot) {
                return isCG;
            }
            Volume sourceVolume = (Volume)sourceObj;
            if (!NullColumnValueGetter.isNullURI(sourceVolume.getConsistencyGroup())) {
                final URI cgId = sourceVolume.getConsistencyGroup();
                if (cgId != null) {
                    final BlockConsistencyGroup group = dbClient.queryObject(
                            BlockConsistencyGroup.class, cgId);
                    isCG = group != null;
                }
            }
        }
        
        return isCG;
    }

    private static final String RESTORE_FROM_CLONE_WF_NAME = "RESTORE_FROM_CLONE_WORKFLOW";
    private static final String RESTORE_FROM_CLONE_GROUP = "BlockDeviceRestoreFromClone";
    private static final String FRACTURE_CLONE_GROUP = "PostBlockDeviceFractureClone";
    
    @Override
    public void restoreFromFullCopy(URI storage, List<URI> clones,
            Boolean updateOpStatus, String opId) throws InternalException {
        CloneCreateWorkflowCompleter completer = null;
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, RESTORE_FROM_CLONE_WF_NAME, false, opId);
            _log.info("Created new restore workflow with operation id {}", opId);

            StorageSystem system = _dbClient.queryObject(StorageSystem.class, storage);
    
            String description = String.format("Restore volume from %s", clones.get(0));
            boolean isCG = isCloneInConsistencyGroup(clones.get(0), _dbClient);
            workflow.createStep(RESTORE_FROM_CLONE_GROUP, description, null,
                    storage, getDeviceType(storage), BlockDeviceController.class,
                    restoreFromCloneMethod(storage, clones, updateOpStatus, isCG),
                    rollbackMethodNullMethod(), null);
                
            String previousStep = RESTORE_FROM_CLONE_GROUP;
            if (isCG && system.deviceIsType(Type.vnxblock)) {
                for (URI cloneUri : clones) {
                    Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage,Arrays.asList(cloneUri), false);
                    String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                            "Waiting for synchronization", previousStep, storage,
                            system.getSystemType(), getClass(), waitForSyncMethod, null, null);
                    previousStep = waitForSyncStep;
                }    
                    
            } else {
                Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, clones, isCG);
                String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                        "Waiting for synchronization", RESTORE_FROM_CLONE_GROUP, storage,
                         system.getSystemType(), getClass(), waitForSyncMethod, null, null);
                previousStep = waitForSyncStep;
            }
            if (system.deviceIsType(Type.vmax) || system.deviceIsType(Type.vnxblock)) { 
                addFractureSteps(workflow, system, clones, previousStep, isCG);
            }
            _log.info("Executing workflow {}", RESTORE_FROM_CLONE_GROUP);
            String msg = String.format("Restore from %s completed successfully", clones.get(0));
            
            completer = new CloneCreateWorkflowCompleter(clones, opId);
   
            workflow.executePlan(completer, msg);

        } catch(Exception e) {
            String msg = String.format("Could not restore from the clone %s", clones.get(0));
            _log.error(msg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }
        
    }
    
    /**
     * Return a Workflow.Method for restoreVolume
     * @param storage storage system
     * @param pool storage pool
     * @param volume target of restore operation
     * @param snapshot snapshot to restore from
     * @param updateOpStatus update operation status flag
     * @return Workflow.Method
     */
    public static Workflow.Method restoreFromCloneMethod(URI storage, List<URI> clone, Boolean updateOpStatus, boolean isCG) {
        return new Workflow.Method("restoreFromCloneStep", storage, clone, updateOpStatus, isCG);
    }

    public boolean restoreFromCloneStep(URI storage, List<URI> clones, Boolean updateOpStatus, boolean isCG, String opId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, storage);
            if (!isCG) {
                completer = new CloneRestoreCompleter(clones.get(0), opId);
                getDevice(storageDevice.getSystemType()).doRestoreFromClone(storageDevice, clones.get(0), completer);
            } else {
                CloneRestoreCompleter taskCompleter = new CloneRestoreCompleter(clones, opId);
                getDevice(storageDevice.getSystemType()).doRestoreFromGroupClone(storageDevice, clones, taskCompleter);
            }
            
        } catch (Exception e) {
            _log.error(String.format("restoreFromClone failed - storage: %s,clone: %s",
                    storage.toString(),clones.get(0).toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
            doFailTask(Volume.class, clones, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }
        return true;
    }

    private void addFractureSteps(Workflow workflow, StorageSystem system, List<URI> clone, String previousStep, boolean isCG) {
        
        _log.info("Adding fracture restore post-step for clone {}", clone.toString());
        String description = String.format("Fracture clone %s", clone.toString());
        workflow.createStep(FRACTURE_CLONE_GROUP, description, previousStep,
                    system.getId(), system.getSystemType(), BlockDeviceController.class,
                    fractureCloneMethod(system.getId(), clone, isCG),
                    null, null);
            
        
    }

    
    public static Workflow.Method fractureCloneMethod(URI storage, List<URI> clone, boolean isCG) {
        return new Workflow.Method(FRACTURE_CLONE_METHOD, storage, clone, isCG);
    }

    public boolean fractureClone(URI storage, List<URI> clone, boolean isCG, String opId) {
        _log.info("Fracture clone: {}", clone.get(0));
        TaskCompleter completer = null;
        try {
            StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, storage);
            WorkflowStepCompleter.stepExecuting(opId);
            if (!isCG) {
                Volume cloneVol = _dbClient.queryObject(Volume.class, clone.get(0));
                completer = new CloneFractureCompleter(clone.get(0), opId);
                WorkflowStepCompleter.stepExecuting(opId);
                // Synchronous operation
                getDevice(storageDevice.getSystemType()).doFractureClone(storageDevice, cloneVol.getAssociatedSourceVolume(), clone.get(0), completer);
            } else {
                _log.info("Fracture group clone.");
                completer = new CloneFractureCompleter(clone, opId);
                WorkflowStepCompleter.stepExecuting(opId);
                // Synchronous operation
                getDevice(storageDevice.getSystemType()).doFractureGroupClone(storageDevice, clone, completer);
            }
        } catch (Exception e) {
            _log.error(
                    String.format("Fracture restore sessions step failed - storage: %s, clone: %s",
                            storage.toString(), clone.toString()));
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
            doFailTask(Volume.class, clone, opId, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            return false;
        }

        return true;
    }
    
    private static final String RESYNC_CLONE_WF_NAME = "RESYNC_CLONE_WORKFLOW";
    private static final String RESYNC_CLONE_GROUP = "BlockDeviceResyncClone";
    
    @Override
    public void resyncFullCopy(URI storage, List<URI> clones,
            Boolean updateOpStatus, String opId) throws InternalException {
        CloneCreateWorkflowCompleter completer = null;
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, RESYNC_CLONE_WF_NAME, false, opId);
            _log.info("Created new resync workflow with operation id {}", opId);
            boolean isCG = isCloneInConsistencyGroup(clones.get(0), _dbClient);
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, storage);
                 String description = String.format("Resync clone %s", clones.get(0));
            workflow.createStep(RESYNC_CLONE_GROUP, description, null,
                    storage, getDeviceType(storage), BlockDeviceController.class,
                    resyncCloneMethod(storage, clones, updateOpStatus, isCG),
                    rollbackMethodNullMethod(), null);
            String previousStep = RESYNC_CLONE_GROUP;
            if (isCG && system.deviceIsType(Type.vnxblock)) {
                for (URI cloneUri : clones) {
                    Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage,Arrays.asList(cloneUri), false);
                    String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                            "Waiting for synchronization", previousStep, storage,
                            system.getSystemType(), getClass(), waitForSyncMethod, null, null);
                    previousStep = waitForSyncStep;
                }    
                
            } else {
                Workflow.Method waitForSyncMethod = waitForSynchronizedMethod(Volume.class, storage, clones, isCG);
                String waitForSyncStep = workflow.createStep(FULL_COPY_WFS_STEP_GROUP,
                        "Waiting for synchronization", previousStep, storage,
                        system.getSystemType(), getClass(), waitForSyncMethod, null, null);
                previousStep = waitForSyncStep;
            }
            if (system.deviceIsType(Type.vnxblock)) {
                addFractureSteps(workflow, system, clones, previousStep, isCG);
            } else {
                setCloneReplicaStateStep(workflow, system, clones, previousStep, ReplicationState.SYNCHRONIZED);
            }
            _log.info("Executing workflow {}", RESYNC_CLONE_GROUP);
            String msg = String.format("Resync %s completed successfully", clones.get(0));
                
            completer = new CloneCreateWorkflowCompleter(clones, opId);
   
            workflow.executePlan(completer, msg);

        } catch(Exception e) {
            String msg = String.format("Could not resync the clone %s", clones.get(0));
            _log.error(msg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }
        
    }
    
    /**
     * Return a Workflow.Method for resync
     * @param storage storage system
     * @param clone list of clones
     * @param updateOpStatus update operation status flag
     * @return Workflow.Method
     */
    public static Workflow.Method resyncCloneMethod(URI storage, List<URI> clone, Boolean updateOpStatus, boolean isCG) {
        return new Workflow.Method("resyncFullCopyStep", storage, clone, updateOpStatus, isCG);
    }

    

    public boolean resyncFullCopyStep(URI storage, List<URI> clone, Boolean updateOpStatus, boolean isCG, String opId)
                        throws ControllerException {
        _log.info("Start resync full copy");
        CloneResyncCompleter taskCompleter = null;
        try {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
            taskCompleter = new CloneResyncCompleter(clone, opId);
            if (isCloneInConsistencyGroup(clone.get(0), _dbClient)) {
                _log.info("resync group full copy");
                getDevice(storageSystem.getSystemType()).doResyncGroupClone(storageSystem, clone, taskCompleter);               
            } else {
                getDevice(storageSystem.getSystemType()).doResyncClone(storageSystem, clone.get(0), taskCompleter);
            }
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
            doFailTask(Volume.class, clone, opId, serviceError);
            return false;
        }
        return true;
    }
    
    
    private void setCloneReplicaStateStep(Workflow workflow,StorageSystem system, List<URI> clones, String previousStep, ReplicationState state) {
        _log.info("Setting the clone replica state.");
        workflow.createStep("SET_FINAL_REPLICA_STATE", "Set the clones replica state", previousStep,
                    system.getId(), system.getSystemType(), BlockDeviceController.class,
                    setCloneStateMethod(clones, state), null, null); 
        
    }

    private final static String SET_CLONE_STATE_METHOD="setCloneState";
    public static Workflow.Method setCloneStateMethod(List<URI> clone, ReplicationState state) {
        return new Workflow.Method(SET_CLONE_STATE_METHOD, clone, state);
    }

    public void setCloneState(List<URI> clones, ReplicationState state, String opId) {
        _log.info("Set clones state");
        List<Volume> cloneVols = _dbClient.queryObject(Volume.class, clones);
        for (Volume cloneVol : cloneVols) {
            cloneVol.setReplicaState(state.name());
        }
        _dbClient.persistObject(cloneVols);
        CloneCreateWorkflowCompleter completer = new CloneCreateWorkflowCompleter(clones, opId);
        completer.ready(_dbClient);
    }
   
}
