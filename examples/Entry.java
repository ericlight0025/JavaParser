class Entry {
    static void start() {
        Entry.validate();
        Service.check();
        Log.write();
    }

    static void validate() {
        Service.normalize();
    }
}
