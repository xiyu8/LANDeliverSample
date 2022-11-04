package com.jason.deliverserver;

import android.os.Bundle;
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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BioPeriodChronicActivity extends AppCompatActivity {

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

        bIOInit();
    }


    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.disconnect_client:
                final String disconnectUserName = disconnect_user_name.getText().toString();
                if(disconnectUserName==null||disconnectUserName.equals("")) return;
                disconnectUser(disconnectUserName);
                break;
            case R.id.send_msg:
                final String  sendMsg= send_msg_content.getText().toString();
                final String sendUser = send_user_name.getText().toString();
                if((sendMsg==null||sendMsg.equals(""))||(sendUser==null||sendUser.equals(""))) {
                    return;
                }
                bioWriteString(sendUser,sendMsg);
                break;
        }
    }

    private void disconnectUser(final String disconnectUserName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket clientSocket = connectedSockets.get(disconnectUserName);
                if (clientSocket == null) return;
                if (!clientSocket.isClosed()) {
                    try {
                        clientSocket.close();
                        outputStreams.remove(disconnectUserName);
                        inputStreams.remove(disconnectUserName);
                        connectedSockets.remove(disconnectUserName);
                        showConnectedUsers();
                    } catch (IOException e) {
                        showError("关闭连接失败：" + e.getCause() + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    outputStreams.remove(disconnectUserName);
                    inputStreams.remove(disconnectUserName);
                    connectedSockets.remove(disconnectUserName);
                    showConnectedUsers();
                }
            }
        }).start();
    }

    private void bIOInit() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket socket = new ServerSocket(8887);
                    while (true) {
                        //接受客户端连接
                        final Socket so = socket.accept();
                        so.setKeepAlive(true);
                        //与客户端通信的工作放到线程池中异步执行
                        threads.submit(new Runnable() {
                            @Override
                            public void run() {
                                bIOHandleSocket(so);
                            }
                        });
                    }
                } catch (IOException e) {
                    //
                }
            }
        }).start();
    }

    private ExecutorService threads = Executors.newFixedThreadPool(6);
    Map<String,InputStream> inputStreams = new HashMap<>();
    Map<String,OutputStream> outputStreams = new HashMap<>();
    Map<String,Socket> connectedSockets = new HashMap<>();
    public void bIOHandleSocket(Socket userSocket) {
        try {
            OutputStream outputStream = userSocket.getOutputStream();
            InputStream inputStream = userSocket.getInputStream();
            String user=verifySocketUser(userSocket,inputStream,outputStream);
            if (user==null||user.equals("")) {
                showError("验证用户失败");
                return;
            }
            inputStreams.put(user,inputStream);
            outputStreams.put(user,outputStream);
            connectedSockets.put(user,userSocket);
            showConnectedUsers();
            handleBIoInputStream(user,inputStream);
        } catch (Exception e) {
            //ignore
        } finally {
            //关闭socket
//      if (so != null) {
//        try {
//          so.close();
//        } catch (IOException e) {
//          //
//        }
//      }
        }
    }


    private String verifySocketUser(Socket so,InputStream inputStream,OutputStream outputStream) {
        String user = null;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            user = reader.readLine();
            inputStreams.put(user, inputStream);
            outputStreams.put(user, outputStream);
            writer.write("success");
            writer.newLine();
            writer.flush();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return user;
    }

    private void handleBIoInputStream(final String user, final InputStream inputStream) {
        new Thread(new Runnable() {
            int contentLength;
            @Override
            public void run() {
                for (; ; ) {
                    try {
//                        byte[] buffer = new byte[1024];
//                        int readedByteCount = inputStream.read(buffer);  //阻塞 等待 接受（没有数据不会返回-1）
//                        if (readedByteCount != -1) {
//                            String ss = new String(buffer, "UTF-8");
//                            showData(user,ss);
//                        }


                        if (contentLength == 0) {
                            byte[] buffer = new byte[4];
                            int readedByteCount = inputStream.read(buffer, 0, 4);
                            if (readedByteCount != -1) {
                                contentLength = Tool.byte4ToInt(buffer, 0);
                            }
                        } else {
                            byte[] buffer = new byte[contentLength];
                            int readedByteCount = inputStream.read(buffer, 0, contentLength);
                            if (readedByteCount != -1) {
                                String ss = new String(buffer, "UTF-8");
                                showData(user, ss);
                            }
                            contentLength = 0;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        }).start();
    }


    private void bioWriteString(final String sendUser,final String sendMsg) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                OutputStream outputStream=outputStreams.get(sendUser);
                if (outputStream == null) {
                    showError("没有找到发送对象");
                    return;
                }
                try {
                    byte[] contentBytes = sendMsg.getBytes(("UTF-8"));
                    int contentLength=contentBytes.length;
                    byte[] contentLengthBytes = Tool.intToByte4(contentLength);
                    byte[] totalBytes = Tool.combineBytes(new byte[4 + contentLength], contentLengthBytes, contentBytes);
                    outputStream.write(totalBytes);
                } catch (IOException e) {
                    if (e.getMessage() != null && e.getMessage().contains("closed")
                            && e.getMessage().contains("Broken")) {
                        inputStreams.remove(sendUser);
                        outputStreams.remove(sendUser);
                        connectedSockets.remove(sendUser);
                        showConnectedUsers();
                    }
                    showError("发送失败：" + e.getCause() + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }


    private void showData(final String user, final String ss) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                receive_msg_content.append(user+"："+ss+"\n\r");
            }
        });

    }

    private void showConnectedUsers() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String temp ="";
                for (String s : connectedSockets.keySet()) {
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
                Toast.makeText(BioPeriodChronicActivity.this, error, Toast.LENGTH_SHORT ).show();
            }
        });
    }


}