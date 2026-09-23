package com.javalight.app;

import com.javalight.logging.Log;
import com.javalight.service.Service;

public class Entry {
    public static void start() {
        Entry.validate();
        Service.check();
        Log.write();
    }

    static void validate() {
        Service.normalize();
    }
}
