package com.ymlion.rtmp;

import com.ymlion.rtmp.bean.RtmpHeader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class Rtmp {
    private Socket socket;
    private String rtmpHost;
    private String appName;
    private String streamName;
    private String tcUrl;
    private boolean connected = false;
    private BufferedInputStream inputStream;
    private BufferedOutputStream outputStream;

    public Rtmp(String rtmpHost, String app, String streamName) {
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
        socket.connect(address, 6000);
        inputStream = new BufferedInputStream(socket.getInputStream());
        outputStream = new BufferedOutputStream(socket.getOutputStream());
        HandShake handShake = new HandShake(outputStream, inputStream);
        handShake.handShake();
        System.out.println("shake hand done.");

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    handleReceivedData();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Command command = new Command(outputStream);
        command.setChunkSize(4096);
        command.connect(appName, tcUrl, streamName);
        command.publish(streamName);
        command.sendMetaData();

        connected = true;
        return connected;
    }

    private void handleReceivedData() throws IOException {
        while (true) {
            ChunkReader reader = new ChunkReader();
            boolean b = reader.readChunk(inputStream);
        }
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

    public void sendVideo(byte[] frame, int time) throws IOException {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        RtmpHeader header = new RtmpHeader();
        header.fmt = 0;
        header.CSID = 4;
        header.timestamp = time;
        header.msgLength = frame.length;
        header.msgType = RtmpHeader.MSG_TYPE_VIDEO;
        header.msgSID = 1;
        header.write(outputStream);
        if (frame.length < 4096) {
            outputStream.write(0x17);
            outputStream.write(frame);
            return;
        }
        int part = frame.length / 4096 + 1;
        int wb = 0;
        for (int i = 0; i < part; i++) {
            if (i == 0) {
                outputStream.write(0x17);
                outputStream.write(frame, wb, 4095);
                wb += 4095;
            } else {
                outputStream.write(0xc4);
                int left = frame.length - wb;
                int size = left > 4096 ? wb : left;
                outputStream.write(frame, wb, size);
            }
        }
        outputStream.flush();
    }
}
