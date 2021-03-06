package com.dwarfeng.bitalarm.impl.handler;

import com.dwarfeng.bitalarm.stack.bean.entity.AlarmHistory;
import com.dwarfeng.bitalarm.stack.bean.entity.AlarmInfo;
import com.dwarfeng.bitalarm.stack.bean.entity.AlarmSetting;
import com.dwarfeng.bitalarm.stack.bean.entity.CurrentAlarm;
import com.dwarfeng.bitalarm.stack.exception.AlarmDisabledException;
import com.dwarfeng.bitalarm.stack.handler.AlarmHandler;
import com.dwarfeng.bitalarm.stack.handler.AlarmLocalCacheHandler;
import com.dwarfeng.bitalarm.stack.handler.ConsumeHandler;
import com.dwarfeng.bitalarm.stack.service.CurrentAlarmMaintainService;
import com.dwarfeng.subgrade.stack.bean.key.LongIdKey;
import com.dwarfeng.subgrade.stack.exception.HandlerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class AlarmHandlerImpl implements AlarmHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmHandlerImpl.class);
    private static final char[] hexCode = "0123456789ABCDEF".toCharArray();
    private static final int BIT_PER_BYTE = 8;

    @Autowired
    private AlarmLocalCacheHandler alarmLocalCacheHandler;
    @Autowired
    private CurrentAlarmMaintainService currentAlarmMaintainService;
    @Autowired
    @Qualifier("alarmUpdatedEventConsumeHandler")
    private ConsumeHandler<AlarmInfo> alarmUpdatedEventConsumeHandler;
    @Autowired
    @Qualifier("alarmInfoValueConsumeHandler")
    private ConsumeHandler<AlarmInfo> alarmInfoValueConsumeHandler;
    @Autowired
    @Qualifier("historyRecordedEventConsumeHandler")
    private ConsumeHandler<AlarmHistory> historyRecordedEventConsumeHandler;
    @Autowired
    @Qualifier("alarmHistoryValueConsumeHandler")
    private ConsumeHandler<AlarmHistory> alarmHistoryValueConsumeHandler;

    // 由于报警逻辑严格与时间相关，此处使用公平锁保证执行顺序的一致性。
    private final Lock lock = new ReentrantLock(true);

    private boolean enabledFlag = false;

    @Override
    public boolean isEnabled() {
        lock.lock();
        try {
            return enabledFlag;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void enable() {
        lock.lock();
        try {
            if (!enabledFlag) {
                LOGGER.info("启用 alarm handler...");
                enabledFlag = true;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void disable() {
        lock.lock();
        try {
            if (enabledFlag) {
                LOGGER.info("禁用 alarm handler...");
                enabledFlag = false;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void processAlarm(long pointId, byte[] data, Date happenedDate) throws HandlerException {
        lock.lock();
        try {
            // 判断是否允许记录，如果不允许，直接报错。
            if (!isEnabled()) {
                throw new AlarmDisabledException();
            }

            // 0. 记录日志，准备工作。
            LOGGER.debug("分析并记录数据点 " + pointId + " 的报警信息: " + toHexString(data));
            LongIdKey pointKey = new LongIdKey(pointId);
            // 1. 获取指定数据点的所有报警设置。
            List<AlarmSetting> alarmSettings = alarmLocalCacheHandler.getAlarmSetting(pointKey);
            if (Objects.isNull(alarmSettings) || alarmSettings.isEmpty()) {
                LOGGER.debug("找不到数据点 " + pointId + " 的任何报警配置，分析过程中止");
                return;
            }
            // 2. 分析所有报警设置。
            for (AlarmSetting alarmSetting : alarmSettings) {
                // 2.1 判断报警设置的index是否越界，如果越界，则中止。
                int index = alarmSetting.getIndex();
                if (index < 0) {
                    LOGGER.warn("无效的报警配置 " + alarmSetting.getKey() + ": 报警配置的 index 为 " + index + ", 小于0");
                    continue;
                }
                if (index >= data.length * BIT_PER_BYTE) {
                    LOGGER.warn("无效的报警配置 " + alarmSetting.getKey() + ": 报警配置的 index 为 " + index +
                            ", 超过了报警数据的长度 " + data.length * BIT_PER_BYTE);
                    continue;
                }
                // 2.2 取出指定index上的报警的bit值，生成alarmInfo。
                AlarmInfo alarmInfo = new AlarmInfo(
                        alarmSetting.getKey(),
                        alarmSetting.getPointId(),
                        alarmSetting.getIndex(),
                        alarmSetting.getAlarmMessage(),
                        alarmSetting.getAlarmType(),
                        happenedDate,
                        checkAlarming(data, index)
                );
                // 2.3 如果报警信息alarming=true
                if (alarmInfo.isAlarming()) {
                    // 如果当前报警不存在报警信息，则新建报警信息。
                    if (!currentAlarmMaintainService.exists(alarmInfo.getKey())) {
                        CurrentAlarm currentAlarm = new CurrentAlarm(
                                alarmInfo.getKey(),
                                alarmInfo.getPointId(),
                                alarmInfo.getIndex(),
                                alarmInfo.getAlarmMessage(),
                                alarmInfo.getAlarmType(),
                                alarmInfo.getHappenedDate()
                        );
                        currentAlarmMaintainService.insertOrUpdate(currentAlarm);
                    }
                }
                // 2.4 否则。
                else {
                    // 如果当前报警存在报警信息，则消费者消费报警历史，撤下当前报警，消费者消费报警历史记录事件。
                    if (currentAlarmMaintainService.exists(alarmInfo.getKey())) {
                        CurrentAlarm currentAlarm = currentAlarmMaintainService.get(alarmInfo.getKey());
                        currentAlarmMaintainService.deleteIfExists(alarmInfo.getKey());
                        AlarmHistory alarmHistory = new AlarmHistory(
                                null,
                                currentAlarm.getKey(),
                                currentAlarm.getIndex(),
                                currentAlarm.getAlarmMessage(),
                                currentAlarm.getAlarmType(),
                                currentAlarm.getHappenedDate(),
                                alarmInfo.getHappenedDate(),
                                alarmInfo.getHappenedDate().getTime() - currentAlarm.getHappenedDate().getTime()
                        );
                        alarmHistoryValueConsumeHandler.accept(alarmHistory);
                        historyRecordedEventConsumeHandler.accept(alarmHistory);
                    }
                }
                // 消费者消费报警信息值。
                alarmInfoValueConsumeHandler.accept(alarmInfo);
                // 消费者消费报警信息更新事件。
                alarmUpdatedEventConsumeHandler.accept(alarmInfo);
            }
        } catch (HandlerException e) {
            throw e;
        } catch (Exception e) {
            throw new HandlerException(e);
        } finally {
            lock.unlock();
        }
    }

    private boolean checkAlarming(byte[] data, int index) {
        // 根据index定位数组的行列。
        int row = index / BIT_PER_BYTE;
        int column = index % BIT_PER_BYTE;
        // 根据row取出具体的某个byte。
        byte b = data[row];
        // 根据column按位运算，返回结果。
        byte bitwize = (byte) (1 << column);
        return (b & bitwize) != 0;
    }

    private String toHexString(byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(hexCode[(b >> 4) & 0xF]);
            r.append(hexCode[(b & 0xF)]);
        }
        return r.toString();
    }
}
