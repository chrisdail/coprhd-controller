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

package com.emc.storageos.db.client.constraint;

import java.net.URI;
import java.util.Date;

import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.ContainmentConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.TimedContainmentConstraintImpl;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.*;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.WorkflowStep;

/**
 * Containment constraint.   For example, relationship between StorageDevice and
 * Volume can be queried using this constraint.
 */
public interface ContainmentConstraint extends Constraint {
    /**
     * Factory for creating containment constraints for various relationships
     */
    public static class Factory {        
        public static ContainmentConstraint getTenantOrgProjectConstraint(URI tenantOrg) {
            DataObjectType doType = TypeMap.getDoType(Project.class);
            ColumnField field = doType.getColumnField("tenantOrg");
            return new ContainmentConstraintImpl(tenantOrg, Project.class, field);
        }
        
        public static ContainmentConstraint getTenantOrgSubTenantConstraint(URI tenantOrg) {
            DataObjectType doType = TypeMap.getDoType(TenantOrg.class);
            ColumnField field = doType.getColumnField("parentTenant");
            return new ContainmentConstraintImpl(tenantOrg, TenantOrg.class, field);
        }

        public static ContainmentConstraint getProjectVolumeConstraint(URI project) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            ColumnField field = doType.getColumnField("project");
            return new ContainmentConstraintImpl(project, Volume.class, field);
        }

