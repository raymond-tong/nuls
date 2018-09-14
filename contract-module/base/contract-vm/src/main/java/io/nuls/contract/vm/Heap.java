package io.nuls.contract.vm;

import io.nuls.contract.vm.code.ClassCode;
import io.nuls.contract.vm.code.FieldCode;
import io.nuls.contract.vm.code.MethodCode;
import io.nuls.contract.vm.code.VariableType;
import io.nuls.contract.vm.natives.io.nuls.contract.sdk.NativeAddress;
import io.nuls.contract.vm.util.CloneUtils;
import io.nuls.contract.vm.util.Constants;
import io.nuls.contract.vm.util.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.*;

public class Heap {

    public static final Map<ObjectRef, Map<String, Object>> INIT_OBJECTS = new HashMap<>(1024);

    public static final Map<String, Object> INIT_ARRAYS = new HashMap<>(1024);

    private VM vm;

    public final HeapMap<ObjectRef, Map<String, Object>> objects = new HeapMap<>(1024);

    public final HeapMap<String, Object> arrays = new HeapMap<>(1024);

    private final Set<ObjectRef> changes = new HashSet<>(1024);

    private ObjectRef contract;

    private byte[] address;

    private Repository repository;

    private BigInteger objectRefCount;

    private static final DataWord OBJECT_REF_COUNT = new DataWord("objectRefCount");

    public Heap(BigInteger objectRefCount) {
        this.objectRefCount = new BigInteger(objectRefCount.toString());
    }

    public void setVm(VM vm) {
        this.vm = vm;
    }

    public ObjectRef newObjectRef(String ref, String desc, int... dimensions) {
        if (StringUtils.isEmpty(ref)) {
            objectRefCount = objectRefCount.add(BigInteger.ONE);
            ref = objectRefCount.toString();
        }
        ObjectRef objectRef = new ObjectRef(ref, desc, dimensions);
        objects.put(objectRef, new LinkedHashMap<>());
        change(objectRef);
        return objectRef;
    }

    public ObjectRef newObjectRef(String desc, int... dimensions) {
        return newObjectRef(null, desc, dimensions);
    }

    public ObjectRef newObject(String ref, ClassCode classCode) {
        ObjectRef objectRef = newObjectRef(ref, classCode.variableType.getDesc());
        initFields(classCode, objectRef);
        return objectRef;
    }

    public ObjectRef newObject(ClassCode classCode) {
        return newObject(null, classCode);
    }

    public ObjectRef newObject(String className) {
        ClassCode classCode = this.vm.getMethodArea().loadClass(className);
        return newObject(classCode);
    }

    public ObjectRef newObject(VariableType variableType) {
        ClassCode classCode = this.vm.getMethodArea().loadClass(variableType.getType());
        return newObject(classCode);
    }

    public Map<String, Object> getFieldsInit(ObjectRef objectRef) {
        Map<String, Object> fields = objects.get(objectRef);
        if (fields == null) {
            fields = INIT_OBJECTS.get(objectRef);
        }
        return fields;
    }

    public Map<String, Object> getFields(ObjectRef objectRef) {
        Map<String, Object> fields = getFieldsInit(objectRef);
        if (fields == null) {
            fields = getFieldsFromState(objectRef);
            if (fields != null) {
                objects.put(objectRef, fields);
            }
        }
        return fields;
    }

    public void putFields(ObjectRef objectRef, Map<String, Object> fields) {
        objects.put(objectRef, fields);
        change(objectRef);
    }

    public Map<String, Object> putFields(ObjectRef objectRef) {
        Map<String, Object> fields = objects.get(objectRef);
        if (fields == null) {
            fields = INIT_OBJECTS.get(objectRef);
            if (fields != null) {
                fields = CloneUtils.clone(fields);
                objects.put(objectRef, fields);
            } else {
                fields = getFieldsFromState(objectRef);
                if (fields != null) {
                    objects.put(objectRef, fields);
                }
            }
        }
        return fields;
    }

    public Map<String, Object> getFieldsFromState(ObjectRef objectRef) {
        if (this.repository == null) {
            return null;
        }
        String key = JsonUtils.encode(objectRef);
        DataWord dataWord = this.repository.getStorageValue(this.address, new DataWord(key));
        if (dataWord == null) {
            return null;
        }
        byte[] value = dataWord.getNoLeadZeroesData();
        Map<String, Object> map = (Map<String, Object>) JsonUtils.decode(new String(value));
        return map;
    }

    public Object getField(ObjectRef objectRef, String fieldName) {
        return getFields(objectRef).get(fieldName);
    }

