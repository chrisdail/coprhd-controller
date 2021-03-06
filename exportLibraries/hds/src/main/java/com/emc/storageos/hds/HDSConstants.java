/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.hds;

public interface HDSConstants {
    
    String HTTPS_URL = "https";
    String HTTP_URL = "http";
    String COLON = ":";
    String COMMA = ",";
    String HICOMMAND_SERVER_URL = "{}://{}:{}/service/StorageManager";
    String SYSTEMLIST_BEAN_ID = "systemList";
    String PORT_CONTROLLER_LIST = "arrayPortControllerList";
    String FAILED_STR = "FAILED";
    String COMPLETED_STR = "COMPLETED";
    String PROCESSING_STR = "PROCESSING";
    String HDS_CONTENT_TYPE_KEY = "Content-Type";
    String TEXT_XML_CONTENT_TYPE = "text/xml";
    String USER_AGENT_KEY = "User-Agent";
    String AUTHORIZATION_KEY = "Authorization";
    String CONTROLLER = "CONTROLLER";
    String HDS_OBJECT_ID_FORMAT = "{}.{}.{}.{}";
    String HDS_ARRAY_OBJECT_ID_FORMAT = "{}.{}.{}";
    String ARRAYGROUP = "ARRAYGROUP";
    String JOURNALPOOL = "JOURNALPOOL";
    String ARRAY = "ARRAY";
    String VOLUME = "VOLUME";
    String AT_THE_RATE_SYMBOL = "@";
    String PLUS_OPERATOR = "+";
    String HYPHEN_OPERATOR = "-";
    String SLASH_OPERATOR = "/";
    String UNDERSCORE_OPERATOR = "_";
    String DOT_OPERATOR = ".";
    String USER_NAME_HEADER = "Username";
    String PASSWORD_HEADER = "Password";
    String HDS = "HDS";
    String DUMMY_HSD = "ViPR-Dummy-HSD";
    
    int MAX_RETRIES = 60;
    long TASK_PENDING_WAIT_TIME = 3000L;
    
