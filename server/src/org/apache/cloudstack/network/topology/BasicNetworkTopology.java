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

package org.apache.cloudstack.network.topology;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.Pod;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.PublicIpAddress;
import com.cloud.network.VpnUser;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.router.NetworkHelper;
import com.cloud.network.router.VirtualRouter;
import com.cloud.network.rules.DhcpEntryRules;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.FirewallRules;
import com.cloud.network.rules.IpAssociationRules;
import com.cloud.network.rules.LoadBalancingRules;
import com.cloud.network.rules.PasswordToRouterRules;
import com.cloud.network.rules.RuleApplier;
import com.cloud.network.rules.RuleApplierWrapper;
import com.cloud.network.rules.SshKeyToRouterRules;
import com.cloud.network.rules.StaticNat;
import com.cloud.network.rules.StaticNatRules;
import com.cloud.network.rules.UserdataPwdRules;
import com.cloud.network.rules.UserdataToRouterRules;
import com.cloud.network.rules.VirtualNetworkApplianceFactory;
import com.cloud.network.rules.VpnRules;
import com.cloud.network.vpc.NetworkACLItem;
import com.cloud.network.vpc.StaticRouteProfile;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.DomainRouterVO;
import com.cloud.vm.NicProfile;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.State;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.UserVmDao;

@Component
public class BasicNetworkTopology implements NetworkTopology {

    private static final Logger s_logger = Logger.getLogger(BasicNetworkTopology.class);

    @Inject
    protected VirtualNetworkApplianceFactory _virtualNetworkApplianceFactory;

    @Autowired
    @Qualifier("basicNetworkVisitor")
    protected BasicNetworkVisitor _basicVisitor;

    @Inject
    protected DataCenterDao _dcDao;

    @Inject
    protected HostDao _hostDao;

    @Inject
    protected NetworkHelper _nwHelper;

    @Inject
    protected UserVmDao _userVmDao;

    @Override
    public StringBuilder createGuestBootLoadArgs(final NicProfile guestNic, final String defaultDns1, final String defaultDns2, final DomainRouterVO router) {
        return null;
    }

    @Override
    public String retrieveGuestDhcpRange(final NicProfile guestNic, final Network guestNetwork, final DataCenter dc) {
        return null;
    }

    @Override
    public NicProfile retrieveControlNic(final VirtualMachineProfile profile) {
        return null;
    }

    @Override
    public boolean applyStaticRoutes(final List<StaticRouteProfile> staticRoutes, final List<DomainRouterVO> routers) throws ResourceUnavailableException {
        throw new CloudRuntimeException("applyStaticRoutes not implemented in Basic Network Topology.");
    }

    @Override
    public boolean applyNetworkACLs(final Network network, final List<? extends NetworkACLItem> rules, final List<? extends VirtualRouter> routers, final boolean isPrivateGateway)
            throws ResourceUnavailableException {
        throw new CloudRuntimeException("applyNetworkACLs not implemented in Basic Network Topology.");
    }

    @Override
    public boolean setupDhcpForPvlan(final boolean add, final DomainRouterVO router, final Long hostId, final NicProfile nic) throws ResourceUnavailableException {
        throw new CloudRuntimeException("setupDhcpForPvlan not implemented in Basic Network Topology.");
    }

    @Override
    public boolean configDhcpForSubnet(final Network network, final NicProfile nic, final VirtualMachineProfile profile, final DeployDestination dest,
            final List<DomainRouterVO> routers) throws ResourceUnavailableException {

        throw new CloudRuntimeException("configDhcpForSubnet not implemented in Basic Network Topology.");
    }

    @Override
    public boolean applyDhcpEntry(final Network network, final NicProfile nic, final VirtualMachineProfile profile, final DeployDestination dest,
            final List<DomainRouterVO> routers) throws ResourceUnavailableException {

        s_logger.debug("APPLYING DHCP ENTRY RULES");

        final String typeString = "dhcp entry";
        final Long podId = dest.getPod().getId();
        boolean isPodLevelException = false;

        // for user vm in Basic zone we should try to re-deploy vm in a diff pod
        // if it fails to deploy in original pod; so throwing exception with Pod
        // scope
        if (podId != null && profile.getVirtualMachine().getType() == VirtualMachine.Type.User && network.getTrafficType() == TrafficType.Guest
                && network.getGuestType() == Network.GuestType.Shared) {
            isPodLevelException = true;
        }

        final boolean failWhenDisconnect = false;

        DhcpEntryRules dhcpRules = _virtualNetworkApplianceFactory.createDhcpEntryRules(network, nic, profile, dest);

        return applyRules(network, routers, typeString, isPodLevelException, podId, failWhenDisconnect, new RuleApplierWrapper<RuleApplier>(dhcpRules));
    }

