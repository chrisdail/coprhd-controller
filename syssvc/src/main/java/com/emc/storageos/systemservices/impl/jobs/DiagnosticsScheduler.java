/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.jobs;

import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.systemservices.impl.healthmonitor.DiagConstants;
import com.emc.storageos.systemservices.impl.healthmonitor.DiagnosticsExec;
import com.emc.storageos.systemservices.impl.healthmonitor.LogAnalyser;
import com.emc.storageos.systemservices.impl.healthmonitor.beans.DiagTestMetadata;
import com.emc.storageos.systemservices.impl.healthmonitor.beans.DiagTestsMetadata;
import com.emc.vipr.model.sys.healthmonitor.DiagTest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler class that runs diagtool script every day and reports alerts ( to
 * /var/log/alerts and /opt/storageos/logs/sysvc.log) if any of the
 * tests fail.
 * Test status is validated against its metadata.
 */
@Service
public class DiagnosticsScheduler implements Runnable, JobConstants {
    private static final Logger _log = LoggerFactory.getLogger(DiagnosticsScheduler
            .class);

    private static final AlertsLogger _alertsLog = AlertsLogger.getAlertsLogger();

    private LogAnalyser _dbLogAnalyser;
    private LogAnalyser _zkLogAnalyser;
    private LogAnalyser _controllerSvcLogAnalyser;

    public void setDbLogAnalyser(LogAnalyser dbLogAnalyser) {
        _dbLogAnalyser = dbLogAnalyser;
    }

    public void setZkLogAnalyser(LogAnalyser zkLogAnalyser) {
        _zkLogAnalyser = zkLogAnalyser;
    }

    public void setControllerSvcLogAnalyser(LogAnalyser controllerSvcLogAnalyser) {
        _controllerSvcLogAnalyser = controllerSvcLogAnalyser;
    }

    /**
     * Sets up the scheduler.
     */
    public DiagnosticsScheduler() {
        _log.info("Initializing diagnostics scheduler");
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        service.scheduleAtFixedRate(this, SERVICE_START_LAG, LAG_BETWEEN_RUNS_ALERTS,
                TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        List<DiagTest> diagTests = DiagnosticsExec.getDiagToolResults(DiagConstants
                .VERBOSE);
        DiagTestMetadata diagTestMetadata;
        for (DiagTest test : diagTests) {
            if( (diagTestMetadata=DiagTestsMetadata.getMetadata().get(test.getName()))!=null ) {
                String[] statusArr = test.getStatus().split(",");
                for (String status : statusArr) {
                    status = status.trim();
                    if(!diagTestMetadata.getOk().contains(status)){
                        if(diagTestMetadata.getWarn().contains(status)){
                            _alertsLog.warn(test);
                        }
                        else if(diagTestMetadata.getError().contains(status)){
                            _alertsLog.error(test);
                        }
                        else if(diagTestMetadata.getCrit().contains(status)){
                            _alertsLog.fatal(test);
                        }
                    }
                }
            }
        }

        //Analysis db and zk logs, if the errors match pre-define patterns, alter it in SystemEvents.
        _dbLogAnalyser.analysisLogs();
        _zkLogAnalyser.analysisLogs();
        _controllerSvcLogAnalyser.analysisLogs();
    }
}
