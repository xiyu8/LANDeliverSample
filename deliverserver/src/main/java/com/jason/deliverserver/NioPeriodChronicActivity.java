package com.jason.deliverserver;

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
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NioPeriodChronicActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bio_peroid_chronic);
        init();
    }

    EditText send_msg_content;
    EditText disconnect_user_name;
    EditText send_user_name;
    TextView receive_msg_content;
    TextView disconnect_client;
    TextView connected_users;
    protected void init() {
        send_msg_content = findViewById(R.id.send_msg_content);
        receive_msg_content = findViewById(R.id.receive_msg_content);
        disconnect_user_name = findViewById(R.id.disconnect_user_name);
        send_user_name = findViewById(R.id.send_user_name);
        disconnect_client = findViewById(R.id.disconnect_client);
        connected_users = findViewById(R.id.connected_users);
        disconnect_user_name.setText("user1");
        send_user_name.setText("user1");

//        bIoInit();
        registerNioSelector();
        initWriteThread(); //prepare write thread
        listenNioSelector();
    }


    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.disconnect_client:
                final String disconnectUserName = disconnect_user_name.getText().toString();
                if(disconnectUserName==null||disconnectUserName.equals("")) return;
                disconnectNioUser(disconnectUserName);
                break;
            case R.id.send_msg:
                final String  sendMsg= send_msg_content.getText().toString();
                final String sendUser = send_user_name.getText().toString();
                if((sendMsg==null||sendMsg.equals(""))||(sendUser==null||sendUser.equals(""))) {
                    return;
                }
                nioWriteString(sendUser,sendMsg);
                send_msg_content.setText("");
                break;
        }
    }

    private void disconnectNioUser(final String disconnectUserName) {
        SocketChannel socketChannel = socketChannels.get(disconnectUserName);
        if(socketChannel==null) {
            SelectionKey selectionKey = socketChannelKeys.get(disconnectUserName);
            if (selectionKey != null) {
                if (socketChannelKeyUsers.get(selectionKey) != null) {
                    socketChannelKeyUsers.remove(selectionKey);
                }
                socketChannelKeys.remove(selectionKey);
                selectionKey.cancel();
            }
            showConnectedUsers();
            return;
        }
        if (!(socketChannel.isConnected())){
            socketChannels.remove(disconnectUserName);
            SelectionKey selectionKey = socketChannelKeys.get(disconnectUserName);
            if (selectionKey != null) {
                socketChannelKeys.remove(disconnectUserName);
                socketChannelKeyUsers.remove(selectionKey);
            }
            try {
                if (selectionKey != null) {
                    selectionKey.cancel();
                }
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            showConnectedUsers();
            return;
        }
        socketChannels.remove(disconnectUserName);
        SelectionKey selectionKey = socketChannelKeys.get(disconnectUserName);
        if (selectionKey != null) {
            socketChannelKeys.remove(disconnectUserName);
            socketChannelKeyUsers.remove(selectionKey);
        }
        try {
            if (selectionKey != null) {
                selectionKey.cancel();
            }
            socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        showConnectedUsers();

    }

    private Selector selector;
    private void registerNioSelector() {
        try {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();// ????????????ServerSocket??????
            serverChannel.configureBlocking(false);// ????????????????????????
            NioPeriodChronicActivity.this.selector = Selector.open();// ???????????????????????????
            serverChannel.socket().bind(new InetSocketAddress(8887));// ?????????????????????ServerSocket?????????port??????
            //????????????????????????????????????????????????????????????SelectionKey.OP_ACCEPT??????,?????????????????????
            //????????????????????????selector.select()????????????????????????????????????selector.select()??????????????????
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (Exception e) {
        }
    }

    public void listenNioSelector() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    //???????????????????????????????????????????????????,????????????????????????
                    try {
                        selector.select();
                        Iterator iterator = NioPeriodChronicActivity.this.selector.selectedKeys().iterator();// ??????selector????????????????????????????????????????????????????????????
                        while (iterator.hasNext()) {
                            SelectionKey selectionKey = (SelectionKey) iterator.next();
                            iterator.remove();// ???????????????key,??????????????????
                            if (selectionKey.isAcceptable()) { // ???????????????????????????
                                ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
                                SocketChannel socketChannel = serverSocketChannel.accept(); // ?????????????????????????????????
                                socketChannel.configureBlocking(false); // ??????????????????
                                socketChannel.register(NioPeriodChronicActivity.this.selector, SelectionKey.OP_READ); //??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                            } else if (selectionKey.isReadable()) { // ????????????????????????
                                nIOHandleReadChannel(selectionKey,(SocketChannel) selectionKey.channel());
                            }
                        }
                    } catch (IOException e) {
                        if (e.getMessage() != null && e.getMessage().contains("closed")
                                && e.getMessage().contains("Broken")) {
                        }
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    Map<String,SocketChannel> socketChannels = new HashMap<>();
    Map<String,SelectionKey> socketChannelKeys = new HashMap<>();
    Map<SelectionKey,String> socketChannelKeyUsers = new HashMap<>();
    public void nIOHandleReadChannel(SelectionKey selectionKey,SocketChannel socketChannel) {
        try {
            String user = socketChannelKeyUsers.get(selectionKey);
            if ( user == null) {
                user=verifyNioChannelUser(socketChannel);
                if (user==null||user.equals("")) {

                    showError("??????????????????");
                    return;
                }
                socketChannels.put(user, socketChannel);
                socketChannelKeys.put(user, selectionKey);
                socketChannelKeyUsers.put(selectionKey, user);
                showConnectedUsers();
            } else {
                handleNIoChannelDataRead(selectionKey,socketChannel);
            }
        } catch (Exception e) {
            //ignore
        } finally {
            //??????socket
        }
    }

    private String verifyNioChannelUser(SocketChannel socketChannel) {
        String user = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            int readedCount = socketChannel.read(buffer);
//            user = reader.readLine();
            byte[] tempBytes = buffer.array();
            byte[] targetBytes = new byte[readedCount];
            for (int i = 0; i < readedCount; i++) {
                targetBytes[i] =tempBytes[i];
            }
            user = new String(targetBytes, "UTF-8");
            user = user.replace("\n", "");
            user = user.replace("\r", "");
            socketChannel.write(ByteBuffer.wrap("success\n\r".getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return user;
    }

    private void handleNIoChannelDataRead(final SelectionKey selectionKey, final SocketChannel socketChannel) {
        int contentLength = 0;
        for (; ; ) {
            try {
                if (contentLength == 0) {
                    ByteBuffer buffer = ByteBuffer.allocate(4);
                    long readedByteCount = socketChannel.read(buffer);
                    if (readedByteCount == -1) { //????????????????????????(=0?????????)
                        disconnectNioUser(socketChannelKeyUsers.get(selectionKey));
                        break;
                    } else {
                        contentLength = Tool.byte4ToInt(buffer.array(), 0);
                    }
                } else {
                    ByteBuffer buffer = ByteBuffer.allocate(contentLength);
                    int readedByteCount = socketChannel.read(buffer);
                    if (readedByteCount != -1) {
                        String ss = new String(buffer.array(), "UTF-8");
                        showData(socketChannelKeyUsers.get(selectionKey), ss);
                    }
                    contentLength = 0;
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

    private void nioWriteString(final String sendUser,final String sendMsg) {

        sendHandle.post(new Runnable() {
            @Override
            public void run() {
                SocketChannel socketChannel = socketChannels.get(sendUser);
                if (socketChannel == null) {
                    showError("???????????????????????????"+sendUser);
                    return;
                }
                if (!socketChannel.isConnected()) {
                    disconnectNioUser(sendUser);
                    showError("????????????????????????"+sendUser);
                    return;
                }

                try {
                    byte[] tempContentBytes = sendMsg.getBytes(("UTF-8"));
                    int contentLength = tempContentBytes.length;
                    byte[] contentLengthBytes = Tool.intToByte4(contentLength);
                    byte[] totalBytes = Tool.combineBytes(new byte[4 + contentLength], contentLengthBytes, tempContentBytes);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(totalBytes);
                    socketChannel.write(byteBuffer);
                } catch (IOException e) {
                    showError("???????????????" + e.getCause() + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

    }


    private void showData(final String user, final String ss) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                receive_msg_content.append(user+"???"+ss+"\n\r");
            }
        });

    }

    private void showConnectedUsers() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String temp ="";
                for (String s : socketChannels.keySet()) {
                    temp += (s+"\n\r");
                }
                connected_users.setText(temp);
            }
        });
    }

    private void showError(final String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(NioPeriodChronicActivity.this, error, Toast.LENGTH_SHORT ).show();
            }
        });
    }


}