package org.jim.server.demo.command.handler;//package org.jim.server.demo.command.handler;
//

import org.apache.commons.lang3.StringUtils;
import org.jim.common.ImPacket;
import org.jim.common.ImStatus;
import org.jim.common.packets.Command;
import org.jim.common.packets.RespBody;
import org.jim.common.utils.ImKit;
import org.jim.common.utils.JsonKit;
import org.jim.server.command.AbCmdHandler;
import org.jim.server.demo.packets.CardListBody;
import org.jim.server.demo.packets.CardListReqBody;
import org.jim.server.demo.packets.CommandExtra;
import org.jim.server.demo.packets.ImStatusExtra;
import org.tio.core.ChannelContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 获取交易卡片信息命令处理器
 */
public class CardListReqHandler extends AbCmdHandler {
    @Override
    public Command command() {
        return Command.valueOf(CommandExtra.COMMAND_GET_CARDLIST_REQ.getCmdName());
    }

    @Override
    public ImPacket handler(ImPacket packet, ChannelContext channelContext) throws Exception {
        RespBody resPacket = null;
        CardListReqBody cardListReqBody = null;
        try {
            cardListReqBody = JsonKit.toBean(packet.getBody(), CardListReqBody.class);
        } catch (Exception e) {//用户消息格式不正确
            return getCardListFailedPacket(channelContext);
        }
        String userid = cardListReqBody.getUserId();
        if (StringUtils.isEmpty(userid))
            return ImKit.ConvertRespPacket(new RespBody(Command.COMMAND_GET_USER_RESP, ImStatus.C10004), channelContext);
        //TODO 根据uid查询cardlist
        List<String> cardList = new ArrayList<>();
        cardList.add("卡片一");
        cardList.add("卡片二");
        CardListBody cardListBody = new CardListBody(userid);
        cardListBody.setContent(cardList);

        resPacket = new RespBody(Command.valueOf(CommandExtra.COMMAND_GET_CARDLIST_RESP.getCmd()), ImStatusExtra.C10022);
        resPacket.setData(cardListBody);
        return ImKit.ConvertRespPacket(resPacket, channelContext);
    }

    /**
     * 获取交易卡片信息失败响应包;
     *
     * @param channelContext
     * @return
     */
    public ImPacket getCardListFailedPacket(ChannelContext channelContext) {
        RespBody resPacket = new RespBody(Command.valueOf(CommandExtra.COMMAND_GET_CARDLIST_RESP.getCmdName()), ImStatusExtra.C10023);
        return ImKit.ConvertRespPacket(resPacket, channelContext);
    }
}
