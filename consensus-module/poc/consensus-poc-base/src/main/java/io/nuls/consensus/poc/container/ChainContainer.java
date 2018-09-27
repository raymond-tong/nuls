/*
 * *
 *  * MIT License
 *  *
 *  * Copyright (c) 2017-2018 nuls.io
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 *
 */

package io.nuls.consensus.poc.container;

import io.nuls.consensus.constant.ConsensusConstant;
import io.nuls.consensus.poc.constant.PocConsensusConstant;
import io.nuls.consensus.poc.context.PocConsensusContext;
import io.nuls.consensus.poc.manager.RoundManager;
import io.nuls.consensus.poc.model.*;
import io.nuls.consensus.poc.protocol.constant.PocConsensusErrorCode;
import io.nuls.consensus.poc.protocol.constant.PunishReasonEnum;
import io.nuls.consensus.poc.protocol.constant.PunishType;
import io.nuls.consensus.poc.protocol.entity.Agent;
import io.nuls.consensus.poc.protocol.entity.Deposit;
import io.nuls.consensus.poc.protocol.entity.RedPunishData;
import io.nuls.consensus.poc.protocol.tx.*;
import io.nuls.consensus.poc.storage.po.PunishLogPo;
import io.nuls.consensus.poc.util.ConsensusTool;
import io.nuls.core.tools.crypto.Base58;
import io.nuls.core.tools.log.BlockLog;
import io.nuls.core.tools.log.Log;
import io.nuls.kernel.constant.KernelErrorCode;
import io.nuls.kernel.context.NulsContext;
import io.nuls.kernel.func.TimeService;
import io.nuls.kernel.model.*;
import io.nuls.kernel.utils.AddressTool;
import io.nuls.kernel.validate.ValidateResult;
import io.nuls.protocol.constant.ProtocolConstant;
import io.nuls.protocol.model.tx.CoinBaseTransaction;
import io.nuls.protocol.service.BlockService;
import sun.nio.cs.FastCharsetProvider;

import java.util.*;

/**
 * @author ln
 */
public class ChainContainer implements Cloneable {

    private Chain chain;
    private RoundManager roundManager;

    public ChainContainer(Chain chain) {
        this.chain = chain;
        roundManager = new RoundManager(chain);
    }

