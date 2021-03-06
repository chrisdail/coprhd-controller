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
package com.emc.storageos.datadomain.restapi.model;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value="share")
public class DDShareInfo {
	
	private String id;
	
	private String name;
	
	// -1: error; 0: path does not exist; 1: path exists
	@SerializedName("path_status")
    @JsonProperty(value="path_status")
	private int pathStatus;
	
	private DDRestLinkRep link;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getPathStatus() {
		return pathStatus;
	}

	public void setPathStatus(int pathStatus) {
		this.pathStatus = pathStatus;
	}

	public DDRestLinkRep getLink() {
		return link;
	}

	public void setLink(DDRestLinkRep link) {
		this.link = link;
	}
	
	public String toString() {
    	return new Gson().toJson(this).toString();
    }

}
