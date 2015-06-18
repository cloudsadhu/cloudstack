// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.api.command.admin.solidfire;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.log4j.Logger;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.helper.ApiHelper;
import org.apache.cloudstack.api.response.ApiSolidFireClusterResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.solidfire.ApiSolidFireService2;
import org.apache.cloudstack.solidfire.dataaccess.SfCluster;

@APICommand(name = "listSolidFireClusters", responseObject = ApiSolidFireClusterResponse.class, description = "List SolidFire Clusters",
    requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListSolidFireClustersCmd extends BaseListCmd {
    private static final Logger s_logger = Logger.getLogger(ListSolidFireClustersCmd.class.getName());
    private static final String s_name = "listsolidfireclustersresponse";

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "SolidFire cluster name")
    private String name;

    @Inject private ApiSolidFireService2 _apiSolidFireService2;

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public void execute() {
        s_logger.info("ListSolidFireClustersCmd.execute invoked");

        try {
            final List<SfCluster> sfClusters;

            if (name != null) {
                sfClusters = new ArrayList<>();

                SfCluster sfCluster = _apiSolidFireService2.listSolidFireCluster(name);

                if (sfCluster != null) {
                    sfClusters.add(sfCluster);
                }
            }
            else {
                sfClusters = _apiSolidFireService2.listSolidFireClusters();
            }

            List<ApiSolidFireClusterResponse> responses = ApiHelper.instance().getApiSolidFireClusterResponse(sfClusters);

            ListResponse<ApiSolidFireClusterResponse> listReponse = new ListResponse<>();

            listReponse.setResponses(responses);
            listReponse.setResponseName(getCommandName());
            listReponse.setObjectName("apilistsolidfireclusters");

            setResponseObject(listReponse);
        }
        catch (Exception ex) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }
}