    public boolean addBlock(Block block) {

        if (!chain.getEndBlockHeader().getHash().equals(block.getHeader().getPreHash()) ||
                chain.getEndBlockHeader().getHeight() + 1 != block.getHeader().getHeight()) {
            return false;
        }

        List<Block> blockList = chain.getBlockList();
        List<BlockHeader> blockHeaderList = chain.getBlockHeaderList();

        List<Agent> agentList = chain.getAgentList();
        List<Deposit> depositList = chain.getDepositList();
        List<PunishLogPo> yellowList = chain.getYellowPunishList();
        List<PunishLogPo> redList = chain.getRedPunishList();

        long height = block.getHeader().getHeight();
        BlockExtendsData extendsData = new BlockExtendsData(block.getHeader().getExtend());
        List<Transaction> txs = block.getTxs();
        for (Transaction tx : txs) {
            int txType = tx.getType();
            if (txType == ConsensusConstant.TX_TYPE_REGISTER_AGENT) {
                // Registered agent transaction
                // 注册代理交易
                CreateAgentTransaction registerAgentTx = (CreateAgentTransaction) tx;

                CreateAgentTransaction agentTx = registerAgentTx.clone();
                Agent agent = agentTx.getTxData();
                agent.setDelHeight(-1L);
                agent.setBlockHeight(height);
                agent.setTxHash(agentTx.getHash());
                agent.setTime(agentTx.getTime());

                agentList.add(agent);
            } else if (txType == ConsensusConstant.TX_TYPE_JOIN_CONSENSUS) {

                // 加入共识交易，设置该交易的高度和删除高度，然后加入列表
                DepositTransaction joinConsensusTx = (DepositTransaction) tx;

                DepositTransaction depositTx = joinConsensusTx.clone();

                Deposit deposit = depositTx.getTxData();
                deposit.setDelHeight(-1L);
                deposit.setBlockHeight(height);
                deposit.setTxHash(depositTx.getHash());
                deposit.setTime(depositTx.getTime());
                depositList.add(deposit);

            } else if (txType == ConsensusConstant.TX_TYPE_CANCEL_DEPOSIT) {

                CancelDepositTransaction cancelDepositTx = (CancelDepositTransaction) tx;

                NulsDigestData joinHash = cancelDepositTx.getTxData().getJoinTxHash();

                Iterator<Deposit> it = depositList.iterator();
                while (it.hasNext()) {
                    Deposit deposit = it.next();
                    cancelDepositTx.getTxData().setAddress(deposit.getAddress());
                    if (deposit.getTxHash().equals(joinHash)) {
                        if (deposit.getDelHeight() == -1L) {
                            deposit.setDelHeight(height);
                        }
                        break;
                    }
                }
            } else if (txType == ConsensusConstant.TX_TYPE_STOP_AGENT) {

                StopAgentTransaction stopAgentTx = (StopAgentTransaction) tx;

                NulsDigestData agentHash = stopAgentTx.getTxData().getCreateTxHash();

                Iterator<Deposit> it = depositList.iterator();
                while (it.hasNext()) {
                    Deposit deposit = it.next();
                    if (deposit.getAgentHash().equals(agentHash) && deposit.getDelHeight() == -1L) {
                        deposit.setDelHeight(height);
                    }
                }

                Iterator<Agent> ita = agentList.iterator();
                while (ita.hasNext()) {
                    Agent agent = ita.next();
                    stopAgentTx.getTxData().setAddress(agent.getAgentAddress());
                    if (agent.getTxHash().equals(agentHash)) {
                        if (agent.getDelHeight() == -1L) {
                            agent.setDelHeight(height);
                        }
                        break;
                    }
                }
            } else if (txType == ConsensusConstant.TX_TYPE_RED_PUNISH) {
                RedPunishTransaction transaction = (RedPunishTransaction) tx;
                RedPunishData redPunishData = transaction.getTxData();
                PunishLogPo po = new PunishLogPo();
                po.setAddress(redPunishData.getAddress());
                po.setHeight(height);
                po.setRoundIndex(extendsData.getRoundIndex());
                po.setTime(tx.getTime());
                po.setType(PunishType.RED.getCode());
                redList.add(po);
                for (Agent agent : agentList) {
                    if (!Arrays.equals(agent.getAgentAddress(), po.getAddress())) {
                        continue;
                    }
                    if (agent.getDelHeight() > 0) {
                        continue;
                    }
                    agent.setDelHeight(height);
                    for (Deposit deposit : depositList) {
                        if (!deposit.getAgentHash().equals(agent.getTxHash())) {
                            continue;
                        }
                        if (deposit.getDelHeight() > 0) {
                            continue;
                        }
                        deposit.setDelHeight(height);
                    }
                }
            } else if (txType == ConsensusConstant.TX_TYPE_YELLOW_PUNISH) {
                YellowPunishTransaction transaction = (YellowPunishTransaction) tx;
                for (byte[] bytes : transaction.getTxData().getAddressList()) {
                    PunishLogPo po = new PunishLogPo();
                    po.setAddress(bytes);
                    po.setHeight(height);
                    po.setRoundIndex(extendsData.getRoundIndex());
                    po.setTime(tx.getTime());
                    po.setType(PunishType.YELLOW.getCode());
                    yellowList.add(po);
                }
            }
        }

        chain.setEndBlockHeader(block.getHeader());
        blockList.add(block);
        blockHeaderList.add(block.getHeader());

        return true;
    }

    public Result verifyBlock(Block block) {
        return verifyBlock(block, false, true);
    }


