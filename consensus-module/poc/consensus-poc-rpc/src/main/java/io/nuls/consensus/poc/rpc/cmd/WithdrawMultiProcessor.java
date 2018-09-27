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
package io.nuls.consensus.poc.rpc.cmd;

import io.nuls.core.tools.str.StringUtils;
import io.nuls.kernel.model.CommandResult;
import io.nuls.kernel.model.NulsDigestData;
import io.nuls.kernel.model.RpcClientResult;
import io.nuls.kernel.processor.CommandProcessor;
import io.nuls.kernel.utils.CommandBuilder;
import io.nuls.kernel.utils.CommandHelper;
import io.nuls.kernel.utils.RestFulUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: tag
 */
public class WithdrawMultiProcessor implements CommandProcessor {
    private RestFulUtils restFul = RestFulUtils.getInstance();
    @Override
    public String getCommand() {
        return "withdrawMulti";
    }

    @Override
    public String getHelp() {
        CommandBuilder builder = new CommandBuilder();
        builder.newLine(getCommandDescription())
                .newLine("\t<address> \ttransfer address - Required")
                .newLine("\t<signAddress> \tsign address - Required")
                .newLine("\t<pubkey>,...<pubkey> \tPublic key that needs to be signed,If multiple commas are used to separate.")
                .newLine("\t<m> \tAt least how many signatures are required to get the money.")
                .newLine("\t<txhash> \tCurrent consensus transaction hash")
                .newLine("\t<txdata> \tExit consensus transaction data currently created");
        return builder.toString();
    }

    @Override
    public String getCommandDescription() {
        return "withdrawMulti --- If it's a trading promoter <address> <signAddress> <pubkey>,...<pubkey> <m> <txhash>" +
                "\t           --- Else <address> <signAddress> <txdata>";
    }

    @Override
    public boolean argsValidate(String[] args) {
        int length = args.length;
        if(length != 4 && length != 6){
            return  false;
        }
        if (!CommandHelper.checkArgsIsNull(args)) {
            return false;
        }
        if(!StringUtils.validAddressSimple(args[1]) || !StringUtils.validAddressSimple(args[2])){
            return false;
        }
        if(length == 6){
            if(!StringUtils.validPubkeys(args[3],args[4])) {
                return false;
            }
            if(!NulsDigestData.validHash(args[5])) {
                return false;
            }
        }else{
            if(args[3] == null || args[3].length() == 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public CommandResult execute(String[] args) {
        String signAddress = args[2];
        RpcClientResult res = CommandHelper.getPassword(signAddress, restFul);
        if(!res.isSuccess()){
            return CommandResult.getFailed(res);
        }
        String password = (String)res.getData();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("address", args[1]);
        parameters.put("signAddress", args[2]);
        if(args.length == 4){
            parameters.put("txdata",args[3]);
        }else{
            String[] pubkeys = args[3].split(",");
            parameters.put("pubkeys", Arrays.asList(pubkeys));
            parameters.put("m",Integer.parseInt(args[4]));
            parameters.put("txHash",Integer.parseInt(args[5]));
        }
        RpcClientResult result = restFul.post("/consensus/withdrawMutil", parameters);
        if (result.isFailed()) {
            return CommandResult.getFailed(result);
        }
        return CommandResult.getResult(CommandResult.dataTransformValue(result));
    }
}
