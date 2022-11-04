package com.jason.deliverclient;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);


    bIOConnect();
    

    send_msg_content = findViewById(R.id.send_msg_content);
    receive_msg_content = findViewById(R.id.receive_msg_content);
    server_ip = findViewById(R.id.server_ip);
    server_port = findViewById(R.id.server_port);

    server_ip.setText(host);
    server_port.setText(port);


  }

  EditText send_msg_content;
  EditText server_ip;
  EditText server_port;
  TextView receive_msg_content;
  SocketChannel socketChannel;
  public void onClick(View view) {
    switch (view.getId()) {
      case R.id.send_msg:
        final String sendMsg = send_msg_content.getText().toString();
        new Thread(new Runnable() {
          @Override
          public void run() {
//            write(sendMsg);
              bioWriteString(sendMsg);
          }
        }).start();
        break;
      case R.id.connect_server:
        port = server_port.getText().toString();
        host = server_ip.getText().toString();
        new Thread(new Runnable() {
          @Override
          public void run() {
            nIOConnect(host,port);
          }
        }).start();
        break;
      case R.id.disconnect_server:
        new Thread(new Runnable() {
          @Override
          public void run() {
            nIODisconnect();
          }
        }).start();
        break;

    }
  }

  private void nIODisconnect() {
    try {
      if (null != socketChannel) {
        socketChannel.close();
        isRegisterRead = false;
      }
      if (null != socketSelector) {
        socketSelector.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  String port = "3000";
  String host = "10.1.65.180";
  Selector socketSelector;
  private void nIOConnect(String host,String port) {
    socketChannel = null;
    socketSelector = null;
    try {
      socketSelector = Selector.open();
      socketChannel = SocketChannel.open();
      socketChannel.configureBlocking(false);
      if (socketChannel.connect(new InetSocketAddress(host, Integer.parseInt(port)))) {
        socketChannel.register(socketSelector, SelectionKey.OP_READ);
//        socketChannel = socketChannel;
//        write(channel);
      } else {
        socketChannel.register(socketSelector, SelectionKey.OP_CONNECT);
      }
      while (true) {
        Log.e("22","222");
        socketSelector.select(5000);
        Set<SelectionKey> selectionKeys = socketSelector.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        SelectionKey key = null;
        while (iterator.hasNext()) {
          try {
            key = iterator.next();
            iterator.remove();
            handle(key, socketSelector);
          } catch (Exception e) {
            e.printStackTrace();
            Log.e("nIOConnect","连接失败：" +e.getMessage());
            if (null != key.channel()) {
              key.channel().close();
            }
            if (null != key) {
              key.cancel();
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (null != socketChannel) {
          socketChannel.close();
        }
        if (null != socketSelector) {
          socketSelector.close();
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }



  private void write(String sendContent){
    if (socketChannel != null) {
      try {
        writeImp(socketChannel,sendContent);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }else {
      Log.e("nIOConnect", "未连接：socketChannel == null");
    }
  }

  private void writeImp(SocketChannel channel,String sendContent) throws IOException {
//    Scanner in = new Scanner(System.in);
//    System.out.println("输入你想说的话：");
//    String message = in.next();
    String message = sendContent;
    byte[] bytes = message.getBytes();
    ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
    byteBuffer.put(bytes);
    byteBuffer.flip();
    channel.write(byteBuffer);
  }

  boolean isRegisterRead = false;
  private void handle(SelectionKey key, Selector socketSelector) throws IOException {
    if (key.isValid()) {
      SocketChannel channel = (SocketChannel) key.channel();
      if (!isRegisterRead&&key.isConnectable()) {
        if (channel.finishConnect()) {
          channel.register(socketSelector, SelectionKey.OP_READ);
          socketChannel = channel;
//          write(channel);
        }
      } else if (key.isReadable()) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(3);
        int readBytes = channel.read(byteBuffer);
        if (readBytes > 0) {
          byteBuffer.flip();
          byte[] bytes = new byte[byteBuffer.remaining()];
          byteBuffer.get(bytes);
          String message = new String(bytes, "UTF-8");
//          System.out.println(message);
          displayReceiveText(message);
        } else if (readBytes < 0) {
          key.cancel();
          channel.close();
        }
      }
    }
  }

  private void displayReceiveText(final String receiveText) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        receive_msg_content.setText(receive_msg_content.getText()+"\n\r"+receiveText);
      }
    });
  }








  private void bIOConnect() {
    new Thread(new Runnable() {
      @Override
      public void run() {

//        try {
//          Socket s = new Socket("192.168.137.49",8887);
//
//          //构建IO
//          InputStream is = s.getInputStream();
//          OutputStream os = s.getOutputStream();
//
//          BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));
//          //向服务器端发送一条消息
//          bw.write("测试客户端和服务器通信，服务器接收到消息返回到客户端\n");
//          bw.flush();
//
//          //读取服务器返回的消息
//          BufferedReader br = new BufferedReader(new InputStreamReader(is));
//          String mess = br.readLine();
//          System.out.println("服务器："+mess);
//
//          Log.e("CCCCCCCCCCCCCC", "-----:" + mess);
//          br.close();
//          bw.close();
//          is.close();
//          os.close();
//          s.close();
//
//
//        } catch (UnknownHostException e) {
//          e.printStackTrace();
//        } catch (IOException e) {
//          e.printStackTrace();
//        }


//public Socket(String host, int port, InetAddress localAddr,int localPort)
//        Socket so = new Socket();
//        SocketAddress address = new InetSocketAddress("www.baidu.com", 80);
//        so.connect(address);
        try {
            Socket so = new Socket("192.168.137.50", 8887);
            InputStream inputStream = so.getInputStream();
            OutputStream outputStream = so.getOutputStream();
            so.setKeepAlive(true);

            handleBIoInputStream(inputStream);
            handleBIoOutputStream(outputStream);

//          BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
//          BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
//
//          //read message from server
//          String recvMsg = reader.readLine();
//          //write back to sever.
//          writer.write(recvMsg);
//          writer.newLine();
//          writer.flush();
        } catch (IOException e) {
          //ignore
        }


      }
    }).start();
  }

  private void handleBIoInputStream(final InputStream inputStream) {
      new Thread(new Runnable() {
          @Override
          public void run() {
              for (; ; ) {
                  try {
//                      if (inputStream.available() > 0) {
//                          BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
//                          reader.ready()
//                      }
                      byte[] buffer = new byte[1024];
                      int readedByteCount = inputStream.read(buffer);
                      if (readedByteCount != -1) {
                          String ss = new String(buffer,"UTF-8");
                          showData(ss);
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
              String temp=receive_msg_content.getText().toString() + ss;
              receive_msg_content.setText(temp);
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
                    outputStream.write(ss.getBytes(("UTF-8")));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }



}