    @Override
    public boolean applyUserData(final Network network, final NicProfile nic, final VirtualMachineProfile profile, final DeployDestination dest, final List<DomainRouterVO> routers)
            throws ResourceUnavailableException {

        s_logger.debug("APPLYING USERDATA RULES");

        final String typeString = "userdata and password entry";
        final Long podId = dest.getPod().getId();
        boolean isPodLevelException = false;

        if (podId != null && profile.getVirtualMachine().getType() == VirtualMachine.Type.User && network.getTrafficType() == TrafficType.Guest
                && network.getGuestType() == Network.GuestType.Shared) {
            isPodLevelException = true;
        }

        final boolean failWhenDisconnect = false;

        UserdataPwdRules pwdRules = _virtualNetworkApplianceFactory.createUserdataPwdRules(network, nic, profile, dest);

        return applyRules(network, routers, typeString, isPodLevelException, podId, failWhenDisconnect, new RuleApplierWrapper<RuleApplier>(pwdRules));
    }

    @Override
    public boolean applyLoadBalancingRules(final Network network, final List<LoadBalancingRule> rules, final List<? extends VirtualRouter> routers)
            throws ResourceUnavailableException {

        if (rules == null || rules.isEmpty()) {
            s_logger.debug("No lb rules to be applied for network " + network.getId());
            return true;
        }

        s_logger.debug("APPLYING LOAD BALANCING RULES");

        final String typeString = "loadbalancing rules";
        final boolean isPodLevelException = false;
        final boolean failWhenDisconnect = false;
        final Long podId = null;

        LoadBalancingRules loadBalancingRules = _virtualNetworkApplianceFactory.createLoadBalancingRules(network, rules);

        return applyRules(network, routers, typeString, isPodLevelException, podId, failWhenDisconnect, new RuleApplierWrapper<RuleApplier>(loadBalancingRules));
    }

    @Override
    public boolean applyFirewallRules(final Network network, final List<? extends FirewallRule> rules, final List<? extends VirtualRouter> routers)
            throws ResourceUnavailableException {
        if (rules == null || rules.isEmpty()) {
            s_logger.debug("No firewall rules to be applied for network " + network.getId());
            return true;
        }

        s_logger.debug("APPLYING FIREWALL RULES");

        final String typeString = "firewall rules";
        final boolean isPodLevelException = false;
        final boolean failWhenDisconnect = false;
        final Long podId = null;

        FirewallRules firewallRules = _virtualNetworkApplianceFactory.createFirewallRules(network, rules);

        return applyRules(network, routers, typeString, isPodLevelException, podId, failWhenDisconnect, new RuleApplierWrapper<RuleApplier>(firewallRules));
    }

    @Override
    public boolean applyStaticNats(final Network network, final List<? extends StaticNat> rules, final List<? extends VirtualRouter> routers) throws ResourceUnavailableException {
        if (rules == null || rules.isEmpty()) {
            s_logger.debug("No static nat rules to be applied for network " + network.getId());
            return true;
        }

        s_logger.debug("APPLYING STATIC NAT RULES");

        final String typeString = "static nat rules";
        final boolean isPodLevelException = false;
        final boolean failWhenDisconnect = false;
        final Long podId = null;

        StaticNatRules natRules = _virtualNetworkApplianceFactory.createStaticNatRules(network, rules);

        return applyRules(network, routers, typeString, isPodLevelException, podId, failWhenDisconnect, new RuleApplierWrapper<RuleApplier>(natRules));
    }