    public void putField(ObjectRef objectRef, String fieldName, Object value) {
        putFields(objectRef).put(fieldName, value);
        change(objectRef);
    }

    public Object getStatic(String className, String fieldName) {
        ObjectRef objectRef = getStaticObjectRef(className);
        return getField(objectRef, fieldName);
    }

    public void putStatic(String className, String fieldName, Object value) {
        ObjectRef objectRef = getStaticObjectRef(className);
        putField(objectRef, fieldName, value);
    }

    private ObjectRef getStaticObjectRef(String className) {
        ClassCode classCode = this.vm.getMethodArea().loadClass(className);
        ObjectRef objectRef = new ObjectRef(classCode.name, classCode.variableType.getDesc());
        Map<String, Object> map = getFieldsInit(objectRef);
        if (map == null) {
            objectRef = newObjectRef(classCode.name, classCode.variableType.getDesc());
        }
        return objectRef;
    }

    public ObjectRef newArray(VariableType type, int... dimensions) {
        ObjectRef objectRef = newObjectRef(type.getDesc(), dimensions);
        return objectRef;
    }

    public Object getArrayInit(ObjectRef arrayRef, Integer key) {
        String arrayKey = arrayRef.getRef() + "_" + key;
        Object object = arrays.get(arrayKey);
        if (object == null) {
            object = INIT_ARRAYS.get(arrayKey);
        }
        return object;
    }

    public Object putArrayInit(ObjectRef arrayRef, Integer key) {
        String arrayKey = arrayRef.getRef() + "_" + key;
        Object object = arrays.get(arrayKey);
        if (object == null) {
            object = INIT_ARRAYS.get(arrayKey);
            if (object != null) {
                object = CloneUtils.cloneObject(object);
                arrays.put(arrayKey, object);
            }
        }
        return object;
    }

    public Object getArrayChunk(ObjectRef arrayRef, int chunkNum, boolean write) {
        getFields(arrayRef);
        String key = Integer.toString(chunkNum);
        String arrayKey = arrayRef.getRef() + "_" + key;
        Object value = null;
        if (write) {
            value = putArrayInit(arrayRef, chunkNum);
        } else {
            value = getArrayInit(arrayRef, chunkNum);
        }
        if (value == null) {
            value = getArrayChunkFromState(arrayRef, arrayKey);
            if (value != null) {
                arrays.put(arrayKey, value);
            }
        }
        if (value == null) {
            int arrayLength = getArrayLength(arrayRef);
            int chunkLength = (chunkNum + 1) * 1024 <= arrayLength ? 1024 : arrayLength % 1024;
            if (arrayRef.getDimensions().length == 1 && arrayRef.getVariableType().isPrimitiveType()) {
                Class componentType = arrayRef.getVariableType().getPrimitiveTypeClass();
                value = Array.newInstance(componentType, chunkLength);
            } else {
                value = new ObjectRef[chunkLength];
            }
            this.arrays.put(arrayKey, value);
            putField(arrayRef, key, key);
        }
        value = getArrayInit(arrayRef, chunkNum);
        return value;
    }

    public Object getArrayChunkFromState(ObjectRef arrayRef, String arrayKey) {
        if (this.repository == null) {
            return null;
        }
        DataWord dataWord = this.repository.getStorageValue(this.address, new DataWord(arrayKey));
        if (dataWord == null) {
            return null;
        }
        byte[] value = dataWord.getNoLeadZeroesData();
        Class clazz = arrayRef.getVariableType().getPrimitiveTypeClass();
        if (!arrayRef.getVariableType().getComponentType().isPrimitive()) {
            clazz = ObjectRef.class;
        }
        Object object = JsonUtils.decodeArray(value, clazz);
        return object;
    }

    public Object getArray(ObjectRef arrayRef, int index) {
        int chunkNum = index / 1024;
        int chunkIndex = index % 1024;
        Object arrayChunk = getArrayChunk(arrayRef, chunkNum, false);
        Object value = Array.get(arrayChunk, chunkIndex);
        if (value == null && arrayRef.getDimensions().length > 1) {
            int[] dimensions = new int[arrayRef.getDimensions().length - 1];
            System.arraycopy(arrayRef.getDimensions(), 1, dimensions, 0, arrayRef.getDimensions().length - 1);
            VariableType variableType = VariableType.valueOf(arrayRef.getVariableType().getDesc().substring(1));
            value = newArray(variableType, dimensions);
            putArray(arrayRef, index, value);
        }
        return value;
    }

