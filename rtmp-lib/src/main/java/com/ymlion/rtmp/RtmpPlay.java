package com.ymlion.rtmp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * 拉流
 */
public class RtmpPlay {
    private Socket socket;
    private String rtmpHost;
    private String appName;
    private String streamName;
    private String tcUrl;
    private boolean connected = false;
    private BufferedInputStream inputStream;
    private BufferedOutputStream outputStream;
    private static final int CHUNK_SIZE = 1024;
    private final Object writeObj = new Object();
    private ChunkReader.ChunkReaderListener listener;

    public RtmpPlay(String rtmpHost, String app, String streamName) {
        this.rtmpHost = rtmpHost;
        this.appName = app;
        tcUrl = "rtmp://" + rtmpHost + "/" + app;
        this.streamName = streamName;
    }

    public boolean connect() throws IOException {
        System.out.println(rtmpHost + " prepare to connect...");
        if (connected) {
            throw new IllegalStateException("rtmp is connected!");
        }
        if (socket == null) {
            socket = new Socket();
        }
        SocketAddress address = new InetSocketAddress(rtmpHost, 1935);
        socket.connect(address, 3000);
        inputStream = new BufferedInputStream(socket.getInputStream());
        outputStream = new BufferedOutputStream(socket.getOutputStream());
        HandShake handShake = new HandShake(outputStream, inputStream);
        handShake.handShake();
        System.out.println("shake hand done.");
        connected = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    handleReceivedData();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        // connect ——> set chunk size ——> set window ack size
        // ——> create stream ——> _check bw ——> get stream length
        // ——> play ——> set buffer length
        Command command = new Command(outputStream);
        command.setChunkSize(CHUNK_SIZE);
        command.connectPlay(appName, tcUrl);
        waitRead();
        command.setWindowAckSize(2500000);
        command.execute(1, 3, "createStream", 2.0, null);
        command.execute(1, 3, "_checkbw", 3.0, null);
        waitRead();
        command.play(streamName);
        command.setBufferLength();

        return true;
    }

    private void waitRead() {
        synchronized (writeObj) {
            try {
                writeObj.wait(3000000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private final Object readX = new Object();
    private ChunkReader reader;

    private void handleReceivedData() throws IOException {
        while (connected) {
            if (reader == null) {
                reader = new ChunkReader();
                reader.setListener(listener);
            }
            boolean next = reader.readChunk(inputStream, writeObj);
            if (!next) {
                synchronized (readX) {
                    try {
                        readX.wait(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void setListener(ChunkReader.ChunkReaderListener listener) {
        this.listener = listener;
    }

    public void close() {
        if (socket == null || !connected) {
            return;
        }
        try {
            connected = false;
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
