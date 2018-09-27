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
package io.nuls.contract;

import io.nuls.contract.util.VMContext;
import io.nuls.contract.vm.natives.io.nuls.contract.sdk.NativeAddress;
import io.nuls.contract.vm.program.ProgramCall;
import io.nuls.contract.vm.program.ProgramCreate;
import io.nuls.contract.vm.program.ProgramExecutor;
import io.nuls.contract.vm.program.ProgramResult;
import io.nuls.contract.vm.program.impl.ProgramExecutorImpl;
import io.nuls.db.service.DBService;
import io.nuls.db.service.impl.LevelDBServiceImpl;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

public class CrowdsaleTest {

    private VMContext vmContext;
    private DBService dbService;
    private ProgramExecutor programExecutor;

    private static final String CROWDSALE_ADDRESS = "Nse5gVscugsS8C1S1svh6CMcpoVWewpa";
    private static final String TOKEN_ADDRESS = "Nse5j2pZLWHH9iF4GW9Cja2sFyVXQtDX";
    private static final String WALLET_ADDRESS = "Nse4XLQZa2KU3WCft42zJD1SjhKhFfvk";
    private static final String SENDER = "NsdtZeAKoWqPy5AWjKyWPZGCudZD6Jx6";
    private static final String BUYER = "NsdwCuCKs2AXFfUT7PxXXJPm2XxybX6H";

    @Before
    public void setUp() {
        dbService = new LevelDBServiceImpl();
        programExecutor = new ProgramExecutorImpl(vmContext, dbService);
    }

    @Test
    public void testCreate() throws IOException {
        InputStream in = new FileInputStream(ContractTest.class.getResource("/crowdsale_contract").getFile());
        byte[] contractCode = IOUtils.toByteArray(in);

        ProgramCreate programCreate = new ProgramCreate();
        programCreate.setContractAddress(NativeAddress.toBytes(CROWDSALE_ADDRESS));
        programCreate.setSender(NativeAddress.toBytes(SENDER));
        programCreate.setPrice(1);
        programCreate.setGasLimit(1000000);
        programCreate.setNumber(1);
        programCreate.setContractCode(contractCode);
        programCreate.args("0", "20000", "10", WALLET_ADDRESS, "20000000", TOKEN_ADDRESS, "10000000");
        System.out.println(programCreate);

        byte[] prevStateRoot = Hex.decode("be6bf2471a4d13177a399ff0201f1d737749aa78979490486cd758fa8d05535b");

        ProgramExecutor track = programExecutor.begin(prevStateRoot);
        ProgramResult programResult = track.create(programCreate);
        track.commit();

        System.out.println(programResult);
        System.out.println("stateRoot: " + Hex.toHexString(track.getRoot()));
        System.out.println();
    }

    @Test
    public void testBuyTokens() throws IOException {
        ProgramCall programCall = new ProgramCall();
        programCall.setContractAddress(NativeAddress.toBytes(CROWDSALE_ADDRESS));
        programCall.setSender(NativeAddress.toBytes(SENDER));
        programCall.setValue(new BigInteger("1000"));
        programCall.setPrice(1);
        programCall.setGasLimit(1000000);
        programCall.setNumber(1);
        programCall.setMethodName("buyTokens");
        programCall.setMethodDesc("");
        programCall.args(BUYER);
        System.out.println(programCall);

        byte[] prevStateRoot = Hex.decode("ed205428a6562ce1680d6b4f0bdca19261791986e078ab6767b60c51da68316f");

        ProgramExecutor track = programExecutor.begin(prevStateRoot);
        ProgramResult programResult = track.call(programCall);
        track.commit();

        System.out.println(programResult);
        System.out.println("stateRoot: " + Hex.toHexString(track.getRoot()));
        System.out.println();

        ProgramCall programCall1 = new ProgramCall();
        programCall1.setContractAddress(NativeAddress.toBytes(TOKEN_ADDRESS));
        programCall1.setSender(NativeAddress.toBytes(SENDER));
        programCall1.setPrice(1);
        programCall1.setGasLimit(1000000);
        programCall1.setNumber(1);
        programCall1.setMethodName("balanceOf");
        programCall1.setMethodDesc("");
        programCall1.args(BUYER);

        track = programExecutor.begin(track.getRoot());
        programResult = track.call(programCall1);
        track.commit();

        System.out.println(programResult);
        System.out.println("stateRoot: " + Hex.toHexString(track.getRoot()));
        System.out.println();
    }

}
