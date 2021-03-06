/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.customconfigcontroller.exceptions;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface CustomConfigControllerExceptions {
    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException configTypeNotFound(final String name);

    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException scopeTypeNotSupported(final String scope, final String name);

    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException scopeValueNotSupported(final String value, final String scope, final String name);

    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException customConfigNotFound(final String name);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException customConfigAlreadyExists(final String name);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException maxLengthConstraintViolated(final String name, final String systemType, final Integer length, final Integer max, final String subString);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException illegalCharsConstraintViolated(final String name, final String systemType);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException illegalFirstCharConstraintViolated(final String name, final String systemType, final String star);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException maxConstraintViolated(final Double val, final Double max);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException minConstraintViolated(final String systemType, final Double val, final Double min);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException illegalDatasourceProperty(final String configValue, final String property);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException illegalStringFunction(final String configValue, final String functionName);

    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException customConfigScopeWithNoDefault(final String customConfigName, String scopeType, String scopeValue);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException resolvedCustomNameEmpty(final String config);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_CUSTOMCONFIG_ERROR)
    public CustomConfigControllerException invalidValueType(final String type, final String value, final String configName);
}
