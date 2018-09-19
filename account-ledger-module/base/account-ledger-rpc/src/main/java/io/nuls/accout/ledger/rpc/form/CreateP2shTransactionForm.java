package io.nuls.accout.ledger.rpc.form;

import io.nuls.accout.ledger.rpc.dto.MultipleTxToDto;
import io.nuls.core.tools.str.StringUtils;
import io.nuls.kernel.model.Na;
import io.nuls.kernel.utils.AddressTool;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tag
 */
@ApiModel(value = "多签交易签名表单form")
public class CreateP2shTransactionForm {
    @ApiModelProperty(name = "address", value = "账户地址", required = true)
    private String address;

    @ApiModelProperty(name = "signAddress", value = "签名地址", required = true)
    private String signAddress;

    @ApiModelProperty(name = "outputs", value = "交易输出", required = true)
    private List<MultipleTxToDto> outputs;

    @ApiModelProperty(name = "password", value = "账户密码", required = false)
    private String password;

    @ApiModelProperty(name = "remark", value = "交易备注")
    private String remark;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getSignAddress() {
        return signAddress;
    }

    public void setSignAddress(String signAddress) {
        this.signAddress = signAddress;
    }

    public List<MultipleTxToDto> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<MultipleTxToDto> outputs) {
        this.outputs = outputs;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public static boolean validToData(String todata){
        if(StringUtils.isBlank(todata)){
            return  false;
        }
        //将多个to拆分
        String[] dataList = todata.split(";");
        if(dataList == null || dataList.length == 0){
            return false;
        }
        for (String data:dataList) {
            //将每个to数据拆分为数据
            String[] separateData = data.split(",");
            if(separateData == null || separateData.length != 2){
                return false;
            }
            if (!AddressTool.validAddress(separateData[0])  || !StringUtils.isNuls(separateData[1])) {
                return false;
            }
        }
        return true;
    }

    public static List<MultipleTxToDto> getTodata(String todata){
        List<MultipleTxToDto> toDatas = new ArrayList<>();
        String[] dataList = todata.split(";");
        long toAmount;
        for (String data:dataList) {
            //将每个to数据拆分为数据
            MultipleTxToDto toData = new MultipleTxToDto();
            String[] separateData = data.split(",");
            Na toNa = Na.parseNuls(separateData[1]);
            toAmount = toNa.getValue();
            if(toAmount <= 0) {
                return null;
            }
            toData.setAmount(toAmount);
            toData.setToAddress(separateData[0]);
            toDatas.add(toData);
        }
        return  toDatas;
    }
}