        public static ContainmentConstraint getStoragePoolVolumeConstraint(URI pool) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            ColumnField field = doType.getColumnField("pool");
            return new ContainmentConstraintImpl(pool, Volume.class, field);
        }
        
        public static ContainmentConstraint getVirtualArrayVolumeConstraint(URI varray) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            ColumnField field = doType.getColumnField("varray");
            return new ContainmentConstraintImpl(varray, Volume.class, field);
        }

        public static ContainmentConstraint getVirtualPoolVolumeConstraint(URI vpool) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            ColumnField field = doType.getColumnField("virtualPool");
            return new ContainmentConstraintImpl(vpool, Volume.class, field);
        }

        public static ContainmentConstraint getStorageDeviceStorageHADomainConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(StorageHADomain.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(device, StorageHADomain.class, field);
        }

        public static ContainmentConstraint getProjectFileshareConstraint(URI project) {
            DataObjectType doType = TypeMap.getDoType(FileShare.class);
            ColumnField field = doType.getColumnField("project");
            return new ContainmentConstraintImpl(project, FileShare.class, field);
        }

        public static ContainmentConstraint getStoragePoolFileshareConstraint(URI pool) {
            DataObjectType doType = TypeMap.getDoType(FileShare.class);
            ColumnField field = doType.getColumnField("pool");
            return new ContainmentConstraintImpl(pool, FileShare.class, field);
        }

        public static ContainmentConstraint getVirtualPoolFileshareConstraint(URI vpool) {
            DataObjectType doType = TypeMap.getDoType(FileShare.class);
            ColumnField field = doType.getColumnField("virtualPool");
            return new ContainmentConstraintImpl(vpool, FileShare.class, field);
        }

        public static ContainmentConstraint getFileshareSnapshotConstraint(URI fs) {
            DataObjectType doType = TypeMap.getDoType(Snapshot.class);
            ColumnField field = doType.getColumnField("parent");
            return new ContainmentConstraintImpl(fs, Snapshot.class, field);
        }
        
        public static ContainmentConstraint getQuotaDirectoryConstraint(URI fs) {
            DataObjectType doType = TypeMap.getDoType(QuotaDirectory.class);
            ColumnField field = doType.getColumnField("parent");
            return new ContainmentConstraintImpl(fs, QuotaDirectory.class, field);
		}
        
        public static ContainmentConstraint getVirtualComputePoolHostConstraint(URI vcp) {
            DataObjectType doType = TypeMap.getDoType(Host.class);
            ColumnField field = doType.getColumnField("computeVirtualPoolId");
            return new ContainmentConstraintImpl(vcp, Host.class, field);
        }

        public static ContainmentConstraint getStorageDeviceVolumeConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(device, Volume.class, field);
        }

        public static ContainmentConstraint getStorageDeviceUnManagedVolumeConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(UnManagedVolume.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(device, UnManagedVolume.class, field);
        }
        
        public static ContainmentConstraint getStorageDeviceRemoteGroupsConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(RemoteDirectorGroup.class);
            ColumnField field = doType.getColumnField("sourceStorageSystem");
            return new ContainmentConstraintImpl(device, RemoteDirectorGroup.class, field);
        }
        
        public static ContainmentConstraint getStorageDeviceUnManagedFileSystemConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(UnManagedFileSystem.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(device, UnManagedFileSystem.class, field);
        }
        public static ContainmentConstraint getStorageDeviceFileshareConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(FileShare.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(device, FileShare.class, field);
        }
        
        public static ContainmentConstraint getStorageDeviceStoragePoolConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(StoragePool.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(device, StoragePool.class, field);
        }
        
        
        public static ContainmentConstraint getStorageDeviceFASTPolicyConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(AutoTieringPolicy.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(device, AutoTieringPolicy.class, field);
        }
        
        public static ContainmentConstraint getStorageDeviceExportMaskConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(ExportMask.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(device, ExportMask.class, field);
        }
        
        public static ContainmentConstraint getStorageDeviceStorageTierConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(StorageTier.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(device, StorageTier.class, field);
        }
        public static ContainmentConstraint getStorageDeviceSnapshotConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(BlockSnapshot.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(device, BlockSnapshot.class, field);
        }
        
        
        public static ContainmentConstraint getStorageDeviceMirrorConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(BlockMirror.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(device, BlockMirror.class, field);
        }
        
       
        public static ContainmentConstraint getVirtualArrayVirtualPoolConstraint(URI varray) {
            DataObjectType doType = TypeMap.getDoType(VirtualPool.class);
            ColumnField field = doType.getColumnField("virtualArrays");
            return new ContainmentConstraintImpl(varray, VirtualPool.class, field);
        }
       
        public static ContainmentConstraint getStorageDeviceStoragePortConstraint(URI portGroup) {
            DataObjectType doType = TypeMap.getDoType(StoragePort.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(portGroup, StoragePort.class, field);
        }
        
        public static ContainmentConstraint getStorageHADomainStoragePortConstraint(URI portGroup) {
            DataObjectType doType = TypeMap.getDoType(StoragePort.class);
            ColumnField field = doType.getColumnField("storageHADomain");
            return new ContainmentConstraintImpl(portGroup, StoragePort.class, field);
        }

        public static ContainmentConstraint getVirtualArrayStorageDeviceConstraint(URI varray) {
            DataObjectType doType = TypeMap.getDoType(StorageSystem.class);
            ColumnField field = doType.getColumnField("varray");
            return new ContainmentConstraintImpl(varray, StorageSystem.class, field);
        }

        public static ContainmentConstraint getProjectExportGroupConstraint(
                URI project) {
            DataObjectType doType = TypeMap.getDoType(ExportGroup.class);
            ColumnField field = doType.getColumnField("project");
            return new ContainmentConstraintImpl(project, ExportGroup.class, field);
        }
        
        public static ContainmentConstraint getVolumeSnapshotConstraint(URI volume) {
            DataObjectType doType = TypeMap.getDoType(BlockSnapshot.class);
            ColumnField field = doType.getColumnField("parent");
            return new ContainmentConstraintImpl(volume, BlockSnapshot.class, field);
        }
        
        public static ContainmentConstraint getProjectBlockSnapshotConstraint(
                URI project) {
            DataObjectType doType = TypeMap.getDoType(BlockSnapshot.class);
            ColumnField field = doType.getColumnField("project");
            return new ContainmentConstraintImpl(project, BlockSnapshot.class, field);
        }
        
        public static ContainmentConstraint getProjectFileSnapshotConstraint(
                URI project) {
            DataObjectType doType = TypeMap.getDoType(Snapshot.class);
            ColumnField field = doType.getColumnField("project");
            return new ContainmentConstraintImpl(project, Snapshot.class, field);
        }

        public static ContainmentConstraint getBlockSnapshotConsitencyConstraint(URI volume) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            ColumnField field = doType.getColumnField("consistentWithVolume");
            return new ContainmentConstraintImpl(volume, Volume.class, field);
        }

        public static ContainmentConstraint getNetworkSystemFCPortConnectionConstraint(URI device) {
            DataObjectType doType = TypeMap.getDoType(FCEndpoint.class);
            ColumnField field = doType.getColumnField("networkDevice");
            return new ContainmentConstraintImpl(device, FCEndpoint.class, field);
        }

        public static ContainmentConstraint getBlockObjectExportGroupConstraint(URI blockObject) {
            DataObjectType doType = TypeMap.getDoType(ExportGroup.class);
            ColumnField field = doType.getColumnField("volumes");
            return new ContainmentConstraintImpl(blockObject, ExportGroup.class, field);
        }

        public static ContainmentConstraint getUserIdTokenConstraint(URI userId) {
            DataObjectType doType = TypeMap.getDoType(Token.class);
            ColumnField field = doType.getColumnField("userid");
            return new ContainmentConstraintImpl(userId, Token.class, field);
        }
        
        public static ContainmentConstraint getUserIdProxyTokenConstraint(URI userId) {
            DataObjectType doType = TypeMap.getDoType(ProxyToken.class);
            ColumnField field = doType.getColumnField("lastKnownIds");
            return new ContainmentConstraintImpl(userId, ProxyToken.class, field);
        }
           
        public static ContainmentConstraint getWorkflowWorkflowStepConstraint(URI workflow) {
        	DataObjectType doType = TypeMap.getDoType(WorkflowStep.class);
        	ColumnField field = doType.getColumnField("workflow");
        	return new ContainmentConstraintImpl(workflow, WorkflowStep.class, field);
        }

        public static Constraint getProjectProtectionSetConstraint(URI project) {
            DataObjectType doType = TypeMap.getDoType(ProtectionSet.class);
            ColumnField field = doType.getColumnField("project");
            return new ContainmentConstraintImpl(project, ProtectionSet.class, field);
        }
		
        public static Constraint getProtectionSystemProtectionSetConstraint(URI protectionSystem) {
            DataObjectType doType = TypeMap.getDoType(ProtectionSet.class);
            ColumnField field = doType.getColumnField("protectionSystem");
            return new ContainmentConstraintImpl(protectionSystem, ProtectionSet.class, field);
        }

        public static Constraint getProtectionSetBlockSnapshotConstraint(URI protectionSet) {
            DataObjectType doType = TypeMap.getDoType(BlockSnapshot.class);
            ColumnField field = doType.getColumnField("protectionSet");
            return new ContainmentConstraintImpl(protectionSet, BlockSnapshot.class, field);
        }

        public static Constraint getProtectionSystemVolumesConstraint(URI protectionSystem) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            ColumnField field = doType.getColumnField("protectionDevice");
            return new ContainmentConstraintImpl(protectionSystem, Volume.class, field);
        }

        public static ContainmentConstraint getConstraint(Class<? extends DataObject> type,
                                               String columnField,
                                               URI resourceUri) {
            DataObjectType doType = TypeMap.getDoType(type);
            ColumnField field = doType.getColumnField(columnField);
            return new ContainmentConstraintImpl(resourceUri, type, field);
        }


        public static ContainmentConstraint getVolumeBlockMirrorConstraint(URI volume) {
            DataObjectType doType = TypeMap.getDoType(BlockMirror.class);
            ColumnField field = doType.getColumnField("source");
            return new ContainmentConstraintImpl(volume, BlockMirror.class, field);
        }

        public static ContainmentConstraint getMatchedPoolVirtualPoolConstraint(URI pool) {
            DataObjectType doType = TypeMap.getDoType(VirtualPool.class);
            ColumnField field = doType.getColumnField("matchedPools");
            return new ContainmentConstraintImpl(pool,VirtualPool.class, field);
        }
        
        public static ContainmentConstraint getAssignedPoolVirtualPoolConstraint(URI pool) {
            DataObjectType doType = TypeMap.getDoType(VirtualPool.class);
            ColumnField field = doType.getColumnField("assignedStoragePools");
            return new ContainmentConstraintImpl(pool,VirtualPool.class, field);
        }
        
        public static ContainmentConstraint getPoolUnManagedVolumeConstraint(URI pool) {
            DataObjectType doType = TypeMap.getDoType(UnManagedVolume.class);
            ColumnField field = doType.getColumnField("storagePool");
            return new ContainmentConstraintImpl(pool,UnManagedVolume.class, field);
        }
        
        public static ContainmentConstraint getStorageSystemUnManagedVolumeConstraint(URI storageSystem) {
            DataObjectType doType = TypeMap.getDoType(UnManagedVolume.class);
            ColumnField field = doType.getColumnField("storageDevice");
            return new ContainmentConstraintImpl(storageSystem, UnManagedVolume.class, field);
        }
        
        public static ContainmentConstraint getPoolUnManagedFileSystemConstraint(URI pool) {
            DataObjectType doType = TypeMap.getDoType(UnManagedFileSystem.class);
            ColumnField field = doType.getColumnField("storagePool");
            return new ContainmentConstraintImpl(pool,UnManagedFileSystem.class, field);
        }
        
        public static ContainmentConstraint getStorageSystemUnManagedExportMaskConstraint(URI storageSystem) {
            DataObjectType doType = TypeMap.getDoType(UnManagedExportMask.class);
            ColumnField field = doType.getColumnField("storageSystem");
            return new ContainmentConstraintImpl(storageSystem, UnManagedExportMask.class, field);
        }
        
        public static ContainmentConstraint getContainedObjectsConstraint(URI parent, 
                Class<? extends DataObject> clzz, String fieldName) {
            DataObjectType doType = TypeMap.getDoType(clzz);
            ColumnField field = doType.getColumnField(fieldName);
            return new ContainmentConstraintImpl(parent, clzz, field);
        }
        
         public static ContainmentConstraint getProjectBlockConsistencyGroupConstraint(URI project) {
			DataObjectType doType = TypeMap.getDoType(BlockConsistencyGroup.class);
			ColumnField field = doType.getColumnField("project");
			return new ContainmentConstraintImpl(project, BlockConsistencyGroup.class, field);
		}

        public static ContainmentConstraint getVolumesByConsistencyGroup(final URI cgId) {
            return getConstraint(Volume.class, "consistencyGroup", cgId);
        }
       
        public static ContainmentConstraint getBlockSnapshotByConsistencyGroup(final URI cgId) {
            return getConstraint(BlockSnapshot.class, "consistencyGroup", cgId);
        } 
         
		public static ContainmentConstraint getExportMaskExportGroupConstraint(URI id) {
            DataObjectType doType = TypeMap.getDoType(ExportGroup.class);
            ColumnField field = doType.getColumnField("exportMasks");
            return new ContainmentConstraintImpl(id, ExportGroup.class, field);
		}

		public static ContainmentConstraint getComputeSystemComputeElemetsConstraint(URI csId) {
			DataObjectType doType = TypeMap.getDoType(ComputeElement.class);
			ColumnField field = doType.getColumnField("computeSystem");
			return new ContainmentConstraintImpl(csId, ComputeElement.class, field);
		}

		public static ContainmentConstraint getComputeImageJobsByComputeImageConstraint(URI ciId) {
			DataObjectType doType = TypeMap.getDoType(ComputeImageJob.class);
			ColumnField field = doType.getColumnField("computeImageId");
			return new ContainmentConstraintImpl(ciId, ComputeImageJob.class, field);
		}
        
        public static ContainmentConstraint getComputeImageJobsByHostConstraint(URI hostId) {
            DataObjectType doType = TypeMap.getDoType(ComputeImageJob.class);
            ColumnField field = doType.getColumnField("hostId");
            return new ContainmentConstraintImpl(hostId, ComputeImageJob.class, field);
        }

        public static ContainmentConstraint getComputeSystemServiceProfileTemplateConstraint(URI csId) {
            DataObjectType doType = TypeMap.getDoType(UCSServiceProfileTemplate.class);
            ColumnField field = doType.getColumnField("computeSystem");
            return new ContainmentConstraintImpl(csId, UCSServiceProfileTemplate.class, field);
        }

        public static ContainmentConstraint getComputeSystemComputeFabricUplinkPortConstraint(URI csId) {
            DataObjectType doType = TypeMap.getDoType(ComputeFabricUplinkPort.class);
            ColumnField field = doType.getColumnField("computeSystem");
            return new ContainmentConstraintImpl(csId, ComputeFabricUplinkPort.class, field);
        }

        public static ContainmentConstraint getComputeSystemComputeUplinkPortChannelConstraint(URI csId) {
            DataObjectType doType = TypeMap.getDoType(ComputeFabricUplinkPortChannel.class);
            ColumnField field = doType.getColumnField("computeSystem");
            return new ContainmentConstraintImpl(csId, ComputeFabricUplinkPortChannel.class, field);
        }

        public static ContainmentConstraint getComputeElementComputeElemetHBAsConstraint(URI ceId) {
            DataObjectType doType = TypeMap.getDoType(ComputeElementHBA.class);
            ColumnField field = doType.getColumnField("computeElement");
            return new ContainmentConstraintImpl(ceId, ComputeElementHBA.class, field);
        }

        public static ContainmentConstraint getHostComputeElemetHBAsConstraint(URI hostId) {
            DataObjectType doType = TypeMap.getDoType(ComputeElementHBA.class);
            ColumnField field = doType.getColumnField("host");
            return new ContainmentConstraintImpl(hostId, ComputeElementHBA.class, field);
        }
        
        public static ContainmentConstraint getComputeSystemVnicTemplateConstraint(URI csId) {
            DataObjectType doType = TypeMap.getDoType(UCSVnicTemplate.class);
            ColumnField field = doType.getColumnField("computeSystem");
            return new ContainmentConstraintImpl(csId, UCSVnicTemplate.class, field);
        }
        
        public static ContainmentConstraint getComputeSystemVhbaTemplateConstraint(URI csId) {
            DataObjectType doType = TypeMap.getDoType(UCSVhbaTemplate.class);
            ColumnField field = doType.getColumnField("computeSystem");
            return new ContainmentConstraintImpl(csId, UCSVhbaTemplate.class, field);
        }
        
        public static ContainmentConstraint getComputeSystemBootPolicyConstraint(URI csId) {
            DataObjectType doType = TypeMap.getDoType(ComputeBootPolicy.class);
            ColumnField field = doType.getColumnField("computeSystem");
            return new ContainmentConstraintImpl(csId, ComputeBootPolicy.class, field);
        }
        
        public static ContainmentConstraint getServiceProfileTemplateComputeBootDefsConstraint(URI sptId) {
            DataObjectType doType = TypeMap.getDoType(ComputeBootDef.class);
            ColumnField field = doType.getColumnField("serviceProfileTemplate");
            return new ContainmentConstraintImpl(sptId, ComputeBootDef.class, field);
        }
        
        public static ContainmentConstraint getComputeBootDefComputeLanBootConstraint(URI bootDefId) {
            DataObjectType doType = TypeMap.getDoType(ComputeLanBoot.class);
            ColumnField field = doType.getColumnField("computeBootDef");
            return new ContainmentConstraintImpl(bootDefId, ComputeLanBoot.class, field);
        }
        
        public static ContainmentConstraint getComputeBootPolicyComputeLanBootConstraint(URI bootPolicyId) {
            DataObjectType doType = TypeMap.getDoType(ComputeLanBoot.class);
            ColumnField field = doType.getColumnField("computeBootPolicy");
            return new ContainmentConstraintImpl(bootPolicyId, ComputeLanBoot.class, field);
        }
        
        public static ContainmentConstraint getComputeLanBootImagePathsConstraint(URI lanBootId) {
            DataObjectType doType = TypeMap.getDoType(ComputeLanBootImagePath.class);
            ColumnField field = doType.getColumnField("computeLanBoot");
            return new ContainmentConstraintImpl(lanBootId, ComputeLanBootImagePath.class, field);
        }
        
        public static ContainmentConstraint getComputeBootPolicyComputeSanBootConstraint(URI bootPolicyId) {
            DataObjectType doType = TypeMap.getDoType(ComputeSanBoot.class);
            ColumnField field = doType.getColumnField("computeBootPolicy");
            return new ContainmentConstraintImpl(bootPolicyId, ComputeSanBoot.class, field);
        }
        
        public static ContainmentConstraint getComputeBootDefComputeSanBootConstraint(URI bootDefId) {
            DataObjectType doType = TypeMap.getDoType(ComputeSanBoot.class);
            ColumnField field = doType.getColumnField("computeBootDef");
            return new ContainmentConstraintImpl(bootDefId, ComputeSanBoot.class, field);
        }
        
        public static ContainmentConstraint getComputeSanBootImageConstraint(URI sanBootId) {
            DataObjectType doType = TypeMap.getDoType(ComputeSanBootImage.class);
            ColumnField field = doType.getColumnField("computeSanBoot");
            return new ContainmentConstraintImpl(sanBootId, ComputeSanBootImage.class, field);
        }
        
        public static ContainmentConstraint getComputeSanBootImagePathConstraint(URI sanBootImageId) {
            DataObjectType doType = TypeMap.getDoType(ComputeSanBootImagePath.class);
            ColumnField field = doType.getColumnField("computeSanBootImage");
            return new ContainmentConstraintImpl(sanBootImageId, ComputeSanBootImagePath.class, field);
        }

        public static ContainmentConstraint getServiceProfileTemplateComputeElemetHBAsConstraint(URI sptId) {
            DataObjectType doType = TypeMap.getDoType(ComputeElementHBA.class);
            ColumnField field = doType.getColumnField("serviceProfileTemplate");
            return new ContainmentConstraintImpl(sptId, ComputeElementHBA.class, field);
        }

        public static ContainmentConstraint getServiceProfileTemplateComputeVnicsConstraint(URI sptId) {
            DataObjectType doType = TypeMap.getDoType(ComputeVnic.class);
            ColumnField field = doType.getColumnField("serviceProfileTemplate");
            return new ContainmentConstraintImpl(sptId, ComputeVnic.class, field);
        }

	public static ContainmentConstraint getVolumeExportGroupConstraint(URI id) {
            DataObjectType doType = TypeMap.getDoType(ExportGroup.class);
            ColumnField field = doType.getColumnField("volumes");
            return new ContainmentConstraintImpl(id, ExportGroup.class, field);
        }

        public static ContainmentConstraint getHostComputeElementConstraint(URI id) {
            DataObjectType doType = TypeMap.getDoType(Host.class);
            ColumnField field = doType.getColumnField("computeElement");
            return new ContainmentConstraintImpl(id, Host.class, field);
        }
        
        public static ContainmentConstraint getMatchedComputeElementComputeVirtualPoolConstraint(URI computeElement) {
            DataObjectType doType = TypeMap.getDoType(ComputeVirtualPool.class);
            ColumnField field = doType.getColumnField("matchedComputeElements");
            return new ContainmentConstraintImpl(computeElement,ComputeVirtualPool.class, field);
        }

        public static ContainmentConstraint getResourceTaskConstraint(URI resourceId) {
            DataObjectType doType = TypeMap.getDoType(Task.class);
            ColumnField field = doType.getColumnField("resource");
            return new ContainmentConstraintImpl(resourceId, Task.class, field);
        }

        public static ContainmentConstraint getTenantOrgTaskConstraint(URI tenantId) {
            DataObjectType doType = TypeMap.getDoType(Task.class);
            ColumnField field = doType.getColumnField("tenant");
            return new ContainmentConstraintImpl(tenantId, Task.class, field);
        }

        /**
         * Returns all tasks for the tenant that were created between startTime and endTime
         */
        public static Constraint getTimedTenantOrgTaskConstraint(URI tenantId, Date startTime, Date endTime) {
            DataObjectType doType = TypeMap.getDoType(Task.class);
            ColumnField field = doType.getColumnField("tenant");
            return new TimedContainmentConstraintImpl(tenantId,
                                                      startTime == null? -1: startTime.getTime(),
                                                      endTime == null? -1:endTime.getTime(),
                                                      Task.class, field);
        }

		public static ContainmentConstraint getFileExportRulesConstraint(URI fs) {
            DataObjectType doType = TypeMap.getDoType(FileExportRule.class);
            ColumnField field = doType.getColumnField("fileSystemId");
            return new ContainmentConstraintImpl(fs, FileExportRule.class, field);
		}
		public static ContainmentConstraint getSnapshotExportRulesConstraint(URI snapshot) {
            DataObjectType doType = TypeMap.getDoType(FileExportRule.class);
            ColumnField field = doType.getColumnField("snapshotId");
            return new ContainmentConstraintImpl(snapshot, FileExportRule.class, field);
		}
		public static ContainmentConstraint getUnManagedFileExportRulesConstraint(URI fs) {
            DataObjectType doType = TypeMap.getDoType(UnManagedFileExportRule.class);
            ColumnField field = doType.getColumnField("fileSystemId");
            return new ContainmentConstraintImpl(fs, UnManagedFileExportRule.class, field);
		}
        public static ContainmentConstraint getUnManagedCifsShareAclsConstraint(URI fs) {
            DataObjectType doType = TypeMap.getDoType(UnManagedCifsShareACL.class);
            ColumnField field = doType.getColumnField("fileSystemId");
            return new ContainmentConstraintImpl(fs, UnManagedCifsShareACL.class, field);
        }
		public static ContainmentConstraint getFileExportRulesConstraintByFileIndex(URI fsIndex) {
            DataObjectType doType = TypeMap.getDoType(FileExportRule.class);
            ColumnField field = doType.getColumnField("fsExportIndex");
            return new ContainmentConstraintImpl(fsIndex, FileExportRule.class, field);
		}
	    public static ContainmentConstraint getRpJournalVolumeParent(URI journalVolume) {
	        DataObjectType doType = TypeMap.getDoType(Volume.class);
	        ColumnField field = doType.getColumnField("rpJournalVolume");
	        return new ContainmentConstraintImpl(journalVolume, Volume.class, field);
	    }
       public static ContainmentConstraint getSecondaryRpJournalVolumeParent(URI journalVolume) {
            DataObjectType doType = TypeMap.getDoType(Volume.class);
            ColumnField field = doType.getColumnField("secondaryRpJournalVolume");
            return new ContainmentConstraintImpl(journalVolume, Volume.class, field);
        }		
		public static ContainmentConstraint getFileCifsShareAclsConstraint(URI fsURI) {
            DataObjectType doType = TypeMap.getDoType(CifsShareACL.class);
            ColumnField field = doType.getColumnField("fileSystemId");
            return new ContainmentConstraintImpl(fsURI, CifsShareACL.class, field);
		}
		public static ContainmentConstraint getSnapshotCifsShareAclsConstraint(URI snapshotURI) {
            DataObjectType doType = TypeMap.getDoType(CifsShareACL.class);
            ColumnField field = doType.getColumnField("snapshotId");
            return new ContainmentConstraintImpl(snapshotURI, CifsShareACL.class, field);
		}
    }
}
