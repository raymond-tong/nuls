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
package io.nuls.kernel.exception;

import io.nuls.kernel.constant.ErrorCode;

/**
 * Created by facjas on 2017/10/31.
 */
public class NulsVerificationException extends NulsRuntimeException {

   /* public NulsVerificationException(String msg) {
        super(KernelErrorCode.VERIFICATION_FAILD, msg);
    }*/

    public NulsVerificationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NulsVerificationException(ErrorCode errorCode, Throwable e) {
        super(errorCode, e);
    }

//    public NulsVerificationException(ErrorCode errorCode, String msg) {
//        super(errorCode, msg);
//    }
}
