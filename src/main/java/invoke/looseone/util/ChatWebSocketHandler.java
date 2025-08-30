package invoke.looseone.util;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final Set<Channel> clients = ConcurrentHashMap.newKeySet();

    public static void addChannel(Channel ch) {
        clients.add(ch);
        ch.closeFuture().addListener((ChannelFutureListener) future -> clients.remove(ch));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
        String text = msg.text();
        for (Channel client : clients) {
            if (client != ctx.channel()) {
                client.writeAndFlush(new TextWebSocketFrame(text));
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        clients.remove(ctx.channel());
    }
}