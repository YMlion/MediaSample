package com.ymlion.rtmp;

import com.ymlion.rtmp.bean.CommandObject;
import com.ymlion.rtmp.bean.Frame;
import com.ymlion.rtmp.bean.RString;
import com.ymlion.rtmp.bean.RtmpHeader;
import com.ymlion.rtmp.util.ByteUtil;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by YMlion on 2017/10/16.
 */

public class ChunkReader {
    private static int READ_CHUNK_SIZE = 128;
    private ChunkReaderListener listener;
    private int lastMsgType = -1;
    private OutputStream out;

    public void setListener(ChunkReaderListener listener) {
        this.listener = listener;
    }

    public boolean readChunk(InputStream in, Object writeObj) throws IOException {
        if (out == null) {
            out = new BufferedOutputStream(new FileOutputStream(
                    "/storage/emulated/0/" + System.currentTimeMillis() + "_.h264"));
        }
        RtmpHeader header = new RtmpHeader();
        int r = header.read(in);
        if (r <= 0) {
            System.err.println("read error " + r);
            return false;
        }

        if (header.msgLength == -1) {
            header.msgLength = READ_CHUNK_SIZE;
        }

        byte[] chunkBody = new byte[header.msgLength];
        int l = in.read(chunkBody);
        if (l <= 0) {
            return false;
        }
        checkType(header, chunkBody, writeObj);
        return true;
    }

    private void checkType(RtmpHeader header, byte[] chunkBody, Object writeObj)
            throws IOException {
        if (header.msgType == -1) {
            header.msgType = lastMsgType;
        } else {
            lastMsgType = header.msgType;
        }
        System.out.print("rtmp chunk header type is " + header.msgType + " : ");
        String log;
        switch (header.msgType) {
            case RtmpHeader.MSG_TYPE_SET_CHUNK_SIZE:
                READ_CHUNK_SIZE = ByteUtil.bytes2Int(4, chunkBody, 0);
                log = "set chunk size " + READ_CHUNK_SIZE;
                break;
            case RtmpHeader.MSG_TYPE_ABORT_MESSAGE:
                log = "abort the same CSID message " + ByteUtil.bytes2Int(4, chunkBody, 0);
                break;
            case RtmpHeader.MSG_TYPE_ACKNOWLEDGEMENT:
                log = "set window size, the max size bytes to send before response "
                        + ByteUtil.bytes2Int(4, chunkBody, 0);
                break;
            case RtmpHeader.MSG_TYPE_USER_CONTROL:
                log = "user control";
                break;
            case RtmpHeader.MSG_TYPE_WINDOW_ACK_SIZE:
                log = "设置发送端在接收到接受端返回的两个ACK间最多可以发送的字节数 " + ByteUtil.bytes2Int(4, chunkBody, 0);
                break;
            case RtmpHeader.MSG_TYPE_SET_PEER_BW:
                log = "限制对端的输出带宽 " + ByteUtil.bytes2Int(4, chunkBody, 0);
                break;
            case RtmpHeader.MSG_TYPE_DATA:
                log = "设置数据";
                RString dataCommad = RString.read(chunkBody, false);
                if (dataCommad.value().equals("onMetaData")) {
                    CommandObject object = CommandObject.read(chunkBody, dataCommad.getSize());
                    if (listener != null) {
                        listener.onPlayStart(object);
                    }
                }
                break;
            case RtmpHeader.MSG_TYPE_COMMAND:
                log = "perform the command ";
                int l = ByteUtil.bytes2Int(2, chunkBody, 1);
                byte[] charBytes = new byte[l];
                System.arraycopy(chunkBody, 3, charBytes, 0, l);
                String com = new String(charBytes);
                if ("onBWDone".equals(com) || "_result".equals(com)) {
                    synchronized (writeObj) {
                        writeObj.notify();
                    }
                }
                log += com;
                break;
            case RtmpHeader.MSG_TYPE_AUDIO:
                log = "audio data";
                if (listener != null) {
                    byte[] audioFrame = new byte[chunkBody.length - 2];
                    System.arraycopy(chunkBody, 2, audioFrame, 0, audioFrame.length);
                    listener.onPlayAudio(
                            new Frame(false, audioFrame, header.timestamp, chunkBody[1] == 0));
                }
                break;
            case RtmpHeader.MSG_TYPE_VIDEO:
                log = "video data " + header.msgLength;
                header.write(out);
                out.write(chunkBody);
                out.flush();
                if (listener != null) {
                    boolean isHeader = chunkBody[1] == 0;
                    if (isHeader) {
                        byte[] videoFrame = new byte[chunkBody.length - 11];
                        System.arraycopy(chunkBody, 11, videoFrame, 0, videoFrame.length);
                        for (byte b : videoFrame) {
                            System.out.print((b & 0xff) + " ");
                        }
                        System.out.println();
                        listener.onPlayVideo(new Frame(true, videoFrame, header.timestamp, true));
                    } else {
                        byte[] videoFrame = new byte[chunkBody.length - 5];
                        System.arraycopy(chunkBody, 5, videoFrame, 0, videoFrame.length);
                        int vl = ByteUtil.bytes2Int(4, videoFrame, 0);
                        log += " " + vl;
                        videoFrame[0] = videoFrame[1] = videoFrame[2] = 0;
                        videoFrame[3] = 1;
                        if (vl + 4 < videoFrame.length) {
                            vl = ByteUtil.bytes2Int(4, videoFrame, vl + 4);
                            log += " " + vl;
                            videoFrame[vl + 4] = videoFrame[vl + 5] = videoFrame[vl + 6] = 0;
                            videoFrame[vl + 7] = 1;
                        }
                        listener.onPlayVideo(new Frame(true, videoFrame, header.timestamp, false));
                    }
                    try {
                        Thread.sleep(35);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                break;
            default:
                log = "";
                break;
        }
        System.out.println(log);
    }

    public interface ChunkReaderListener {
        void onPlayStart(CommandObject metaData);

        void onPlayAudio(Frame frame);

        void onPlayVideo(Frame frame);
    }
}
