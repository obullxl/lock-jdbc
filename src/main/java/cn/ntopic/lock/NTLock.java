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
     * 1. 首次抢占排它锁
     * 2. 延长已抢占的排他锁
     *
     * @param lockName 排它锁名称，非空，1~64字符
     * @param timeout  锁超时时间，值>0
     * @param timeUnit 超时时间单位
     * @return 锁结果
     * @throws IllegalArgumentException 参数非法
     */
    NTLockResult lock(String lockName, int timeout, TimeUnit timeUnit);

    /**
     * 1. 首次抢占：包括排它锁，和并发池锁
     * 2. 延长已抢占的锁：包括排他锁，和并发池锁
     *
     * @param lockDTO  锁信息，包括排它锁和并发池锁
     * @param timeout  锁超时时间，值>0
     * @param timeUnit 超时时间单位
     * @return 锁结果
     * @throws IllegalArgumentException 参数非法
     */
    NTLockResult lock(NTLockDTO lockDTO, int timeout, TimeUnit timeUnit);

    /**
     * 随机抢占锁池并发锁
     *
     * @param poolName 并发锁池名称，非空，1~64字符
     * @param count    并发锁池并发数量，值>0，当=1时，相当于排它锁
     * @param timeout  锁超时时间，值>0
     * @param timeUnit 超时时间单位
     * @return 锁结果
     * @throws IllegalArgumentException 参数非法
     */
    NTLockResult lockPool(String poolName, int count, int timeout, TimeUnit timeUnit);

    /**
     * 释放排它锁
     *
     * @param lockName 排它锁名称，非空，1~64字符
     * @return 释放结果，true-代表释放成功，false-代表失败或者未知异常
     * @throws IllegalArgumentException 参数非法
     */
    boolean release(String lockName);

    /**
     * 释放排它锁，或者释放并发池锁
     *
     * @param lockDTO 申请锁对象
     * @return 释放结果，true-代表释放成功，false-代表失败或者未知异常
     * @throws IllegalArgumentException 参数非法
     */
    boolean release(NTLockDTO lockDTO);
}
