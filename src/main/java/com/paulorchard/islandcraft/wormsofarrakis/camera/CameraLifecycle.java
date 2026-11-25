package com.paulorchard.islandcraft.wormsofarrakis.camera;

/**
 * Prompt 16 part 3: every way a death camera ends, and what each owes the player. Engine-free. The entity is always discarded; the player's own view is sent back
 * ({@code ClientboundSetCameraPacket(player)}) whenever the same connected player object is still there: on a respawn the player object is new (the client resets its view by itself), on a
 * log-off nobody is connected, and a packet that could not be sent cannot be sent again.
 */
public final class CameraLifecycle {
    private CameraLifecycle() {}

    public enum EndPath {
        RESPAWN, LOGOFF, PLAYER_GONE, SEND_FAILED, DIMENSION, TIMEOUT, SERVER_STOP, EVENT_END, STOP_COMMAND, PREVIEW_OVER, ENTITY_REMOVED, NEW_CAMERA
    }

    public static boolean sendsViewBack(EndPath p) {
        return switch (p) {
            case RESPAWN, LOGOFF, PLAYER_GONE, SEND_FAILED -> false;
            default -> true;
        };
    }
}
