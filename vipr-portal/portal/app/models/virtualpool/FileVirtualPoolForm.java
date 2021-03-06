/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.virtualpool;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static util.BourneUtil.getViprClient;

import java.util.List;
import java.util.Set;

import jobs.vipr.ConnectedFileVirtualPoolsCall;
import jobs.vipr.MatchingFileStoragePoolsCall;
import play.Logger;
import play.data.validation.Min;
import play.data.validation.Required;
import util.VirtualPoolUtils;
import util.builders.FileVirtualPoolBuilder;
import util.builders.FileVirtualPoolUpdateBuilder;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.vipr.client.core.FileVirtualPools;
import com.emc.vipr.client.core.util.ResourceUtils;

public class FileVirtualPoolForm extends VirtualPoolCommonForm<FileVirtualPoolRestRep> {
    @Min(0)
    public Integer maxSnapshots;
    
    public Boolean longTermRetention;

    @Override
    public void load(FileVirtualPoolRestRep virtualPool) {
        doLoad(virtualPool);
        FileVirtualPools vpools = getViprClient().fileVpools();
        loadQuota(vpools);
        loadTenantACLs(vpools);
    }

    protected void doLoad(FileVirtualPoolRestRep virtualPool) {
        loadCommon(virtualPool);
        FileVirtualPoolProtectionParam protection = virtualPool.getProtection();
        if ((protection != null) && (protection.getSnapshots() != null)) {
            maxSnapshots = protection.getSnapshots().getMaxSnapshots();
        }
        longTermRetention = virtualPool.getLongTermRetention();
    }

    @Override
    public FileVirtualPoolRestRep save() {
        FileVirtualPoolRestRep virtualPool = doSave();
        FileVirtualPools vpools = getViprClient().fileVpools();
        saveQuota(vpools);
        saveTenantACLs(vpools);
        return virtualPool;
    }

    protected FileVirtualPoolRestRep doSave() {
        FileVirtualPoolRestRep virtualPool;
        if (isNew()) {
            FileVirtualPoolBuilder builder = apply(new FileVirtualPoolBuilder());
            virtualPool = VirtualPoolUtils.create(builder.getVirtualPool());
            this.id = ResourceUtils.stringId(virtualPool);
        }
        else {
            FileVirtualPoolRestRep oldVirtualPool = VirtualPoolUtils.getFileVirtualPool(id);
            FileVirtualPoolUpdateBuilder builder = apply(new FileVirtualPoolUpdateBuilder(oldVirtualPool));
            virtualPool = VirtualPoolUtils.update(id, builder.getVirtualPoolUpdate());
            List<NamedRelatedResourceRep> matchingPools = VirtualPoolUtils.refreshMatchingPools(virtualPool);
            Logger.info("Refreshed File Virtual Pool '%s' matching pools: %d", virtualPool.getName(), matchingPools.size());
        }
        virtualPool = saveStoragePools(virtualPool);
        return virtualPool;
    }

    private FileVirtualPoolBuilder apply(FileVirtualPoolBuilder builder) {
        applyCommon(builder);
        builder.setSnapshots(maxSnapshots);
        builder.setLongTermRetention(longTermRetention);
        return builder;
    }

    private FileVirtualPoolUpdateBuilder apply(FileVirtualPoolUpdateBuilder builder) {
        applyCommon(builder);
        builder.setSnapshots(maxSnapshots);
        builder.setLongTermRetention(longTermRetention);
        return builder;
    }

    @Override
    protected FileVirtualPoolRestRep updateStoragePools(Set<String> add, Set<String> remove) {
        return VirtualPoolUtils.updateAssignedFilePools(id, add, remove);
    }

    public MatchingFileStoragePoolsCall matchingStoragePools() {
        FileVirtualPoolBuilder builder = new FileVirtualPoolBuilder();
        apply(builder);
        builder.setUseMatchedPools(true);
        return new MatchingFileStoragePoolsCall(builder.getVirtualPool());
    }

    public ConnectedFileVirtualPoolsCall connectedVirtualPools() {
        return new ConnectedFileVirtualPoolsCall(uris(virtualArrays));
    }
}
