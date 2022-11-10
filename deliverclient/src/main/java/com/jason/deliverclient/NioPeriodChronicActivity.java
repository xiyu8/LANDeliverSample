package com.jason.deliverclient;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class NioPeriodChronicActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bio_peroid_chronic);

        send_msg_content = findViewById(R.id.send_msg_content);
        receive_msg_content = findViewById(R.id.receive_msg_content);
        server_ip = findViewById(R.id.server_ip);
        server_port = findViewById(R.id.server_port);
        user_name = findViewById(R.id.user_name);
        connection_status = findViewById(R.id.connection_status);

        server_ip.setText("192.168.137.50");
        server_port.setText("8887");
        user_name.setText("user1");
    }

    String port = null;
    String host = null;
    String userName = null;

    EditText send_msg_content;
    EditText server_ip;
    EditText server_port;
    TextView receive_msg_content;
    TextView user_name;
    TextView connection_status;

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.connect_server:
                if (host != null && !host.equals("")) {
                    showError("当前已有连接");
                    return;
                }
                port = server_port.getText().toString();
                host = server_ip.getText().toString();
                userName = user_name.getText().toString();
                if (port == null || host == null || userName == null) {
                    return;
                }
                host.replace(" ", "");
                port.replace(" ", "");
                userName.replace(" ", "");
                if (port.equals("") || host.equals("") || userName.equals("")) {
                    return;
                }
                initNioChannel();
                initWriteThread();
                if (mSocketChannel == null) {
                    showError("创建连接通道失败");
                    return;
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mSocketChannel.connect(new InetSocketAddress(host, Integer.parseInt(port)));
                            listenNiOConnect(userName);
                        } catch (IOException e) {
                            if (e.getMessage() != null && e.getMessage().contains("closed")
                                    && e.getMessage().contains("Broken")) {
                                nioDisconnect();
                            }
                            showError("连接失败：" + e.getCause() + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }).start();
                break;
            case R.id.send_msg:
                final String sendMsg = send_msg_content.getText().toString();
                if (sendMsg == null || sendMsg.equals("")) {
                    return;
                }
                if (mSocketChannel == null) {
                    showError("当前未连接");
                    return;
                }
                nioWriteString(sendMsg);
                send_msg_content.setText("");
                break;
            case R.id.disconnect_server:
                nioDisconnect();
                break;

        }
    }

    Selector selector;
    SocketChannel mSocketChannel;

    public void initNioChannel() {
        try {
            mSocketChannel = SocketChannel.open();
            mSocketChannel.configureBlocking(false);
            NioPeriodChronicActivity.this.selector = Selector.open();
            //用channel.finishConnect();才能完成连接
            mSocketChannel.register(selector, SelectionKey.OP_CONNECT);
        } catch (IOException e) {
            showError("初始化nio失败");
            e.printStackTrace();
        }
    }


    private void listenNiOConnect(String userName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        selector.select();
                        // 获得selector中选中的项的迭代器
                        Iterator iterator = NioPeriodChronicActivity.this.selector.selectedKeys().iterator();
                        while (iterator.hasNext()) {
                            SelectionKey selectionKey = (SelectionKey) iterator.next();
                            iterator.remove();
                            if (selectionKey.isConnectable()) {
                                SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                                if (socketChannel.isConnectionPending()) {
                                    socketChannel.finishConnect();
                                }
                                socketChannel.configureBlocking(false);
                                socketChannel.register(NioPeriodChronicActivity.this.selector, SelectionKey.OP_READ);
                                registerUser(socketChannel, userName);
                            } else if (selectionKey.isReadable()) {
                                nioHandleReadChannel(selectionKey, (SocketChannel) selectionKey.channel());
                            }
                        }
                    } catch (IOException e) {
                        if (e.getMessage() != null && e.getMessage().contains("closed")
                                && e.getMessage().contains("Broken")) {
                            nioDisconnect();
                        }
                        showError("连接失败：" + e.getCause() + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private String registerUser(SocketChannel socketChannel, String userName) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap((userName + "\n\r").getBytes(StandardCharsets.UTF_8));
            socketChannel.write(byteBuffer);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("closed")
                    && e.getMessage().contains("Broken")) {
                nioDisconnect();
            }
            showError("连接失败：" + e.getCause() + e.getMessage());
            e.printStackTrace();
        }
        return "verify fail";
    }

    private boolean checkRegister(SocketChannel socketChannel) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            int readedCount = socketChannel.read(buffer);
            if (readedCount == -1 || readedCount == 0) {
                return false;
            }
            byte[] tempBytes = buffer.array();
            byte[] targetBytes = new byte[readedCount];
            for (int i = 0; i < readedCount; i++) {
                targetBytes[i] = tempBytes[i];
            }
            String ret = new String(targetBytes, "UTF-8");
            if (ret.contains("success")) {
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (e.getMessage() != null && e.getMessage().contains("closed")
                    && e.getMessage().contains("Broken")) {
                nioDisconnect();
            }
            showError("校验用户失败：" + e.getCause() + e.getMessage());
            return false;
        }
        return false;
    }

    boolean isAuth = false;
    public void nioHandleReadChannel(SelectionKey selectionKey, SocketChannel socketChannel) {
        try {
            if (!isAuth) {
                isAuth = checkRegister(socketChannel);
                if (!isAuth) {
                    showError("验证用户失败");
                    return;
                }
                showConnection(host,port,userName);
            } else {
                handleNioChannelDataRead(selectionKey, socketChannel);
            }
        } catch (Exception e) {
            //ignore
        } finally {
            //关闭socket
        }
    }

    private void handleNioChannelDataRead(SelectionKey selectionKey, SocketChannel socketChannel) {
        int contentLength = 0;
        for (; ; ) {
            try {
                if (contentLength == 0) {
                    ByteBuffer buffer = ByteBuffer.allocate(4);
                    long readedByteCount = socketChannel.read(buffer);
                    if (readedByteCount == -1) { //客户端关闭了连接(=0未判断)
                        nioDisconnect();
                        break;
                    } else {
                        contentLength = Tool.byte4ToInt(buffer.array(), 0);
                    }
                } else {
                    ByteBuffer buffer = ByteBuffer.allocate(contentLength);
                    int readedByteCount = socketChannel.read(buffer);
                    if (readedByteCount != -1) {
                        String ss = new String(buffer.array(), "UTF-8");
                        showData(ss);
                    }
                    contentLength = 0;
                    break;
                }
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("closed")
                        && e.getMessage().contains("Broken")) {
                    nioDisconnect();
                }
                e.printStackTrace();
            }
        }
    }

    private void nioDisconnect() {
        host = null;
        port = null;
        isAuth = false;
        if (mSocketChannel == null) {
            return;
        }
        if (!(mSocketChannel.isConnected())) {
            try {
                mSocketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            showConnection(null, null, null);
            return;
        }
        try {
            mSocketChannel.close();
        } catch (IOException e) {
            showError("关闭连接失败：" + e.getCause() + e.getMessage());
            e.printStackTrace();
        }
        showConnection(null, null, null);
    }

    Thread sendThread;
    Handler sendHandle;

    private void initWriteThread() {
        sendThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                sendHandle = new Handler();
                Looper.loop();
            }
        });
        sendThread.start();
    }

    private void nioWriteString(final String ss) {
        if (sendHandle != null) {
            sendHandle.post(new Runnable() {
                @Override
                public void run() {
                    if (mSocketChannel == null) {
                        showError("发送失败：连接已断开");
                    }
                    try {
                        byte[] contentBytes = ss.getBytes(("UTF-8"));
                        int contentLength = contentBytes.length;
                        byte[] contentLengthBytes = Tool.intToByte4(contentLength);
                        byte[] totalBytes = Tool.combineBytes(new byte[4 + contentLength], contentLengthBytes, contentBytes);
                        ByteBuffer byteBuffer = ByteBuffer.wrap(totalBytes);
                        mSocketChannel.write(byteBuffer);
                    } catch (IOException e) {
                        if (e.getMessage() != null && e.getMessage().contains("closed")
                                && e.getMessage().contains("Broken")) {
                            nioDisconnect();
                        }
                        showError("发送失败：" + e.getCause() + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
        } else {
            showError("发送线程被终止");
        }
    }


    private void showData(final String ss) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                receive_msg_content.append(ss + "\n\r");
            }
        });
    }


    private void showConnection(final String ip, final String port, final String user) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (ip == null) {
                    connection_status.setText("unconnect");
                } else {
                    connection_status.setText("ip:" + ip + "port:" + port + "user:" + user);
                }
            }
        });
    }

    private void showError(final String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(NioPeriodChronicActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}