    public Result verifyBlock(Block block, boolean isDownload, boolean isNeedCheckCoinBaseTx) {

        if (block == null || chain.getEndBlockHeader() == null) {
            return Result.getFailed();
        }

        BlockHeader blockHeader = block.getHeader();
        if (blockHeader == null) {
            return Result.getFailed();
        }
        //todo 是否是重复验证
        block.verifyWithException();

        // Verify that the block is properly connected
        // 验证区块是否正确连接
        NulsDigestData preHash = blockHeader.getPreHash();

        BlockHeader bestBlockHeader = chain.getEndBlockHeader();

        if (!preHash.equals(bestBlockHeader.getHash())) {
            Log.error("block height " + blockHeader.getHeight() + " prehash is error! hash :" + blockHeader.getHash());
            return Result.getFailed();
        }

        BlockExtendsData extendsData = new BlockExtendsData(blockHeader.getExtend());
        BlockExtendsData bestExtendsData = new BlockExtendsData(bestBlockHeader.getExtend());
        //判断轮次信息是否正确
        if (extendsData.getRoundIndex() < bestExtendsData.getRoundIndex() ||
                (extendsData.getRoundIndex() == bestExtendsData.getRoundIndex() && extendsData.getPackingIndexOfRound() <= bestExtendsData.getPackingIndexOfRound())) {
            Log.error("new block rounddata error, block height : " + blockHeader.getHeight() + " , hash :" + blockHeader.getHash());
            return Result.getFailed();
        }

        MeetingRound currentRound = roundManager.getCurrentRound();

        if (isDownload && currentRound.getIndex() > extendsData.getRoundIndex()) {
            MeetingRound round = roundManager.getRoundByIndex(extendsData.getRoundIndex());
            if (round != null) {
                currentRound = round;
            }
        }

        boolean hasChangeRound = false;

        // Verify that the block round and time are correct
        // 验证区块轮次和时间是否正确
//        if(roundData.getRoundIndex() > currentRound.getIndex()) {
//            Log.error("block height " + blockHeader.getHeight() + " round index is error!");
//            Result.getFailed();
//        }
        if (extendsData.getRoundIndex() > currentRound.getIndex()) {
            if (extendsData.getRoundStartTime() > TimeService.currentTimeMillis() + ProtocolConstant.BLOCK_TIME_INTERVAL_MILLIS) {
                Log.error("block height " + blockHeader.getHeight() + " round startTime is error, greater than current time! hash :" + blockHeader.getHash());
                return Result.getFailed();
            }
            if (!isDownload && (extendsData.getRoundStartTime() + (extendsData.getPackingIndexOfRound() - 1) * ProtocolConstant.BLOCK_TIME_INTERVAL_MILLIS) > TimeService.currentTimeMillis() + ProtocolConstant.BLOCK_TIME_INTERVAL_MILLIS) {
                Log.error("block height " + blockHeader.getHeight() + " is the block of the future and received in advance! hash :" + blockHeader.getHash());
                return Result.getFailed();
            }
            if (extendsData.getRoundStartTime() < currentRound.getEndTime()) {
                Log.error("block height " + blockHeader.getHeight() + " round index and start time not match! hash :" + blockHeader.getHash());
                return Result.getFailed();
            }
            MeetingRound tempRound = roundManager.getNextRound(extendsData, !isDownload);
            if (tempRound.getIndex() > currentRound.getIndex()) {
                tempRound.setPreRound(currentRound);
                hasChangeRound = true;
            }
            currentRound = tempRound;
        } else if (extendsData.getRoundIndex() < currentRound.getIndex()) {
            MeetingRound preRound = currentRound.getPreRound();
            while (preRound != null) {
                if (extendsData.getRoundIndex() == preRound.getIndex()) {
                    currentRound = preRound;
                    break;
                }
                preRound = preRound.getPreRound();
            }
        }

        if (extendsData.getRoundIndex() != currentRound.getIndex() || extendsData.getRoundStartTime() != currentRound.getStartTime()) {
            Log.error("block height " + blockHeader.getHeight() + " round startTime is error! hash :" + blockHeader.getHash());
            return Result.getFailed();
        }

        Log.debug(currentRound.toString());

        if (extendsData.getConsensusMemberCount() != currentRound.getMemberCount()) {
            Log.error("block height " + blockHeader.getHeight() + " packager count is error! hash :" + blockHeader.getHash());
            return Result.getFailed();
        }
        // Verify that the packager is correct
        // 验证打包人是否正确
        MeetingMember member = currentRound.getMember(extendsData.getPackingIndexOfRound());
        if (!Arrays.equals(member.getPackingAddress(), blockHeader.getPackingAddress())) {
            Log.error("block height " + blockHeader.getHeight() + " packager error! hash :" + blockHeader.getHash());
            return Result.getFailed();
        }

        if (member.getPackEndTime() != block.getHeader().getTime()) {
            Log.error("block height " + blockHeader.getHeight() + " time error! hash :" + blockHeader.getHash());
            return Result.getFailed();
        }

        boolean success = verifyBaseTx(block, currentRound, member);
        if (!success) {
            Log.error("block height " + blockHeader.getHeight() + " verify tx error! hash :" + blockHeader.getHash());
            return Result.getFailed();
        }
        // 由于合约交易的特殊性，此处校验逻辑在首次验证区块时移动到所有交易验证完之后
        if (isNeedCheckCoinBaseTx) {
            boolean isCorrect = verifyCoinBaseTx(block, currentRound, member);
            if (!isCorrect) {
                return Result.getFailed();
            }
        }

        if (hasChangeRound) {
            roundManager.addRound(currentRound);
        }
        Object[] objects = new Object[]{currentRound, member};
        return Result.getSuccess().setData(objects);
    }

