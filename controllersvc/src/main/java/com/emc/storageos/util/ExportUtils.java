/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.conn.util.InetAddressUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.TreeMultimap;

public class ExportUtils {

    // Logger
    private final static Logger _log = LoggerFactory.getLogger(ExportUtils.class);

    public static final String NO_VIPR = "NO_VIPR";   // used to exclude VIPR use of export mask
    
    /**
     * Get an initiator as specified by the initiator's network port.
     * @param networkPort The initiator's port WWN or IQN.
     * @return A reference to an initiator.
     */
    public static Initiator getInitiator(String networkPort, DbClient dbClient) {
        Initiator initiator = null;
        URIQueryResultList resultsList = new URIQueryResultList();

        // find the initiator
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(
                networkPort), resultsList);
        Iterator<URI> resultsIter = resultsList.iterator();
        while (resultsIter.hasNext()) {
            initiator = dbClient.queryObject(Initiator.class, resultsIter.next());
            // there should be one initiator, so return as soon as it is found
            if (initiator != null && !initiator.getInactive()) {
                return initiator;
            }
        }
        return null;
    }
    
    /**
     * A utility function method to get the user-created initiators from an export mask.
     * If an initiator is not found for a given user-created WWN, it is simply
     * ignored and no error is raised.
     * @param exportMask the export mask
     * @param dbClient an instance of DbClient
     * @return a list of Initiators
     */
    public static List<Initiator> getExportMaskExistingInitiators(ExportMask exportMask, DbClient dbClient) {
        List<Initiator> initiators = new ArrayList<Initiator>();
        Initiator initiator = null;
        if (exportMask.getExistingInitiators() != null && 
            exportMask.getExistingInitiators().size() > 0) {
            for (String initStr : exportMask.getExistingInitiators()) {
                initStr = Initiator.toPortNetworkId(initStr);
                initiator = getInitiator(initStr, dbClient);
                if (initiator != null) {
                    initiators.add(initiator);
                }
            }
        }
        return initiators;
    }

    /**
     * Fetches and returns the initiators for an export mask.  If the ExportMask's
     * existing initiators are set, they will also be returned if an instance can
     * be found in ViPR for the given initiator port id.
     * 
     * @param exportMask the export mask
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active initiators in the export mask
     */
    public static List<URI> getExportMaskAllInitiators(ExportMask exportMask, DbClient dbClient) {
        List<URI> initiators = new ArrayList<URI>();
        if (exportMask.getInitiators() != null && 
                exportMask.getInitiators().size() > 0) {
            initiators.addAll(StringSetUtil.stringSetToUriList(exportMask.getInitiators()));
        }
        if (exportMask.getExistingInitiators() != null && 
            exportMask.getExistingInitiators().size() > 0) {
            for (String initStr : exportMask.getExistingInitiators()) {
                initStr = Initiator.toPortNetworkId(initStr);
                Initiator init = getInitiator(initStr, dbClient);
                if (init != null && !initiators.contains(init.getId())) {
                    initiators.add(init.getId());
                }
            }
        }
        return initiators;
    }

    /**
     * Fetches and returns the initiators for one or more export masks.  
     * 
     * @param exportMaskUris the export mask URIs
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active initiators in the export mask
     */
    public static List<Initiator> getExportMasksInitiators(Collection<URI> exportMaskUris, DbClient dbClient) {
        List<Initiator> list= new ArrayList<Initiator>();
        for (URI exportMaskUri : exportMaskUris) {
            list.addAll(getExportMaskInitiators(exportMaskUri, dbClient));
        }
        return list;
    }
    
    /**
     * Get all initiator ports in mask.
     * 
     * @param exportMask
     * @param dbClient
     * @return
     */
    public static Set<String> getExportMaskAllInitiatorPorts(ExportMask exportMask, DbClient dbClient) {
        Set<String> ports = new HashSet<String>();
        if (exportMask.getInitiators() != null && exportMask.getInitiators().size() > 0) {
            List<URI> iniUris = StringSetUtil.stringSetToUriList(exportMask.getInitiators());
            List<Initiator> initiators = dbClient.queryObject(Initiator.class, iniUris);
            for (Initiator ini : initiators) {
                if (ini == null || ini.getInitiatorPort() == null)
                    continue;
                ports.add(Initiator.normalizePort(ini.getInitiatorPort()));
            }
        }

        if (exportMask.getExistingInitiators() != null && exportMask.getExistingInitiators().size() > 0) {
            for (String initStr : exportMask.getExistingInitiators()) {
                ports.add(initStr);
            }
        }
        return ports;

    }
    
    /**
     * Fetches and returns the initiators for an export mask.  
     * 
     * @param exportMaskUri the export mask URI
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active initiators in the export mask
     */
    public static List<Initiator> getExportMaskInitiators(URI exportMaskUri, DbClient dbClient) {
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskUri);
        return getExportMaskInitiators(exportMask, dbClient);
    }
    
    /**
     * Fetches and returns the initiators for an export mask.  If the ExportMask's
     * existing initiators are set, they will also be returned if an instance can
     * be found in ViPR for the given initiator port id.
     * 
     * @param exportMask the export mask
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active initiators in the export mask
     */
    public static List<Initiator> getExportMaskInitiators(ExportMask exportMask, DbClient dbClient) {
        if (exportMask != null && exportMask.getInitiators() != null) {
            List<URI> initiators = StringSetUtil.stringSetToUriList(exportMask.getInitiators());
            return dbClient.queryObject(Initiator.class, initiators);
        }
        return new ArrayList<Initiator>();
    }

    
    /**
     * Fetches and returns the initiators for an export group.  
     * 
     * @param exportGroup the export grop
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active initiators in the export mask
     */
    public static List<Initiator> getExportGroupInitiators(ExportGroup exportGroup, DbClient dbClient) {
        List<URI> initiators = StringSetUtil.stringSetToUriList(exportGroup.getInitiators());
        return dbClient.queryObject(Initiator.class, initiators);
    }

    /**
     * Return the storage ports allocated to each initiators in an export mask by looking 
     * up the zoningMap.
     * @param mask
     * @param initiator
     * @return
     */
    public static List<URI> getInitiatorPortsInMask(ExportMask mask, Initiator initiator, DbClient dbClient) {     
        List<URI> list = new ArrayList<URI>();
        StringSetMap zoningMap = mask.getZoningMap();
        String strUri = initiator.getId().toString();
        if (zoningMap != null && zoningMap.containsKey(strUri) && 
                initiator.getProtocol().equals(Protocol.FC.toString())) {
            list = StringSetUtil.stringSetToUriList(zoningMap.get(strUri));
        }
        return list;
    }
    
    /**
     * Returns the storage ports allocated to each initiator based 
     * on the connectivity between them.
     * 
     * @param mask
     * @param initiator
     * @param dbClient
     * @return
     */
    public static List<URI> getPortsInInitiatorNetwork(ExportMask mask, Initiator initiator, DbClient dbClient) 
    {     
        List<URI> list = new ArrayList<URI>();
        List<StoragePort> ports = getStoragePorts(mask, dbClient);
        NetworkLite networkLite = NetworkUtil.getEndpointNetworkLite(initiator.getInitiatorPort(), dbClient);
        if (networkLite != null) 
        {
            for (StoragePort port : ports)
            {
                if (port.getNetwork() != null && 
                        port.getNetwork().equals(networkLite.getId())) 
                {
                    list.add(port.getId());
                }
            }
            
            if (list.isEmpty() && networkLite.getRoutedNetworks() != null)
            {
                for (StoragePort port : ports) 
                {
                    if (port.getNetwork() != null && 
                            networkLite.getRoutedNetworks().contains(port.getNetwork().toString())) 
                    {
                        list.add(port.getId());
                    }
                }
            }
        }
        return list;
    }

    /**
     * Fetches and returns the storage ports for an export mask
     * @param exportMask the export mask
     * @param dbClient an instance of {@link DbClient}
     * @return a list of active storage ports used by the export mask
     */
    public static List<StoragePort> getStoragePorts(ExportMask exportMask, DbClient dbClient) {
        List<StoragePort> ports = new ArrayList<StoragePort>();
        if (exportMask.getStoragePorts() != null) {
            StoragePort port = null;
            for (String initUri : exportMask.getStoragePorts()) {
                port = dbClient.queryObject(StoragePort.class, URI.create(initUri));
                if (port != null && !port.getInactive()){
                    ports.add(port);
                }
            }
        }
        _log.info("Found {} storage ports in export mask {}", ports.size(), exportMask.getMaskName());
        return ports;
    }
    
    /**
     * Creates a map of storage ports keyed by the port WWN.
     * @param ports the storage ports
     * 
     * @return a map of portWwn-to-port of storage ports
     */
    public static Map<String, StoragePort> getStoragePortsByWwnMap(Collection<StoragePort> ports) {
        Map<String, StoragePort> map = new HashMap<String, StoragePort>();
        for (StoragePort port : ports) {
            map.put(port.getPortNetworkId(), port);
        }
        return map;
    }

    /**
     * Fetches all the export masks in which a block object is member
     * @param blockObject the block object
     * @param dbClient an instance of {@link DbClient}
     * @return a list of export masks in which a block object is member
     */
    public static Map<ExportMask, ExportGroup> getExportMasks(BlockObject blockObject, DbClient dbClient) {
        Map<ExportMask, ExportGroup> exportMasksMap = new HashMap<ExportMask, ExportGroup>();
        URIQueryResultList exportGroups = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.
                Factory.getBlockObjectExportGroupConstraint(blockObject.getId()), exportGroups);
        for (URI egUri : exportGroups) {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, egUri);
            if (exportGroup.getInactive()) continue;
            if (exportGroup.getExportMasks() != null) {
                for (String exportMaskUriStr : exportGroup.getExportMasks()) {
                    ExportMask exportMask = dbClient.queryObject(ExportMask.class,
                            URI.create(exportMaskUriStr));
                    if (exportMask != null && !exportMask.getInactive() && exportMask
                            .getStorageDevice().equals(blockObject.getStorageController())
                            && exportMask.hasVolume(blockObject.getId())
                            && exportMask.getInitiators() != null && exportMask.getStoragePorts() != null) {
                        exportMasksMap.put(exportMask, exportGroup);
                    }
                }
            }
        }
        _log.info("Found {} export masks for block object {}", exportMasksMap.size(), blockObject.getLabel());
        return exportMasksMap;
    }

    /**
     * Gets all the export masks that this initiator is member of.
     * @param initiator the initiator
     * @param dbClient an instance of {@link DbClient}
     * @return all the export masks that this initiator is member of
     */
    public static List<ExportMask> getInitiatorExportMasks(
            Initiator initiator, DbClient dbClient) {
        List<ExportMask> exportMasks = new ArrayList<ExportMask>();
        URIQueryResultList egUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getExportGroupInitiatorConstraint(initiator.getId().toString())
                , egUris);
        ExportGroup exportGroup = null;
        for (URI egUri : egUris) {
            exportGroup = dbClient.queryObject(ExportGroup.class, egUri);
            if (exportGroup == null || exportGroup.getInactive() || exportGroup.getExportMasks() == null) continue;
            Collection<String> exportMaskUris = exportGroup.getExportMasks();
            for (String exportMaskUri : exportMaskUris) {
                ExportMask exportMask = dbClient.queryObject(ExportMask.class, URI.create(exportMaskUri));
                if (exportMask != null && 
                	!exportMask.getInactive() &&
                    exportMask.hasInitiator(initiator.getId().toString()) &&
                    exportMask.getVolumes() != null && 
                    exportMask.getStoragePorts() != null) {
                    exportMasks.add(exportMask);
                }
            }
        }
        _log.info("Found {} export masks for initiator {}", exportMasks.size(), initiator.getInitiatorPort());
        return exportMasks;
    }

    /**
     * Returns all the ExportGroups the initiator is a member of.
     * @param initiator Initiator
     * @param dbClient
     * @return List<ExportGroup> that contain a key to the Initiator URI
     */
    public static List<ExportGroup> getInitiatorExportGroups(
            Initiator initiator, DbClient dbClient) {
        List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();
        URIQueryResultList egUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getExportGroupInitiatorConstraint(initiator.getId().toString())
                , egUris);
        ExportGroup exportGroup = null;
        for (URI egUri : egUris) {
            exportGroup = dbClient.queryObject(ExportGroup.class, egUri);
            if (exportGroup == null || exportGroup.getInactive() || exportGroup.getExportMasks() == null) continue;
            exportGroups.add(exportGroup);
        }
        return exportGroups;
    }

    /**
     * Returns all the ExportGroups the initiator and volume/snapshot is a member of.
     * @param initiator Initiator
     * @param blockObjectId ID of a volume or snapshot
     * @param dbClient db client handle
     * @return List<ExportGroup> that contain a key to the Initiator URI
     */
    public static List<ExportGroup> getInitiatorVolumeExportGroups(
            Initiator initiator, URI blockObjectId, DbClient dbClient) {
        List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();
        URIQueryResultList egUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getExportGroupInitiatorConstraint(initiator.getId().toString())
                , egUris);
        for (URI egUri : egUris) {
        	ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, egUri);
            if (exportGroup == null || exportGroup.getInactive() || exportGroup.getExportMasks() == null) continue;
            if (exportGroup.hasBlockObject(blockObjectId)) {
                exportGroups.add(exportGroup);
            }
        }
        return exportGroups;
    }

    /**
     * Cleans up the export group references to the export mask and volumes therein
     * 
     * @param exportMask export mask
     */
    public static void cleanupAssociatedMaskResources(DbClient dbClient, ExportMask exportMask) {
    	List<ExportGroup> exportGroups = ExportMaskUtils.getExportGroups(dbClient, exportMask);
    	if (exportGroups != null) {
    		// Remove the mask references in the export group
    		for (ExportGroup exportGroup : exportGroups) {
    			// Remove this mask from the export group
    			exportGroup.removeExportMask(exportMask.getId().toString());

    			// Remove the volumes from the export group
    			if (exportMask.getUserAddedVolumes() != null) {
                    Set<URI> removeSet = new HashSet<>();
                    TreeMultimap<String, URI> volumesToExportMasks =
                            buildVolumesToExportMasksMap(dbClient, exportGroup);
    				for (String volumeURIString : exportMask.getUserAddedVolumes().values()) {
                        // Should only remove those volumes in the ExportGroup that are not already in another
                        // ExportMask associated with the ExportGroup. For example, if there is an ExportGroup
                        // for a cluster, there could be an ExportMask for each host, which could have the volume.
                        // In that case, we do not want to remove the volume from the ExportGroup
    					if (!volumeIsInAnotherExportMask(exportMask, volumeURIString, volumesToExportMasks)) {
                            URI volumeURI = URI.create(volumeURIString);
                            removeSet.add(volumeURI);
                        }
    				}
                    List<URI> volumeURIs = new ArrayList<>(removeSet);
                    exportGroup.removeVolumes(volumeURIs);
    			}
    		}

    		// Update all of the export groups in the DB
    		dbClient.updateAndReindexObject(exportGroups);
    	}
    }

    /**
     * Create a TreeMultimap that gives a mapping of a volume URI String to a list of
     * ExportMasks that the volume is associated with. All ExportMasks are associated
     * with the ExportGroup.
     *
     * @param dbClient    [in] - DB client object
     * @param exportGroup [in] - ExportGroup object to use build the mapping
     * @return Mapping of volume URI String to list of ExportMask URIs.
     */
    private static TreeMultimap<String, URI> buildVolumesToExportMasksMap(DbClient dbClient,
                                                                          ExportGroup exportGroup) {
        TreeMultimap<String, URI> volumesToExportMasks = TreeMultimap.create();
        for (String exportMaskURIString : exportGroup.getExportMasks()) {
            URI exportMaskURI = URI.create(exportMaskURIString);
            ExportMask checkMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            if (checkMask.getUserAddedVolumes() != null) {
                for (String volUriStr : checkMask.getUserAddedVolumes().values()) {
                    volumesToExportMasks.put(volUriStr, checkMask.getId());
                }
            }
        }
        return volumesToExportMasks;
    }

    /**
     * Routine will determine if the volume is associated with an ExportMask other than 'exportMask'.
     *
     * @param exportMask  [in] ExportMask that is currently being validated
     * @param volumeURIString   [in] VolumeURI String
     * @param volumesToExportMasks [in] - Used for checking the volume
     * @return true if the Volume is in an ExportMask associated to ExportGroup
     */
    private static boolean volumeIsInAnotherExportMask(ExportMask exportMask, String volumeURIString,
                                                       TreeMultimap<String, URI> volumesToExportMasks) {
        boolean isInAnotherMask = false;
        if (volumesToExportMasks.containsKey(volumeURIString)) {
            // Create a temporary set (so that it can be modified)
            Set<URI> exportMaskURIs = new HashSet<>(volumesToExportMasks.get(volumeURIString));
            // Remove the 'exportMask' URI from the list. If anything is left, then it means that
            // there is another ExportMask with the volume in it.
            exportMaskURIs.remove(exportMask.getId());
            isInAnotherMask = !exportMaskURIs.isEmpty();
        }
        return isInAnotherMask;
    }

    public static String getFileMountPoint(String fileStoragePort, String path) {
    	if(InetAddressUtils.isIPv6Address(fileStoragePort)) {
    		fileStoragePort = "[" + fileStoragePort + "]";
        }
    	
    	return fileStoragePort + ":" + path;
    }
    
    static public boolean isExportMaskShared(DbClient dbClient, URI exportMaskURI, Collection<URI> exportGroupURIs) {
        List<ExportGroup> results =
                CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, ExportGroup.class,
                        ContainmentConstraint.Factory.getConstraint(ExportGroup.class, "exportMasks", exportMaskURI));
        int count = 0;
        for (ExportGroup exportGroup : results) {
            count++;
            if (exportGroupURIs != null) {
                exportGroupURIs.add(exportGroup.getId());
            }
        }
        return count > 1;
    }

    static public int getNumberOfExportGroupsWithVolume(Initiator initiator, URI blockObjectId, DbClient dbClient) {
        List<ExportGroup> list = getInitiatorVolumeExportGroups(initiator, blockObjectId, dbClient);
        return (list != null) ? list.size() : 0;
    }

    static public String computeResourceForInitiator(ExportGroup exportGroup, Initiator initiator) {
        String value = NullColumnValueGetter.getNullURI().toString();
        if (exportGroup.forCluster()) {
            value = initiator.getClusterName();
        } else if (exportGroup.forHost() || (exportGroup.forInitiator() && initiator.getHost() != null)) {
            value = initiator.getHost().toString();
        }
        return value;
    }

    /**
     * Using the ExportGroup object, produces a mapping of the BlockObject URI to LUN value
     *
     * @param dbClient
     * @param storage
     * @param exportGroup
     * @return
     */
    static public Map<URI, Integer> getExportGroupVolumeMap(DbClient dbClient, StorageSystem storage,
                                                            ExportGroup exportGroup) {
        Map<URI, Integer> map = new HashMap<>();
        if (exportGroup != null && exportGroup.getVolumes() != null) {
            for (Map.Entry<String, String> entry : exportGroup.getVolumes().entrySet()) {
                URI uri = URI.create(entry.getKey());
                Integer lun = Integer.valueOf(entry.getValue());
                BlockObject blockObject = BlockObject.fetch(dbClient, uri);
                if (blockObject != null && blockObject.getStorageController().equals(storage.getId())) {
                    map.put(uri, lun);
                }
            }
        }
        return map;
    }

    /**
     * This method checks to see if there are storagePorts in exportMask storagePorts
     * which do not exist in the zoningMap so as to remove those ports from the storage
     * view. This is called from exportGroupRemoveVolumes.
     * 
     * @param exportMask reference to exportMask
     * @return list of storagePort URIs that don't exist in zoningMap.
     */
    public static List<URI> checkIfStoragePortsNeedsToBeRemoved(ExportMask exportMask){
    	List<URI> storagePortURIs = new ArrayList<URI>();
    	StringSetMap zoningMap = exportMask.getZoningMap();
    	StringSet existingStoragePorts = exportMask.getStoragePorts();
    	StringSet zoningMapStoragePorts = new StringSet();
    	if(zoningMap !=null){
			for (String initiatorId : zoningMap.keySet()) {
				StringSet ports = zoningMap.get(initiatorId);
				if(ports !=null && !ports.isEmpty()){
					zoningMapStoragePorts.addAll(ports);
				}
			}
		}
     	existingStoragePorts.removeAll(zoningMapStoragePorts);
    	if(existingStoragePorts.size() > 0){
    		storagePortURIs = StringSetUtil.stringSetToUriList(existingStoragePorts);
    		_log.info("Storage ports needs to be removed are:"+ storagePortURIs);;
    	}
    	
    	return storagePortURIs;
    }
    
    /**
     * This method updates zoning map to add new assignments.
     * 
     * @param dbClient an instance of {@link DbClient}
     * @param exportMask The reference to exportMask
     * @param assignments New assignments Map of initiator to storagePorts that will be updated in the zoning map
     * @param exportMasksToUpdateOnDeviceWithStoragePorts OUT param -- Map of ExportMask to new Storage ports
     * 
     * @return returns an updated exportMask
     */
    public static ExportMask updateZoningMap(DbClient dbClient, ExportMask exportMask, Map<URI, List<URI>> assignments, 
    		Map<URI, List<URI>> exportMasksToUpdateOnDeviceWithStoragePorts){

    	StringSetMap existingZoningMap = exportMask.getZoningMap();
    	for (URI initiatorURI: assignments.keySet()){
    		boolean initiatorMatchFound = false;
    		if(existingZoningMap !=null && !existingZoningMap.isEmpty()){
    			for (String initiatorId : existingZoningMap.keySet()) {
    				if(initiatorURI.toString().equals(initiatorId)){	
    					StringSet ports = existingZoningMap.get(initiatorId);
    					if (ports != null && !ports.isEmpty()){ 
    						initiatorMatchFound = true;
    						StringSet newTargets = StringSetUtil.uriListToStringSet(assignments.get(initiatorURI));
    						if(!ports.containsAll(newTargets)){
    							ports.addAll(newTargets);
    							// Adds zoning map entry with new and existing ports. Its kind of updating storage ports for the initiator.
    							exportMask.addZoningMapEntry(initiatorId, ports);
    							updateExportMaskStoragePortsMap(exportMask, exportMasksToUpdateOnDeviceWithStoragePorts,
    			    		    		 assignments, initiatorURI);
    						}
    					}
    				}
    			}
    		}
    		if(!initiatorMatchFound){
    			// Adds new zoning map entry for the initiator with new assignments as there isn't one already.
    			exportMask.addZoningMapEntry(initiatorURI.toString(), StringSetUtil.uriListToStringSet(assignments.get(initiatorURI)));
    			updateExportMaskStoragePortsMap(exportMask, exportMasksToUpdateOnDeviceWithStoragePorts,
   		    		 assignments, initiatorURI);
    		}
    	}
    	dbClient.persistObject(exportMask);

    	return exportMask;
    }
    
    /**
     * This method just updates the passed in exportMasksToUpdateOnDeviceWithStoragePorts map with 
     * the new storage ports assigned for the initiator for a exportMask.
     * 
     * @param exportMask The reference to exportMask 
     * @param exportMasksToUpdateOnDeviceWithStoragePorts OUT param -- map of exportMask to update with new storage ports
     * @param assignments New assignments Map of initiator to storage ports 
     * @param initiatorURI The initiator URI for which storage ports are updated in the exportMask
     */
    private static void updateExportMaskStoragePortsMap(ExportMask exportMask, Map<URI, List<URI>> exportMasksToUpdateOnDeviceWithStoragePorts,
    		Map<URI, List<URI>> assignments, URI initiatorURI){

    	if(exportMasksToUpdateOnDeviceWithStoragePorts.get(exportMask.getId())!= null){
    		exportMasksToUpdateOnDeviceWithStoragePorts.get(exportMask.getId()).addAll(assignments.get(initiatorURI));
    	}else {
    		exportMasksToUpdateOnDeviceWithStoragePorts.put(exportMask.getId(), assignments.get(initiatorURI));
    	}
    }

    /**
     * Take in a list of storage port names (hex digits separated by colons),
     * then returns a list of URIs representing the StoragePort URIs they represent.
     * 
     * This method ignores the storage ports from cinder storage systems.
     *
     * @param storagePorts [in] - Storage port name, hex digits separated by colons
     * @return List of StoragePort URIs
     */
    public static List<String> storagePortNamesToURIs(DbClient dbClient,
            List<String> storagePorts) {
        List<String> storagePortURIStrings = new ArrayList<String>();
        Map<URI, String> systemURIToType = new HashMap<URI, String>();
        for (String port : storagePorts) {
            URIQueryResultList portUriList = new URIQueryResultList();
            dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getStoragePortEndpointConstraint(port), portUriList);
            Iterator<URI> storagePortIter = portUriList.iterator();
            while (storagePortIter.hasNext()) {
                URI portURI = storagePortIter.next();
                StoragePort sPort = dbClient.queryObject(StoragePort.class, portURI);
                if (sPort != null && !sPort.getInactive()) {
                    String systemType = getStoragePortSystemType(dbClient, sPort, systemURIToType);
                    // ignore cinder managed storage system's port
                    if (!DiscoveredDataObject.Type.openstack.name().equals(systemType)) {
                        storagePortURIStrings.add(portURI.toString());
                    }
                }
            }
        }
        return storagePortURIStrings;
    }

    private static String getStoragePortSystemType(DbClient dbClient,
            StoragePort port, Map<URI, String> systemURIToType) {
        URI systemURI = port.getStorageDevice();
        String systemType = systemURIToType.get(systemURI);
        if (systemType == null) {
            StorageSystem system = dbClient.queryObject(StorageSystem.class, systemURI);
            systemType = system.getSystemType();
            systemURIToType.put(systemURI, systemType);
        }
        return systemType;
    }
    
    /**
     * Checks to see if the export group is for RecoverPoint
     * 
     * @param exportGroup
     *            The export group to check
     * @return True if this export group is for RecoverPoint, false otherwise.
     */
    public static boolean checkIfExportGroupIsRP(ExportGroup exportGroup) {
        if (exportGroup == null) {
            return false;
        }
        
        return exportGroup.checkInternalFlags(Flag.RECOVERPOINT);
    }
    
    /**
     * Checks to see if the initiators passed in are for RecoverPoint.
     * 
     * Convenience method to load the actual Initiators from the StringSet first
     * before calling checkIfInitiatorsForRP(List<Initiator> initiatorList).
     * 
     * @param dbClient
     *            DB Client
     * @param initiatorList
     *            The StringSet of initiator IDs to check
     * @return True if there are RecoverPoint Initiators in the passed in list,
     *         false otherwise
     */
    public static boolean checkIfInitiatorsForRP(DbClient dbClient, StringSet initiatorList) {
        if (dbClient == null || initiatorList == null) {
            return false;
        }
        
        List<Initiator> initiators = new ArrayList<Initiator>();
        for (String initiatorId : initiatorList) {
            Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(initiatorId));
            if (initiator != null) {
                initiators.add(initiator);
            }
        }
        
        return checkIfInitiatorsForRP(initiators);
    }
    
    /**
     * Check the list of passed in initiators and check if HostName is null or
     * Host is not null, if so, these are NOT RecovePoing initiators so return
     * false. Otherwise we can assume they are, return true.
     * 
     * @param initiatorList
     *            List of Initiators
     * @return True if there are RecoverPoint Initiators in the passed in list,
     *         false otherwise
     */
    public static boolean checkIfInitiatorsForRP(List<Initiator> initiatorList) {
        if (initiatorList == null) {
            return false;
        }
        
        _log.debug("Checking Initiators to see if this is RP");
        boolean isRP = true;        
        for (Initiator initiator : initiatorList) {
            if (!initiator.checkInternalFlags(Flag.RECOVERPOINT)) {
                isRP = false;
                break;
            }                    
        }
        
        _log.debug("Are these RP initiators? " + (isRP ? "Yes!" : "No!"));
        return isRP;
    }   
    
    /**
     * Figure out whether or not we need to use the EMC Force flag for the SMIS
     * operation being performed on this volume.
     * 
     * @param _dbClient
     *            DB Client
     * @param volumeURI
     *            Volume to check
     * @return Whether or not to use the EMC force flag
     */
    public static boolean useEMCForceFlag(DbClient _dbClient, URI volumeURI) {
        boolean forceFlag = false;
        // If there are any volumes that are RP, then we need to use the force flag on this operation
        BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, volumeURI);
        if (bo instanceof Volume) {
            Volume volume = (Volume)bo;
            if (volume != null && volume.checkForRp()) {
                forceFlag = true;
            }
        }
        return forceFlag;
    }
    
    /**
     * Get the varrays used for the set of volumes for a storage system.
     * For the VPlex, it will include the HA virtual array if there are distributed volumes.
     * @param exportGroup -- ExportGroup instance
     * @param storageURI -- the URI of the Storage System
     * @param dbClient
     */
    public static List<URI> getVarraysForStorageSystemVolumes(ExportGroup exportGroup, URI storageURI, DbClient dbClient) {
        List<URI> varrayURIs = new ArrayList<URI>();
        varrayURIs.add(exportGroup.getVirtualArray());
        Map<URI, Map<URI, Integer>> systemToVolumeMap = getStorageToVolumeMap(exportGroup, false, dbClient);
        if (systemToVolumeMap.containsKey(storageURI)) {
            Set<URI> blockObjectURIs = systemToVolumeMap.get(storageURI).keySet();
            for (URI blockObjectURI : blockObjectURIs) {
                BlockObject blockObject = BlockObject.fetch(dbClient, blockObjectURI);
                Set<URI> blockObjectVarrays = getBlockObjectVarrays(blockObject, dbClient);
                varrayURIs.addAll(blockObjectVarrays);
            }
        }
        return varrayURIs;
    }
    
    /**
     * Given an exportGroup, generate a map of Storage System URI to a map of BlockObject URI to lun id
     * for BlockObjects in the Export Group.
     * @param exportGroup
     * @param protection
     * @param dbClient
     * @return Map of Storage System URI to map of BlockObject URI to lun id Integer
     */
    public static Map<URI, Map<URI, Integer>> getStorageToVolumeMap(ExportGroup exportGroup, boolean protection, DbClient dbClient) {
        Map<URI, Map<URI, Integer>> map = new HashMap<URI, Map<URI, Integer>>();

        StringMap volumes = exportGroup.getVolumes();
        if (volumes == null) {
            return map;
        }

        for (String uriString: volumes.keySet()) {
            URI blockURI = URI.create(uriString);
            BlockObject block = BlockObject.fetch(dbClient, blockURI);
            // If this is an RP-based Block Snapshot, use the protection controller instead of the underlying block controller
            URI storage = (block.getProtectionController()!=null&&protection&&block.getId().toString().contains("BlockSnapshot")) ?
                    block.getProtectionController() : block.getStorageController();

            if (map.get(storage) == null)
                map.put(storage, new HashMap<URI, Integer>());
            map.get(storage).put(blockURI, Integer.valueOf(volumes.get(uriString)));
        }
        return map;
    }
    
    /**
     * Get the possible Varrays a BlockObject can be associated with.
     * Handles the Vplex... which can be the BlockObject's varray, 
     * or the HA Virtual array in the Vpool.
     * @param blockObject
     * @param dbClient
     * @return Set<URI> of Varray URIs
     */
    public static  Set<URI> getBlockObjectVarrays(BlockObject blockObject, DbClient dbClient) {
        Set<URI> varrayURIs = new HashSet<URI>();
        varrayURIs.add(blockObject.getVirtualArray());
        VirtualPool vpool = getBlockObjectVirtualPool(blockObject, dbClient);
        if (vpool != null) {
            if (vpool.getHaVarrayVpoolMap() != null)
                for (String varrayId : vpool.getHaVarrayVpoolMap().keySet()) {
                    URI varrayURI = URI.create(varrayId);
                    if (!varrayURIs.contains(varrayURI)) {
                        varrayURIs.add(varrayURI);
                    }
                }
        }
        return varrayURIs;
    }
    
    /** 
     * Get the Virtual Pool for a Block Object.
     * @param blockObject
     * @param dbClient
     * @return VirtualPool or null if could not locate
     */
    public static VirtualPool getBlockObjectVirtualPool(BlockObject blockObject, DbClient dbClient) {
        Volume volume = null;
        if (blockObject instanceof BlockSnapshot) {
            BlockSnapshot snapshot = (BlockSnapshot) blockObject;
            volume = dbClient.queryObject(Volume.class, snapshot.getParent());
        } else if (blockObject instanceof Volume) {
            volume = (Volume) blockObject;
        }
        if (volume != null) {
            VirtualPool vpool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            return vpool;
        }
        return null;
    }
    
    /**
     * Filters Initiators for non-VPLEX systems by the ExportGroup varray.
     * Initiators not in the Varray are removed from the newInitiators list.
     * @param exportGroup -- ExportGroup used to get virtual array.
     * @param newInitiators -- List of new initiators to be processed
     * @param storageURI -- storage system URI
     * @param dbClient -- DbClient for database
     * @return filteredInitiators -- New list of filtered initiators
     */
    public static List<URI> filterNonVplexInitiatorsByExportGroupVarray(
            ExportGroup exportGroup, List<URI> newInitiators, URI storageURI, DbClient dbClient) {
        List<URI> filteredInitiators = new ArrayList<URI>(newInitiators);
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageURI);
        // in the case of RecoverPoint, storageURI refers to a ProtectionSystem so storageSystem will be null
        if (storageSystem != null && !storageSystem.getSystemType().equals(DiscoveredDataObject.Type.vplex.name())) {
            filterOutInitiatorsNotAssociatedWithVArray(exportGroup.getVirtualArray(), filteredInitiators, dbClient);
        }
        return filteredInitiators;
    }
    
    /**
     * Routine will examine the 'newInitiators' list and remove any that do not have any association
     * to the VirtualArray.
     * @param virtualArrayURI [in] - VirtualArray URI reference
     * @param newInitiators   [in/out] - List of initiator URIs to examine.
     * @param dbClient [in] -- Used to access database
     */
    public static void filterOutInitiatorsNotAssociatedWithVArray(URI virtualArrayURI, 
                                                  List<URI> newInitiators, DbClient dbClient) {
        Iterator<URI> it = newInitiators.iterator();
        while (it.hasNext()) {
            URI uri = it.next();
            Initiator initiator = dbClient.queryObject(Initiator.class, uri);
            if (initiator == null) {
                _log.info(String.format("Initiator %s was not found in DB. Will be eliminated from request payload.", 
                        uri.toString()));
                it.remove();
                continue;
            }
            if (!isInitiatorInVArraysNetworks(virtualArrayURI, initiator, dbClient)) {
                _log.info(String.format("Initiator %s (%s) will be eliminated from the payload " +
                        "because it was not associated with Virtual Array %s",
                        initiator.getInitiatorPort(), initiator.getId().toString(), 
                        virtualArrayURI.toString()));
                it.remove();
            }
        }
    }
    
    /**
     * Validate if the initiator is linked to the VirtualArray through some Network
     *
     * @param virtualArrayURI [in] - VirtualArray URI reference
     * @param initiator [in] - the initiator
     * @return true iff the initiator belongs to a Network and that Network has the VirtualArray
     */
    public static boolean isInitiatorInVArraysNetworks(URI virtualArrayURI, Initiator initiator, DbClient dbClient) {
        boolean foundAnAssociatedNetwork = false;
        Set<NetworkLite> networks = NetworkUtil.getEndpointAllNetworksLite(initiator.getInitiatorPort(), dbClient);
        if (networks == null || networks.isEmpty()) {
            // No network associated with the initiator, so it should be removed from the list
            _log.info(String.format("Initiator %s (%s) is not associated with any network.",
                    initiator.getInitiatorPort(), initiator.getId().toString()));
            return false;
        } else {
            // Search through the networks determining if the any are associated with ExportGroup's VirtualArray.
            for (NetworkLite networkLite : networks) {
                if (networkLite == null) {
                    continue;
                }
                Set<String> varraySet = networkLite.fetchAllVirtualArrays();
                if (varraySet != null && varraySet.contains(virtualArrayURI.toString())) {
                    _log.info(String.format("Initiator %s (%s) was found to be associated to VirtualArray %s through network %s.",
                            initiator.getInitiatorPort(), initiator.getId().toString(), virtualArrayURI.toString(), networkLite.getNativeGuid()));
                    foundAnAssociatedNetwork = true;
                    // Though we could break this loop here, let's continue the loop so that
                    // we can log what other networks that the initiator is seen in
                }
            }
        }
        return foundAnAssociatedNetwork;
    }

    /**
     * Check if any ExportGroups passed in contain the initiator
     *
     * @param dbClient        [in] - DB client object
     * @param exportGroupURIs [in] - List of ExportGroup URIs referencing ExportGroups to check
     * @param initiator       [in] - The initiator check
     * @return true if any of the ExportGroups referenced in the exportGroupURIs list has the initiator
     */
    public static boolean checkIfAnyExportGroupsContainInitiator(DbClient dbClient, Set<URI> exportGroupURIs, Initiator initiator) {
        Iterator<ExportGroup> exportGroupIterator =
                dbClient.queryIterativeObjects(ExportGroup.class, exportGroupURIs, true);
        while (exportGroupIterator.hasNext()) {
            ExportGroup exportGroup = exportGroupIterator.next();
            if (exportGroup.hasInitiator(initiator)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if any ExportGroups passed in contain the initiator and block object
     *
     * @param dbClient        [in] - DB client object
     * @param exportGroupURIs [in] - List of ExportGroup URIs referencing ExportGroups to check
     * @param initiator       [in] - The initiator check
     * @param blockObject     [in] - The block object
     * @return true if any of the ExportGroups referenced in the exportGroupURIs list has the initiator
     */
	public static boolean checkIfAnyExportGroupsContainInitiatorAndBlockObject(
			DbClient dbClient, Set<URI> exportGroupURIs, Initiator initiator,
			BlockObject blockObject) {
        Iterator<ExportGroup> exportGroupIterator =
                dbClient.queryIterativeObjects(ExportGroup.class, exportGroupURIs, true);
        while (exportGroupIterator.hasNext()) {
            ExportGroup exportGroup = exportGroupIterator.next();
            if (exportGroup.hasInitiator(initiator) && exportGroup.hasBlockObject(blockObject.getId())) {
                return true;
            }
        }
        return false;
	}
    
    /**
     * Returns the list of export groups referencing the mask
     * @param uri the export mask UTI
     * @param dbClient and instance of {@link DbClient}
     * @return the list of export groups referencing the mask
     */
    public static List<ExportGroup> getExportGroupsForMask(URI uri, DbClient dbClient) {
        URIQueryResultList exportGroupUris = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.
                Factory.getExportMaskExportGroupConstraint(uri), exportGroupUris);
        return DataObjectUtils.iteratorToList(dbClient.queryIterativeObjects(ExportGroup.class, 
                DataObjectUtils.iteratorToList(exportGroupUris)));
    }
    
    /**
     * Find out if the mirror is part of any export group/export mask. 
     * If yes, remove the mirror and add the promoted volume.
     * @param mirror
     * @param promotedVolume
     * @param dbClient
     */
    public static void updatePromotedMirrorExports(BlockMirror mirror, Volume promotedVolume, DbClient dbClient) {
        URIQueryResultList egUris = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.
                Factory.getBlockObjectExportGroupConstraint(mirror.getId()), egUris);
        List<ExportGroup> exportGroups = dbClient.queryObject(ExportGroup.class, egUris);
        Set<ExportMask> mirrorExportMasks = new HashSet<ExportMask>();
        List<DataObject> updatedObjects = new ArrayList<DataObject>();
        for(ExportGroup exportGroup : exportGroups) {
            if(!exportGroup.getInactive() && exportGroup.getExportMasks() != null) {
                List<URI> exportMasks = new ArrayList<URI>(Collections2.transform(
                        exportGroup.getExportMasks(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
                mirrorExportMasks.addAll(dbClient.queryObject(ExportMask.class, exportMasks));
                //remove the mirror from export group and add the promoted volume
                String lunString = exportGroup.getVolumes().get(mirror.getId().toString());
                _log.info("Removing mirror {} from export group {}", mirror.getId(), exportGroup.getId());
                exportGroup.removeVolume(mirror.getId());
                _log.info("Adding promoted volume {} to export group {}", promotedVolume.getId(), exportGroup.getId());
                exportGroup.getVolumes().put(promotedVolume.getId().toString(), lunString);
                updatedObjects.add(exportGroup);
            }
        }
        
        for(ExportMask exportMask : mirrorExportMasks) {
            if(!exportMask.getInactive() 
                    && exportMask.getStorageDevice().equals(mirror.getStorageController()) 
                    && exportMask.hasVolume(mirror.getId())
                    && exportMask.getInitiators() != null && exportMask.getStoragePorts() != null) {
                String lunString = exportMask.getVolumes().get(mirror.getId().toString());
                _log.info("Removing mirror {} from export mask {}", mirror.getId(), exportMask.getId());
                exportMask.removeVolume(mirror.getId());
                exportMask.removeFromUserCreatedVolumes(mirror);
                _log.info("Adding promoted volume {} to export mask {}", promotedVolume.getId(), exportMask.getId());
                exportMask.addToUserCreatedVolumes(promotedVolume);
                exportMask.getVolumes().put(promotedVolume.getId().toString(), lunString);
                updatedObjects.add(exportMask);
            }
        }
        
        dbClient.updateAndReindexObject(updatedObjects);
    }
}
