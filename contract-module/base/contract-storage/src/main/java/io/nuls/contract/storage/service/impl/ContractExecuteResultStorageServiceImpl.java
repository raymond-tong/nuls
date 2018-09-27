/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2018 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.contract.storage.service.impl;

import io.nuls.contract.dto.ContractResult;
import io.nuls.contract.storage.constant.ContractStorageConstant;
import io.nuls.contract.storage.service.ContractExecuteResultStorageService;
import io.nuls.core.tools.log.Log;
import io.nuls.db.constant.DBErrorCode;
import io.nuls.db.service.DBService;
import io.nuls.kernel.exception.NulsException;
import io.nuls.kernel.exception.NulsRuntimeException;
import io.nuls.kernel.lite.annotation.Autowired;
import io.nuls.kernel.lite.annotation.Component;
import io.nuls.kernel.lite.annotation.Service;
import io.nuls.kernel.lite.core.bean.InitializingBean;
import io.nuls.kernel.model.NulsDigestData;
import io.nuls.kernel.model.Result;

import java.io.IOException;

/**
 * @desription:
 * @author: PierreLuo
 * @date: 2018/6/24
 */
@Component
public class ContractExecuteResultStorageServiceImpl implements ContractExecuteResultStorageService, InitializingBean {

    /**
     * 通用数据存储服务
     * Universal data storage services.
     */
    @Autowired
    private DBService dbService;

    /**
     * 该方法在所有属性被设置之后调用，用于辅助对象初始化
     * This method is invoked after all properties are set, and is used to assist object initialization.
     */
    @Override
    public void afterPropertiesSet() throws NulsException {
        Result result = dbService.createArea(ContractStorageConstant.DB_NAME_CONTRACT_EXECUTE_RESULT);
        if (result.isFailed() && !DBErrorCode.DB_AREA_EXIST.equals(result.getErrorCode())) {
            throw new NulsRuntimeException(result.getErrorCode());
        }
    }

    @Override
    public Result saveContractExecuteResult(NulsDigestData hash, ContractResult executeResult) {
        Result result;
        try {
            result = dbService.putModel(ContractStorageConstant.DB_NAME_CONTRACT_EXECUTE_RESULT, hash.getDigestBytes(), executeResult);
        } catch (Exception e) {
            Log.error("save contract execute result error", e);
            return Result.getFailed();
        }
        return result;
    }

    @Override
    public Result deleteContractExecuteResult(NulsDigestData hash) {
        try {
            return dbService.delete(ContractStorageConstant.DB_NAME_CONTRACT_EXECUTE_RESULT, hash.getDigestBytes());
        } catch (Exception e) {
            Log.error("delete contract execute result error", e);
            return Result.getFailed();
        }
    }

    @Override
    public boolean isExistContractExecuteResult(NulsDigestData hash) {
        if (hash == null) {
            return false;
        }
        byte[] contractExecuteResult = new byte[0];
        try {
            contractExecuteResult = dbService.get(ContractStorageConstant.DB_NAME_CONTRACT_EXECUTE_RESULT, hash.getDigestBytes());
        } catch (Exception e) {
            Log.error("check contract execute result error", e);
            return false;
        }
        if(contractExecuteResult == null) {
            return false;
        }
        return true;
    }

    @Override
    public ContractResult getContractExecuteResult(NulsDigestData hash) {
        if(hash == null) {
            return null;
        }
        try {
            return dbService.getModel(ContractStorageConstant.DB_NAME_CONTRACT_EXECUTE_RESULT, hash.getDigestBytes(), ContractResult.class);
        } catch (Exception e) {
            Log.error("get contract execute result error", e);
            return null;
        }
    }
}
