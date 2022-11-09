package com.jason.deliverclient;

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
import java.net.Socket;

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
                String port = server_port.getText().toString();
                String host = server_ip.getText().toString();
                String user = user_name.getText().toString();
                if (port == null || host == null || user == null) {
                    return;
                }
                host.replace(" ", "");
                port.replace(" ", "");
                user.replace(" ", "");
                if (port.equals("") || host.equals("") || user.equals("")) {
                    return;
                }
                bIOConnect(host, port,user);
                break;
            case R.id.send_msg:
                final String sendMsg = send_msg_content.getText().toString();
                if (sendMsg == null || sendMsg.equals("")) {
                    return;
                }
                if(clientSocket==null){
                    showError("当前未连接");
                    return;
                }
                bioWriteString(sendMsg);
                break;
            case R.id.disconnect_server:
                bIODisconnect();
                break;

        }
    }

    private void bIODisconnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (clientSocket == null) return;
                if (!clientSocket.isClosed()) {
                    try {
                        clientSocket.close();
                        host = null;
                        port = null;
                        clientSocket = null;
                        showConnection(null, null, null);
                    } catch (IOException e) {
                        showError("关闭连接失败：" + e.getCause() + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    host = null;
                    port = null;
                    clientSocket = null;
                    showConnection(null, null, null);
                }
            }
        }).start();
    }


    Socket clientSocket;
    private void bIOConnect(final String host, final String port, final String user) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    clientSocket = new Socket(host, Integer.parseInt(port));
                    clientSocket.setKeepAlive(true);

                    InputStream inputStream = clientSocket.getInputStream();
                    OutputStream outputStream = clientSocket.getOutputStream();
                    String verifiedError=verifyUser(clientSocket,inputStream ,outputStream,user);
                    if (verifiedError!=null) {
                        clientSocket.close();
                        showError("验证用户失败：" + verifiedError);
                        return;
                    }

                    handleBIoInputStream(inputStream);
                    handleBIoOutputStream(outputStream);

                    showConnection( host, port, user);
                } catch (IOException e) {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        try {
                            clientSocket.close();
                            clientSocket = null;
                        } catch (IOException ex) {
                            showError("关闭连接失败：" + e.getCause() + e.getMessage());
                            ex.printStackTrace();
                        }
                    }
                    showError("连接失败：" + e.getCause() + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }


    private String verifyUser(Socket clientSocket,InputStream inputStream ,OutputStream outputStream, String user) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write(user);
            writer.newLine();
            writer.flush();

            String recvMsg = reader.readLine();
            if (recvMsg.contains("success")) {
                return null;
            } else {
                return recvMsg;
            }
        } catch (IOException e) {
            showError("连接失败：" + e.getCause() + e.getMessage());
            e.printStackTrace();
        }
        return "verify fail";
    }

    private void handleBIoInputStream(final InputStream inputStream) {
        new Thread(new Runnable() {
            int contentLength;
            @Override
            public void run() {
                for (; ; ) {
                    try {
//                        byte[] buffer = new byte[1024];
////                        int readedByteCount = inputStream.read(buffer);
////                        if (readedByteCount != -1) {
////                            String ss = new String(buffer,"UTF-8");
////                            showData(ss);
////                        }

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
                                showData(ss);
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

    private void showData(final String ss){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                receive_msg_content.append(ss+"\n\r");
            }
        });
    }

    OutputStream outputStream;
    private void handleBIoOutputStream(OutputStream outputStream ) {
        this.outputStream = outputStream;
    }

    private void bioWriteString(final String ss) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] contentBytes = ss.getBytes(("UTF-8"));
                    int contentLength=contentBytes.length;
                    byte[] contentLengthBytes = Tool.intToByte4(contentLength);
                    byte[] totalBytes = Tool.combineBytes(new byte[4 + contentLength], contentLengthBytes, contentBytes);
                    outputStream.write(totalBytes);
                } catch (IOException e) {
                    if (e.getMessage() != null && e.getMessage().contains("closed")
                            && e.getMessage().contains("Broken")) {
                        outputStream = null;
                        host = null;
                        port = null;
                        clientSocket = null;
                    }
                    showError("发送失败：" + e.getCause() + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void showConnection(final String ip, final String port, final String user) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (ip == null) {
                    connection_status.setText("unconnect");
                }else {
                    connection_status.setText("ip:"+ip+"port:"+port+"user:"+user);
                }
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