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

package io.nuls.consensus.poc.process;

import com.google.common.primitives.UnsignedBytes;
import io.nuls.consensus.poc.constant.ConsensusStatus;
import io.nuls.consensus.poc.constant.PocConsensusConstant;
import io.nuls.consensus.poc.container.ChainContainer;
import io.nuls.consensus.poc.context.ConsensusStatusContext;
import io.nuls.consensus.poc.locker.Lockers;
import io.nuls.consensus.poc.manager.ChainManager;
import io.nuls.consensus.poc.model.BlockExtendsData;
import io.nuls.consensus.poc.model.Chain;
import io.nuls.consensus.poc.model.MeetingMember;
import io.nuls.consensus.poc.model.MeetingRound;
import io.nuls.consensus.poc.protocol.entity.Agent;
import io.nuls.consensus.poc.protocol.entity.Deposit;
import io.nuls.consensus.poc.storage.po.PunishLogPo;
import io.nuls.consensus.poc.util.ConsensusTool;
import io.nuls.contract.dto.ContractResult;
import io.nuls.contract.service.ContractService;
import io.nuls.core.tools.crypto.Hex;
import io.nuls.core.tools.log.ChainLog;
import io.nuls.core.tools.log.Log;
import io.nuls.kernel.context.NulsContext;
import io.nuls.kernel.exception.NulsException;
import io.nuls.kernel.func.TimeService;
import io.nuls.kernel.model.*;
import io.nuls.kernel.validate.ValidateResult;
import io.nuls.ledger.service.LedgerService;
import io.nuls.protocol.service.BlockService;
import io.nuls.protocol.service.TransactionService;

import java.io.IOException;
import java.util.*;

/**
 * @author ln
 */
public class ForkChainProcess {

    private ChainManager chainManager;

    private BlockService blockService = NulsContext.getServiceBean(BlockService.class);

    private long time = 0L;
    private long lastClearTime = 0L;

    private LedgerService ledgerService = NulsContext.getServiceBean(LedgerService.class);
    private ContractService contractService = NulsContext.getServiceBean(ContractService.class);
    private TransactionService tansactionService = NulsContext.getServiceBean(TransactionService.class);

    private NulsProtocolProcess nulsProtocolProcess = NulsProtocolProcess.getInstance();

    public ForkChainProcess(ChainManager chainManager) {
        this.chainManager = chainManager;
    }

