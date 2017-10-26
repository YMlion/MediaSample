package com.ymlion.rtmp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Created by YMlion on 2017/10/12.
 */

public class HandShake {
    private static final int HAND_SHAKE_LENGTH = 1536;
    private BufferedOutputStream outputStream;
    private BufferedInputStream inputStream;
    private byte[] s1;

    public HandShake(BufferedOutputStream outputStream, BufferedInputStream inputStream) {
        this.outputStream = outputStream;
        this.inputStream = inputStream;
    }

    public void handShake() throws IOException {
        writeC0();
        writeC1();
        outputStream.flush();
        readS0();
        readS1();
        writeC2();
        readS2();
    }

    private void writeC0() throws IOException {
        System.out.println("shake hand : write C0");
        outputStream.write(3);
    }

    private void writeC1() throws IOException {
        System.out.println("shake hand : write C1");
        byte[] timeBytes = new byte[4];
        int time = (int) (System.currentTimeMillis() / 1000);
        for (int i = 0; i < 4; i++) {
            timeBytes[i] = (byte) (time >>> ((3 - i) * 8));
        }
        byte[] zero = new byte[] { 0, 0, 0, 0 };
        byte[] randomBytes = new byte[1528];
        Random random = new Random();
        random.nextBytes(randomBytes);
        byte[] c1 = new byte[HAND_SHAKE_LENGTH];
        System.arraycopy(timeBytes, 0, c1, 0, 4);
        System.arraycopy(zero, 0, c1, 4, 4);
        System.arraycopy(randomBytes, 0, c1, 8, 1528);
        outputStream.write(c1);
    }

    private void readS0() throws IOException {
        System.out.println("shake hand : read S0");
        int v = inputStream.read() & 0xff;
        if (v != 3) {
            System.out.println("server rtmp version is " + v);
        }
    }

    private void readS1() throws IOException {
        System.out.println("shake hand : read S1");
        s1 = new byte[HAND_SHAKE_LENGTH];
        int total = 0;
        do {
            total += inputStream.read(s1, total, HAND_SHAKE_LENGTH - total);
        } while (total < HAND_SHAKE_LENGTH);
        System.out.println("read s1 total size is " + total);
    }

    private void readS2() throws IOException {
        System.out.println("shake hand : read S2");
        byte[] s2 = new byte[HAND_SHAKE_LENGTH];
        int total = 0;
        do {
            total += inputStream.read(s2, total, HAND_SHAKE_LENGTH - total);
        } while (total < HAND_SHAKE_LENGTH);
        System.out.println("read s2 total size is " + total);
    }

    private void writeC2() throws IOException {
        System.out.println("shake hand : write C2 " + s1.length);
        outputStream.write(s1);
    }
}