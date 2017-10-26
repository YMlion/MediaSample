package com.ymlion.rtmp;

import com.ymlion.rtmp.bean.RtmpHeader;
import com.ymlion.rtmp.util.ByteUtil;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by YMlion on 2017/10/16.
 */

public class ChunkReader {
    public boolean readChunk(InputStream in, Object writeObj) throws IOException {
        RtmpHeader header = new RtmpHeader();
        int r = header.read(in);
        if (r <= 0) {
            System.err.println("read error " + r);
            return false;
        }

        byte[] chunkBody = new byte[header.msgLength];
        int l = in.read(chunkBody);
        if (l <= 0) {
            return false;
        }
        checkType(header.msgType, chunkBody, writeObj);
        return true;
    }

    private void checkType(int msgType, byte[] chunkBody, Object writeObj) {
        System.out.print("rtmp chunk header type is " + msgType + " : ");
        String log;
        switch (msgType) {
            case RtmpHeader.MSG_TYPE_SET_CHUNK_SIZE:
                log = "set chunk size " + ByteUtil.bytes2Int(4, chunkBody, 0);
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
            default:
                log = "";
                break;
        }
        System.out.println(log);
    }
}