    public boolean doProcess() throws IOException, NulsException {

        if (ConsensusStatusContext.getConsensusStatus().ordinal() < ConsensusStatus.RUNNING.ordinal()) {
            return false;
        }
        Lockers.CHAIN_LOCK.lock();
        try {

            printChainStatusLog();

            // Monitor the status of the orphan chain, if it is available, join the verification chain
            // 监控孤立链的状态，如果有可连接的，则加入验证链里面
            monitorOrphanChains();

            long newestBlockHeight = chainManager.getBestBlockHeight() + PocConsensusConstant.CHANGE_CHAIN_BLOCK_DIFF_COUNT;

            ChainContainer newChain = chainManager.getMasterChain();
            if (null == newChain) {
                return false;
            }
            //获得主链最新块，如果分叉链和主链高度一致，但是最新块hash不一致，然后排序hash来决定要不要进行特殊回滚处理
            BlockHeader newChainBlockHeader = newChain.getBestBlock().getHeader();

            Iterator<ChainContainer> iterator = chainManager.getChains().iterator();
            while (iterator.hasNext()) {
                ChainContainer forkChain = iterator.next();
                if (forkChain.getChain() == null || forkChain.getChain().getStartBlockHeader() == null || forkChain.getChain().getEndBlockHeader() == null) {
                    iterator.remove();
                    continue;
                }
                long newChainHeight = forkChain.getChain().getEndBlockHeader().getHeight();
                BlockHeader forkChainBlockHeader = forkChain.getChain().getEndBlockHeader();
                byte[] rightHash = null;
                //String forkChainBlockHash = forkChainBlockHeader.getHash().getDigestHex();
                byte[] forkChainBlockHash = forkChainBlockHeader.getHash().getDigestBytes();
                //如果高度相同，则排序选一个hash，作为大家都认同的块
                if (newChainBlockHeader.getHeight() == newChainHeight){
                    byte[] newChainBlockHash = newChainBlockHeader.getHash().getDigestBytes();
                    rightHash = rightHash(newChainBlockHash, forkChainBlockHash);
                }
                if (newChainHeight > newestBlockHeight
                        || (newChainHeight == newestBlockHeight && forkChain.getChain().getEndBlockHeader().getTime() < newChain.getChain().getEndBlockHeader().getTime())
                        || (newChainBlockHeader.getHeight() == newChainHeight && Arrays.equals(forkChainBlockHash, rightHash))) {
                    if (newChainBlockHeader.getHeight() == newChainHeight && Arrays.equals(forkChainBlockHash, rightHash)) {
                        Log.info("-+-+-+-+-+-+-+-+- Change chain with the same height but different hash block -+-+-+-+-+-+-+-+-");
                        Log.info("-+-+-+-+-+-+-+-+- height: "+ newChainHeight + ", Right hash：" + rightHash);
                    }
                    newChain = forkChain;
                    newestBlockHeight = newChainHeight;
                }
            }

            if (!newChain.equals(chainManager.getMasterChain())) {

                ChainLog.debug("discover the fork chain {} : start {} - {} , end {} - {} , exceed the master {} - {} - {}, start verify the fork chian", newChain.getChain().getId(), newChain.getChain().getStartBlockHeader().getHeight(), newChain.getChain().getStartBlockHeader().getHash(), newChain.getChain().getEndBlockHeader().getHeight(), newChain.getChain().getEndBlockHeader().getHash(), chainManager.getMasterChain().getChain().getId(), chainManager.getBestBlockHeight(), chainManager.getBestBlock().getHeader().getHash());

                //ChainContainer resultChain = verifyNewChain(newChain);
                //Verify the new chain, combined with the current latest chain, to get the status of the branch node
                //验证新的链，结合当前最新的链，获取到分叉节点时的状态
                ChainContainer resultChain = chainManager.getMasterChain().getBeforeTheForkChain(newChain);

                //Combined with the new bifurcated block chain, combine and verify one by one
                //结合新分叉的块链， 逐个组合并验证
                List<Object[]> verifyResultList = new ArrayList<>();
                for (Block forkBlock : newChain.getChain().getBlockList()) {
                    Result success = resultChain.verifyAndAddBlock(forkBlock, true, false);
                    if (success.isFailed()) {
                        resultChain = null;
                        break;
                    } else {
                        verifyResultList.add((Object[]) success.getData());
                    }
                }

                if (resultChain == null) {
                    ChainLog.debug("verify the fork chain fail {} remove it", newChain.getChain().getId());

                    chainManager.getChains().remove(newChain);
                } else {
                    //Verify pass, try to switch chain
                    //验证通过，尝试切换链
                    boolean success = changeChain(resultChain, newChain, verifyResultList);
                    if (success) {
                        chainManager.getChains().remove(newChain);
                    }
                    ChainLog.debug("verify the fork chain {} success, change master chain result : {} , new master chain is {} : {} - {}", newChain.getChain().getId(), success, chainManager.getBestBlock().getHeader().getHeight(), chainManager.getBestBlock().getHeader().getHash());
                }
            }

            clearExpiredChain();
        } finally {
            Lockers.CHAIN_LOCK.unlock();
        }
        return true;
    }

    /**
     * 当两个高度一致的块hash不同时，排序统一选取前面一个hash为正确的
     */
    private byte[] rightHash(byte[] hash1, byte[] hash2) {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();
        if (comparator.compare(hash1, hash2) <= 0) {
            return hash1;
        }
        return hash2;
    }

    private void printChainStatusLog() {
        if (chainManager.getMasterChain() == null || chainManager.getMasterChain().getChain() == null || chainManager.getMasterChain().getChain().getEndBlockHeader() == null) {
            return;
        }

        if (time == 0L) {
            printLog();
        } else if (System.currentTimeMillis() - time > 5 * 60 * 1000L) {
            printLog();
        }
    }

