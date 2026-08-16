package first.wildfires.client.celestial;

/** Reserved capability boundary for a future, explicit Photon compatibility implementation. */
final class PhotonSkyBridge {

    private PhotonSkyBridge() {
    }

    static boolean recognizesActivePack() {
        return false;
    }

    static boolean isImplemented() {
        return false;
    }
}
