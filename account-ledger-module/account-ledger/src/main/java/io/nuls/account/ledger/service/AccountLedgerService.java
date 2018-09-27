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

package io.nuls.account.ledger.service;

import io.nuls.account.ledger.model.MultipleAddressTransferModel;
import io.nuls.account.ledger.model.TransactionInfo;
import io.nuls.account.model.Account;
import io.nuls.account.model.Balance;
import io.nuls.account.ledger.model.CoinDataResult;
import io.nuls.account.model.MultiSigAccount;
import io.nuls.core.tools.crypto.ECKey;
import io.nuls.kernel.exception.NulsException;
import io.nuls.kernel.model.Coin;
import io.nuls.kernel.model.Na;
import io.nuls.kernel.model.NulsDigestData;
import io.nuls.kernel.model.Result;
import io.nuls.kernel.model.Transaction;
import io.nuls.kernel.script.Script;
import io.nuls.kernel.script.TransactionSignature;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface AccountLedgerService {

    /**
     * save a confirmed tx to account ledger.
     * save if the tx is relative to local accounts, or do nothing.
     *
     * @param tx transaction to save
     * @return return the tx count saved,
     */
    Result<Integer> saveConfirmedTransaction(Transaction tx);

    /**
     * save a unconfirmed tx to account ledger.
     * save if the tx is relative to local accounts, or do nothing.
     *
     * @param tx transaction to save
     * @return return the tx count saved,
     */
    Result<Integer> verifyAndSaveUnconfirmedTransaction(Transaction tx);


    /**
     * save a tx to account ledger.
     * save if the tx is relative to local accounts, or do nothing.
     *
     * @param txs transactions to save
     * @return return the tx count saved,
     */
    Result<Integer> saveConfirmedTransactionList(List<Transaction> txs);


    /**
     * get an .unconfirmed transaction
     *
     * @param hash transaction hash
     * @return return the unconfirmed tx
     */
    Result<Transaction> getUnconfirmedTransaction(NulsDigestData hash);

    /**
     * get all.unconfirmed transactions
     *
     * @return return all the unconfirmed txs
     */
    Result<List<Transaction>> getAllUnconfirmedTransaction();

    /**
     * rollbackTransaction a tx in account ledger
     * save if the tx is relative to local accounts, or do nothing
     *
     * @param tx transaction to rollbackTransaction
     * @return return the tx count rollbacked
     */
    Result<Integer> deleteTransaction(Transaction tx);

    /**
     * rollbackTransaction a tx list in account ledger.
     * save if the tx is relative to local accounts, or do nothing
     *
     * @param txs transactions to rollbackTransaction
     * @return return the tx count rollbacked
     */
    Result<Integer> rollbackTransactions(List<Transaction> txs);

    /**
     * get the balance of an local account.
     *
     * @param address account address
     * @return return balance of account, return 0 if  account is not a local account
     * @throws NulsException NulsException
     */
    Result<Balance> getBalance(byte[] address);

    /**
     * get usable coinData
     *
     * @param address account address
     * @param amount  amount want to use
     * @param size    size of transaction ,to calc the fee
     * @param price   price 1/KB
     * @return return balance of account, return 0 if  account is not a local account
     * @throws NulsException NulsException
     */
    CoinDataResult getCoinData(byte[] address, Na amount, int size, Na price) throws NulsException;

    /**
     * A transfers NULS to B
     *
     * @param from     address of A
     * @param to       address of B
     * @param values   NULS amount
     * @param password password of A
     * @param remark   remarks of transaction
     * @param price    Unit price of fee
     * @return Result
     */
    Result transfer(byte[] from, byte[] to, Na values, String password, byte[] remark, Na price);

    /**
     * create and send a dapp transaction
     * @param from from address
     * @param password
     * @param data
     * @param remark
     * @return
     */
    Result dapp(byte[] from, String password, byte[] data, byte[] remark);

    /**
     * calculation fee of transaction
     *
     * @param from   address of A
     * @param to     address of B
     * @param values NULS amount
     * @param remark remarks of transaction
     * @param price  Unit price of fee
     * @return Result
     */
    Result transferFee(byte[] from, byte[] to, Na values, String remark, Na price);

    /**
     * create a transaction by inputs data and outputs data
     *
     * @param inputs  used utxos
     * @param outputs new utxos
     * @param remark  remarks of transaction
     * @return Result
     */
    Result createTransaction(List<Coin> inputs, List<Coin> outputs, byte[] remark);

    /**
     * 签名交易
     *
     * @param tx    tx
     * @param ecKey ecKey
     * @return Transaction
     * @throws IOException IOException
     */
    Transaction signTransaction(Transaction tx, ECKey ecKey) throws IOException;

    /**
     * 广播交易
     *
     * @param tx tx
     * @return Result
     */
    Result broadcast(Transaction tx);

    //    /**
//     * get local address list
//     *
//     * @return true if a address is a local address
//     */
    Result unlockCoinData(Transaction tx, long newLockTime);

    Result rollbackUnlockTxCoinData(Transaction tx);

    /**
     * load the local ledger of a account when an account imported
     *
     * @param address address
     * @return true if a address is a local address
     */
    Result importLedgerByAddress(String address);

    /**
     * @param address address
     * @return Result
     */
    Result<List<TransactionInfo>> getTxInfoList(byte[] address);

    /**
     * @param address address
     * @return Result
     */
    Result<List<Coin>> getLockedUtxo(byte[] address);

    /**
     * delete unconfirmed transactions of an account
     *
     * @param address address
     * @return Result
     */
    Result<Integer> deleteUnconfirmedTx(byte[] address);

    /**
     * 根据账户计算一次交易(不超出最大交易数据大小下)的最大金额
     * Calculate the maximum amount of a transaction (not exceeding the maximum transaction data size) based on the account
     *
     * @param address
     * @param tx
     * @param price
     * @return
     */
    Result<Na> getMaxAmountOfOnce(byte[] address, Transaction tx, Na price);

    /**
     * 根据账户计算一次交易(不超出最大交易数据大小下)的最大金额
     * Calculate the maximum amount of a transaction (not exceeding the maximum transaction data size) based on the account
     *
     * @param address
     * @param tx
     * @param price
     * @return
     */
    Result<Na> getMultiMaxAmountOfOnce(byte[] address, Transaction tx, Na price,int size);

    /**
     * 多地址转账
     * @param fromModelList
     * @param toModelList
     * @param password
     * @param amount
     * @param remark
     * @param price
     * @return
     */

    Result multipleAddressTransfer(List<MultipleAddressTransferModel> fromModelList, List<MultipleAddressTransferModel> toModelList, String password,Na amount, String remark, Na price);

    /**
     * 零钱汇整
     *
     * @param address  自己转给自己的地址
     * @param password
     * @param price
     * @return
     */
    Result changeWhole(byte[] address, String password, Na price);

    /**
     * Get the fee for the transfer transaction
     *
     * @param address
     * @param amount
     * @param size
     * @param price
     * @return
     */
    Na getTxFee(byte[] address, Na amount, int size, Na price);

    /**
     * 零钱换整手续费预估
     *
     * @param address
     * @param price
     * @return
     */
    Result estimateFee(byte[] address, Na price);

    /**
     * 获取可用总额和有多少条utxo--零钱汇整功能
     *
     * @param address
     * @return
     */
    Result getAvailableTotalUTXO(byte[] address);

    /**
     * A transfers NULS to B   多签交易
     * @param fromAddr
     * @param signAddr
     * @param outputs
     * @param values    NULS amount
     * @param password  password of A
     * @param remark    remarks of transaction
     * @param price     Unit price of fee
     * @param pubkeys   公钥列表
     * @param m         至少需要签名验证通过
     * @param txdata    签名的交易数据
     * @return Result
     */
    Result transferP2SH(byte[] fromAddr, byte[] signAddr , List<MultipleAddressTransferModel> outputs, Na values, String password, String remark, Na price, List<String>pubkeys, int m, String txdata);


    /**
     * get usable coinData 多签账户的CoinData
     *
     * @param address account address
     * @param amount  amount want to use
     * @param size    size of transaction ,to calc the fee
     * @param price   price 1/KB
     * @return return balance of account, return 0 if  account is not a local account
     * @throws NulsException NulsException
     */
    CoinDataResult getMutilCoinData(byte[] address, Na amount, int size, Na price);

    /**
     * A transfers NULS to B   多签交易
     *
     * @param fromAddr 输入地址
     * @param signAddr 签名地址
     * @param outputs  输出地址
     * @param password password of A
     * @param remark   remarks of transaction
     * @return Result
     */
    Result createP2shTransfer(String fromAddr, String signAddr, List<MultipleAddressTransferModel> outputs, String password, String remark);

    /**
     * A transfers NULS to B   多签交易
     *
     * @param signAddr 签名地址
     * @param password password of A
     * @param txdata   需要签名的数据
     * @return Result
     */
    Result signMultiTransaction(String signAddr,String password,String txdata);

    /**
     * A transfers NULS to B   多签账户签名
     *
     * @param tx         需要签名的交易
     * @param transactionSignature 已签名的签名数据
     * @param account    签名账户
     * @param password   password
     * @return Result
     */
    Result txMultiProcess(Transaction tx, TransactionSignature transactionSignature, Account account, String password);

    /**
     * A transfers NULS to B   获取多签账户的赎回脚本
     *
     * @param multiSigAccount        多签账户
     * @return Result
     */
    Script getRedeemScript(MultiSigAccount multiSigAccount);

    /**
     * A transfers NULS to B   获取交易的签名类型
     *
     * @param utxoList        交易花的UTXO
     * @return Result
     */
    Result getSignatureType(List<String> utxoList);
}
