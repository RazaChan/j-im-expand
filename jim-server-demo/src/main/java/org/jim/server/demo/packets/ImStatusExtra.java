/**
 *
 */
package org.jim.server.demo.packets;

import org.jim.common.Status;

/**
 * 版本: [1.0]
 * 功能说明:
 * 作者: WChao 创建时间: 2017年7月27日 上午10:33:14
 */
public enum ImStatusExtra implements Status {

    C10022(10022, "get user cardList ok!", "获取交易卡片信息成功!"),
    C10023(10023, "get user cardList failed!", "获取交易卡片信息失败!"),
    ;
    private int status;

    private String description;

    private String text;

    private ImStatusExtra(int status, String description, String text) {
        this.status = status;
        this.description = description;
        this.text = text;
    }

    public int getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getText() {
        return text;
    }

    @Override
    public int getCode() {
        return this.status;
    }

    @Override
    public String getMsg() {
        return this.getDescription() + " " + this.getText();
    }
}
