/**
 * Author: obullxl@163.com
 * Copyright (c) 2020-2023 All Rights Reserved.
 */
package cn.ntopic.lock.model;

import cn.ntopic.lock.utils.NTDateUtils;

import java.io.Serializable;
import java.util.Date;

/**
 * 锁对象
 *
 * @author obullxl 2023年06月24日: 新增
 */
public class NTLockDTO implements Serializable {

    /**
     * 锁分组
     */
    private final String pool;

    /**
     * 锁名称
     */
    private final String name;

    /**
     * 锁定服务器
     */
    private final String ownHost;

    /**
     * 锁定服务器IP
     */
    private final String ownIp;

    /**
     * 锁定服务器线程ID
     */
    private final long ownId;

    /**
     * 超时时间（格式：yyyy-MM-dd HH:mm:ss.SSS）
     */
    private final String expire;

    /**
     * 锁池大小
     */
    private int size = 1;

    /**
     * 锁定次数
     */
    private int times = 1;

    /**
     * 修改时间（格式：yyyy-MM-dd HH:mm:ss.SSS）
     */
    private String modify;

    /**
     * CTOR-构建锁对象
     */
    public NTLockDTO(String pool, String name, String ownHost, String ownIp, long ownId, String expire) {
        this.pool = pool;
        this.name = name;
        this.ownHost = ownHost;
        this.ownIp = ownIp;
        this.ownId = ownId;
        this.expire = expire;
    }

    /**
     * 获取时间
     */
    public Date fetchExpireTime() {
        return NTDateUtils.parse(this.getExpire());
    }

    @Override
    public String toString() {
        return String.format("NTLockDTO[pool=%s, name=%s, ownHost=%s, ownIp=%s, ownId=%s, expire=%s, size=%s, times=%s, modify=%s]",
                this.getPool(), this.getName(), this.getOwnHost(), this.getOwnIp(), this.getOwnId(), this.getExpire()
                , this.getSize(), this.getTimes(), this.getModify());
    }

    // ~~~~~~~~~~~~~~~~ getters and setters ~~~~~~~~~~~~~~~~~~~ //


    public String getPool() {
        return pool;
    }

    public String getName() {
        return name;
    }

    public String getOwnHost() {
        return ownHost;
    }

    public String getOwnIp() {
        return ownIp;
    }

    public long getOwnId() {
        return ownId;
    }

    public String getExpire() {
        return expire;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getTimes() {
        return times;
    }

    public void setTimes(int times) {
        this.times = times;
    }

    public String getModify() {
        return modify;
    }

    public void setModify(String modify) {
        this.modify = modify;
    }
}
