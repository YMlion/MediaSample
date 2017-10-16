package com.ymlion.rtmp;

import com.ymlion.rtmp.bean.CommandObject;
import com.ymlion.rtmp.bean.RNull;
import com.ymlion.rtmp.bean.RNumber;
import com.ymlion.rtmp.bean.RObject;
import com.ymlion.rtmp.bean.RString;
import com.ymlion.rtmp.bean.RtmpHeader;
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

    public void connect(String app, String tcUrl, String streamName) throws IOException {
        System.out.println("rtmp start connecting...");
        RString name = new RString("connect", false);
        byte[] nameBytes = name.getBytes();
        byte[] number = new RNumber(1.0).getBytes();
        CommandObject object = new CommandObject();
        object.put("app", app);
        object.put("type", "nonprivate");
        object.put("tcUrl", tcUrl);
        byte[] objBytes = object.getBytes();
        RtmpHeader header = new RtmpHeader();
        header.fmt = 0;
        header.CSID = 3;
        header.timestamp = 0;
        header.msgType = 20;
        header.msgSID = 0;
        header.msgLength = nameBytes.length + number.length + objBytes.length;
        header.write(out);
        out.write(nameBytes);
        out.write(number);
        out.write(objBytes);
        out.flush();
        System.out.println("rtmp connected.");
        execute("releaseStream", 2.0, streamName);
        execute("FCPublish", 2.0, streamName);
        execute("createStream", 2.0, (List<String>) null);
        execute("_checkbw", 5.0, (List<String>) null);
    }

    public void publish(String streamName) throws IOException {
        List<String> list = new ArrayList<>();
        list.add("live");
        list.add("live");
        execute("publish", 6.0, list);
    }

    private void execute(String command, double number, String string) throws IOException {
        List<String> strings = new ArrayList<>();
        strings.add(string);
        execute(command, number, strings);
    }

    private void execute(String command, double number, List<String> strings) throws IOException {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
        header.fmt = 1;
        header.CSID = 3;
        header.timestamp = 0;
        header.msgType = 20;
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
}
