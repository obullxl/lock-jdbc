/**
 * Author: obullxl@163.com
 * Copyright (c) 2020-2023 All Rights Reserved.
 */
package cn.ntopic.lock.model;

import java.io.Serializable;

/**
 * 锁定、延长锁定结果对象
 *
 * @author obullxl 2023年06月24日: 新增
 */
public class NTLockResult implements Serializable {

    /**
     * 锁定结果
     */
    private final boolean success;

    /**
     * 锁对象
     */
    private final NTLockDTO lockDTO;

    /**
     * 锁定失败描述
     */
    private String message;

    /**
     * CTOR-构建锁结果
     */
    private NTLockResult(boolean success, NTLockDTO lockDTO) {
        this.success = success;
        this.lockDTO = lockDTO;
    }

    /**
     * 构建成功结果
     */
    public static NTLockResult makeSuccess(NTLockDTO lockDTO) {
        return new NTLockResult(true, lockDTO);
    }

    /**
     * 构建失败结果
     */
    public static NTLockResult makeFailure(NTLockDTO lockDTO) {
        return new NTLockResult(false, lockDTO);
    }

    /**
     * 构建失败结果
     */
    public static NTLockResult makeFailure(NTLockDTO lockDTO, String message) {
        NTLockResult result = makeFailure(lockDTO);
        result.setMessage(message);

        return result;
    }

    @Override
    public String toString() {
        return String.format("NTLockResult[success=%s, message=%s, lockDTO=%s]",
                this.isSuccess(), this.getMessage(), this.getLockDTO());
    }

    // ~~~~~~~~~~~~~~~~ getters and setters ~~~~~~~~~~~~~~~~~~~ //


    public boolean isSuccess() {
        return success;
    }

    public NTLockDTO getLockDTO() {
        return lockDTO;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