    String VIRTUALVOLUME = "VirtualVolume";
    String ARRAYGROUP_RESPONSE_BEAN_ID = "thickPoolList";
    String HOST_GROUP_DOMAIN_TYPE = "0";
    String HSD_RESPONSE_BEAN_ID = "hsdList";
    String ISCSI_TARGET_DOMAIN_TYPE = "1";
    String ISCSI = "ISCSI";
    String iSCSI = "iSCSI";
    String FC = "FC";
    String LU = "LU";
    String HDS_DM_MGMT_URL_PATH = "/service/StorageManager";
    String FIBRE = "Fibre";
    String IP = "IP";
    String TARGET = "Target";
    int MAX_VOLUME_NAME_LENGTH = 63;
    String HOST_LIST_BEAN_NAME = "hostList";
    String LOGICALUNIT_LIST_BEAN_NAME = "luList";
    String VIRTUAL_LOGICALUNIT_LIST_BEAN_NAME = "virtualLuList";
    int HOST_PORT_WWN_ALREADY_EXISTS = 7122;
    int HOST_ALREADY_EXISTS = 7134;
    int COMPOSITE_ELEMENT_MEMBER = 1;
    String UNMANAGED_VOLUME = "UnManagedVolume";
    String DP_POOL_FUNCTION = "5";
    String HITACHI_INPUT_XML_CONTEXT_FILE = "/hitachi_input_context.xml";
    String HITACHI_SMOOKS_CONFIG_FILE = "smooks-hitachi-input-config.xml";
    String HITACHI_SMOOKS_REPLICATION_CONFIG_FILE = "smooks-hds-replication-config.xml";
    String HITACHI_SMOOKS_THINIMAGE_CONFIG_FILE = "smooks-hds-thinimage-config.xml";
    String SMOOKS_CONFIG_FILE = "smooks-storagearray.xml";
    String HOST_INFO_SMOOKS_CONFIG_FILE = "smooks-hostinfo-config.xml";
    String EMULATION_OPENV = "OPEN-V";
    int MAX_SHADOWIMAGE_PAIR_COUNT = 3;
    int MAX_SNAPSHOT_COUNT = 1024;
    
    
    // Input XML Generation Constants
    String CONDITION = "Condition";
    String STORAGEARRAY = "StorageArray";
    String OBJECTLABEL = "ObjectLabel";
    String ARRAY_GROUP = "ArrayGroup";
    String ADD = "Add";
    String MODIFY = "Modify";
    String DELETE = "Delete";
    String LOGICALUNIT = "LogicalUnit";
    String LDEV = "LDEV";
    String LOGICALUNIT_BEAN_NAME = "logicalunit";
    String MODEL = "model";
    String JOURNAL_POOL = "JournalPool";
    String CREATE_THIN_VOLUMES_OP = "createThinVolumes";
    String CREATE_SNAPSHOT_VOLUME_OP = "createSnapshotVolume";
    String DELETE_SNAPSHOT_VOLUME_OP = "deleteSnapshotVolume";
    String CREATE_THICK_VOLUMES_OP = "createThickVolumes";
    String MODIFY_THIN_VOLUME_OP = "modifyThinVolume";
    String FORMAT_VOLUME_OP = "formatVolume";
    String GET_ARRAYGROUP_INFO_OP = "getArrayGroupInfo";
    String GET_JOURNAL_POOL_INFO_OP = "getJournalPoolInfo";
    String GET_LOGICALUNITS_OP = "getLogicalUnits";
    String GET_SYSTEMS_INFO_OP = "getSystemsInfo";
    String GET_API_VERSION_INFO_OP = "getServerInfo";
    String ADD_HOST_WITH_WWN_OP = "addHostWithWorldWideNames";
    String ADD_HOST_WITH_ISCSINAMES_OP = "addHostWithISCSINames";
    String GET_HSD_INFO_OP = "getHSDInfoFromSystem"; 
    String ADD_WWN_TO_HSD_OP = "addWWNToHSD";
    String BATCH_ADD_WWN_TO_HSD_OP = "batchAddInitiatorsToHSDs";
    String DELETE_WWN_FROM_HSD_OP = "deleteWWNFromHSD";
    String REMOVE_ISCSI_NAME_FROM_HSD_OP = "removeiSCSINameFromHSD";
    String ADD_HSD_TO_SYSTEM_OP = "addHSDToSystem";
    String BATCH_ADD_HSDS_TO_SYSTEM_OP = "batchAddHSDsToSystem";
    String BATCH_DELETE_HSDS_FROM_SYSTEM = "batchDeleteHSDsFromSystem";
    String ADD_PATH_TO_HSD_OP = "addPathToHSD";
    String GET_FREE_LUN_INFO_OP = "getFreeLunInfo";
    String DELETE_HSD_FROM_SYSTEM_OP = "deleteHSDFromSystem";
    String DELETE_PATH_FROM_HSD_OP = "deletePathsFromHSD";
    String GET_SYSTEM_DETAILS_OP = "getSystemDetails";
    String GET_SYSTEM_TIERING_DETAILS_OP = "getSystemTieringDetails";
    String ADD_LUSE_VOLUME_OP = "addLUSEVolumeToSystem";
    String RELEASE_LUSE_VOLUME_OP = "releaseLUSEVolume";
    String ADD_LABEL_TO_OBJECT_OP = "addLabelToObject";
    String GET_STORAGE_POOL_TIERING_INFO_OP = "getJournalPoolTieringInfo";
    String GET_ALL_HOST_INFO_OP = "getAllHostInfo";
    String CREATE_SHADOW_IMAGE_PAIR_OP = "createShadowImagePair";
    String MODIFY_SHADOW_IMAGE_PAIR_OP = "modifyShadowImagePair";
    String DELETE_PAIR_OP = "deletePair";
    String GET_SNAPSHOT_GROUP_INFO_OP = "getSnapshotGroupInfo";
    String GET_THINIMAGE_POOL_INFO_OP = "getThinImagePoolInfo";
    String CREATE_THIN_IMAGE_PAIR_OP = "createThinImagePair";
    String RESTORE_THIN_IMAGE_PAIR_OP = "restoreThinImagePair";
    String DELETE_THIN_IMAGE_PAIR_OP = "deleteThinImagePair";
    String REFRESH_HOST_OP = "refreshHost";
    
