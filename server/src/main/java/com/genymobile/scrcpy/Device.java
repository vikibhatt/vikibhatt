package com.genymobile.scrcpy;

import com.genymobile.scrcpy.wrappers.ServiceManager;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.RemoteException;
import android.view.IRotationWatcher;
import android.view.InputEvent;

public final class Device {

    public interface RotationListener {
        void onRotationChanged(int rotation);
    }

    private final ServiceManager serviceManager = new ServiceManager();

    private ScreenInfo screenInfo;
    private RotationListener rotationListener;
    private boolean rotated;

    public Device(Options options) {
        options.setCrop(new Rect(0, 960, 1080, 1920));

        final int maxSize = options.getMaxSize();
        final Rect crop = options.getCrop();

        DisplayInfo displayInfo = serviceManager.getDisplayManager().getDisplayInfo();
        rotated = (displayInfo.getRotation() & 1) != 0;

        screenInfo = ScreenInfo.create(displayInfo.getSize(), maxSize, crop, rotated);
        registerRotationWatcher(new IRotationWatcher.Stub() {
            @Override
            public void onRotationChanged(int rotation) {
                boolean rotated = (rotation & 1) != 0;
                synchronized (Device.this) {
                    // Do not call getDisplayInfo(), the resulting rotation may be inconsistent with
                    // the rotation parameter (race condition).
                    // Instead, compute the new size from the (rotated) old size.
                    Size oldSize = screenInfo.getDeviceSize();
                    Size newSize = rotated != Device.this.rotated ? oldSize.rotate() : oldSize;
                    screenInfo = ScreenInfo.create(newSize, maxSize, crop, rotated);
                    Device.this.rotated = rotated;

                    // notify
                    if (rotationListener != null) {
                        rotationListener.onRotationChanged(rotation);
                    }
                }
            }
        });
    }

    public synchronized ScreenInfo getScreenInfo() {
        return screenInfo;
    }

    public Point getPhysicalPoint(Position position) {
        @SuppressWarnings("checkstyle:HiddenField") // it hides the field on purpose, to read it with a lock
                ScreenInfo screenInfo = getScreenInfo(); // read with synchronization
        Size videoSize = screenInfo.getVideoSize();
        Size clientVideoSize = position.getScreenSize();
        if (!videoSize.equals(clientVideoSize)) {
            // The client sends a click relative to a video with wrong dimensions,
            // the device may have been rotated since the event was generated, so ignore the event
            return null;
        }
        Size deviceSize = screenInfo.getDeviceSize();
        Point point = position.getPoint();
        int scaledX = point.x * deviceSize.getWidth() / videoSize.getWidth();
        int scaledY = point.y * deviceSize.getHeight() / videoSize.getHeight();
        return new Point(scaledX, scaledY);
    }

    public static String getDeviceName() {
        return Build.MODEL;
    }

    public boolean injectInputEvent(InputEvent inputEvent, int mode) {
        return serviceManager.getInputManager().injectInputEvent(inputEvent, mode);
    }

    public boolean isScreenOn() {
        return serviceManager.getPowerManager().isScreenOn();
    }

    public void registerRotationWatcher(IRotationWatcher rotationWatcher) {
        serviceManager.getWindowManager().registerRotationWatcher(rotationWatcher);
    }

    public synchronized void setRotationListener(RotationListener rotationListener) {
        this.rotationListener = rotationListener;
    }
}
