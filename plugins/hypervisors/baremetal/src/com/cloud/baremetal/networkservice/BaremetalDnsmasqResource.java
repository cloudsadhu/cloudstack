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
// 
// Automatically generated by addcopyright.py at 01/29/2013
// Apache License, Version 2.0 (the "License"); you may not use this
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// 
// Automatically generated by addcopyright.py at 04/03/2012
package com.cloud.baremetal.networkservice;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.PingRoutingCommand;
import com.cloud.agent.api.routing.DhcpEntryCommand;
import com.cloud.utils.script.Script;
import com.cloud.utils.ssh.SSHCmdHelper;
import com.cloud.vm.VirtualMachine.State;
import com.trilead.ssh2.SCPClient;

public class BaremetalDnsmasqResource extends BaremetalDhcpResourceBase {
	private static final Logger s_logger = Logger.getLogger(BaremetalDnsmasqResource.class);

	public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
		com.trilead.ssh2.Connection sshConnection = null;
		try {
			super.configure(name, params);
			s_logger.debug(String.format("Trying to connect to DHCP server(IP=%1$s, username=%2$s, password=%3$s)", _ip, _username, _password));
			sshConnection = SSHCmdHelper.acquireAuthorizedConnection(_ip, _username, _password);
			if (sshConnection == null) {
				throw new ConfigurationException(
						String.format("Cannot connect to DHCP server(IP=%1$s, username=%2$s, password=%3$s", _ip, _username, _password));
			}

			if (!SSHCmdHelper.sshExecuteCmd(sshConnection, "[ -f '/usr/sbin/dnsmasq' ]")) {
				throw new ConfigurationException("Cannot find dnsmasq at /usr/sbin/dnsmasq on " + _ip);
			}

			SCPClient scp = new SCPClient(sshConnection);
			
			String editHosts = "scripts/network/exdhcp/dnsmasq_edithosts.sh";
			String editHostsPath = Script.findScript("", editHosts);
			if (editHostsPath == null) {
				throw new ConfigurationException("Can not find script dnsmasq_edithosts.sh at " + editHosts);
			}
			scp.put(editHostsPath, "/usr/bin/", "0755");
			
			String prepareDnsmasq = "scripts/network/exdhcp/prepare_dnsmasq.sh";
			String prepareDnsmasqPath = Script.findScript("", prepareDnsmasq);
			if (prepareDnsmasqPath == null) {
				throw new ConfigurationException("Can not find script prepare_dnsmasq.sh at " + prepareDnsmasq);
			}
			scp.put(prepareDnsmasqPath, "/usr/bin/", "0755");

            /*
			String prepareCmd = String.format("sh /usr/bin/prepare_dnsmasq.sh %1$s %2$s %3$s", _gateway, _dns, _ip);
			if (!SSHCmdHelper.sshExecuteCmd(sshConnection, prepareCmd)) {
				throw new ConfigurationException("prepare dnsmasq at " + _ip + " failed");
			}
			*/
			
			s_logger.debug("Dnsmasq resource configure successfully");
			return true;
		} catch (Exception e) {
			s_logger.debug("Dnsmasq resorce configure failed", e);
			throw new ConfigurationException(e.getMessage());
		} finally {
			SSHCmdHelper.releaseSshConnection(sshConnection);
		}
	}
	
	@Override
	public PingCommand getCurrentStatus(long id) {
		com.trilead.ssh2.Connection sshConnection = SSHCmdHelper.acquireAuthorizedConnection(_ip, _username, _password);
		if (sshConnection == null) {
			return null;
		} else {
			SSHCmdHelper.releaseSshConnection(sshConnection);
			return new PingRoutingCommand(getType(), id, new HashMap<String, State>());
		}
	}

	Answer execute(DhcpEntryCommand cmd) {
		com.trilead.ssh2.Connection sshConnection = null;
		try {
			sshConnection = SSHCmdHelper.acquireAuthorizedConnection(_ip, _username, _password);
			if (sshConnection == null) {
				return new Answer(cmd, false, "ssh authenticate failed");
			}
			String addDhcp = String.format("/usr/bin/dnsmasq_edithosts.sh %1$s %2$s %3$s", cmd.getVmMac(), cmd.getVmIpAddress(), cmd.getVmName());
			if (!SSHCmdHelper.sshExecuteCmd(sshConnection, addDhcp)) {
				return new Answer(cmd, false, "add Dhcp entry failed");
			} else {
				return new Answer(cmd);
			}
		} finally {
			SSHCmdHelper.releaseSshConnection(sshConnection);
		}
	}
	
	@Override
	public Answer executeRequest(Command cmd) {
		if (cmd instanceof DhcpEntryCommand) {
			return execute((DhcpEntryCommand)cmd);
		} else {
			return super.executeRequest(cmd);
		}
	}
}
