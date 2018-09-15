package org.jim.server.demo.packets;

import org.jim.common.packets.Message;

public class CardListReqBody extends Message {

    private static final long serialVersionUID = 9150276436182997805L;
    private String userId;//接收用户id;
    private Integer offset;//分页偏移量
    private Integer count;//数量

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
