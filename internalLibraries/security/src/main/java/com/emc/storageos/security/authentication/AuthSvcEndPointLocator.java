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

package com.emc.storageos.security.authentication;


/**
 * Class for looking up registered authsvc endpoints in coordinator
 */
public class AuthSvcEndPointLocator extends EndPointLocator {

    public AuthSvcEndPointLocator() {
        setServiceLocatorInfo(ServiceLocatorInfo.AUTH_SVC);
    }
}