    String LU_FORMAT_TARGET = "LogicalUnitFormat";
    String LOGICALUNIT_LIST = "LogicalUnit_List";
    String DELETE_VOLUMES_OP = "deleteVolumes";
    String GET = "Get";
    String STAR = "*";
    String HOST = "Host";
    String WWN_LIST = "WorldWideName_List";
    String ISCSINAME_LIST = "ISCSIName_List";
    String HOST_STORAGE_DOMAIN = "HostStorageDomain";
    String CONFIG_FILE = "ConfigFile";
    String PATH = "Path";
    String PATHLIST_RESPONSE_BEANID = "pathList";
    String WORLDWIDENAME = "WorldWideName";
    String ISCSINAME = "ISCSIName";
    String ADD_WWN_TO_HSD_TARGET = "WWNForHostStorageDomain";
    String WWN_FOR_HSD_TARGET = "WWNForHostStorageDomain";
    String ADD_ISCSI_NAME_TO_HSD_OP = "addiSCSINameToHSD";
    String ISCSI_NAME_FOR_HSD_TARGET = "ISCSINameForHostStorageDomain";
    String LUN_TARGET = "LUN";
    String PATH_LIST = "Path_List";
    String HOSTGROUP_LIST = "HostStorageDomain_List";
    String ADDWWNTOHOSTGROUP_LIST = "WWNForHostStorageDomain_List";
    String FREELUN = "FreeLUN";
    String SERVER_INFO = "ServerInfo";
    String SNAPSHOTGROUP = "SnapshotGroup";
    
    String QUOTATION_STR = "\"";
    String EMPTY_STR = "";
    String SPACE_STR = " ";
    String AMS2100_MODEL = "AMS2100";
    String AMS_SERIES_MODEL = "AMS";
    String HUS_SERIES_MODEL = "HUS";
    String HITACHI_D800S_MODEL_STR = "D800S";
    String HUSVM_MODEL = "HM700";
    String HUSVM_ARRAYFAMILY_MODEL = "HUS VM";
    String VSP_ARRAYFAMILY_MODEL = "VSP";
    String USPV_ARRAYFAMILY_MODEL = "USP_V";
    String USP_ARRAYFAMILY_MODEL = "USP";
    String VSP_G1000_ARRAYFAMILY_MODEL = "VSP G1000";
    String LUSE_TARGET = "LUSE";
    String DPTYPE_THICK = "-1";
    String DPTYPE_THIN = "0";
    
    String SHADOW_IMAGE="ShadowImage";
    String REPLICATION="Replication";
    String REPLICATION_GROUP="ReplicationGroup";
    String REPLICATION_INFO="ReplicationInfo";
    String SPLIT  ="split";
    String QUICK_FORMAT_TYPE = "quick";
    String SI="SI";
    int HDS_MAX_NICKNAME_ALLOWED_LENGTH = 32;
    
    String HITACHI_NAMESPACE = "root/hitachi/smis";
    String REPLICATION_INFO_OBJ_ID="ReplicationInfo.objectId";
    String REPLICATION_GROUP_OBJ_ID = "ReplicationGroup.objectId";
    String VIPR_REPLICATION_GROUP_NAME = "ViPR-Replication-Group";
    String VIPR_SNAPSHOT_GROUP_NAME = "ViPR-Snapshot-Group";
    String INBAND2="inband2";
    String THIN_IMAGE="ThinImage";
    String RESTORE_INBAND2="restore;inband2";
    int LOCK_WAIT_SECONDS = 300;
    String NO_CLUSTER_ID = "-1";
    String HOST_REFRESH = "HostRefresh";
    
    String HICOMMAND_OS_TYPE_HPUX = "HP-UX";
    String HICOMMAND_HOST_TYPE_VMWARE = "3";
    String SATA_DRIVE_VALUE = "1";
    String SAS_DRIVE_VALUE = "4";
    String SSD_DRIVE_VALUE = "5";
}
