package com.jason.deliverserver;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//import io.reactivex.Observable;
//import io.reactivex.ObservableEmitter;
//import io.reactivex.ObservableOnSubscribe;
//import io.reactivex.ObservableSource;
//import io.reactivex.disposables.Disposable;
//import io.reactivex.functions.BiFunction;
//import io.reactivex.functions.Consumer;
//import io.reactivex.functions.Function;
//import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {


    EditText send_msg_content;
    EditText server_ip;
    EditText server_port;
    TextView receive_msg_content;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);


    bIOConnect();


      send_msg_content = findViewById(R.id.send_msg_content);
      receive_msg_content = findViewById(R.id.receive_msg_content);
      server_ip = findViewById(R.id.server_ip);
      server_port = findViewById(R.id.server_port);

//      server_ip.setText(host);
//      server_port.setText(port);

//    new Thread(new Runnable() {
//      @Override
//      public void run() {
//        nIOConnect();
//
//      }
//    }).start();



//    Disposable disposable = Observable
//            .create(new ObservableOnSubscribe<Integer>() {
//              @Override
//              public void subscribe(ObservableEmitter<Integer> e) throws Exception {
//                Log.e("connectedSokectList","当前连接数"+connectedSokectSet.size());
//                if(connectedSokectSet.isEmpty()){
//                  return;
//                }
//                Iterator<SocketChannel> iterator = connectedSokectSet.iterator();
//                while (iterator.hasNext()){
//                  SocketChannel socketChannel=iterator.next();
//                  Boolean removeFlag=handleSocket(socketChannel);
//                  if(removeFlag){
//                    iterator.remove();
//                  }
//                }
//
//              }
//            })
//            .doOnSubscribe(new Consumer<Disposable>() {
//              @Override
//              public void accept(Disposable disposable) throws Exception {
//
//              }
//            })
//            .doOnNext(new Consumer<BaseResponse>() {
//              @Override
//              public void accept(BaseResponse baseResponse) throws Exception {
////                LogUtil.i("vdc:", "vdc token refresh success");
//              }
//            })
//            .doOnError(new Consumer<Throwable>() {
//              @Override
//              public void accept(Throwable throwable) throws Exception {
////                LogUtil.e("vdc:", "vdc token refresh fail:" + throwable.getMessage());
//              }
//            })
//            //轮询过程中 有网络错误，立即重试；重试失败后，延时一段固定时间，重试
//            .retryWhen(new Function<Observable<Throwable>, ObservableSource<?>>() {
//              @Override
//              public ObservableSource<?> apply(Observable<Throwable> throwableObservable) throws Exception {
//                return throwableObservable
//                        .zipWith(Observable.range(1, 3/*立即重试次数n-1*/), new BiFunction<Throwable, Integer, Integer>() {
//                          @Override
//                          public Integer apply(Throwable throwable, Integer integer) throws Exception {
//                            tempError = throwable;
//                            //TODO：立即重试错误类型判断，根据情况，不再重试，直接抛出错误   throwable
//                            // return -1;
//                            return integer;
//                          }
//                        })
//                        .flatMap(new Function<Integer, ObservableSource<?>>() {
//                          @Override
//                          public ObservableSource<?> apply(Integer retryCount) throws Exception {
//                            if (retryCount == -1) return Observable.error(tempError);
//                            if (retryCount == 3) return Observable.error(tempError);
//                            return Observable.timer(0/*立即重试的时间间隔*/, TimeUnit.SECONDS);
//                          }
//                        });
//              }
//            })
//            .delay(60, TimeUnit.SECONDS)//此处可设延迟重试的 重试策略
//            .repeat()
//            .observeOn(Schedulers.io())
//            .subscribeOn(Schedulers.io())
//            .subscribe();



  }


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
//            case R.id.connect_server:
//                port = server_port.getText().toString();
//                host = server_ip.getText().toString();
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        nIOConnect(host,port);
//                    }
//                }).start();
//                break;
//            case R.id.disconnect_server:
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        nIODisconnect();
//                    }
//                }).start();
//                break;

        }
    }


    private boolean caculateIsAlive(long lastSendTime)  {
    return System.currentTimeMillis()-lastSendTime > keepAliveTime;
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  private boolean handleSocket(SocketChannel channel) {
    int channel_code = channel.hashCode();
    SocketAddress ipLocation;
    try {
      ipLocation = channel.getRemoteAddress();
      Long sendTime = connectionTimePool.get("LST_" + channel_code);


      if (sendTime != null) {
        if (caculateIsAlive(sendTime)) {
          //超过时间
          try {
            if (channel.isConnected()) {
              channel.close();
              connectionTimePool.remove("LST_" + channel_code);
            } else {
              Log.e("handleSocket","当前通道，ip:" + ipLocation + "已经关闭... ..." + ",上次回应时间：" + sendTime);
            }
            return true;
          } catch (IOException e) {
            Log.e("handleSocket","通道，ip:" + ipLocation + "关闭时发生了异常");
          }
        } else {
          return false;
        }
      }


      if (channel.isConnected()) {
        channel.close();
        Log.e("handleSocket","连接被TCP管理线程关闭，ip:" + ipLocation + "：未检测到登陆时间... ...");
      } else {
        Log.e("handleSocket","当前通道，ip:" + ipLocation + "已经关闭... ...");
      }



    } catch (Exception e) {
      Log.e("handleSocket","通道关闭时发生了异常"+ e.getMessage());
    }
    return true;
  }


  private void nIOConnect() {
    int port = 3000;
    ServerSocketChannel socketChannel = null;
    Selector selector = null;
    try {
      selector = Selector.open();
      socketChannel = ServerSocketChannel.open();
      //设置连接模式为非阻塞模式
      socketChannel.configureBlocking(false);
      socketChannel.socket().bind(new InetSocketAddress(port));
      //在selector上注册通道，监听连接事件
      socketChannel.register(selector, SelectionKey.OP_ACCEPT);
      while (true) {
        //设置selector 每隔一秒扫描所有channel
        int s = selector.select(1000);
        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterable = selectionKeys.iterator();
        SelectionKey key = null;
        while (iterable.hasNext()) {
          key = iterable.next();
          iterable.remove();
          //对key进行处理
          try {
            dispatchSocketSelectorKeyKey(key, selector);
          } catch (Exception e) {
            if (null != key) {
              key.cancel();
              if (null != key.channel()) {
                key.channel().close();
              }
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (null != selector) {
          selector.close();
        }
        if (null != socketChannel) {
          socketChannel.close();
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }


  private long keepAliveTime=5000;
  Map<String, Long> connectionTimePool = new HashMap<>();
  Set<SocketChannel> connectedSokectSet = new HashSet<>();
  private void dispatchSocketSelectorKeyKey(SelectionKey key, Selector selector) throws IOException {
    if (key.isValid()) {
      //判断是否是连接请求，对所有连接请求进行处理
      if (key.isAcceptable()) {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel channel = serverSocketChannel.accept();
        handleAcceptableSocketChannel(channel,selector);

      } else if (key.isReadable()) {
        SocketChannel channel = (SocketChannel) key.channel();
        handleReadableSocketChannel(channel);

      }
    }
  }

  private void handleAcceptableSocketChannel(SocketChannel channel,Selector selector) throws IOException {
    channel.configureBlocking(false);
    //在selector上注册通道，监听读事件
    channel.register(selector, SelectionKey.OP_READ);

    connectionTimePool.put("LST_" + channel.hashCode(),/*new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())*/System.currentTimeMillis());
    //将SocketChannel放入HashSet中管理
    connectedSokectSet.add(channel);
  }

  private void handleReadableSocketChannel(SocketChannel channel) throws IOException {
    //分配一个1024字节的缓冲区
    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    int readBytes = channel.read(byteBuffer);
    if (readBytes > 0) {
      //从写模式切换到读模式
      byteBuffer.flip();
      byte[] bytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(bytes);
      String message = new String(bytes, "UTF-8");
      System.out.println("收到客户端消息: " + message);
      //回复客户端
      message = "answer: " + message;
      byte[] responseByte = message.getBytes();
      ByteBuffer writeBuffer = ByteBuffer.allocate(responseByte.length);
      writeBuffer.put(responseByte);
      writeBuffer.flip();
      channel.write(writeBuffer);

      connectionTimePool.put("LST_" + channel.hashCode(),/*new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())*/System.currentTimeMillis());
    }
  }



  /////////////////////////////////////////////////////////////////////////

  private void bIOConnect() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        //try with resource 写法绑定本地端口
        int i = 0;
        try {
            ServerSocket socket = new ServerSocket(8887);
          while (true) {
            //接受客户端连接
            System.out.println("111111111111:" + (i++));
            final Socket so = socket.accept();
              so.setKeepAlive(true);
            //与客户端通信的工作放到线程池中异步执行
            threads.submit(new Runnable() {
              @Override
              public void run() {
                bIOHandle(so);
              }
            });
          }
        } catch (IOException e) {
          //
        }


      }
    }).start();
  }

  int j = 0;
  private static final String MESSAGE = "aaaaaaaaaaaaaaaaaa";
  private ExecutorService threads = Executors.newFixedThreadPool(6);

    ArrayList<InputStream> inputStreams = new ArrayList<>();
    ArrayList<OutputStream> outputStreams = new ArrayList<>();
  public void bIOHandle(Socket so) {
    //try with resource 写法打开输入输出流
    try{
        OutputStream out = so.getOutputStream();
        InputStream in = so.getInputStream();
//      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "utf-8"));
//      BufferedReader reader = new BufferedReader(new InputStreamReader(in));
//
//      //send data to client.
//      writer.write(MESSAGE);
//      writer.newLine();
//      writer.flush();
//
//      //recv data from client.
//      String clientResp = reader.readLine();
      System.out.println("111111111111:" + (j++));
//      System.out.println(MESSAGE.equals(clientResp));

        inputStreams.add(in);
        handleBIoInputStream(in);
        outputStreams.add(out);
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

    private void bioWriteString(final String sendMsg) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (outputStreams.size() > 0 && outputStreams.get(outputStreams.size()-1) != null) {
                    try {
                        outputStreams.get(outputStreams.size()-1).write(sendMsg.getBytes(("UTF-8")));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }


}
