/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.Writer;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.TimeSeriesQueryResult;
import com.emc.storageos.db.client.model.AuditLog;

/**
 *  Implementation of DB time series based query result for audit logs
 */
public class AuditLogQueryResult implements TimeSeriesQueryResult<AuditLog> {

    final private Logger _logger = LoggerFactory
            .getLogger(AuditLogQueryResult.class);

    private final AtomicLong _resultsCount = new AtomicLong(0);

    /**
     * indication of situation when streaming should be stopped
     */
    boolean _stopStreaming = false;

    /**
     * the marshaller to be used for serializing audit logs
     */
    private AuditLogMarshaller _marshaller;

    /**
     * the putput writer for serializing audit logs
     */
    private Writer _out;

    /**
     * Create new metering query results with max count
     * 
     * @param out
     *            the writer for writing results one by one
     * @param limit
     *            the maximum number of records to return, 0 for no limit
     */
    AuditLogQueryResult(AuditLogMarshaller marshaller, Writer out) {
        _out = out;

        _marshaller = marshaller;
    }

    @Override
    public void data(AuditLog data, long insertionTimeMs) {

        _logger.debug("AuditLog #{}", _resultsCount.get());

        try {
            if (!_stopStreaming) {
                _marshaller.marshal(data, _out);
                _resultsCount.incrementAndGet();
            }
        } catch (MarshallingExcetion e) {
            _logger.error("Error during auditlog marshaling", e);
            _stopStreaming = true;
        }
    }

    @Override
    public void done() {
        _logger.info("Query Result Size = {}", _resultsCount.get());
    }

    @Override
    public void error(Throwable e) {
        _logger.error("Error during query execution", e);
    }

}
