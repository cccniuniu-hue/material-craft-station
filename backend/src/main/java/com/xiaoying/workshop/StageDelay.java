package com.xiaoying.workshop;

import org.springframework.stereotype.Component;

@Component
public class StageDelay {
    public void await(int milliseconds) throws InterruptedException { Thread.sleep(milliseconds); }
}
