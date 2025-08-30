package invoke.looseone;

import invoke.looseone.db.DBController;
import invoke.looseone.db.FileDownloadHandler;
import invoke.looseone.db.KeyController;
import invoke.looseone.db.UIDController;
import invoke.looseone.secure.AdminPanel;
import invoke.looseone.util.ChatWebSocketHandler;
import invoke.looseone.util.Config;
import invoke.looseone.util.ErrHandler;
import invoke.looseone.util.ThreadUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.stream.ChunkedWriteHandler;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class Main {
    static final String STATIC_FOLDER = "web";

    public static void main(String[] args) {
        try {
            DBController.loadUsers();
        } catch (Exception e) {
            System.err.println("Failed to load users: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                ThreadUtil.removeExpiredSubscriptions();
                Config.log("subs checked.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.HOURS);

        Config.log("Server starting on port: " + Config.port);
        System.setProperty("java.util.logging.ConsoleHandler.level", "SEVERE");

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        //SslContext sslCtx;
        //try {
            //sslCtx = SslContextBuilder.forServer(new File("fullchain.pem"), new File("privkey.pem")).build();
        //} catch (SSLException e) {
            //e.printStackTrace();
            //return;
        //}
        try {
            ServerBootstrap b = new ServerBootstrap();
            //SslContext finalSslCtx = sslCtx;
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            //pipeline.addLast(finalSslCtx.newHandler(ch.alloc()));
                            pipeline.addLast(new ErrHandler());
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            pipeline.addLast(new ChunkedWriteHandler());
                            pipeline.replace(this, "websocketHandler", new ChatWebSocketHandler());
                            pipeline.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
                                    String uri = req.uri();
                                    HttpMethod method = req.method();

                                    if (uri.startsWith("/download/")) {
                                        FileDownloadHandler.handle(ctx, req);
                                        return;
                                    }

                                    if (uri.startsWith("/download_launcher_with_token/")) {
                                        KeyController.handleDownloadWithToken(ctx, req);
                                        return;
                                    }

                                    if (uri.equals("/api/v2/RustEx")) {
                                        DBController.handleRustEx(ctx, req);
                                        return;
                                    }

                                    if (uri.equals("/chat")) {
                                        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                                                getWebSocketLocation(req), null, true);
                                        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(req);
                                        if (handshaker == null) {
                                            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                                        } else {
                                            handshaker.handshake(ctx.channel(), req);
                                            ChatWebSocketHandler.addChannel(ctx.channel());
                                        }
                                        return;
                                    }

                                    try {
                                        if (method.equals(HttpMethod.POST)) {
                                            switch (uri) {
                                                case "/getuid_login":
                                                    UIDController.handleUIDLogin(ctx, req);
                                                    return;
                                                case "/auth_usr":
                                                    DBController.handleAuthUser(ctx, req);
                                                    return;
                                                case "/gethwid_login":
                                                    DBController.handleHWID(ctx, req);
                                                    return;
                                                case "/getgroup_login":
                                                    DBController.handleGroup(ctx, req);
                                                    return;
                                                case "/get_subscriptions":
                                                    DBController.handleGetSubscriptions(ctx, req);
                                                    return;
                                                case "/admin/auth":
                                                    AdminPanel.handleAdminAuth(ctx, req);
                                                    return;
                                                case "/admin/users":
                                                    AdminPanel.handleAdminGetUsers(ctx, req);
                                                    return;
                                                case "/admin/update_user":
                                                    AdminPanel.handleAdminUpdateUser(ctx, req);
                                                    return;
                                                case "/register_usr":
                                                    DBController.handleRegisterUser(ctx, req);
                                                    return;
                                                case "/activate_kk":
                                                    KeyController.handleKeyActivation(ctx, req);
                                                    return;
                                                case "/dev/handleSub":
                                                    DBController.handleSubscription(ctx, req);
                                                    return;
                                                case "/dev/verifyHwid":
                                                    DBController.handleHwidVerification(ctx, req);
                                                    return;
                                                case "/dev/handleUID":
                                                    UIDController.handleUID(ctx, req);
                                                    return;
                                            }
                                        }

                                        if (uri.equals("/") || uri.equals("/index.html")) {
                                            sendFile(ctx, "index.html");
                                        } else if (uri.equals("/auth.html")) {
                                            sendFile(ctx, "auth.html");
                                        } else if (uri.contains("cabinet")) {
                                            sendFile(ctx, "cabinet.html");
                                        } else if (uri.contains("shop")) {
                                            sendFile(ctx, "shop.html");
                                        } else if (uri.contains("faq")) {
                                            sendFile(ctx, "faq.html");
                                        } else if (uri.startsWith("/static/")) {
                                            sendFile(ctx, uri.substring(1));
                                        } else if (uri.equals("/admin") || uri.equals("/admin.html")) {
                                            sendFile(ctx, "admin.html");
                                        } else if (uri.equals("/favicon.ico")) {
                                            sendNotFound(ctx);
                                        } else {
                                            sendNotFound(ctx);
                                        }
                                    } catch (Exception e) {
                                        Config.log("Error handling request: " + e.toString());
                                        e.printStackTrace();
                                    }
                                }

                                private void sendFile(ChannelHandlerContext ctx, String filePath) throws IOException {
                                    File file = new File(STATIC_FOLDER, filePath);
                                    if (file.exists()) {
                                        byte[] bytes = Files.readAllBytes(file.toPath());
                                        FullHttpResponse response = new DefaultFullHttpResponse(
                                                HTTP_1_1,
                                                OK,
                                                Unpooled.copiedBuffer(bytes)
                                        );
                                        response.headers().set(HttpHeaderNames.CONTENT_TYPE, getContentType(filePath));
                                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
                                        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                                    } else {
                                        //Config.log("File NOT FOUND: " + file.getAbsolutePath());
                                        sendNotFound(ctx);
                                    }
                                }

                                private void sendNotFound(ChannelHandlerContext ctx) {
                                    FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.NO_CONTENT);
                                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                                }

                                private String getContentType(String filename) {
                                    if (filename.endsWith(".html")) return "text/html";
                                    if (filename.endsWith(".css")) return "text/css";
                                    if (filename.endsWith(".js")) return "application/javascript";
                                    return "application/octet-stream";
                                }
                            });
                        }
                    });

            try {
                ChannelFuture f = b.bind("0.0.0.0", 8080).sync();
                Config.log("Server successfully started and listening on port http://localhost:5000");
                f.channel().closeFuture().sync();
            } catch (Exception bindEx) {
                System.err.println("Failed to bind to port 443: " + bindEx.getMessage());
                bindEx.printStackTrace();
            }
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private static String getWebSocketLocation(FullHttpRequest req) {
        return "wss://" + req.headers().get(HttpHeaderNames.HOST) + "/chat";
    }
}
