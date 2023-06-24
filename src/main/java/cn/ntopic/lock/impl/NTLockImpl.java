/**
 * Author: obullxl@163.com
 * Copyright (c) 2020-2023 All Rights Reserved.
 */
package cn.ntopic.lock.impl;

import cn.ntopic.lock.NTLock;
import cn.ntopic.lock.model.NTLockDTO;
import cn.ntopic.lock.model.NTLockResult;
import cn.ntopic.lock.utils.NTLockUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务实现
 *
 * @author obullxl 2023年06月24日: 新增
 */
public class NTLockImpl implements NTLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(NTLockImpl.class);

    /**
     * 属性-数据源
     */
    private final DataSource ntDataSource;

    /**
     * 属性-数据表名
     */
    private String tableName = "nt_lock";

    public NTLockImpl(DataSource ntDataSource) {
        if (ntDataSource == null) {
            throw new IllegalArgumentException("锁数据源为NULL.");
        }

        this.ntDataSource = ntDataSource;
    }

    /**
     * 初始化
     */
    public void init() {
        // ignore
    }

    /**
     * 尝试创建数据表
     */
    public void createTable() {
        Connection conn = null;
        try {
            conn = this.ntDataSource.getConnection();

            // 1. 检测数据表是否存在
            ResultSet rs = null;
            try {
                rs = conn.getMetaData().getTables(null, null, this.tableName, null);
                if (rs.next()) {
                    LOGGER.info("锁数据表存在-无需创建[{}].", this.tableName);
                    return;
                }
            } finally {
                this.closeQuietly(rs);
            }

            // 2. 创建数据表
            PreparedStatement stmt = null;
            try {
                StringBuilder createSQL = new StringBuilder();
                createSQL.append(String.format("CREATE TABLE %s", this.tableName));
                createSQL.append("(");
                createSQL.append("pool      VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',");
                createSQL.append("name      VARCHAR(64) NOT NULL,");
                createSQL.append("own_host  VARCHAR(64) NOT NULL,");
                createSQL.append("own_ip    VARCHAR(64) NOT NULL,");
                createSQL.append("expire    VARCHAR(32) NOT NULL,");
                createSQL.append("size      INT         NOT NULL DEFAULT 1,");
                createSQL.append("times     INT         NOT NULL DEFAULT 1,");
                createSQL.append("modify    VARCHAR(32) NOT NULL,");
                createSQL.append("PRIMARY KEY (pool, name)");
                createSQL.append(")");

                String createTableSQL = createSQL.toString();
                LOGGER.info("锁数据表建表SQL:{}", createTableSQL);

                stmt = conn.prepareStatement(createTableSQL);
                stmt.executeUpdate();
                LOGGER.info("创建锁数据表成功[{}].", this.tableName);
            } finally {
                this.closeQuietly(stmt);
            }
        } catch (Throwable e) {
            LOGGER.error("检测锁数据表是否存在异常，请求人工创建锁数据表[{}].", this.tableName, e);
            throw new RuntimeException("检测锁数据表是否存在异常，请求人工创建锁数据表(" + this.tableName + ")", e);
        } finally {
            this.closeQuietly(conn);
        }
    }

    @Override
    public NTLockResult lock(String lockName, int timeout, TimeUnit timeUnit) {
        final Date now = new Date();

        // 参数检测
        if (lockName == null || lockName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("锁名称参数非法(" + MAX_NAME_LENGTH + ")");
        }

        if (timeout <= 0) {
            throw new IllegalArgumentException("超时时间参数非法(" + MAX_NAME_LENGTH + ")");
        }

        // 组装锁信息
        final Date newExpire = new Date(now.getTime() + timeUnit.toMillis(timeout));

        final NTLockDTO newLockDTO = new NTLockDTO(DEFAULT_POOL
                , lockName, NTLockUtils.HOST, NTLockUtils.IP, NTLockUtils.format(newExpire));
        newLockDTO.setSize(1);
        newLockDTO.setTimes(1);
        newLockDTO.setModify(NTLockUtils.format(new Date()));

        // 抢锁：查询 -> 插入 / 过期检测 -> 更新
        Connection conn = null;
        boolean autoCommit = true;
        try {
            conn = this.ntDataSource.getConnection();
            autoCommit = conn.getAutoCommit();

            if (!autoCommit) {
                conn.setAutoCommit(true);
            }

            // 1. 查询是否已经存在锁
            Optional<NTLockDTO> optLockDTO = this.select(conn, newLockDTO.getPool(), newLockDTO.getName());

            if (!optLockDTO.isPresent()) {
                // 2. 不存在锁，插入锁对象
                this.insert(conn, newLockDTO);

                // 插入锁/抢锁成功返回
                return NTLockResult.makeSuccess(newLockDTO);
            }

            // 3. 锁已经存在，则检测是否已经过期
            final NTLockDTO existLockDTO = optLockDTO.get();
            final Date existExpire = NTLockUtils.parse(existLockDTO.getExpire());

            if (!existExpire.after(now)) {
                // 3.1 当前锁已过期，尝试重新抢占锁定
                if (this.updateTaken(conn, newLockDTO)) {
                    // 更新锁/延长锁定成功返回
                    return NTLockResult.makeSuccess(newLockDTO);
                }

                // 更新锁/延长锁定失败返回
                return NTLockResult.makeFailure(newLockDTO, "锁已经过期-抢占失败");
            }

            // 3.2 未过期，检测是否为延长锁定
            if (!existLockDTO.getOwnHost().equals(newLockDTO.getOwnHost())
                    || !existLockDTO.getOwnIp().equals(newLockDTO.getOwnIp())) {
                // 非当前服务器，锁已经被其他抢占，直接失败
                return NTLockResult.makeFailure(newLockDTO, String.format("锁已经被(%s / %s)抢占-过期时间(%s)"
                        , existLockDTO.getOwnHost(), existLockDTO.getOwnIp(), existLockDTO.getExpire()));
            } else {
                // 当前服务器，未过期，则当前操作为延长锁定
                if (newExpire.after(existExpire)) {
                    if (this.updateExpire(conn, newLockDTO)) {
                        // 更新锁/延长锁定成功返回
                        return NTLockResult.makeSuccess(newLockDTO);
                    }

                    // 更新锁失败，但是还未过期，应该返回成功
                    return NTLockResult.makeSuccess(existLockDTO);
                }

                // 当前锁定时间更晚，直接返回
                return NTLockResult.makeSuccess(existLockDTO);
            }
        } catch (Throwable e) {
            return NTLockResult.makeFailure(newLockDTO, "抢锁过程发生异常(" + lockName + ")");
        } finally {
            if (!autoCommit) {
                this.closeAutoCommit(conn);
            }

            closeQuietly(conn);
        }
    }

    @Override
    public NTLockResult release(NTLockDTO lockDTO) {
        return null;
    }

    /**
     * 查询锁信息
     */
    private Optional<NTLockDTO> select(Connection conn, String pool, String name) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String selectSQL = String.format("SELECT * FROM %s WHERE pool=? AND name=?", this.tableName);
            stmt = conn.prepareStatement(selectSQL);
            stmt.setString(1, pool);
            stmt.setString(2, name);

            rs = stmt.executeQuery();

            return this.makeLockDTO(rs);
        } finally {
            this.closeQuietly(rs);
            this.closeQuietly(stmt);
        }
    }

    /**
     * 插入锁信息
     */
    private void insert(Connection conn, NTLockDTO lockDTO) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String insertSQL = String.format("INSERT INTO %s (pool,name,own_host,own_ip,expire,size,times,modify) VALUES (?,?,?,?,?,?,?,?)", this.tableName);
            stmt = conn.prepareStatement(insertSQL);

            stmt.setString(1, lockDTO.getPool());
            stmt.setString(2, lockDTO.getName());
            stmt.setString(3, lockDTO.getOwnHost());
            stmt.setString(4, lockDTO.getOwnIp());
            stmt.setString(5, lockDTO.getExpire());
            stmt.setInt(6, lockDTO.getSize());
            stmt.setInt(7, lockDTO.getTimes());
            stmt.setString(8, lockDTO.getModify());

            stmt.executeUpdate();
        } finally {
            this.closeQuietly(stmt);
        }
    }

    /**
     * 更新锁信息--延长锁定
     */
    private boolean updateExpire(Connection conn, NTLockDTO lockDTO) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String updateSQL = String.format("UPDATE %s SET expire=?,times=times+1,modify=? WHERE pool=? AND name=?", this.tableName);
            stmt = conn.prepareStatement(updateSQL);

            stmt.setString(1, lockDTO.getExpire());
            stmt.setString(2, lockDTO.getModify());
            stmt.setString(3, lockDTO.getPool());
            stmt.setString(4, lockDTO.getName());

            boolean update = stmt.executeUpdate() >= 1;
            if (update) {
                lockDTO.setTimes(lockDTO.getTimes() + 1);
            }

            return update;
        } finally {
            this.closeQuietly(stmt);
        }
    }

    /**
     * 更新锁信息--抢占锁定
     */
    private boolean updateTaken(Connection conn, NTLockDTO lockDTO) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String updateSQL = String.format("UPDATE %s SET own_host=?,own_ip=?,expire=?,times=1,modify=? WHERE pool=? AND name=?", this.tableName);
            stmt = conn.prepareStatement(updateSQL);

            stmt.setString(1, lockDTO.getOwnHost());
            stmt.setString(2, lockDTO.getOwnIp());
            stmt.setString(3, lockDTO.getExpire());
            stmt.setString(4, lockDTO.getModify());
            stmt.setString(5, lockDTO.getPool());
            stmt.setString(6, lockDTO.getName());

            boolean update = stmt.executeUpdate() >= 1;
            if (update) {
                lockDTO.setTimes(lockDTO.getTimes() + 1);
            }

            return update;
        } finally {
            this.closeQuietly(stmt);
        }
    }

    /**
     * 构建锁对象
     */
    private Optional<NTLockDTO> makeLockDTO(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return Optional.empty();
        }

        // 组装锁对象
        NTLockDTO lockDTO = new NTLockDTO(rs.getString("pool"), rs.getString("name"), rs.getString("own_host"), rs.getString("own_ip"), rs.getString("expire"));
        lockDTO.setSize(rs.getInt("size"));
        lockDTO.setTimes(rs.getInt("times"));
        lockDTO.setModify(rs.getString("modify"));

        return Optional.of(lockDTO);
    }

    /**
     * 设置非自动提交
     */
    private void closeAutoCommit(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(false);
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    /**
     * 尝试关闭
     */
    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    // ~~~~~~~~~~~~~ getters and setters ~~~~~~~~~~~~~~ //

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("锁数据表名参数非法(" + tableName + ")");
        }

        this.tableName = tableName;
    }
}
