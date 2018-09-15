package org.jim.server.demo.packets;

public enum CommandExtra {
    COMMAND_GET_CARDLIST_REQ(21,"COMMAND_GET_CARDLIST_REQ"),
    COMMAND_GET_CARDLIST_RESP(22,"COMMAND_GET_CARDLIST_RESP"),
    ;

    CommandExtra(int cmd, String cmdName) {
        this.cmd = cmd;
        this.cmdName = cmdName;
    }
    private final int cmd;

    public int getCmd() {
        return cmd;
    }

    public String getCmdName() {
        return cmdName;
    }

    private final String cmdName;

}
