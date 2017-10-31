package com.ymlion.rtmp;

import com.ymlion.rtmp.bean.CommandObject;
import com.ymlion.rtmp.bean.RArray;
import com.ymlion.rtmp.bean.RNull;
import com.ymlion.rtmp.bean.RNumber;
import com.ymlion.rtmp.bean.RObject;
import com.ymlion.rtmp.bean.RString;
import com.ymlion.rtmp.bean.RtmpHeader;
import com.ymlion.rtmp.util.ByteUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by YMlion on 2017/10/13.
 */

public class Command {

    private OutputStream out;

    public Command(OutputStream outputStream) {
        out = outputStream;
    }

    public void connect(String app, String tcUrl) throws IOException {
        System.out.println("rtmp start connecting...");
        RString name = new RString("connect", false);
        byte[] nameBytes = name.getBytes();
        byte[] number = new RNumber(1.0).getBytes();
        CommandObject object = new CommandObject();
        object.put("app", app);
        object.put("type", "nonprivate");
        object.put("flashVer", "FMLE/3.0 (compatible; FMsc/1.0)");
        object.put("swfUrl", tcUrl);
        object.put("tcUrl", tcUrl);
        RtmpHeader header = new RtmpHeader();
        header.fmt = 0;
        header.CSID = 3;
        header.timestamp = 0;
        header.msgType = RtmpHeader.MSG_TYPE_COMMAND;
        header.msgSID = 0;
        header.msgLength = nameBytes.length + number.length + object.getByteSize();
        header.write(out);
        out.write(nameBytes);
        out.write(number);
        object.write(out);
        out.flush();
        System.out.println("rtmp connected.");
    }

    public void connectPlay(String app, String tcUrl) throws IOException {
        System.out.println("rtmp start connecting...");
        RString name = new RString("connect", false);
        byte[] nameBytes = name.getBytes();
        byte[] number = new RNumber(1.0).getBytes();
        CommandObject object = new CommandObject();
        object.put("app", app);
        object.put("flashVer", "LNX 9,0,124,2");
        object.put("tcUrl", tcUrl);
        object.put("fpad", false);
        object.put("capabilities", 15);
        object.put("audioCodecs", 4071);
        object.put("videoCodecs", 252);
        object.put("videoFunction", 1);
        RtmpHeader header = new RtmpHeader();
        header.fmt = 0;
        header.CSID = 3;
        header.timestamp = 0;
        header.msgType = RtmpHeader.MSG_TYPE_COMMAND;
        header.msgSID = 0;
        header.msgLength = nameBytes.length + number.length + object.getByteSize();
        header.write(out);
        out.write(nameBytes);
        out.write(number);
        object.write(out);
        out.flush();
        System.out.println("rtmp connected.");
    }

    public void publish(String streamName) throws IOException {
        List<String> list = new ArrayList<>();
        list.add(streamName);
        list.add("live");
        execute(1, 8, "publish", 6.0, list);
    }

    public void execute(int CSID, String command, double number, String string) throws IOException {
        List<String> strings = new ArrayList<>();
        strings.add(string);
        execute(1, CSID, command, number, strings);
    }

    public void execute(int fmt, int CSID, String command, double number, List<String> strings)
            throws IOException {
        System.out.println("execute " + command);
        int total = 0;
        RObject name = new RString(command, false);
        total += name.getSize();
        RObject num = new RNumber(number);
        total += num.getSize();
        RObject rNull = new RNull();
        total += rNull.getSize();
        List<RObject> objects = null;
        if (strings != null && strings.size() > 0) {
            objects = new ArrayList<>();
            for (String string : strings) {
                RString s = new RString(string, false);
                total += s.getSize();
                objects.add(s);
            }
        }
        RtmpHeader header = new RtmpHeader();
        header.fmt = fmt;
        header.CSID = CSID;
        header.timestamp = 0;
        header.msgType = RtmpHeader.MSG_TYPE_COMMAND;
        header.msgLength = total;
        header.write(out);
        name.write(out);
        num.write(out);
        rNull.write(out);
        if (objects != null) {
            for (RObject object : objects) {
                object.write(out);
            }
        }
        out.flush();
    }

