package me.cvhc.lolightreceiver;

import org.apache.commons.math3.util.ArithmeticUtils;

class Global {
    static final int BIT_ZERO_FREQ = 30;
    static final int BIT_ONE_FREQ = 20;
    static final int ORIGINAL_VIDEO_FPS = ArithmeticUtils.lcm(BIT_ONE_FREQ, BIT_ZERO_FREQ);
    static final int DATARATE = ArithmeticUtils.gcd(BIT_ONE_FREQ, BIT_ZERO_FREQ);
    static final int INTERMEDIATE_DATA_FPS = 4 * ORIGINAL_VIDEO_FPS;
}
