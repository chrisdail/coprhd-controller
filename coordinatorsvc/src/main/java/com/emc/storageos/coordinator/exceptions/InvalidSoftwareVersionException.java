/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class InvalidSoftwareVersionException extends FatalCoordinatorException {

    private static final long serialVersionUID = 3600447300786533101L;

    protected InvalidSoftwareVersionException(final ServiceCode code, final Throwable cause, final String detailBase, final String detailKey, final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }
}