    public void sendMetaData() throws IOException {
        System.out.println("rtmp set data frame.");
        RString name = new RString("@setDataFrame", false);
        RString name2 = new RString("onMetaData", false);
        CommandObject object = new RArray();
        object.put("duration", 0);
        object.put("width", 720);
        object.put("height", 480);
        object.put("videodatarate", 0);
        //object.put("framerate", 30);
        object.put("framerate", 0);
        object.put("videocodecid", 7);
        object.put("audiodatarate", 0);
        //object.put("audiodatarate", 128);
        object.put("audiosamplerate", 48000);
        object.put("audiosamplesize", 16);
        object.put("audiochannels", 2);
        object.put("stereo", true);
        object.put("audiocodecid", 10);
        object.put("major_brand", "mp42");
        object.put("minor_version", "0");
        object.put("filesize", 0);
        RtmpHeader header = new RtmpHeader();
        header.fmt = 0;
        header.CSID = 4;
        header.timestamp = 0;
        header.msgType = RtmpHeader.MSG_TYPE_DATA;
        header.msgSID = 1;
        int totalSize = name.getSize() + name2.getSize() + object.getByteSize();
        header.msgLength = totalSize;
        header.write(out);
        // 默认是128时，需要分割msg为几个chunk
        /*int part = totalSize / 128;
        int p = 128 - name.getSize() - name2.getSize();
        byte[] data = new byte[129];
        byte[] objectBytes = object.getBytes();
        name.write(out);
        name2.write(out);
        System.arraycopy(objectBytes, 0, data, 0, p);
        out.write(data, 0, p);
        System.out.println("set data frame size is " +totalSize + "; " + p);
        for (int i = 0; i < part; i++) {
            int size = 128;
            if (i == part - 1) {
                size = objectBytes.length - p;
            }
            data[0] = (byte) 0xc4;
            System.arraycopy(objectBytes, p, data, 1, size);
            p += 128;
            out.write(data, 0, size + 1);
            System.out.println("set data frame size is " + p);
        }*/
        name.write(out);
        name2.write(out);
        object.write(out);
        out.flush();
    }

    public void setChunkSize(int size) throws IOException {
        sendControlMsg(size, RtmpHeader.MSG_TYPE_SET_CHUNK_SIZE);
    }

    public void setWindowAckSize(int size) throws IOException {
        sendControlMsg(size, RtmpHeader.MSG_TYPE_WINDOW_ACK_SIZE);
    }

    private void sendControlMsg(int num, int type) throws IOException {
        RtmpHeader header = new RtmpHeader();
        header.fmt = 0;
        header.CSID = 2;
        header.timestamp = 0;
        header.msgLength = 4;
        header.msgType = type;
        header.msgSID = 0;
        byte[] chunkSize = new byte[4];
        ByteUtil.writeInt(4, num, chunkSize, 0);
        header.write(out);
        out.write(chunkSize);
    }

    public void play(String streamName) throws IOException {
        List<String> list = new ArrayList<>();
        list.add(streamName);
        execute(0, 8, "play", 6.0, list);
    }

    public void setBufferLength() throws IOException {
        System.out.println("set buffer length.");
        RtmpHeader header = new RtmpHeader();
        header.fmt = 1;
        header.CSID = 2;
        header.timestamp = 1;
        header.msgLength = 10;
        header.msgType = 4;
        byte[] chunkSize = new byte[10];
        ByteUtil.writeInt(2, 3, chunkSize, 0);
        ByteUtil.writeInt(4, 1, chunkSize, 2);
        ByteUtil.writeInt(4, 3000, chunkSize, 6);
        header.write(out);
        out.write(chunkSize);
        out.flush();
    }
}