    public boolean verifyCoinBaseTx(Block block, MeetingRound currentRound, MeetingMember member) {
        Transaction tx = block.getTxs().get(0);
        CoinBaseTransaction coinBaseTransaction = ConsensusTool.createCoinBaseTx(member, block.getTxs(), currentRound, block.getHeader().getHeight() + PocConsensusConstant.COINBASE_UNLOCK_HEIGHT);
        if (null == coinBaseTransaction || !tx.getHash().equals(coinBaseTransaction.getHash())) {
            BlockLog.debug("the coin base tx is wrong! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
            Log.error("the coin base tx is wrong! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
            return false;
        }
        return true;
    }

    // Verify conbase transactions and penalties
    // 验证conbase交易和处罚交易
    public boolean verifyBaseTx(Block block, MeetingRound currentRound, MeetingMember member) {
        List<Transaction> txs = block.getTxs();
        Transaction tx = txs.get(0);
        if (tx.getType() != ProtocolConstant.TX_TYPE_COINBASE) {
            BlockLog.debug("Coinbase transaction order wrong! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
//            Log.error("Coinbase transaction order wrong! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
            return false;
        }
        List<RedPunishTransaction> redPunishTxList = new ArrayList<>();
        YellowPunishTransaction yellowPunishTx = null;
        for (int i = 1; i < txs.size(); i++) {
            Transaction transaction = txs.get(i);
            if (transaction.getType() == ProtocolConstant.TX_TYPE_COINBASE) {
                BlockLog.debug("Coinbase transaction more than one! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
//                Log.error("Coinbase transaction more than one! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
                return false;
            }
            if (null == yellowPunishTx && transaction.getType() == ConsensusConstant.TX_TYPE_YELLOW_PUNISH) {
                yellowPunishTx = (YellowPunishTransaction) transaction;
            } else if (null != yellowPunishTx && transaction.getType() == ConsensusConstant.TX_TYPE_YELLOW_PUNISH) {
                BlockLog.debug("Yellow punish transaction more than one! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
//                Log.error("Yellow punish transaction more than one! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
                return false;
            } else if (transaction.getType() == ConsensusConstant.TX_TYPE_RED_PUNISH && ((RedPunishTransaction) transaction).getTxData().getReasonCode() == PunishReasonEnum.TOO_MUCH_YELLOW_PUNISH.getCode()) {
                redPunishTxList.add((RedPunishTransaction) transaction);
            }
        }

        YellowPunishTransaction yellowPunishTransaction = null;
        try {
            yellowPunishTransaction = ConsensusTool.createYellowPunishTx(chain.getBestBlock(), member, currentRound);
            if (yellowPunishTransaction == yellowPunishTx) {
                return true;
            } else if (yellowPunishTransaction == null || yellowPunishTx == null) {
                BlockLog.debug("The yellow punish tx is wrong! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
//                Log.error("The yellow punish tx is wrong! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
                return false;
            } else if (!yellowPunishTransaction.getHash().equals(yellowPunishTx.getHash())) {
                BlockLog.debug("The yellow punish tx's hash is wrong! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
//                Log.error("The yellow punish tx's hash is wrong! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash());
                return false;
            }

        } catch (Exception e) {
            BlockLog.debug("The tx's wrong! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash(), e);
//            Log.error("The tx's wrong! height: " + block.getHeader().getHeight() + " , hash : " + block.getHeader().getHash(), e);
            return false;
        }
        if (null == yellowPunishTransaction && !redPunishTxList.isEmpty()) {
            BlockLog.debug("the red punish is wrong!");
            return false;
        } else {
            List<byte[]> addressList = yellowPunishTransaction.getTxData().getAddressList();
            Set<String> punishAddress = new HashSet<>();
            for (byte[] address : addressList) {
                MeetingMember item = currentRound.getMemberByAgentAddress(address);
                if (null == item) {
                    item = currentRound.getPreRound().getMemberByAgentAddress(address);
                }
                if (item.getCreditVal() <= PocConsensusConstant.RED_PUNISH_CREDIT_VAL) {
                    punishAddress.add(AddressTool.getStringAddressByBytes(item.getAgent().getAgentAddress()));
                }
            }
            if (punishAddress.size() != redPunishTxList.size()) {
                BlockLog.debug("the count of red punishs is wrong!" + block.getHeader().getHash());
                return false;
            }
            for (RedPunishTransaction redTx : redPunishTxList) {
                if (!punishAddress.contains(AddressTool.getStringAddressByBytes(redTx.getTxData().getAddress()))) {
                    BlockLog.debug("There is a wrong red punish tx!" + block.getHeader().getHash());
                    return false;
                }
                if (NulsContext.MAIN_NET_VERSION > 1 && redTx.getTime() != block.getHeader().getTime()) {
                    BlockLog.debug("red punish CoinData & TX time is wrong! " + block.getHeader().getHash());
                    return false;
                }

            }

        }
        return true;
    }

    public Result verifyAndAddBlock(Block block, boolean isDownload, boolean isNeedCheckCoinBaseTx) {
        Result result = verifyBlock(block, isDownload, isNeedCheckCoinBaseTx);
        if (result.isSuccess()) {
            if (!addBlock(block)) {
                return Result.getFailed();
            }
        }
        return result;
    }

    public boolean rollback(Block block) {

        Block bestBlock = chain.getBestBlock();

        if (block == null || !block.getHeader().getHash().equals(bestBlock.getHeader().getHash())) {
            Log.warn("rollbackTransaction block is not best block");
            return false;
        }

        List<Block> blockList = chain.getBlockList();

        if (blockList == null || blockList.size() == 0) {
            return false;
        }
        if (blockList.size() <= 2) {
            addBlockInBlockList(blockList);
            if (blockList.size() > 0) {
                Block startBlock = blockList.get(0);
                if (startBlock != null && chain.getStartBlockHeader().getHeight() > startBlock.getHeader().getHeight()) {
                    chain.setStartBlockHeader(startBlock.getHeader());
                }
            }
        }

        blockList.remove(blockList.size() - 1);

        List<BlockHeader> blockHeaderList = chain.getBlockHeaderList();

        chain.setEndBlockHeader(blockHeaderList.get(blockHeaderList.size() - 2));
        BlockHeader rollbackBlockHeader = blockHeaderList.remove(blockHeaderList.size() - 1);

        // update txs
        List<Agent> agentList = chain.getAgentList();
        List<Deposit> depositList = chain.getDepositList();
        List<PunishLogPo> yellowList = chain.getYellowPunishList();
        List<PunishLogPo> redPunishList = chain.getRedPunishList();

        long height = rollbackBlockHeader.getHeight();

        for (int i = agentList.size() - 1; i >= 0; i--) {
            Agent agent = agentList.get(i);

            if (agent.getDelHeight() == height) {
                agent.setDelHeight(-1L);
            }

            if (agent.getBlockHeight() == height) {
                agentList.remove(i);
            }
        }

        for (int i = depositList.size() - 1; i >= 0; i--) {
            Deposit deposit = depositList.get(i);

            if (deposit.getDelHeight() == height) {
                deposit.setDelHeight(-1L);
            }

            if (deposit.getBlockHeight() == height) {
                depositList.remove(i);
            }
        }

        for (int i = yellowList.size() - 1; i >= 0; i--) {
            PunishLogPo tempYellow = yellowList.get(i);
            if (tempYellow.getHeight() < height) {
                break;
            }
            if (tempYellow.getHeight() == height) {
                yellowList.remove(i);
            }
        }

        for (int i = redPunishList.size() - 1; i >= 0; i--) {
            PunishLogPo redPunish = redPunishList.get(i);
            if (redPunish.getHeight() < height) {
                break;
            }
            if (redPunish.getHeight() == height) {
                redPunishList.remove(i);
            }
        }

        // 判断是否需要重新计算轮次
        roundManager.checkIsNeedReset();

        return true;
    }

    private void addBlockInBlockList(List<Block> blockList) {
        BlockService blockService = NulsContext.getServiceBean(BlockService.class);
        if (blockList.isEmpty()) {
            blockList.add(blockService.getBestBlock().getData());
        }
        while (blockList.size() < PocConsensusConstant.INIT_BLOCKS_COUNT) {
            Block preBlock = blockList.get(0);
            if (preBlock.getHeader().getHeight() == 0) {
                break;
            }
            blockList.add(0, blockService.getBlock(preBlock.getHeader().getPreHash()).getData());
        }
    }

    /**
     * Get the state of the complete chain after the combination of a chain and the current chain bifurcation point, that is, first obtain the bifurcation point between the bifurcation chain and the current chain.
     * Then create a brand new chain, copy all the states before the bifurcation point of the main chain to the brand new chain
     * <p>
     * 获取一条链与当前链分叉点组合之后的完整链的状态，也就是，先获取到分叉链与当前链的分叉点，
     * 然后创建一条全新的链，把主链分叉点之前的所有状态复制到全新的链
     *
     * @return ChainContainer
     */
    public ChainContainer getBeforeTheForkChain(ChainContainer chainContainer) {

        Chain newChain = new Chain();
        newChain.setId(chainContainer.getChain().getId());
        newChain.setStartBlockHeader(chain.getStartBlockHeader());
        newChain.setEndBlockHeader(chain.getEndBlockHeader());
        newChain.setBlockHeaderList(new ArrayList<>(chain.getBlockHeaderList()));
        newChain.setBlockList(new ArrayList<>(chain.getBlockList()));

        if (chain.getAgentList() != null) {
            List<Agent> agentList = new ArrayList<>();

            for (Agent agent : chain.getAgentList()) {
                try {
                    agentList.add(agent.clone());
                } catch (CloneNotSupportedException e) {
                    Log.error(e);
                }
            }

            newChain.setAgentList(agentList);
        }
        if (chain.getDepositList() != null) {
            List<Deposit> depositList = new ArrayList<>();

            for (Deposit deposit : chain.getDepositList()) {
                try {
                    depositList.add(deposit.clone());
                } catch (CloneNotSupportedException e) {
                    Log.error(e);
                }
            }

            newChain.setDepositList(depositList);
        }
        if (chain.getYellowPunishList() != null) {
            newChain.setYellowPunishList(new ArrayList<>(chain.getYellowPunishList()));
        }
        if (chain.getRedPunishList() != null) {
            newChain.setRedPunishList(new ArrayList<>(chain.getRedPunishList()));
        }
        ChainContainer newChainContainer = new ChainContainer(newChain);

        // Bifurcation
        // 分叉点
        BlockHeader pointBlockHeader = chainContainer.getChain().getStartBlockHeader();

        List<Block> blockList = newChain.getBlockList();
        for (int i = blockList.size() - 1; i >= 0; i--) {
            Block block = blockList.get(i);
            if (pointBlockHeader.getPreHash().equals(block.getHeader().getHash())) {
                break;
            }
            newChainContainer.rollback(block);
        }

        newChainContainer.initRound();

        return newChainContainer;
    }

    /**
     * Get the block information of the current chain and branch chain after the cross point and combine them into a new branch chain
     * <p>
     * 获取当前链与分叉链对比分叉点之后的区块信息，组合成一个新的分叉链
     *
     * @return ChainContainer
     */
    public ChainContainer getAfterTheForkChain(ChainContainer chainContainer) {

        // Bifurcation
        // 分叉点
        BlockHeader pointBlockHeader = chainContainer.getChain().getStartBlockHeader();

        Chain chain = new Chain();

        List<Block> blockList = getChain().getBlockList();

        boolean canAdd = false;
        for (int i = 0; i < blockList.size(); i++) {

            Block block = blockList.get(i);

            if (canAdd) {
                chain.getBlockList().add(block);
                chain.getBlockHeaderList().add(block.getHeader());
            }

            if (pointBlockHeader.getPreHash().equals(block.getHeader().getHash())) {
                canAdd = true;
                if (i + 1 < blockList.size()) {
                    chain.setStartBlockHeader(blockList.get(i + 1).getHeader());
                    chain.setEndBlockHeader(getChain().getEndBlockHeader());
                    chain.setPreChainId(chainContainer.getChain().getId());
                }
                continue;
            }
        }
        return new ChainContainer(chain);
    }

    public MeetingRound getCurrentRound() {
        return roundManager.getCurrentRound();
    }

    public MeetingRound getOrResetCurrentRound() {
        return roundManager.resetRound(true);
    }

    public MeetingRound getOrResetCurrentRound(boolean isRealTime) {
        return roundManager.resetRound(isRealTime);
    }

    public MeetingRound initRound() {
        return roundManager.initRound();
    }

    public void clearRound(int count) {
        roundManager.clearRound(count);
    }

    public Block getBestBlock() {
        return chain.getBestBlock();
    }

    public Chain getChain() {
        return chain;
    }

    public void setChain(Chain chain) {
        this.chain = chain;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ChainContainer)) {
            return false;
        }
        ChainContainer other = (ChainContainer) obj;
        if (other.getChain() == null || this.chain == null) {
            return false;
        }
        return other.getChain().getId().equals(this.chain.getId());
    }

    public RoundManager getRoundManager() {
        return roundManager;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
