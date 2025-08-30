package invoke.looseone.db;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileDownloadHandler {

    public static void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri(); // /download/native.dll
        String filename = uri.substring(uri.lastIndexOf('/') + 1);
        Path filePath = Paths.get("RustEx/" + filename);

        if (!Files.exists(filePath)) {
            send404(ctx);
            return;
        }

        try {
            byte[] fileBytes = Files.readAllBytes(filePath);
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(fileBytes)
            );

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, fileBytes.length);
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } catch (IOException e) {
            send500(ctx);
        }
    }

    private static void send404(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND
        );
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void send500(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR
        );
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}