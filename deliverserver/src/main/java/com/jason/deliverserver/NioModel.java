package com.jason.deliverserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class NioModel {


  public void start() {
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
        selector.select(1000);
        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterable = selectionKeys.iterator();
        SelectionKey key = null;
        while (iterable.hasNext()) {
          key = iterable.next();
          //对key进行处理
          try {
            handlerKey(key, selector);
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


  private void handlerKey(SelectionKey key, Selector selector) throws IOException {
    if (key.isValid()) {
      //判断是否是连接请求，对所有连接请求进行处理
      if (key.isAcceptable()) {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel channel = serverSocketChannel.accept();
        channel.configureBlocking(false);
        //在selector上注册通道，监听读事件
        channel.register(selector, SelectionKey.OP_READ);
      } else if (key.isReadable()) {
        SocketChannel channel = (SocketChannel) key.channel();
        //分配一个1024字节的缓冲区
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        int readBytes = channel.read(byteBuffer);
        if (readBytes > 0) {
          //从写模式切换到读模式
          byteBuffer.flip();
          byte[] bytes = new byte[byteBuffer.remaining()];
          byteBuffer.get(bytes);
          String message  = new String(bytes, "UTF-8");
          System.out.println("收到客户端消息: " + message);
          //回复客户端
          message = "answer: " + message;
          byte[] responseByte = message.getBytes();
          ByteBuffer writeBuffer = ByteBuffer.allocate(responseByte.length);
          writeBuffer.put(responseByte);
          writeBuffer.flip();
          channel.write(writeBuffer);
        }
      }
    }
  }



}
