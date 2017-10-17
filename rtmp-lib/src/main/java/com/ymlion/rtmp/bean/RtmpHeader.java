package com.ymlion.rtmp.bean;

import com.ymlion.rtmp.util.ByteUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.ymlion.rtmp.util.ByteUtil.writeInt;

/**
 * Created by YMlion on 2017/10/13.
 */

public class RtmpHeader {

    /**
     * 协议控制消息：set chunk size, default is 128 bytes, 4 bytes, 1st bit is 0, 31 bits is chunk size
     * <p>
     * Message Stream ID必须为0（代表控制流信息），CSID必须为2
     */
    public static final int MSG_TYPE_SET_CHUNK_SIZE = 0x1;
    /**
     * 协议控制消息：abort the message that have same CSID, 4 bytes--CSID
     * <p>
     * Message Stream ID必须为0（代表控制流信息），CSID必须为2
     */
    public static final int MSG_TYPE_ABORT_MESSAGE = 0x2;
    /**
     * 协议控制消息：set window size, the max size bytes to send before response, 4 bytes
     * <p>
     * Message Stream ID必须为0（代表控制流信息），CSID必须为2
     */
    public static final int MSG_TYPE_ACKNOWLEDGEMENT = 0x3;
    /**
     * 包含用户控制事件，例如开始传输，发送时chunk头部中的msgSID = 1, CSID = 2, msgType = 4
     */
    public static final int MSG_TYPE_USER_CONTROL = 0x4;
    /**
     * 协议控制消息：发送端在接收到接受端返回的两个ACK间最多可以发送的字节数, 4 bytes
     * <p>
     * Message Stream ID必须为0（代表控制流信息），CSID必须为2
     */
    public static final int MSG_TYPE_WINDOW_ACK_SIZE = 0x5;
    /**
     * 协议控制消息：限制对端的输出带宽, 5 bytes: size is 4 bytes, 1 byte is limit type
     * <p>
     * Message Stream ID必须为0（代表控制流信息），CSID必须为2
     * <p>
     * 1. Hard(Limit Type＝0):接受端应该将Window Ack Size设置为消息中的值
     * <p>
     * 2. Soft(Limit Type=1):接受端可以讲Window Ack Size设为消息中的值，也可以保存原来的值（前提是原来的Size小与该控制消息中的Window Ack
     * Size）
     * <p>
     * 3. Dynamic(Limit Type=2):如果上次的Set Peer Bandwidth消息中的Limit Type为0，本次也按Hard处理，否则忽略本消息，不去设置Window
     * Ack Size。
     */
    public static final int MSG_TYPE_SET_PEER_BW = 0x6;
    /**
     * audio message
     */
    public static final int MSG_TYPE_AUDIO = 0x8;
    /**
     * video message
     */
    public static final int MSG_TYPE_VIDEO = 0x9;
    /**
     * data message, like metadata
     */
    public static final int MSG_TYPE_DATA = 0x12;
    /**
     * shared object message
     */
    public static final int MSG_TYPE_SHARED = 0x13;
    /**
     * command message, like connect, publish etc.
     */
    public static final int MSG_TYPE_COMMAND = 0x14;
    /**
     * aggregate message
     */
    public static final int MSG_TYPE_AGGREGATE = 0x16;

    /**
     * chunk type, 2 bit
     */
    public int fmt;
    /**
     * chunk stream id, basic head size - 2 bit, little-endian
     */
    public int CSID;
    /**
     * time stamp, 3 bytes
     */
    public int timestamp;
    /**
     * message length, indicate chunk data size, 3 bytes
     */
    public int msgLength;

    /**
     * message type, like video, audio etc. 1 byte
     */
    public int msgType;

    /**
     * message stream id, 4 bytes, little-endian
     */
    public int msgSID;

    public void write(OutputStream out) throws IOException {
        int total;
        byte[] header = new byte[12];
        if (CSID < 64) {// [3, 63]
            total = 1;
            header[0] = (byte) (fmt << 6 | CSID);
        } else if (CSID < 320) {// [64, 319]
            total = 2;
            header[0] = (byte) (fmt << 6 | (CSID & 0x3f));
            header[1] = (byte) (CSID >> 6);
        } else {// [320, 65599]
            total = 3;
            header[0] = (byte) (fmt << 6 | (CSID & 0x3f));
            header[1] = (byte) (CSID >> 6 & 0xff);
            header[2] = (byte) (CSID >> 14);
        }
        switch (fmt) {
            case 0:
                writeInt(3, timestamp, header, total);
                writeInt(3, msgLength, header, total + 3);
                writeInt(1, msgType, header, total + 6);
                header[8] = (byte) (msgSID & 0xff);
                header[9] = (byte) (msgSID >> 8 & 0xff);
                header[10] = (byte) (msgSID >> 16 & 0xff);
                header[11] = (byte) (msgSID >> 24 & 0xff);
                total += 11;
                break;
            case 1:
                writeInt(3, timestamp, header, total);
                writeInt(3, msgLength, header, total + 3);
                writeInt(1, msgType, header, total + 6);
                total += 7;
                break;
            case 2:
                writeInt(3, timestamp, header, total);
                total += 3;
                break;
            default:
                break;
        }
        out.write(header, 0, total);
    }

    public int read(InputStream in) throws IOException {
        int b1 = in.read();
        if (b1 == -1) {
            return -1;
        }
        fmt = b1 >>> 6;
        CSID = b1 & 0x3f;
        System.out.println("rtmp chunk header fmt is " + fmt + " ; cs id is " + CSID);
        byte[] head = new byte[11];
        int r = 0;
        switch (fmt) {
            case 0:
                r = in.read(head);
                msgLength = ByteUtil.bytes2Int(3, head, 3);
                msgType = head[6];
                System.out.println("rtmp chunk header type is " + msgType);
                break;
            case 1:
                r = in.read(head, 0, 7);
                msgLength = ByteUtil.bytes2Int(3, head, 3);
                break;
            case 2:
                r = in.read(head, 0, 3);
                msgLength = 128;
                break;
            default:
                msgLength = 128;
                break;
        }
        if (r <= 0) {
            return -1;
        }

        return msgLength;
    }
}