    private void printLog() {
        time = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();

        sb.append("=========================\n");

        sb.append("Master Chain Status : \n");
        sb.append(getChainStatus(chainManager.getMasterChain()));

        sb.append("\n");

        List<ChainContainer> chains = chainManager.getChains();

        if (chains != null && chains.size() > 0) {
            sb.append("fork chains : \n");
            for (ChainContainer chain : chains) {
                sb.append(getChainStatus(chain));
            }
            sb.append("\n");
        }

        List<ChainContainer> iss = chainManager.getOrphanChains();

        if (iss != null && iss.size() > 0) {
            sb.append("orphan chains : \n");
            for (ChainContainer chain : iss) {
                sb.append(getChainStatus(chain));
            }
            sb.append("\n");
        }

        ChainLog.debug(sb.toString());
    }

    private String getChainStatus(ChainContainer chain) {
        StringBuilder sb = new StringBuilder();

        if (chain == null || chain.getChain() == null) {
            return sb.toString();
        }

        sb.append("id: " + chain.getChain().getId() + "\n");

        if (chain.getChain().getStartBlockHeader() == null) {
            sb.append("start Block Header is null \n");
        } else {
            sb.append("start height : " + chain.getChain().getStartBlockHeader().getHeight() + " \n");
            sb.append("start hash : " + chain.getChain().getStartBlockHeader().getHash() + " \n");
        }
        if (chain.getChain().getEndBlockHeader() == null) {
            sb.append("end Block Header is null \n");
        } else {
            sb.append("end height : " + chain.getChain().getEndBlockHeader().getHeight() + " \n");
            sb.append("end hash : " + chain.getChain().getEndBlockHeader().getHash() + " \n");
        }

        List<BlockHeader> blockHeaderList = chain.getChain().getBlockHeaderList();

        if (blockHeaderList != null && blockHeaderList.size() > 0) {
            sb.append("start blockHeaders height : " + blockHeaderList.get(0).getHeight() + " \n");
            sb.append("end blockHeaders height : " + blockHeaderList.get(blockHeaderList.size() - 1).getHeight() + " \n");
            sb.append("start blockHeaders hash : " + blockHeaderList.get(0).getHash() + " \n");
            sb.append("end blockHeaders hash : " + blockHeaderList.get(blockHeaderList.size() - 1).getHash() + " \n");
        }

        List<Block> block = chain.getChain().getBlockList();

        if (block != null && block.size() > 0) {
            sb.append("start blocks height : " + block.get(0).getHeader().getHeight() + " \n");
            sb.append("end blocks height : " + block.get(block.size() - 1).getHeader().getHeight() + " \n");
            sb.append("start blocks hash : " + block.get(0).getHeader().getHash() + " \n");
            sb.append("end blocks hash : " + block.get(block.size() - 1).getHeader().getHash() + " \n");
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Monitor the orphan chain, if there is a connection with the main chain or the forked chain, the merged chain
     * <p>
     * 监控孤立链，如果有和主链或者分叉链连上的情况，则合并链
     */
    private void monitorOrphanChains() {
        List<ChainContainer> orphanChains = chainManager.getOrphanChains();

        Iterator<ChainContainer> iterator = orphanChains.iterator();
        while (iterator.hasNext()) {
            ChainContainer orphanChain = iterator.next();
            if (checkOrphanChainHasConnection(orphanChain)) {
                iterator.remove();
            }
        }
    }

    private boolean checkOrphanChainHasConnection(ChainContainer orphanChain) {
        // Determine whether the orphan chain is connected to the main chain
        // 判断该孤立链是否和主链相连
        BlockHeader startBlockHeader = orphanChain.getChain().getStartBlockHeader();

        List<BlockHeader> blockHeaderList = chainManager.getMasterChain().getChain().getBlockHeaderList();

        int count = blockHeaderList.size() > PocConsensusConstant.MAX_ISOLATED_BLOCK_COUNT ? PocConsensusConstant.MAX_ISOLATED_BLOCK_COUNT : blockHeaderList.size();
        for (int i = blockHeaderList.size() - 1; i >= blockHeaderList.size() - count; i--) {
            BlockHeader header = blockHeaderList.get(i);
            if (startBlockHeader.getPreHash().equals(header.getHash()) && startBlockHeader.getHeight() == header.getHeight() + 1) {
                //yes connectioned
                orphanChain.getChain().setPreChainId(chainManager.getMasterChain().getChain().getId());

                chainManager.getChains().add(orphanChain);

                ChainLog.debug("discover the OrphanChain {} : start {} - {} , end {} - {} , connection the master chain of {} - {} - {}, move into the fork chians", orphanChain.getChain().getId(), startBlockHeader.getHeight(), startBlockHeader.getHash().getDigestHex(), orphanChain.getChain().getEndBlockHeader().getHeight(), orphanChain.getChain().getEndBlockHeader().getHash(), chainManager.getMasterChain().getChain().getId(), chainManager.getMasterChain().getChain().getBestBlock().getHeader().getHeight(), chainManager.getMasterChain().getChain().getBestBlock().getHeader().getHash());

                return true;
            } else if (startBlockHeader.getHeight() > header.getHeight()) {
                break;
            }
        }

        // Determine whether the lone chain is connected to the forked chain to be verified
        // 判断该孤链是否和待验证的分叉链相连
        for (ChainContainer forkChain : chainManager.getChains()) {

            Chain chain = forkChain.getChain();

            if (startBlockHeader.getHeight() > chain.getEndBlockHeader().getHeight() + 1 || startBlockHeader.getHeight() <= chain.getStartBlockHeader().getHeight()) {
                continue;
            }

            blockHeaderList = chain.getBlockHeaderList();

            for (int i = 0; i < blockHeaderList.size(); i++) {
                BlockHeader header = blockHeaderList.get(i);
                if (startBlockHeader.getPreHash().equals(header.getHash()) && startBlockHeader.getHeight() == header.getHeight() + 1) {
                    //yes connectioned
                    orphanChain.getChain().setPreChainId(chain.getPreChainId());
                    orphanChain.getChain().setStartBlockHeader(chain.getStartBlockHeader());

                    orphanChain.getChain().getBlockHeaderList().addAll(0, blockHeaderList.subList(0, i + 1));
                    orphanChain.getChain().getBlockList().addAll(0, chain.getBlockList().subList(0, i + 1));

                    chainManager.getChains().add(orphanChain);

                    if (i == blockHeaderList.size() - 1) {
                        chainManager.getChains().remove(forkChain);
                    }

                    ChainLog.debug("discover the OrphanChain {} : start {} - {} , end {} - {} , connection the fork chain of : start {} - {} , end {} - {}, move into the fork chians", orphanChain.getChain().getId(), startBlockHeader.getHeight(), startBlockHeader.getHash().getDigestHex(), orphanChain.getChain().getEndBlockHeader().getHeight(), orphanChain.getChain().getEndBlockHeader().getHash(), chainManager.getMasterChain().getChain().getId(), chain.getStartBlockHeader().getHeight(), chain.getStartBlockHeader().getHash(), chain.getEndBlockHeader().getHeight(), chain.getEndBlockHeader().getHash());

                    return true;
                } else if (startBlockHeader.getHeight() == header.getHeight() + 1) {
                    break;
                }
            }
        }

        // Determine whether the orphan chains are connected
        // 判断孤立链之间是否相连
        for (ChainContainer orphan : chainManager.getOrphanChains()) {
            if (orphan.getChain().getEndBlockHeader().getHash().equals(orphanChain.getChain().getStartBlockHeader().getPreHash()) &&
                    orphan.getChain().getEndBlockHeader().getHeight() + 1 == orphanChain.getChain().getStartBlockHeader().getHeight()) {
                Chain chain = orphan.getChain();
                chain.setEndBlockHeader(orphanChain.getChain().getEndBlockHeader());
                chain.getBlockHeaderList().addAll(orphanChain.getChain().getBlockHeaderList());
                chain.getBlockList().addAll(orphanChain.getChain().getBlockList());
                return true;
            }
        }

        return false;
    }

    /*
     * Verify the block header information of the new chain, and if they all pass, start switching
     * However, in the case that both are passed, it may also fail because the transaction is not verified.
     * The transaction cannot be verified here because the data has not been rolled back
     *
     * 验证新链的区块头信息，如果都通过，才开始切换
     * 但是都通过的情况下，也有可能失败，因为交易是没有经过验证的
     * 这里不能同时验证交易，因为数据没有回滚
     */
    private ChainContainer verifyNewChain(ChainContainer needVerifyChain) {
        //Verify the new chain, combined with the current latest chain, to get the status of the branch node
        //验证新的链，结合当前最新的链，获取到分叉节点时的状态
        ChainContainer forkChain = chainManager.getMasterChain().getBeforeTheForkChain(needVerifyChain);

        //Combined with the new bifurcated block chain, combine and verify one by one
        //结合新分叉的块链， 逐个组合并验证
        for (Block forkBlock : needVerifyChain.getChain().getBlockList()) {
            Result success = forkChain.verifyAndAddBlock(forkBlock, true, false);
            if (success.isFailed()) {
                return null;
            }
        }
        return forkChain;
    }

    /*
     * Switching the master chain to a new chain and verifying the block header before the switch is legal, so only the transactions in the block need to be verified here.
     * In order to ensure the correctness of the transaction verification, you first need to roll back all blocks after the fork of the main chain, and then the new chain will start to go into service.
     * If the verification fails during the warehousing process, it means that the transaction in the block is illegal, then the new connection that proves the need to switch is not trusted.
     * Once the new chain is not trusted, you need to add the previously rolled back block back
     * This method needs to be synchronized with the add block method
     *
     * 把master链切换成新的链，切换之前已经做过区块头的验证，都是合法的，所以这里只需要验证区块里面的交易即可
     * 为保证交易验证的正确性，首先需要回滚掉主链分叉点之后的所有区块，然后新链开始入库，入库过程中会做验证
     * 如果入库过程中验证失败，说明是区块里面的交易不合法，那么证明需要切换的新连是不可信的
     * 一旦出现新链不可信的情况，则需要把之前回滚掉的区块再添加回去
     * 本方法需要和添加区块方法同步
     */
    private boolean changeChain(ChainContainer newMasterChain, ChainContainer originalForkChain, List<Object[]> verifyResultList) throws NulsException, IOException {

        if (newMasterChain == null || originalForkChain == null || verifyResultList == null) {
            return false;
        }

        //Now the master chain, the forked chain after the switch, needs to be put into the list of chains to be verified.
        //现在的主链，在切换之后的分叉链，需要放入待验证链列表里面
        ChainContainer oldChain = chainManager.getMasterChain().getAfterTheForkChain(originalForkChain);

        //rollbackTransaction
        List<Block> rollbackBlockList = oldChain.getChain().getBlockList();

        ChainLog.debug("rollbackTransaction the master chain , need rollbackTransaction block count is {}, master chain is {} : {} - {} , service best block : {} - {}", rollbackBlockList.size(), chainManager.getMasterChain().getChain().getId(), chainManager.getBestBlock().getHeader().getHeight(), chainManager.getBestBlock().getHeader().getHash(), blockService.getBestBlock().getData().getHeader().getHeight(), blockService.getBestBlock().getData().getHeader().getHash());

        //Need descending order
        //需要降序排列
        Collections.reverse(rollbackBlockList);

        boolean rollbackResult = rollbackBlocks(rollbackBlockList);
        if (!rollbackResult) {
            return false;
        }

        //add new block
        List<Block> addBlockList = originalForkChain.getChain().getBlockList();

        boolean changeSuccess = true;

        List<Block> successList = new ArrayList<>();

        Long newBestHeight = addBlockList.get(addBlockList.size() - 1).getHeader().getHeight();

        Result<Block> preBlockResult = blockService.getBlock(addBlockList.get(0).getHeader().getPreHash());
        Block preBlock = preBlockResult.getData();
        Block newBlock;
        //Need to sort in ascending order, the default is
        //需要升序排列，默认就是
        //for (Block newBlock : addBlockList) {
        for (int i = 0, size = addBlockList.size(); i < size; i++) {
            newBlock = addBlockList.get(i);
            Log.info("==========================================切换主链, 高度: {} 开始验证. + ", newBlock.getHeader().getHeight());
            newBlock.verifyWithException();

            Map<String, Coin> toMaps = new HashMap<>();
            Set<String> fromSet = new HashSet<>();


            /**
             * pierre add 智能合约相关
             */
            long bestHeight = preBlock.getHeader().getHeight();
            byte[] stateRoot = ConsensusTool.getStateRoot(preBlock.getHeader());
            preBlock = newBlock;
            byte[] receiveStateRoot = ConsensusTool.getStateRoot(newBlock.getHeader());
            Result<ContractResult> invokeContractResult = null;
            ContractResult contractResult = null;
            Map<String, Coin> contractUsedCoinMap = new HashMap<>();

            for (Transaction tx : newBlock.getTxs()) {

                if (tx.isSystemTx()) {
                    continue;
                }

                ValidateResult result = tx.verify();
                if (result.isSuccess()) {
                    result = ledgerService.verifyCoinData(tx, toMaps, fromSet, bestHeight);
                    if (result.isFailed()) {
                        ErrorData errorData = (ErrorData) result.getData();
                        Log.info("failed message:" + errorData.getMsg());
                        changeSuccess = false;
                        break;
                    }
                } else {
                    ErrorData errorData = (ErrorData) result.getData();
                    Log.info("failed message:" + errorData.getMsg());
                    changeSuccess = false;
                    break;
                }

                // 验证区块时发现智能合约交易就调用智能合约
                //if(ContractUtil.isContractTransaction(tx)) {
                //    invokeContractResult = contractService.invokeContract(tx, bestHeight, stateRoot);
                //    contractResult = invokeContractResult.getData();
                //    if (contractResult != null) {
                //        Result<byte[]> handleContractResult = contractService.verifyContractResult(
                //                tx, contractResult,
                //                stateRoot, newBlock.getHeader().getTime(),
                //                toMaps, contractUsedCoinMap, bestHeight);
                //        // 更新世界状态
                //        stateRoot = handleContractResult.getData();
                //    }
                //}
            }

            if (!changeSuccess) {
                break;
            }

            stateRoot = contractService.processTxs(newBlock.getTxs(), bestHeight, newBlock, stateRoot, toMaps, contractUsedCoinMap, true).getData();

            // 验证世界状态根
            if ((receiveStateRoot != null || stateRoot != null) && !Arrays.equals(receiveStateRoot, stateRoot)) {
                Log.info("contract stateRoot incorrect. receiveStateRoot is {}, stateRoot is {}.", receiveStateRoot != null ? Hex.encode(receiveStateRoot) : receiveStateRoot, stateRoot != null ? Hex.encode(stateRoot) : stateRoot);
                changeSuccess = false;
                break;
            }

            //// 验证区块交易结束后移除临时余额区
            //contractService.removeContractTempBalance();
            //if (contractResult != null) {
            //    // 验证世界状态根
            //    if (!Arrays.equals(receiveStateRoot, stateRoot)) {
            //        Log.info("contract stateRoot incorrect.");
            //        changeSuccess = false;
            //        break;
            //    }
            //}

            // 验证CoinBase交易
            Object[] objects = verifyResultList.get(i);
            MeetingRound currentRound = (MeetingRound) objects[0];
            MeetingMember member = (MeetingMember) objects[1];
            if (!chainManager.getMasterChain().verifyCoinBaseTx(newBlock, currentRound, member)) {
                changeSuccess = false;
                break;
            }

            if (!changeSuccess) {
                break;
            }
            ValidateResult validateResult1 = tansactionService.conflictDetect(newBlock.getTxs());
            if (validateResult1.isFailed()) {
                Log.info("failed message:" + validateResult1.getMsg());
                changeSuccess = false;
                break;
            }

            try {
                Result result = blockService.saveBlock(newBlock);
                boolean success = result.isSuccess();
                if (success) {
                    //更新版本协议内容
                    nulsProtocolProcess.processProtocolUpGrade(newBlock.getHeader());
                    successList.add(newBlock);
                } else {
                    ChainLog.debug("save block error : " + result.getMsg() + " , block height : " + newBlock.getHeader().getHeight() + " , hash: " + newBlock.getHeader().getHash());
                    changeSuccess = false;
                    break;
                }
            } catch (Exception e) {
                Log.info("change fork chain error at save block, ", e);
                changeSuccess = false;
                break;
            }
            //Log.info("=========================================切换主链, 高度: {} 验证结束. - ", newBlock.getHeader().getHeight());
        }

        ChainLog.debug("add new blocks complete, result {}, success count is {} , now service best block : {} - {}", changeSuccess, successList.size(), blockService.getBestBlock().getData().getHeader().getHeight(), blockService.getBestBlock().getData().getHeader().getHash());

        if (changeSuccess) {
            chainManager.setMasterChain(newMasterChain);
            newMasterChain.initRound();
            NulsContext.getInstance().setBestBlock(newMasterChain.getBestBlock());

            if (oldChain.getChain().getBlockList().size() > 0) {
                chainManager.getChains().add(oldChain);
            }
        } else {
            //Fallback status
            //回退状态
            //Log.info("=========================================切换失败，回滚区块. + ");
            Collections.reverse(successList);
            for (Block rollBlock : successList) {
                Result rs = blockService.rollbackBlock(rollBlock);
                if (rs.isSuccess()) {
                    //回滚版本更新统计数据
                    nulsProtocolProcess.processProtocolRollback(rollBlock.getHeader());
                }
                RewardStatisticsProcess.rollbackBlock(rollBlock);
            }

            Collections.reverse(rollbackBlockList);
            for (Block addBlock : rollbackBlockList) {
                Result rs = blockService.saveBlock(addBlock);
                if (rs.isSuccess()) {
                    //更新版本协议内容
                    nulsProtocolProcess.processProtocolUpGrade(addBlock.getHeader());
                }
                RewardStatisticsProcess.addBlock(addBlock);
            }
            //Log.info("=========================================切换失败，回滚区块. - ");
        }
        return changeSuccess;
    }

    private boolean rollbackBlocks(List<Block> rollbackBlockList) {

        List<Block> rollbackList = new ArrayList<>();
        for (Block rollbackBlock : rollbackBlockList) {
            try {
                boolean success = blockService.rollbackBlock(rollbackBlock).isSuccess();
                if (success) {
                    //回滚版本更新统计数据
                    nulsProtocolProcess.processProtocolRollback(rollbackBlock.getHeader());
                    //Log.info("=========================================回滚区块成功, 高度: {}.", rollbackBlock.getHeader().getHeight());
                    RewardStatisticsProcess.rollbackBlock(rollbackBlock);
                    rollbackList.add(rollbackBlock);
                } else {
                    Collections.reverse(rollbackList);
                    for (Block block : rollbackList) {
                        try {
                            //Log.info("=========================================回滚区块失败, 高度: {}.", rollbackBlock.getHeader().getHeight());
                            Result rs = blockService.saveBlock(block);
                            if (rs.isSuccess()) {
                                //更新版本协议内容
                                nulsProtocolProcess.processProtocolUpGrade(block.getHeader());
                            }
                            RewardStatisticsProcess.addBlock(block);
                        } catch (Exception ex) {
                            Log.error("Rollback failed, failed to save block during recovery", ex);
                            break;
                        }
                    }
                    Log.error("Rollback block height : " + rollbackBlock.getHeader().getHeight() + " hash : " + rollbackBlock.getHeader().getHash() + " failed, change chain failed !");
                    return false;
                }
            } catch (Exception e) {
                Collections.reverse(rollbackList);
                for (Block block : rollbackList) {
                    try {
                        Result rs = blockService.saveBlock(block);
                        if (rs.isSuccess()) {
                            //更新版本协议内容
                            nulsProtocolProcess.processProtocolUpGrade(block.getHeader());
                        }
                        RewardStatisticsProcess.addBlock(block);
                    } catch (Exception ex) {
                        Log.error("Rollback failed, failed to save block during recovery", ex);
                        break;
                    }
                }
                Log.error("Rollback failed during switch chain, skip this chain", e);
                e.printStackTrace();
                return false;
            }
        }

        ChainLog.debug("rollbackTransaction complete, success count is {} , now service best block : {} - {}", rollbackList.size(), blockService.getBestBlock().getData().getHeader().getHeight(), blockService.getBestBlock().getData().getHeader().getHash());
        return true;
    }

    protected void clearExpiredChain() {
        if (TimeService.currentTimeMillis() - lastClearTime < PocConsensusConstant.CLEAR_INTERVAL_TIME) {
            return;
        }
        lastClearTime = TimeService.currentTimeMillis();
        //clear the master data
        clearMasterDatas();

        //clear the expired chain
        long bestHeight = chainManager.getBestBlockHeight();

        Iterator<ChainContainer> it = chainManager.getChains().iterator();
        while (it.hasNext()) {
            ChainContainer chain = it.next();
            if (checkChainIsExpired(chain, bestHeight)) {
                it.remove();
            }
        }

        it = chainManager.getOrphanChains().iterator();
        while (it.hasNext()) {
            ChainContainer orphanChain = it.next();
            if (checkChainIsExpired(orphanChain, bestHeight)) {
                it.remove();
            }
        }
    }

    private boolean checkChainIsExpired(ChainContainer orphanChain, long bestHeight) {
        if (bestHeight - orphanChain.getChain().getEndBlockHeader().getHeight() > PocConsensusConstant.MAX_ISOLATED_BLOCK_COUNT) {
            return true;
        }
        return false;
    }

    private void clearMasterDatas() {
        clearMasterChainRound();
        clearMasterChainData();
    }

    private void clearMasterChainData() {
        Chain masterChain = chainManager.getMasterChain().getChain();
        long bestHeight = masterChain.getEndBlockHeader().getHeight();

        List<BlockHeader> blockHeaderList = masterChain.getBlockHeaderList();
        List<Block> blockList = masterChain.getBlockList();

        if (blockHeaderList.size() > 30000) {
            masterChain.setBlockHeaderList(blockHeaderList.subList(blockHeaderList.size() - 30000, blockHeaderList.size()));
        }
        if (blockList.size() > PocConsensusConstant.MAX_ISOLATED_BLOCK_COUNT) {
            masterChain.setBlockList(blockList.subList(blockList.size() - PocConsensusConstant.MAX_ISOLATED_BLOCK_COUNT, blockList.size()));
        }

        List<Agent> agentList = masterChain.getAgentList();
        List<Deposit> depositList = masterChain.getDepositList();

        Iterator<Agent> ait = agentList.iterator();
        while (ait.hasNext()) {
            Agent agent = ait.next();
            if (agent.getDelHeight() > 0L && (bestHeight - 1000) > agent.getDelHeight()) {
                ait.remove();
            }
        }

        Iterator<Deposit> dit = depositList.iterator();
        while (dit.hasNext()) {
            Deposit deposit = dit.next();
            if (deposit.getDelHeight() > 0L && (bestHeight - 1000) > deposit.getDelHeight()) {
                dit.remove();
            }
        }

        BlockExtendsData roundData = new BlockExtendsData(chainManager.getBestBlock().getHeader().getExtend());

        List<PunishLogPo> yellowList = masterChain.getYellowPunishList();
        Iterator<PunishLogPo> yit = yellowList.iterator();
        while (yit.hasNext()) {
            PunishLogPo punishLog = yit.next();
            if (punishLog.getRoundIndex() < roundData.getPackingIndexOfRound() - PocConsensusConstant.INIT_HEADERS_OF_ROUND_COUNT) {
                yit.remove();
            }
        }
    }

    private void clearMasterChainRound() {
        chainManager.getMasterChain().clearRound(PocConsensusConstant.CLEAR_MASTER_CHAIN_ROUND_COUNT);
    }


}