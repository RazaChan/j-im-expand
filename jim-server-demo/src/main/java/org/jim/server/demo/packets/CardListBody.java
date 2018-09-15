package org.jim.server.demo.packets;

import java.io.Serializable;
import java.util.List;

public class CardListBody implements Serializable {
    private String userid;//用户id;
    private List<String> content;//内容

    public CardListBody(String userid) {
        this.userid = userid;
    }

    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public List<String> getContent() {
        return content;
    }

    public void setContent(List<String> content) {
        this.content = content;
    }


}
