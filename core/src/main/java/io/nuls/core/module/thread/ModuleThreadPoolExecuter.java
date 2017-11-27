package io.nuls.core.module.thread;

import io.nuls.core.module.BaseNulsModule;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Niels
 * @date 2017/11/27
 */
public class ModuleThreadPoolExecuter {
    private static final ModuleThreadPoolExecuter POOL = new ModuleThreadPoolExecuter();

    private Map<Short,ModuleProcess> PROCCESS_MAP = new HashMap<>();

    private ModuleThreadPoolExecuter(){}
    public static final ModuleThreadPoolExecuter getInstance(){
        return POOL;
    }

    public void startModule(BaseNulsModule module){
        //todo
    }
    public void stopModule(BaseNulsModule module){
        //todo
    }
    public void getProcessState(BaseNulsModule module){
        //todo
    }
}