    @Override
    public boolean associatePublicIP(final Network network, final List<? extends PublicIpAddress> ipAddress, final List<? extends VirtualRouter> routers)
            throws ResourceUnavailableException {
        if (ipAddress == null || ipAddress.isEmpty()) {
            s_logger.debug("No ip association rules to be applied for network " + network.getId());
            return true;
        }

        s_logger.debug("APPLYING IP RULES");

        final String typeString = "ip association";
        final boolean isPodLevelException = false;
        final boolean failWhenDisconnect = false;
        final Long podId = null;

        IpAssociationRules ipAddresses = _virtualNetworkApplianceFactory.createIpAssociationRules(network, ipAddress);

        return applyRules(network, routers, typeString, isPodLevelException, podId, failWhenDisconnect, new RuleApplierWrapper<RuleApplier>(ipAddresses));
    }

    @Override
    public String[] applyVpnUsers(final Network network, final List<? extends VpnUser> users, final List<DomainRouterVO> routers) throws ResourceUnavailableException {
        if (routers == null || routers.isEmpty()) {
            s_logger.warn("Failed to add/remove VPN users: no router found for account and zone");
            throw new ResourceUnavailableException("Unable to assign ip addresses, domR doesn't exist for network " + network.getId(), DataCenter.class, network.getDataCenterId());
        }

        s_logger.debug("APPLYING VPN RULES");

        boolean agentResults = true;

        for (final DomainRouterVO router : routers) {
            if (router.getState() != State.Running) {
                s_logger.warn("Failed to add/remove VPN users: router not in running state");
                throw new ResourceUnavailableException("Unable to assign ip addresses, domR is not in right state " + router.getState(), DataCenter.class,
                        network.getDataCenterId());
            }

            VpnRules vpnRules = _virtualNetworkApplianceFactory.createVpnRules(network, users);

            // Currently we receive just one answer from the agent. In the
            // future we have to parse individual answers and set
            // results accordingly
            final boolean agentResult = vpnRules.accept(_basicVisitor, router);
            agentResults = agentResults && agentResult;
        }

        final String[] result = new String[users.size()];
        for (int i = 0; i < result.length; i++) {
            if (agentResults) {
                result[i] = null;
            } else {
                result[i] = String.valueOf(agentResults);
            }
        }

        return result;
    }

    @Override
    public boolean savePasswordToRouter(final Network network, final NicProfile nic, final VirtualMachineProfile profile, final List<? extends VirtualRouter> routers)
            throws ResourceUnavailableException {

        _userVmDao.loadDetails((UserVmVO) profile.getVirtualMachine());

        s_logger.debug("SAVE PASSWORD TO ROUTE RULES");

        final String typeString = "save password entry";
        final boolean isPodLevelException = false;
        final boolean failWhenDisconnect = false;
        final Long podId = null;

        PasswordToRouterRules routerRules = _virtualNetworkApplianceFactory.createPasswordToRouterRules(network, nic, profile);

        return applyRules(network, routers, typeString, isPodLevelException, podId, failWhenDisconnect, new RuleApplierWrapper<RuleApplier>(routerRules));
    }

    @Override
    public boolean saveSSHPublicKeyToRouter(final Network network, final NicProfile nic, final VirtualMachineProfile profile, final List<? extends VirtualRouter> routers,
            final String sshPublicKey) throws ResourceUnavailableException {
        s_logger.debug("SAVE SSH PUB KEY TO ROUTE RULES");

        final String typeString = "save SSHkey entry";
        final boolean isPodLevelException = false;
        final boolean failWhenDisconnect = false;
        final Long podId = null;

        SshKeyToRouterRules keyToRouterRules = _virtualNetworkApplianceFactory.createSshKeyToRouterRules(network, nic, profile, sshPublicKey);

        return applyRules(network, routers, typeString, isPodLevelException, podId, failWhenDisconnect, new RuleApplierWrapper<RuleApplier>(keyToRouterRules));
    }

    @Override
    public boolean saveUserDataToRouter(final Network network, final NicProfile nic, final VirtualMachineProfile profile, final List<? extends VirtualRouter> routers)
            throws ResourceUnavailableException {
        s_logger.debug("SAVE USERDATA TO ROUTE RULES");

        final String typeString = "save userdata entry";
        final boolean isPodLevelException = false;
        final boolean failWhenDisconnect = false;
        final Long podId = null;

        UserdataToRouterRules userdataToRouterRules = _virtualNetworkApplianceFactory.createUserdataToRouterRules(network, nic, profile);

        return applyRules(network, routers, typeString, isPodLevelException, podId, failWhenDisconnect, new RuleApplierWrapper<RuleApplier>(userdataToRouterRules));
    }

