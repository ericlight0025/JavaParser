class Service {
    static void check() {
        Repository.query();
        Service.notifyUser();
    }

    static void normalize() {
        Repository.sanitize();
    }

    static void notifyUser() {
        Log.audit();
        Entry.start();
    }
}
