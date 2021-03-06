package com.emc.vipr.sanity

import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import com.emc.vipr.sanity.catalog.ApprovalServiceHelper
import com.emc.vipr.sanity.catalog.AssetOptionServiceHelper
import com.emc.vipr.sanity.catalog.CatalogImageServiceHelper
import com.emc.vipr.sanity.catalog.CatalogPreferencesServiceHelper
import com.emc.vipr.sanity.catalog.CatalogServiceHelper
import com.emc.vipr.sanity.catalog.ExecutionWindowServiceHelper
import com.emc.vipr.sanity.catalog.OrderServiceHelper
import com.emc.vipr.sanity.catalog.ServiceDescriptorServiceHelper
import com.emc.vipr.sanity.catalog.UserPreferencesServiceHelper
import com.emc.vipr.sanity.setup.ProjectSetup
import com.emc.vipr.sanity.setup.Sanity
import com.emc.vipr.sanity.setup.SecuritySetup
import com.emc.vipr.sanity.setup.SystemSetup


public class CatalogAPISanity {
    
    @BeforeClass static void setup() {
        Logger.getRootLogger().setLevel(Level.INFO);
        Sanity.initialize()
        SystemSetup.commonSetup()
        SecuritySetup.setupActiveDirectory()
        ProjectSetup.setup()
        println "Setup Complete"
        println ""
    }
    
    @Test void catalogCategoryTest() {
        CatalogServiceHelper.catalogCategoryServiceTest()
    }
    
    @Test void serviceDescriptorServiceTest() {
        ServiceDescriptorServiceHelper.serviceDescriptorServiceTest()
    }
    
    @Test void executionWindowServiceTest() {
        ExecutionWindowServiceHelper.executionWindowServiceTest()
    }
    
    @Test void catalogServiceServiceTest() {
        CatalogServiceHelper.catalogServiceServiceTest()
    }
    
    @Test void catalogImageServiceTest() {
        CatalogImageServiceHelper.catalogImageServiceTest()
    }
    
    @Test void orderServiceTest() {
        OrderServiceHelper.orderServiceTest()
    }
    
    @Test void approvalServiceTest() {
        ApprovalServiceHelper.approvalServiceTest()
    }
    
    @Test void assetOptionServiceTest() {
        AssetOptionServiceHelper.assetOptionServiceTest()
    }
    
    @Test void catalogPreferencesServiceTest() {
        CatalogPreferencesServiceHelper.catalogPreferencesServiceTest()
    }
    
    @Test void userPreferencesServiceTest() {
        UserPreferencesServiceHelper.userPreferencesServiceTest()
    }
    
    @AfterClass static void cleanup() {
        CatalogServiceHelper.catalogCategoryServiceTearDown()
        println "Teardown Complete [Catalog Category]"
        println ""
        
        ExecutionWindowServiceHelper.executionWindowServiceTearDown()
        println "Teardown Complete [Execution Windows]"
        println ""
        
        CatalogServiceHelper.catalogServiceServiceTearDown()
        println "Teardown Complete [Catalog Service]"
        println ""
        
        CatalogImageServiceHelper.catalogImageServiceTearDown()
        println "Teardown Complete [Catalog Image]"
        println ""
        
        OrderServiceHelper.orderServiceTearDown()
        println "Teardown Complete [Order]"
        println ""
        
        ApprovalServiceHelper.approvalServiceTearDown()
        println "Teardown Complete [Approval]"
        println ""
        
        AssetOptionServiceHelper.assetOptionServiceTearDown()
        println "Teardown Complete [AssetOption]"
        println ""
        
        CatalogPreferencesServiceHelper.catalogPreferencesServiceTearDown()
        println "Teardown Complete [Catalog Preferences]"
        println ""

        UserPreferencesServiceHelper.userPreferencesServiceTearDown()
        println "Teardown Complete [User Preferences]"
        println ""
    }
}