    @Override
    public boolean applyRules(final Network network, final List<? extends VirtualRouter> routers, final String typeString, final boolean isPodLevelException, final Long podId,
            final boolean failWhenDisconnect, final RuleApplierWrapper<RuleApplier> ruleApplierWrapper) throws ResourceUnavailableException {

        if (routers == null || routers.isEmpty()) {
            s_logger.warn("Unable to apply " + typeString + ", virtual router doesn't exist in the network " + network.getId());
            throw new ResourceUnavailableException("Unable to apply " + typeString, DataCenter.class, network.getDataCenterId());
        }

        RuleApplier ruleApplier = ruleApplierWrapper.getRuleType();

        final DataCenter dc = _dcDao.findById(network.getDataCenterId());
        final boolean isZoneBasic = dc.getNetworkType() == NetworkType.Basic;

        // isPodLevelException and podId is only used for basic zone
        assert !(!isZoneBasic && isPodLevelException || isZoneBasic && isPodLevelException && podId == null);

        final List<VirtualRouter> connectedRouters = new ArrayList<VirtualRouter>();
        final List<VirtualRouter> disconnectedRouters = new ArrayList<VirtualRouter>();
        boolean result = true;
        final String msg = "Unable to apply " + typeString + " on disconnected router ";
        for (final VirtualRouter router : routers) {
            if (router.getState() == State.Running) {
                s_logger.debug("Applying " + typeString + " in network " + network);

                if (router.isStopPending()) {
                    if (_hostDao.findById(router.getHostId()).getState() == Status.Up) {
                        throw new ResourceUnavailableException("Unable to process due to the stop pending router " + router.getInstanceName()
                                + " haven't been stopped after it's host coming back!", DataCenter.class, router.getDataCenterId());
                    }
                    s_logger.debug("Router " + router.getInstanceName() + " is stop pending, so not sending apply " + typeString + " commands to the backend");
                    continue;
                }

                try {
                    ruleApplier.accept(_basicVisitor, router);

                    connectedRouters.add(router);
                } catch (final AgentUnavailableException e) {
                    s_logger.warn(msg + router.getInstanceName(), e);
                    disconnectedRouters.add(router);
                }

                // If rules fail to apply on one domR and not due to
                // disconnection, no need to proceed with the rest
                if (!result) {
                    if (isZoneBasic && isPodLevelException) {
                        throw new ResourceUnavailableException("Unable to apply " + typeString + " on router ", Pod.class, podId);
                    }
                    throw new ResourceUnavailableException("Unable to apply " + typeString + " on router ", DataCenter.class, router.getDataCenterId());
                }

            } else if (router.getState() == State.Stopped || router.getState() == State.Stopping) {
                s_logger.debug("Router " + router.getInstanceName() + " is in " + router.getState() + ", so not sending apply " + typeString + " commands to the backend");
            } else {
                s_logger.warn("Unable to apply " + typeString + ", virtual router is not in the right state " + router.getState());
                if (isZoneBasic && isPodLevelException) {
                    throw new ResourceUnavailableException("Unable to apply " + typeString + ", virtual router is not in the right state", Pod.class, podId);
                }
                throw new ResourceUnavailableException("Unable to apply " + typeString + ", virtual router is not in the right state", DataCenter.class, router.getDataCenterId());
            }
        }

        if (!connectedRouters.isEmpty()) {
            if (!isZoneBasic && !disconnectedRouters.isEmpty() && disconnectedRouters.get(0).getIsRedundantRouter()) {
                // These disconnected redundant virtual routers are out of sync now, stop them for synchronization
                _nwHelper.handleSingleWorkingRedundantRouter(connectedRouters, disconnectedRouters, msg);
            }
        } else if (!disconnectedRouters.isEmpty()) {
            for (final VirtualRouter router : disconnectedRouters) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug(msg + router.getInstanceName() + "(" + router.getId() + ")");
                }
            }
            if (isZoneBasic && isPodLevelException) {
                throw new ResourceUnavailableException(msg, Pod.class, podId);
            }
            throw new ResourceUnavailableException(msg, DataCenter.class, disconnectedRouters.get(0).getDataCenterId());
        }

        result = true;
        if (failWhenDisconnect) {
            result = !connectedRouters.isEmpty();
        }
        return result;
    }
}