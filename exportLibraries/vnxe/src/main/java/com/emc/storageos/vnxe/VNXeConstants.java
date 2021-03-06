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
package com.emc.storageos.vnxe;

public class VNXeConstants {
	public static final String CONTENT = "content";
	public static final String ENTRIES = "entries";
	public static final String TIMEOUT = "timeout";
	public static final String FILTER = "filter";
	public static final String NAME_FILTER = "name eq ";
	public static final String STORAGE_RESOURCE_FILTER = "storageResource.id eq ";
	public static final String NASSERVER_FILTER = "nasServer.id eq ";
	public static final String FILE_SYSTEM_FILTER = "parentFilesystem.id eq ";
	public static final String SNAP_FILTER = "parentFilesystemSnap.id eq ";
	public static final String ISCSINODE_FILTER = "iscsiNode.id eq ";
	public static final String IPADDRESS_FILTER = "address eq ";
	public static final String AND = " and ";
	public static final String INITIATORID_FILTER= "initiatorId eq ";
	public static final String LUN_FILTER= "lun.id eq ";
	public static final String LUN_SNAP_FILTER= "lunSnap.id eq ";
	public static final String PORTWWN_FILTER = "portWWN eq ";
	public static final int REDIRECT_MAX = 100;
	public static final int MAX_NAME_LENGTH = 63;

}
