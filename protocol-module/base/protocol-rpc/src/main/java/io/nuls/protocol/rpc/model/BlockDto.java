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

package io.nuls.protocol.rpc.model;

import io.nuls.consensus.poc.model.BlockExtendsData;
import io.nuls.core.tools.crypto.Hex;
import io.nuls.core.tools.log.Log;
import io.nuls.kernel.constant.TransactionErrorCode;
import io.nuls.kernel.constant.TxStatusEnum;
import io.nuls.kernel.context.NulsContext;
import io.nuls.kernel.exception.NulsRuntimeException;
import io.nuls.kernel.model.*;
import io.nuls.protocol.constant.ProtocolConstant;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: Niels Wang
 */
@ApiModel(value = "blockJSON 区块信息(包含区块头信息, 交易信息), 只返回对应的部分数据")
public class BlockDto {

    @ApiModelProperty(name = "hash", value = "区块的hash值")
    private String hash;

    @ApiModelProperty(name = "preHash", value = "上一个区块的hash值")
    private String preHash;

    @ApiModelProperty(name = "merkleHash", value = "梅克尔hash")
    private String merkleHash;

    @ApiModelProperty(name = "stateRoot", value = "智能合约世界状态根")
    private String stateRoot;

    @ApiModelProperty(name = "time", value = "区块生成时间")
    private Long time;

    @ApiModelProperty(name = "height", value = "区块高度")
    private Long height;

    @ApiModelProperty(name = "txCount", value = "区块打包交易数量")
    private Long txCount;

    @ApiModelProperty(name = "packingAddress", value = "打包地址")
    private String packingAddress;

    @ApiModelProperty(name = "scriptSign", value = "签名Hex.encode(byte[])")
    private String scriptSign;

    @ApiModelProperty(name = "extend", value = "扩展信息Hex.encode(byte[])")
    private String extend;

    @ApiModelProperty(name = "roundIndex", value = "共识轮次")
    private Long roundIndex;

    @ApiModelProperty(name = "consensusMemberCount", value = "参与共识成员数量")
    private Integer consensusMemberCount;

    @ApiModelProperty(name = "roundStartTime", value = "当前共识轮开始时间")
    private Long roundStartTime;

    @ApiModelProperty(name = "packingIndexOfRound", value = "当前轮次打包出块的名次")
    private Integer packingIndexOfRound;

    @ApiModelProperty(name = "reward", value = "共识奖励")
    private Long reward;

    @ApiModelProperty(name = "fee", value = "获取的打包手续费")
    private Long fee;

    @ApiModelProperty(name = "confirmCount", value = "确认次数")
    private Long confirmCount;

    @ApiModelProperty(name = "size", value = "大小")
    private int size;

    @ApiModelProperty(name = "txList", value = "transactionsJSON")
    private List<TransactionDto> txList;

    public BlockDto(Block block) throws IOException {
        this(block.getHeader());
        this.size = block.size();
        this.txList = new ArrayList<>();
        Na fee = Na.ZERO;
        for (Transaction tx : block.getTxs()) {
            this.txList.add(new TransactionDto(tx));
            fee = fee.add(tx.getFee());
            if (tx.getType() == ProtocolConstant.TX_TYPE_COINBASE) {
                setBlockReward(tx);
            }
            tx.setStatus(TxStatusEnum.CONFIRMED);
        }
        this.fee = fee.getValue();
    }

    private void setBlockReward(Transaction tx) {
        CoinData coinData = tx.getCoinData();
        if (null == coinData) {
            throw new NulsRuntimeException(TransactionErrorCode.COINDATA_NOT_FOUND);
        }
        Na rewardNa = Na.ZERO;
        for (Coin coin : coinData.getTo()) {
            rewardNa = rewardNa.add(coin.getNa());
        }
        this.reward = rewardNa.getValue();
    }

    public BlockDto(BlockHeader header) throws IOException {
        long bestBlockHeight = NulsContext.getInstance().getBestBlock().getHeader().getHeight();
        this.hash = header.getHash().getDigestHex();
        this.preHash = header.getPreHash().getDigestHex();
        this.merkleHash = header.getMerkleHash().getDigestHex();
        this.time = header.getTime();
        this.height = header.getHeight();
        this.txCount = header.getTxCount();
        this.packingAddress = Address.fromHashs(header.getPackingAddress()).getBase58();
        this.scriptSign = Hex.encode(header.getBlockSignature().serialize());
        this.extend = Hex.encode(header.getExtend());
        this.confirmCount = bestBlockHeight - this.height;
        try {
            BlockExtendsData roundData = new BlockExtendsData(header.getExtend());
            this.roundIndex = roundData.getRoundIndex();
            this.roundStartTime = roundData.getRoundStartTime();
            this.consensusMemberCount = roundData.getConsensusMemberCount();
            this.packingIndexOfRound = roundData.getPackingIndexOfRound();
            if(roundData.getStateRoot() != null) {
                this.stateRoot = Hex.encode(roundData.getStateRoot());
            }
        } catch (Exception e) {
            Log.error(e);
        }
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getPreHash() {
        return preHash;
    }

    public void setPreHash(String preHash) {
        this.preHash = preHash;
    }

    public String getMerkleHash() {
        return merkleHash;
    }

    public void setMerkleHash(String merkleHash) {
        this.merkleHash = merkleHash;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Long getHeight() {
        return height;
    }

    public void setHeight(Long height) {
        this.height = height;
    }

    public Long getTxCount() {
        return txCount;
    }

    public void setTxCount(Long txCount) {
        this.txCount = txCount;
    }

    public String getPackingAddress() {
        return packingAddress;
    }

    public void setPackingAddress(String packingAddress) {
        this.packingAddress = packingAddress;
    }

    public String getScriptSig() {
        return scriptSign;
    }

    public void setScriptSig(String scriptSig) {
        this.scriptSign = scriptSig;
    }

    public Long getRoundIndex() {
        return roundIndex;
    }

    public void setRoundIndex(Long roundIndex) {
        this.roundIndex = roundIndex;
    }

    public Integer getConsensusMemberCount() {
        return consensusMemberCount;
    }

    public void setConsensusMemberCount(Integer consensusMemberCount) {
        this.consensusMemberCount = consensusMemberCount;
    }

    public Long getRoundStartTime() {
        return roundStartTime;
    }

    public void setRoundStartTime(Long roundStartTime) {
        this.roundStartTime = roundStartTime;
    }

    public Integer getPackingIndexOfRound() {
        return packingIndexOfRound;
    }

    public void setPackingIndexOfRound(Integer packingIndexOfRound) {
        this.packingIndexOfRound = packingIndexOfRound;
    }

    public List<TransactionDto> getTxList() {
        return txList;
    }

    public void setTxList(List<TransactionDto> txList) {
        this.txList = txList;
    }

    public Long getReward() {
        return reward;
    }

    public void setReward(Long reward) {
        this.reward = reward;
    }

    public Long getFee() {
        return fee;
    }

    public void setFee(Long fee) {
        this.fee = fee;
    }

    public Long getConfirmCount() {
        return confirmCount;
    }

    public void setConfirmCount(Long confirmCount) {
        this.confirmCount = confirmCount;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getStateRoot() {
        return stateRoot;
    }

    public void setStateRoot(String stateRoot) {
        this.stateRoot = stateRoot;
    }

    public String getExtend() {
        return extend;
    }

    public void setExtend(String extend) {
        this.extend = extend;
    }
}