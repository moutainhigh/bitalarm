package com.dwarfeng.bitalarm.sdk.bean.entity;

import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.serializer.ToStringSerializer;
import com.dwarfeng.bitalarm.stack.bean.entity.AlarmSetting;
import com.dwarfeng.subgrade.sdk.bean.key.JSFixedFastJsonLongIdKey;
import com.dwarfeng.subgrade.stack.bean.Bean;

import java.util.Objects;

/**
 * JSFixed FastJson 报警设置。
 *
 * @author DwArFeng
 * @since 1.0.0
 */
public class JSFixedFastJsonAlarmSetting implements Bean {

    private static final long serialVersionUID = 8574218003809064096L;

    public static JSFixedFastJsonAlarmSetting of(AlarmSetting alarmSetting) {
        if (Objects.isNull(alarmSetting)) {
            return null;
        } else {
            return new JSFixedFastJsonAlarmSetting(
                    JSFixedFastJsonLongIdKey.of(alarmSetting.getKey()),
                    alarmSetting.getPointId(),
                    alarmSetting.isEnabled(),
                    alarmSetting.getIndex(),
                    alarmSetting.getAlarmMessage(),
                    alarmSetting.getAlarmType(),
                    alarmSetting.getRemark()
            );
        }
    }

    @JSONField(name = "key", ordinal = 1)
    private JSFixedFastJsonLongIdKey key;

    @JSONField(name = "point_id", ordinal = 2, serializeUsing = ToStringSerializer.class)
    private long pointId;

    @JSONField(name = "enabled", ordinal = 3)
    private boolean enabled;

    @JSONField(name = "index", ordinal = 4)
    private int index;

    @JSONField(name = "alarm_message", ordinal = 5)
    private String alarmMessage;

    @JSONField(name = "alarm_type", ordinal = 6)
    private byte alarmType;

    @JSONField(name = "remark", ordinal = 7)
    private String remark;

    public JSFixedFastJsonAlarmSetting() {
    }

    public JSFixedFastJsonAlarmSetting(
            JSFixedFastJsonLongIdKey key, long pointId, boolean enabled, int index, String alarmMessage, byte alarmType,
            String remark) {
        this.key = key;
        this.pointId = pointId;
        this.enabled = enabled;
        this.index = index;
        this.alarmMessage = alarmMessage;
        this.alarmType = alarmType;
        this.remark = remark;
    }

    public JSFixedFastJsonLongIdKey getKey() {
        return key;
    }

    public void setKey(JSFixedFastJsonLongIdKey key) {
        this.key = key;
    }

    public long getPointId() {
        return pointId;
    }

    public void setPointId(long pointId) {
        this.pointId = pointId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getAlarmMessage() {
        return alarmMessage;
    }

    public void setAlarmMessage(String alarmMessage) {
        this.alarmMessage = alarmMessage;
    }

    public byte getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(byte alarmType) {
        this.alarmType = alarmType;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public String toString() {
        return "JSFixedFastJsonAlarmSetting{" +
                "key=" + key +
                ", pointId=" + pointId +
                ", enabled=" + enabled +
                ", index=" + index +
                ", alarmMessage='" + alarmMessage + '\'' +
                ", alarmType=" + alarmType +
                ", remark='" + remark + '\'' +
                '}';
    }
}