    public void putArray(ObjectRef arrayRef, int index, Object value) {
        int chunkNum = index / 1024;
        int chunkIndex = index % 1024;
        Object arrayChunk = getArrayChunk(arrayRef, chunkNum, true);
        Array.set(arrayChunk, chunkIndex, value);
        change(arrayRef);
    }

    public void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
        if (length < 1) {
            return;
        }
        checkArray(src, srcPos);
        checkArray(src, srcPos + length - 1);
        checkArray(dest, destPos);
        checkArray(dest, destPos + length - 1);

        while (length > 0) {
            int srcChunk = srcPos / 1024;
            int srcIndex = srcPos % 1024;
            int destChunk = destPos / 1024;
            int destIndex = destPos % 1024;
            int index = Math.max(srcIndex, destIndex);
            int copyLength = 1024 - index;
            copyLength = Math.min(copyLength, length);
            arrayChunkCopy(src, srcChunk, srcIndex, dest, destChunk, destIndex, copyLength);
            srcPos += copyLength;
            destPos += copyLength;
            length -= copyLength;
        }
    }

    public void arrayChunkCopy(Object src, int srcChunk, int srcPos, Object dest, int destChunk, int destPos, int length) {
        Object srcArray = src;
        if (src instanceof ObjectRef) {
            srcArray = getArrayChunk((ObjectRef) src, srcChunk, false);
        } else {
            srcPos = srcChunk * 1024 + srcPos;
        }
        Object destArray = dest;
        if (dest instanceof ObjectRef) {
            ObjectRef destObjectRef = (ObjectRef) dest;
            destArray = getArrayChunk(destObjectRef, destChunk, true);
            change(destObjectRef);
        } else {
            destPos = destChunk * 1024 + destPos;
        }
        System.arraycopy(srcArray, srcPos, destArray, destPos, length);
    }

    public ObjectRef newArray(char[] chars) {
        if (chars == null) {
            return null;
        }
        ObjectRef objectRef = newArray(VariableType.CHAR_ARRAY_TYPE, chars.length);
        arraycopy(chars, 0, objectRef, 0, chars.length);
        return objectRef;
    }

    public ObjectRef newArray(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ObjectRef objectRef = newArray(VariableType.BYTE_ARRAY_TYPE, bytes.length);
        arraycopy(bytes, 0, objectRef, 0, bytes.length);
        return objectRef;
    }

    public ObjectRef newArray(Object array, VariableType variableType, int length) {
        if (array == null) {
            return null;
        }
        ObjectRef objectRef = newArray(variableType, length);
        arraycopy(array, 0, objectRef, 0, length);
        return objectRef;
    }

    public ObjectRef newString(String str) {
        if (str == null) {
            return null;
        }
        ObjectRef objectRef = newObjectRef(VariableType.STRING_TYPE.getDesc());
        putField(objectRef, Constants.HASH, str.hashCode());
        putField(objectRef, Constants.VALUE, newArray(str.toCharArray()));
        return objectRef;
    }

    public ObjectRef newBigInteger(String value) {
        ObjectRef objectRef = runNewObject(VariableType.BIGINTEGER_TYPE, value);
        return objectRef;
    }

    public ObjectRef newAddress(String value) {
        ObjectRef objectRef = runNewObject(VariableType.ADDRESS_TYPE, value);
        return objectRef;
    }

    public ObjectRef newCharacter(char value) {
        return runNewObjectWithArgs(VariableType.CHAR_WRAPPER_TYPE, Constants.CHAR_CONSTRUCTOR_DESC, value);
    }

    public ObjectRef runNewObject(VariableType variableType, byte[] bytes) {
        ObjectRef ref = newArray(bytes);
        return runNewObjectWithArgs(variableType, Constants.BYTES_CONSTRUCTOR_DESC, ref);
    }

    public ObjectRef runNewObject(VariableType variableType) {
        return runNewObjectWithArgs(variableType, Constants.CONSTRUCTOR_DESC);
    }

    public ObjectRef runNewObject(VariableType variableType, String str) {
        ObjectRef strRef = newString(str);
        return runNewObjectWithArgs(variableType, Constants.CONSTRUCTOR_STRING_DESC, strRef);
    }

    public ObjectRef runNewObjectWithArgs(VariableType variableType, String methodDesc, Object... args) {
        ClassCode classCode = this.vm.getMethodArea().loadClass(variableType.getType());
        ObjectRef objectRef = newObject(classCode);
        MethodCode methodCode = this.vm.getMethodArea().loadMethod(objectRef.getVariableType().getType(), Constants.CONSTRUCTOR_NAME, methodDesc);
        if (methodCode == null) {
            throw new RuntimeException(String.format("can't new %s", variableType.getType()));
        }
        Object[] runArgs = new Object[args.length + 1];
        runArgs[0] = objectRef;
        for (int i = 1; i < runArgs.length; i++) {
            runArgs[i] = args[i - 1];
        }
        this.vm.run(methodCode, runArgs, false);
        return objectRef;
    }

    public ObjectRef getClassRef(String desc) {
        ObjectRef objectRef = new ObjectRef(desc, Constants.CLASS_DESC);
        Object object = getFields(objectRef);
        if (object == null) {
            ClassCode classCode = this.vm.getMethodArea().loadClass(Constants.CLASS_NAME);
            objectRef = newObject(desc, classCode);
        }
        return objectRef;
    }

    public Object getObject(ObjectRef objectRef) {
        if (objectRef == null) {
            return null;
        } else if (objectRef.isArray() && objectRef.getVariableType().isPrimitiveType() && objectRef.getDimensions().length == 1) {
            Class componentType = objectRef.getVariableType().getPrimitiveTypeClass();
            Object array = Array.newInstance(componentType, objectRef.getDimensions());
            int length = getArrayLength(objectRef);
            arraycopy(objectRef, 0, array, 0, length);
            return array;
        } else if (VariableType.STRING_ARRAY_TYPE.equals(objectRef.getVariableType())) {
            int length = getArrayLength(objectRef);
            String[] strings = new String[length];
            for (int i = 0; i < strings.length; i++) {
                ObjectRef ref = (ObjectRef) getArray(objectRef, i);
                String str = runToString(ref);
                strings[i] = str;
            }
            return strings;
        } else if (VariableType.STRING_TYPE.equals(objectRef.getVariableType())) {
            ObjectRef charsRef = (ObjectRef) getField(objectRef, Constants.VALUE);
            char[] chars = (char[]) getObject(charsRef);
            String str = new String(chars);
            return str;
        } else if (VariableType.BIGINTEGER_TYPE.equals(objectRef.getVariableType())) {
            return toBigInteger(objectRef);
        } else {
            return runToString(objectRef);
        }
    }

    public String runToString(ObjectRef objectRef) {
        if (objectRef == null) {
            return null;
        }
        String type = objectRef.getVariableType().getType();
        if (objectRef.getVariableType().isArray() && objectRef.getVariableType().isPrimitiveType()) {
            type = VariableType.OBJECT_TYPE.getType();
        }
        MethodCode methodCode = this.vm.getMethodArea().loadMethod(type, Constants.TO_STRING_METHOD_NAME, Constants.TO_STRING_METHOD_DESC);
        this.vm.run(methodCode, new Object[]{objectRef}, false);
        Object result = this.vm.getResultValue();
        String value = (String) getObject((ObjectRef) result);
        return value;
    }

    public String stackTrace(ObjectRef objectRef) {
        if (objectRef == null) {
            return null;
        }
        StringBuilder s = new StringBuilder();
        s.append(runToString(objectRef));
        s.append("\n");
        ObjectRef stackTraceElementsRef = (ObjectRef) getField(objectRef, "stackTraceElements");
        int size = stackTraceElementsRef.getDimensions()[0];
        for (int i = 0; i < size; i++) {
            ObjectRef stackTraceElementRef = (ObjectRef) getArray(stackTraceElementsRef, i);
            s.append("\tat " + runToString(stackTraceElementRef));
            s.append("\n");
        }
        return s.toString();
    }

    public BigInteger toBigInteger(ObjectRef objectRef) {
        String value = runToString(objectRef);
        if (value == null) {
            return null;
        }
        return new BigInteger(value);
    }

    public ObjectRef newContract(byte[] address, ClassCode contractCode, Repository repository) {
        ObjectRef objectRef = newObject(NativeAddress.toString(address), contractCode);
        this.contract = objectRef;
        this.address = address;
        this.repository = repository;
        return this.contract;
    }

    public ObjectRef loadContract(byte[] address, ClassCode contractCode, Repository repository) {
        if (this.contract != null) {
            return this.contract;
        }
        ObjectRef objectRef = new ObjectRef(NativeAddress.toString(address), contractCode.variableType.getDesc());
        this.contract = objectRef;
        this.address = address;
        this.repository = repository;
        this.objectRefCount = this.repository.getStorageValue(this.address, OBJECT_REF_COUNT).toBigInteger();
        String className = this.contract.getVariableType().getType();
        ObjectRef staticObjectRef = getStaticObjectRef(className);
        Map<String, Object> fields = getFieldsFromState(staticObjectRef);
        if (fields != null) {
            objects.put(staticObjectRef, fields);
        }
        return this.contract;
    }

    public Map<DataWord, DataWord> contractState() {
        Map<DataWord, DataWord> contractState = new HashMap<>(1024);
        contractState.put(OBJECT_REF_COUNT, new DataWord(this.objectRefCount));
        Set<ObjectRef> stateObjectRefs = new HashSet<>();
        String className = this.contract.getVariableType().getType();
        ObjectRef staticObjectRef = getStaticObjectRef(className);
        stateObjectRefs(stateObjectRefs, staticObjectRef);
        stateObjectRefs(stateObjectRefs, this.contract);
        for (ObjectRef objectRef : stateObjectRefs) {
            if (!this.changes.contains(objectRef)) {
                continue;
            }
            Map<String, Object> fields = getFieldsInit(objectRef);
            if (fields == null) {
                continue;
            }
            String key = JsonUtils.encode(objectRef);
            String value = JsonUtils.encode(fields);
            contractState.put(new DataWord(key), new DataWord(value));
            if (objectRef.isArray()) {
                for (String k : fields.keySet()) {
                    String arrayKey = objectRef.getRef() + "_" + k;
                    Object object = getArrayInit(objectRef, Integer.valueOf(k));
                    if (object != null) {
                        Class clazz = objectRef.getVariableType().getPrimitiveTypeClass();
                        if (!objectRef.getVariableType().getComponentType().isPrimitive()) {
                            clazz = ObjectRef.class;
                        }
                        String arrayValue = JsonUtils.encodeArray(object, clazz);
                        contractState.put(new DataWord(arrayKey), new DataWord(arrayValue));
                    }
                }
            }
        }
        return contractState;
    }

    public void stateObjectRefs(Set<ObjectRef> stateObjectRefs, ObjectRef objectRef) {
        if (!stateObjectRefs.contains(objectRef)) {
            stateObjectRefs.add(objectRef);
            Map<String, Object> fields = getFieldsInit(objectRef);
            if (fields != null) {
                for (String key : fields.keySet()) {
                    Object object = fields.get(key);
                    if (object != null) {
                        if (object instanceof ObjectRef) {
                            stateObjectRefs(stateObjectRefs, (ObjectRef) object);
                        }
                        if (objectRef.isArray()) {
                            Object array = getArrayInit(objectRef, Integer.valueOf(key));
                            if (array != null && !objectRef.getVariableType().getComponentType().isPrimitive()) {
                                int length = Array.getLength(array);
                                for (int i = 0; i < length; i++) {
                                    Object a = Array.get(array, i);
                                    if (a != null) {
                                        stateObjectRefs(stateObjectRefs, (ObjectRef) a);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void change(ObjectRef objectRef) {
        if (objectRef != null && !this.changes.contains(objectRef)) {
            this.changes.add(objectRef);
        }
    }

    private void initFields(ClassCode classCode, ObjectRef objectRef) {
        if (StringUtils.isNotBlank(classCode.superName)) {
            ClassCode superClassCode = this.vm.getMethodArea().loadClass(classCode.superName);
            initFields(superClassCode, objectRef);
        }
        for (FieldCode fieldCode : classCode.fields.values()) {
            if (!fieldCode.isStatic) {
                putField(objectRef, fieldCode.name, fieldCode.variableType.getDefaultValue());
            }
        }
    }

    private void checkArray(Object array, int index) {
        if (array instanceof ObjectRef) {
            checkArray((ObjectRef) array, index);
        } else {
            if (array == null) {
                throw new NullPointerException();
            }
            int length = Array.getLength(array);
            if (index < 0 || index >= length) {
                throw new ArrayIndexOutOfBoundsException(index);
            }
        }
    }

    private void checkArray(ObjectRef arrayRef, int index) {
        if (arrayRef == null) {
            throw new NullPointerException();
        }
        int length = getArrayLength(arrayRef);
        if (index < 0 || index >= length) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
    }

    private int getArrayLength(ObjectRef arrayRef) {
        int length = arrayRef.getDimensions()[0];
        return length;
    }

    public boolean existContract(byte[] address) {
        if (this.repository != null && this.repository.isExist(address)) {
            return true;
        } else {
            return false;
        }
    }

    public BigInteger getObjectRefCount() {
        return objectRefCount;
    }

}
