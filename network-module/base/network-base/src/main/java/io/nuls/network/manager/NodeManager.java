/*
 * MIT License
 *
 * Copyright (c) 2017-2018 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package io.nuls.network.manager;

import io.netty.channel.Channel;
import io.netty.channel.socket.SocketChannel;
import io.nuls.core.tools.date.DateUtil;
import io.nuls.core.tools.log.Log;
import io.nuls.core.tools.network.IpUtil;
import io.nuls.core.tools.str.StringUtils;
import io.nuls.kernel.context.NulsContext;
import io.nuls.kernel.func.TimeService;
import io.nuls.kernel.thread.manager.TaskManager;
import io.nuls.network.constant.NetworkConstant;
import io.nuls.network.constant.NetworkParam;
import io.nuls.network.model.Node;
import io.nuls.network.model.NodeGroup;
import io.nuls.network.protocol.message.NetworkMessageBody;
import io.nuls.network.protocol.message.P2PNodeBody;
import io.nuls.network.protocol.message.P2PNodeMessage;
import io.nuls.network.storage.service.NetworkStorageService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class NodeManager implements Runnable {

    private static NodeManager instance = new NodeManager();

    private NodeManager() {

    }

    public static NodeManager getInstance() {
        return instance;
    }

    private NetworkParam networkParam = NetworkParam.getInstance();

    private Map<String, NodeGroup> nodeGroups = new ConcurrentHashMap<>();

    private ReentrantLock lock = new ReentrantLock();

    private ConnectionManager connectionManager;

    private NodeDiscoverHandler nodeDiscoverHandler;

    private NetworkStorageService networkStorageService;

    private BroadcastHandler broadcastHandler;

    boolean running;

    //是否已尝试过自己连接自己
    volatile boolean connectedMySelf;
    //当前节点是否是种子节点
    volatile boolean isSeed;

    private List<Node> seedNodes;
    //存放断开连接的节点
    private Map<String, Node> disConnectNodes = new ConcurrentHashMap<>();
    //存放连接成功但还未握手成功的节点
    private Map<String, Node> connectedNodes = new ConcurrentHashMap<>();
    //存放握手成功的节点
    private Map<String, Node> handShakeNodes = new ConcurrentHashMap<>();

    /**
     * 初始化主动连接节点组合(outGroup)被动连接节点组(inGroup)
     */
    public void init() {
        connectionManager = ConnectionManager.getInstance();
        nodeDiscoverHandler = NodeDiscoverHandler.getInstance();
        broadcastHandler = BroadcastHandler.getInstance();
        // init default NodeGroup
        NodeGroup inNodes = new NodeGroup(NetworkConstant.NETWORK_NODE_IN_GROUP);
        NodeGroup outNodes = new NodeGroup(NetworkConstant.NETWORK_NODE_OUT_GROUP);
        nodeGroups.put(inNodes.getName(), inNodes);
        nodeGroups.put(outNodes.getName(), outNodes);
        //判断自己是否是种子节点
        for (String ip : IpUtil.getIps()) {
            if (isSeedNode(ip)) {
//                networkParam.setMaxInCount(networkParam.getMaxInCount() * 2);
                isSeed = true;
            }
        }
    }

    /**
     * 启动的时候，从数据库里取出所有的节点，尝试连接
     * 同时开启获取对方最新信息的线程
     */
    public void start() {
        //获取我自己的外网ip，防止自己连自己外网的情况出现
        String externalIp = getNetworkStorageService().getExternalIp();
        if (externalIp != null) {
            networkParam.getLocalIps().add(externalIp);
        }

        List<Node> nodeList = getNetworkStorageService().getLocalNodeList();
        nodeList.addAll(getSeedNodes());
        for (Node node : nodeList) {
            addNode(node);
        }
        running = true;
        TaskManager.createAndRunThread(NetworkConstant.NETWORK_MODULE_ID, "NetworkNodeManager", this);
        nodeDiscoverHandler.start();
    }

    /**
     * 重置网络节点
     */
    public void reset() {
        Log.debug("---!!!!!!!---------------network nodeManager reset---------------!!!!!!-----");
//        for (Node node : disConnectNodes.values()) {
//            node.setFailCount(NetworkConstant.CONEECT_FAIL_MAX_COUNT);
//        }
        for (Node node : handShakeNodes.values()) {
            removeNode(node);
        }
    }

    public Node getNode(String nodeId) {
        Node node = disConnectNodes.get(nodeId);
        if (node == null) {
            node = connectedNodes.get(nodeId);
        }
        if (node == null) {
            node = handShakeNodes.get(nodeId);
        }
        return node;
    }

    public Node getHandshakeNode(String nodeId) {
        Node node = handShakeNodes.get(nodeId);
        return node;
    }

    /**
     * 获取当前所有节点（包括未连接成功和已连接的）
     */
    public Map<String, Node> getNodes() {
        Map<String, Node> nodeMap = new HashMap<>();
        nodeMap.putAll(disConnectNodes);
        nodeMap.putAll(connectedNodes);
        nodeMap.putAll(handShakeNodes);
        return nodeMap;
    }

    public List<Node> getCanConnectNodes() {
        List nodeList = new ArrayList();
        for (Node node : disConnectNodes.values()) {
            if (node.getType() == Node.OUT && node.isCanConnect()) {
                nodeList.add(node);
            }
        }
        for (Node node : connectedNodes.values()) {
            if (node.getType() == Node.OUT) {
                nodeList.add(node);
            }
        }
        for (Node node : handShakeNodes.values()) {
            if (node.getType() == Node.OUT) {
                nodeList.add(node);
            }
        }
        return nodeList;
    }

    public Map<String, Node> getConnectedNodes() {
        Map<String, Node> nodeMap = new HashMap<>();
        nodeMap.putAll(connectedNodes);
        nodeMap.putAll(handShakeNodes);
        return nodeMap;
    }

    public Collection<Node> getAvailableNodes() {
        return handShakeNodes.values();
    }

    public NodeGroup getNodeGroup(String groupName) {
        return nodeGroups.get(groupName);
    }

    /**
     * 添加主动连接节点，并创建连接
     */
    public boolean addNode(Node node) {
        //判断是否是本地地址
        if (networkParam.getLocalIps().contains(node.getIp())) {
            return false;
        }
        if (node.getStatus() != Node.WAIT) {
            return false;
        }
        lock.lock();
        try {
            //同一ip地址，不再重复连接
            Collection<Node> nodeMap = getNodes().values();
            int count = 0;
            for (Node n : nodeMap) {
                if (node.getIp().equals(n.getIp()) && n.getType() == Node.OUT) {
                    return false;
                }
                if (node.isCanConnect()) {
                    count++;
                }
            }
            if (count >= 50) {
                return false;
            }

            node.setType(Node.OUT);
            node.setTestConnect(false);
            disConnectNodes.put(node.getId(), node);
            connectionManager.connectionNode(node);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 处理已经成功连接的节点
     */
    public boolean processConnectedNode(Node node, Channel channel) {
        lock.lock();
        try {
            if (connectedNodes.containsKey(node.getId()) || handShakeNodes.containsKey(node.getId())) {
                return false;
            }
            //主动连接时，不能连同一ip
            if (node.getType() == Node.OUT) {
                for (Node n : getConnectedNodes().values()) {
                    if (n.getIp().equals(node.getIp())) {
                        return false;
                    }
                }
            }
            node.setChannel(channel);
            disConnectNodes.remove(node.getId());
            connectedNodes.put(node.getId(), node);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void removeNode(String nodeId) {
        Node node = getNode(nodeId);
        if (node != null) {
            removeNode(node);
        } else {
            Log.info("------------remove node is null-----------" + nodeId);
            getNetworkStorageService().deleteNode(nodeId);
        }
    }

    /**
     * 删除节点分为主动删除和被动删除，当主动删除节点时，
     * 先需要关闭连接，之后再删除节点
     */
    public void removeNode(Node node) {
        lock.lock();
        try {
            if (node.getChannel() != null && node.getChannel().isActive()) {
                node.getChannel().close();
                return;
            }
            node.destroy();
            removeNodeFromGroup(node);
            removeNodeHandler(node);
        } finally {
            lock.unlock();
        }
    }

    public void removeHandshakeNode(String nodeId) {
        Node node = getHandshakeNode(nodeId);
        if (node != null) {
            removeNode(node);
        } else {
//            Log.info("------------removeHandshakeNode node is null-----------" + nodeId);
            getNetworkStorageService().deleteNode(nodeId);
        }
    }

    private void removeNodeHandler(Node node) {
        // 被动节点、种子节点、非法节点直接删除
        if (node.getType() == Node.BAD || node.getType() == Node.IN) {
            disConnectNodes.remove(node.getId());
            connectedNodes.remove(node.getId());
            handShakeNodes.remove(node.getId());
            if (node.getStatus() == Node.BAD) {
                getNetworkStorageService().deleteNode(node.getId());
            }
            return;
        }
        if (isSeedNode(node.getIp())) {
            disConnectNodes.remove(node.getId());
            connectedNodes.remove(node.getId());
            handShakeNodes.remove(node.getId());
            return;
        }
        //如果是本机ip地址，直接删除
        if (isSeedNode(node.getIp()) || networkParam.getLocalIps().contains(node.getIp())) {
            disConnectNodes.remove(node.getId());
            return;
        }

        if (connectedNodes.containsKey(node.getId())) {
            connectedNodes.remove(node.getId());
        }
        if (handShakeNodes.containsKey(node.getId())) {
            handShakeNodes.remove(node.getId());
        }

        if (node.getFailCount() <= NetworkConstant.CONNECT_FAIL_MAX_COUNT) {
            node.setLastFailTime(TimeService.currentTimeMillis());
            if (!disConnectNodes.containsKey(node.getId())) {
                disConnectNodes.put(node.getId(), node);
            }
        } else {
            disConnectNodes.remove(node.getId());
            getNetworkStorageService().deleteNode(node.getId());
        }
    }

    public boolean handshakeNode(String groupName, Node node, NetworkMessageBody versionMessage) {
        lock.lock();
        try {
            if (!checkFullHandShake(node)) {
                return false;
            }
            if (!connectedNodes.containsKey(node.getId())) {
                return false;
            }
            node.setStatus(Node.HANDSHAKE);
            node.setBestBlockHash(versionMessage.getBestBlockHash());
            node.setBestBlockHeight(versionMessage.getBestBlockHeight());

            connectedNodes.remove(node.getId());
            handShakeNodes.put(node.getId(), node);
            return addNodeToGroup(groupName, node);
        } finally {
            lock.unlock();
        }
    }

    public void saveNode(Node node) {
        if (!isSeedNode(node.getIp())) {
            getNetworkStorageService().saveNode(node);
        }

    }

    public void saveExternalIp(String ip) {
        NetworkParam.getInstance().getLocalIps().add(ip);
        getNetworkStorageService().saveExternalIp(ip);
    }

    private NetworkStorageService getNetworkStorageService() {
        if (null == this.networkStorageService) {
            this.networkStorageService = NulsContext.getServiceBean(NetworkStorageService.class);
        }
        return this.networkStorageService;
    }

    public void tryToConnectMySelf() {
        String externalIp = getNetworkStorageService().getExternalIp();
        if (StringUtils.isBlank(externalIp)) {
            return;
        }
        //当非服务器节点收到自己的外网IP时，尝试连接自己外网ip，看能否连通
        if (!connectedMySelf) {
            connectedMySelf = true;
            Node node = new Node();
            node.setIp(externalIp);
            node.setPort(networkParam.getPort());
            node.setSeverPort(networkParam.getPort());
            node.setType(Node.OUT);
            connectionManager.connectionNode(node);
        }
    }

    /**
     * 广播本机外网服务器节点信息
     */
    public void broadNodeSever() {
        String externalIp = getNetworkStorageService().getExternalIp();
        if (!StringUtils.isBlank(externalIp)) {
            P2PNodeBody p2PNodeBody = new P2PNodeBody(externalIp, networkParam.getPort());
            P2PNodeMessage message = new P2PNodeMessage(p2PNodeBody);
            broadcastHandler.broadcastToAllNode(message, null, true, 100);
        }
    }

    private boolean checkFullHandShake(Node node) {
        if (node.getType() == Node.IN) {
            NodeGroup inGroup = getNodeGroup(NetworkConstant.NETWORK_NODE_IN_GROUP);
            return inGroup.size() < networkParam.getMaxInCount();
        } else {
            NodeGroup outGroup = getNodeGroup(NetworkConstant.NETWORK_NODE_OUT_GROUP);
            return outGroup.size() < networkParam.getMaxOutCount();
        }
    }

    /**
     * 添加节点到节点组
     */
    public boolean addNodeToGroup(String groupName, Node node) {
        NodeGroup nodeGroup = nodeGroups.get(groupName);
        if (nodeGroup == null) {
            //todo throw new NulsExcetpion
            //throw new RuntimeException("group not found");
            return false;
        }
        if (groupName.equals(NetworkConstant.NETWORK_NODE_IN_GROUP) && nodeGroup.size() >= networkParam.getMaxInCount()) {
            return false;
        }
        if (groupName.equals(NetworkConstant.NETWORK_NODE_OUT_GROUP) && nodeGroup.size() >= networkParam.getMaxOutCount()) {
            return false;
        }
        node.addGroup(groupName);
        nodeGroup.addNode(node);
        return true;
    }

    private void removeNodeFromGroup(Node node) {
        for (String groupName : node.getGroupSet()) {
            NodeGroup group = nodeGroups.get(groupName);
            if (group != null) {
                group.removeNode(node.getId());
            }
        }
        node.getGroupSet().clear();
    }

    /**
     * 获取种子节点
     */
    public List<Node> getSeedNodes() {
        if (seedNodes != null) {
            return seedNodes;
        }
        seedNodes = new ArrayList<>();
        for (String seedIp : networkParam.getSeedIpList()) {
            String[] ipPort = seedIp.split(":");
            seedNodes.add(new Node(ipPort[0], Integer.parseInt(ipPort[1]), Integer.parseInt(ipPort[1]), Node.OUT));
        }
        return seedNodes;
    }

    /**
     * 是否是种子节点
     */
    public boolean isSeedNode(String ip) {
        for (Node node : getSeedNodes()) {
            if (node.getIp().equals(ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 随机保留2个种子节点的连接，其他的全部断开
     */
    private void removeSeedNode() {
        Collection<Node> nodes = handShakeNodes.values();
        int count = 0;
        List<String> seedIpList = networkParam.getSeedIpList();
        Collections.shuffle(seedIpList);

        for (Node n : nodes) {
            if (seedIpList.contains(n.getIp())) {
                count++;
                if (count > 1) {
                    removeNode(n);
                }
            }
        }
    }

    private boolean checkIpExist(String ip) {
        Collection<Node> nodeMap = getNodes().values();
        for (Node node : nodeMap) {
            if (node.getIp().equals(ip)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        while (running) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            /*System.out.println("--------disConnectNodes:" + disConnectNodes.size());
            for (Node node : disConnectNodes.values()) {
                System.out.println(node.toString());
            }

            System.out.println("--------connectedNodes:" + connectedNodes.size());
            for (Node node : connectedNodes.values()) {
                System.out.println(node.toString());
            }

            System.out.println("--------handShakeNodes:" + handShakeNodes.size());
            for (Node node : handShakeNodes.values()) {
                System.out.println(node.toString());
            }*/

            if (handShakeNodes.size() > 9) {
                removeSeedNode();
            } else if (handShakeNodes.size() <= 2) {
                //如果已连接成功数太少，立刻尝试连接种子节点
                for (Node node : getSeedNodes()) {
                    addNode(node);
                }
            } else if (handShakeNodes.size() < networkParam.getMaxOutCount() && connectedNodes.size() == 0) {
                for (Node node : disConnectNodes.values()) {
                    if (node.isCanConnect() && node.getStatus() == Node.WAIT) {
                        connectionManager.connectionNode(node);
                    }
                }
            }

            //定期尝试重新连接，检测网络节点
            long now = TimeService.currentTimeMillis();
            for (Node node : disConnectNodes.values()) {
                if (node.getStatus() == Node.WAIT) {
                    if (node.isCanConnect() && now > node.getLastFailTime() + 5 * DateUtil.MINUTE_TIME) {
                        connectionManager.connectionNode(node);
                    } else if (now > node.getLastFailTime() + node.getFailCount() * DateUtil.MINUTE_TIME) {
                        connectionManager.connectionNode(node);
                    }
                }
            }
        }
    }

    public void shutdown() {
        running = false;
        for (Node node : handShakeNodes.values()) {
            if(node.getType() == Node.OUT) {
                removeNode(node);
            }
        }
    }
}
