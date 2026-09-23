package com.javalight.service;

import com.javalight.app.Entry;
import com.javalight.data.Repository;
import com.javalight.logging.Log;

public class Service {
    public static void check() {
        Repository.query();
        Service.notifyUser();
    }

    public static void normalize() {
        Repository.sanitize();
    }

    private static void notifyUser() {
        Log.audit();
        Entry.start();
    }
}
