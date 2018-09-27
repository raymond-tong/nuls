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
package io.nuls.consensus.poc.model;

import io.nuls.consensus.poc.storage.po.EvidencePo;
import io.nuls.kernel.model.BlockHeader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个出块地址，在同一个高度打包了两个不同的块。
 * @author: Charlie
 * @date: 2018/9/4
 */
public class Evidence {
    private long roundIndex;
    private BlockHeader blockHeader1;
    private BlockHeader blockHeader2;

    public Evidence(long roundIndex, BlockHeader blockHeader1, BlockHeader blockHeader2){
        this.roundIndex = roundIndex;
        this.blockHeader1 = blockHeader1;
        this.blockHeader2 = blockHeader2;
    }
    public Evidence(EvidencePo evidencePo){
        this.roundIndex = evidencePo.getRoundIndex();
        this.blockHeader1 = evidencePo.getBlockHeader1();
        this.blockHeader2 = evidencePo.getBlockHeader2();
    }

    public EvidencePo toEvidencePo(){
        return new EvidencePo(this.roundIndex, this.blockHeader1, this.blockHeader2);
    }

    public static Map<String, List<EvidencePo>> bifurcationEvidenceMapToPoMap(Map<String, List<Evidence>> map){

        Map<String, List<EvidencePo>> poMap = new HashMap<>();
        for(Map.Entry<String, List<Evidence>> entry : map.entrySet()){
            List<EvidencePo> list = new ArrayList<>();
            for(Evidence evidence : entry.getValue()){
                list.add(evidence.toEvidencePo());
            }
            poMap.put(entry.getKey(), list);
        }

        return poMap;
    }

    public static Map<String, List<Evidence>> bifurcationEvidencePoMapToMap(Map<String, List<EvidencePo>> poMap){

        Map<String, List<Evidence>> map = new HashMap<>(poMap.size());
        for(Map.Entry<String, List<EvidencePo>> entry : poMap.entrySet()){
            List<Evidence> list = new ArrayList<>();
            for(EvidencePo evidencePo : entry.getValue()){
                list.add(new Evidence(evidencePo));
            }
            map.put(entry.getKey(), list);
        }
        return map;
    }


    public long getRoundIndex() {
        return roundIndex;
    }

    public void setRoundIndex(long roundIndex) {
        this.roundIndex = roundIndex;
    }

    public BlockHeader getBlockHeader1() {
        return blockHeader1;
    }

    public void setBlockHeader1(BlockHeader blockHeader1) {
        this.blockHeader1 = blockHeader1;
    }

    public BlockHeader getBlockHeader2() {
        return blockHeader2;
    }

    public void setBlockHeader2(BlockHeader blockHeader2) {
        this.blockHeader2 = blockHeader2;
    }
}
