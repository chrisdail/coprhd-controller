/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.svcs.errorhandling.resources;

import com.sun.jersey.api.client.ClientResponse;

public class MethodNotAllowedException extends APIException {
    private static final long serialVersionUID = 5909342104246700241L;

    private MethodNotAllowedException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(ClientResponse.Status.METHOD_NOT_ALLOWED, code, cause, detailBase, detailKey, detailParams);
    }
}
