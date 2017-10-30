package com.ymlion.rtmp;

import com.ymlion.rtmp.bean.Frame;
import com.ymlion.rtmp.bean.RtmpHeader;
import com.ymlion.rtmp.util.ByteUtil;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.List;

/**
 * 推流
 */
public class Rtmp {
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
        Command command = new Command(outputStream);
        command.setChunkSize(CHUNK_SIZE);
        command.connect(appName, tcUrl, streamName);
        waitRead();
        command.execute(3, "releaseStream", 2.0, streamName);
        waitRead();
        command.execute(3, "FCPublish", 3.0, streamName);
        waitRead();
        command.execute(3, "createStream", 4.0, (List<String>) null);
        command.execute(3, "_checkbw", 5.0, (List<String>) null);
        waitRead();
        command.publish(streamName);
        command.sendMetaData();

        return true;
    }

    private void waitRead() {
        synchronized (writeObj) {
            try {
                writeObj.wait(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private final Object readX = new Object();

    private void handleReceivedData() throws IOException {
        while (connected) {
            ChunkReader reader = new ChunkReader();
            boolean next = reader.readChunk(inputStream, writeObj);
            if (!next) {
                synchronized (readX) {
                    try {
                        readX.wait(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
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

    /**
     * 写入sps和pps
     */
    public synchronized byte[] configureAVC(byte[] spsData, byte[] ppsData) {
        System.out.println("write sps and pps " + spsData.length + "; " + ppsData.length);
        byte[] avc = new byte[13 + spsData.length + 3 + ppsData.length];
        avc[0] = 0x17; // 1 : key frame, 7 : avc
        avc[1] = 0;// avc sequence header
        avc[2] = avc[3] = avc[4] = 0;// 3 unused avc byte
        avc[5] = 1; // configuration version
        avc[6] = 0x64; // 100 avc profile indication
        avc[7] = 0; // profile compatibility
        avc[8] = 0x2a; // avc level indication
        avc[9] = (byte) 0xff; // length size minus one
        avc[10] = (byte) 0xe1; // sps num
        ByteUtil.writeInt(2, spsData.length, avc, 11);
        System.arraycopy(spsData, 0, avc, 13, spsData.length);
        int l = 13 + spsData.length;
        avc[l] = 1;
        ByteUtil.writeInt(2, ppsData.length, avc, l + 1);
        System.arraycopy(ppsData, 0, avc, l + 3, ppsData.length);
        return avc;
    }

    /**
     * rtmp协议中发送的数据为flv封装格式，android手机录制的未处理的数据时h264压缩的，需要使用flv格式进行封装，但不
     * 需要完全按照flv格式进行封装，通过对rtmp协议传输的视频帧分析，可得出rtmp中传输的数据时flv格式标准中的video
     * tag，但没有tag header。
     * <p>
     * h264格式是由一个个的NALU单元组成，每个单元以四个00 00 00 01或三个00 00 01字节分割，每个单元的第一个byte的
     * 后5个bit表明该单元是什么类型。每一帧数据大部分情况下就是一个NALU单元，除了第一帧数据包含了sps和pps，根据每个单
     * 元的类型和数据来进行封装。
     * 封装为flv也是对NALU单元的封装。
     * </p>
     * 视频和音频封装规则差不多。
     *
     * @throws IOException
     */
    public synchronized void sendVideo(Frame frame) throws IOException {
        byte[] data = encapsulateFrame(frame.getData());
        if (data == null) {
            throw new IOException("帧数据分析失败");
        }
        RtmpHeader header = new RtmpHeader();
        header.fmt = 0;
        header.CSID = 4;
        header.timestamp = (int) frame.getTime();
        header.msgLength = data.length;
        header.msgType = RtmpHeader.MSG_TYPE_VIDEO;
        header.msgSID = 1;
        header.write(outputStream);
        writeData(data);
    }

    private void writeData(byte[] data) throws IOException {
        if (data.length < CHUNK_SIZE) {
            outputStream.write(data);
            return;
        }
        int part = data.length / CHUNK_SIZE + 1;
        int wb = 0;
        for (int i = 0; i < part; i++) {
            if (i == 0) {
                outputStream.write(data, wb, CHUNK_SIZE);
                wb += CHUNK_SIZE;
            } else {
                outputStream.write(0xc4);
                int left = data.length - wb;
                int size = left > CHUNK_SIZE ? CHUNK_SIZE : left;
                outputStream.write(data, wb, size);
                System.out.println("size : " + left + "; " + wb + "; " + i + "; " + part);
                wb += CHUNK_SIZE;
            }
        }
        outputStream.flush();
    }

    /**
     * sps和pps是第一个video tag，且只发送一次
     * <p>
     * 后面的每一个video tag，有两种类型
     * <p>
     * 1. 关键帧，该帧的数据一般是“分隔符 + SEI + I帧”，分隔符和SEI不是必须的，但目前未遇到有分隔符的
     * </p>
     * <p>
     * 2.非关键帧，即P帧
     * </p>
     * 整个数据就是 122222....122222...这样循环，一个I帧后面很多P帧
     * </p>
     */
    private byte[] encapsulateFrame(byte[] frame) {
        if (frame[0] != 0) {
            return frame;
        }
        int s = 0;// 3 or 4
        for (byte b : frame) {
            if (b != 0 && b != 1) {
                break;
            }
            s++;
        }
        byte naluType = (byte) (frame[s] & 0x1f);
        byte[] data = null;
        switch (naluType) {
            case 7:// sps，和pps是一起的
                int ppsIndex = findNextNALU(s, frame);
                byte[] sps = new byte[ppsIndex - s - s];
                System.arraycopy(frame, s, sps, 0, sps.length);
                byte[] pps = new byte[frame.length - ppsIndex];
                System.arraycopy(frame, ppsIndex, pps, 0, pps.length);
                data = configureAVC(sps, pps);
                break;
            case 6:// SEI，之后肯定有一个I帧
                int s1 = findNextNALU(s, frame);// 下一个NALU的位置
                data = new byte[13 - s - s + frame.length];
                data[0] = 0x17;
                data[1] = 1; // avc sequence nalu
                ByteUtil.writeInt(3, 0, data, 2);
                ByteUtil.writeInt(4, s1 - s - s, data, 5);
                System.arraycopy(frame, s, data, 9, s1 - s - s);
                ByteUtil.writeInt(4, frame.length - s1, data, s1 - s - s + 9);
                System.arraycopy(frame, s1, data, s1 - s - s + 13, frame.length - s1);
                break;
            case 5:// I frame
                data = new byte[frame.length - s + 9];
                data[0] = 0x17;
                data[1] = 1;
                ByteUtil.writeInt(3, 0, data, 2);
                ByteUtil.writeInt(4, frame.length - s, data, 5);
                System.arraycopy(frame, s, data, 9, frame.length - s);
                break;
            case 1:// P frame
                data = new byte[frame.length - s + 9];
                data[0] = 0x27;
                data[1] = 1;
                ByteUtil.writeInt(3, 0, data, 2);
                ByteUtil.writeInt(4, frame.length - s, data, 5);
                System.arraycopy(frame, s, data, 9, frame.length - s);
                break;
            case 9:// 分隔符
                break;
        }
        return data;
    }

    private int findNextNALU(int off, byte[] data) {
        for (int i = off; i < data.length; i++) {
            if (data[i] == 0) {
                if (data[i + 1] == 0) {
                    if (data[i + 2] == 0) {
                        if (data[i + 3] == 1) {
                            return i + 4;
                        } else {
                            i += 3;
                        }
                    } else {
                        i += 2;
                    }
                } else {
                    i++;
                }
            }
        }
        return off;
    }

    public void sendAudio(Frame frame) throws IOException {
        byte[] data = new byte[frame.getData().length + 2];
        data[0] = (byte) 0xaf;
        data[1] = (byte) (frame.getData().length > 2 ? 1 : 0);
        System.arraycopy(frame.getData(), 0, data, 2, frame.getData().length);
        RtmpHeader header = new RtmpHeader();
        header.fmt = 0;
        header.CSID = 4;
        header.timestamp = (int) frame.getTime();
        header.msgLength = data.length;
        header.msgType = RtmpHeader.MSG_TYPE_AUDIO;
        header.msgSID = 1;
        header.write(outputStream);
        writeData(data);
    }
}
