/**
 * Author: obullxl@163.com
 * Copyright (c) 2020-2023 All Rights Reserved.
 */
package cn.ntopic.lock;

import cn.ntopic.lock.model.NTLockDTO;
import cn.ntopic.lock.model.NTLockResult;

import java.util.concurrent.TimeUnit;

/**
 * 分布式序列服务
 *
 * @author obullxl 2023年06月24日: 新增
 */
public interface NTLock {

    /**
     * 默认分组名称
     */
    String DEFAULT_POOL = "DEFAULT";

    /**
     * 锁分组最大长度
     */
    int MAX_POOL_LENGTH = 64;

    /**
     * 锁名称最大长度
     */
    int MAX_NAME_LENGTH = 64;

    /**
     * 申请排它锁，或者延长已经抢占的锁
     *
     * @param lockName 排它锁名称，非空，1~64字符
     * @param timeout  锁超时时间，值>0
     * @param timeUnit 超时时间单位
     * @return 锁结果
     * @throws IllegalArgumentException 参数非法
     * @throws RuntimeException         执行异常
     */
    NTLockResult lock(String lockName, int timeout, TimeUnit timeUnit);

    /**
     * 释放排它锁，或者释放并发池锁
     *
     * @param lockDTO 申请锁对象
     * @return 锁结果
     * @throws IllegalArgumentException 参数非法
     * @throws RuntimeException         执行异常
     */
    NTLockResult release(NTLockDTO lockDTO);
}
