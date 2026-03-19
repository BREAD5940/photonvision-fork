/*
 * MIT License
 *
 * Copyright (c) PhotonVision
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.photonvision;

import edu.wpi.first.hal.FRCNetComm.tResourceType;
import edu.wpi.first.hal.HAL;
import edu.wpi.first.networktables.IntegerSubscriber;
import edu.wpi.first.networktables.MultiSubscriber;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.PubSubOption;
import edu.wpi.first.wpilibj.Alert;
import edu.wpi.first.wpilibj.Alert.AlertType;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Timer;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Core;
import org.photonvision.common.networktables.PacketSubscriber;
import org.photonvision.targeting.PhotonPipelineResult;
import org.photonvision.timesync.TimeSyncSingleton;

/** Represents a camera that is connected to PhotonVision. */
public class PhotonCamera implements AutoCloseable {
    private static int InstanceCount = 1;
    public static final String kTableName = "photonvision";
    private static final String PHOTON_ALERT_GROUP = "PhotonAlerts";

    private final NetworkTable cameraTable;
    PacketSubscriber<PhotonPipelineResult> resultSubscriber;
    IntegerSubscriber heartbeatSubscriber;
    MultiSubscriber topicNameSubscriber;
    NetworkTable rootPhotonTable;

    @Override
    public void close() {
        resultSubscriber.close();
        heartbeatSubscriber.close();
        topicNameSubscriber.close();
    }

    private final String path;
    private final String name;

    private static boolean VERSION_CHECK_ENABLED = true;
    private static long VERSION_CHECK_INTERVAL = 5;
    double lastVersionCheckTime = 0;

    private long prevHeartbeatValue = -1;
    private double prevHeartbeatChangeTime = 0;
    private static final double HEARTBEAT_DEBOUNCE_SEC = 0.5;

    double prevTimeSyncWarnTime = 0;
    private static final double WARN_DEBOUNCE_SEC = 5;

    private final Alert disconnectAlert;
    private final Alert timesyncAlert;

    /**
     * Sets whether or not coprocessor version checks will occur. Setting this to true will silence
     * all console warnings about coproccessor connection, so be careful when enabling this and ensure
     * all your coprocessors are communicating to the robot properly and everything has matching
     * versions.
     *
     * @param enabled Whether or not to enable coprocessor version checks
     */
    public static void setVersionCheckEnabled(boolean enabled) {
        VERSION_CHECK_ENABLED = enabled;
    }

    /**
     * Constructs a PhotonCamera from a root table.
     *
     * @param instance The NetworkTableInstance to pull data from. This can be a custom instance in
     *     simulation, but should *usually* be the default NTInstance from
     *     NetworkTableInstance::getDefault
     * @param cameraName The name of the camera, as seen in the UI.
     */
    public PhotonCamera(NetworkTableInstance instance, String cameraName) {
        name = cameraName;
        disconnectAlert =
                new Alert(
                        PHOTON_ALERT_GROUP, "PhotonCamera '" + name + "' is disconnected.", AlertType.kWarning);
        timesyncAlert = new Alert(PHOTON_ALERT_GROUP, "", AlertType.kWarning);
        rootPhotonTable = instance.getTable(kTableName);
        this.cameraTable = rootPhotonTable.getSubTable(cameraName);
        path = cameraTable.getPath();
        var rawBytesEntry =
                cameraTable
                        .getRawTopic("rawBytes")
                        .subscribe(
                                PhotonPipelineResult.photonStruct.getTypeString(),
                                new byte[0],
                                PubSubOption.periodic(0.01),
                                PubSubOption.sendAll(true),
                                PubSubOption.pollStorage(20));
        resultSubscriber = new PacketSubscriber<>(rawBytesEntry, PhotonPipelineResult.photonStruct);
        heartbeatSubscriber = cameraTable.getIntegerTopic("heartbeat").subscribe(-1);

        // Existing is enough to make this multisubscriber do its thing
        topicNameSubscriber =
                new MultiSubscriber(
                        instance, new String[] {"/photonvision/"}, PubSubOption.topicsOnly(true));

        HAL.report(tResourceType.kResourceType_PhotonCamera, InstanceCount);
        InstanceCount++;

        // HACK - start a TimeSyncServer, if we haven't yet.
        TimeSyncSingleton.load();

        // HACK - check if things are compatible
        verifyDependencies();
    }

    static void verifyDependencies() {
        // spotless:off
        if (!Core.VERSION.equals(PhotonVersion.opencvTargetVersion)) {
            String bfw = """




                    >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\s
                    >>> !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\s
                    >>>                                          \s
                    >>> You are running an incompatible version  \s
                    >>> of PhotonVision !                        \s
                    >>>                                          \s
                    >>> PhotonLib """
                    + PhotonVersion.versionString
                    + " is built for OpenCV "
                    + PhotonVersion.opencvTargetVersion
                    + "\n"
                    + ">>> but you are using OpenCV "
                    + Core.VERSION
                    + """
                    \n>>>                                          \s
                    >>> This is neither tested nor supported.    \s
                    >>> You MUST update WPILib, PhotonLib, or both.
                    >>> Check `./gradlew dependencies` and ensure\s
                    >>> all mentions of OpenCV match the version \s
                    >>> that PhotonLib was built for. If you find a
                    >>> a mismatched version in a dependency, you\s
                    >>> must take steps to update the version of \s
                    >>> OpenCV used in that dependency. If you do\s
                    >>> not control that dependency and an updated\s
                    >>> version is not available, contact the    \s
                    >>> developers of that dependency.           \s
                    >>>                                          \s
                    >>> Your code will now crash.                \s
                    >>> We hope your day gets better.            \s
                    >>>                                          \s
                    >>> !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\s
                    >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\s
                    """;
            // spotless:on

            DriverStation.reportWarning(bfw, false);
            DriverStation.reportError(bfw, false);
            throw new UnsupportedOperationException(bfw);
        }
    }

    /**
     * Constructs a PhotonCamera from the name of the camera.
     *
     * @param cameraName The nickname of the camera (found in the PhotonVision UI).
     */
    public PhotonCamera(String cameraName) {
        this(NetworkTableInstance.getDefault(), cameraName);
    }

    /**
     * The list of pipeline results sent by PhotonVision since the last call to getAllUnreadResults().
     * Calling this function clears the internal FIFO queue, and multiple calls to
     * getAllUnreadResults() will return different (potentially empty) result arrays. Be careful to
     * call this exactly ONCE per loop of your robot code! FIFO depth is limited to 20 changes, so
     * make sure to call this frequently enough to avoid old results being discarded, too!
     *
     * @return The list of pipeline results
     */
    public List<PhotonPipelineResult> getAllUnreadResults() {
        verifyVersion();
        updateDisconnectAlert();

        // Grab the latest results. We don't care about the timestamps from NT - the metadata header has
        // this, latency compensated by the Time Sync Client
        var changes = resultSubscriber.getAllChanges();
        List<PhotonPipelineResult> ret = new ArrayList<>(changes.size());
        for (var c : changes) {
            var result = c.value;
            checkTimeSyncOrWarn(result);
            ret.add(result);
        }

        return ret;
    }

    /**
     * Returns the latest pipeline result. This is simply the most recent result Received via NT.
     * Calling this multiple times will always return the most recent result.
     *
     * <p>Replaced by {@link #getAllUnreadResults()} over getLatestResult, as this function can miss
     * results, or provide duplicate ones!
     *
     * @return The latest pipeline result
     */
    @Deprecated(since = "2024", forRemoval = true)
    public PhotonPipelineResult getLatestResult() {
        verifyVersion();
        updateDisconnectAlert();

        // Grab the latest result. We don't care about the timestamp from NT - the metadata header has
        // this, latency compensated by the Time Sync Client
        var ret = resultSubscriber.get();

        if (ret.timestamp == 0) return new PhotonPipelineResult();

        var result = ret.value;

        checkTimeSyncOrWarn(result);

        return result;
    }

    private void updateDisconnectAlert() {
        disconnectAlert.set(!isConnected());
    }

    private void checkTimeSyncOrWarn(PhotonPipelineResult result) {
        if (result.metadata.timeSinceLastPong > 5L * 1000000L) {
            String warningText =
                    "PhotonVision coprocessor at path "
                            + path
                            + " is not connected to the TimeSyncServer? It's been "
                            + String.format("%.2f", result.metadata.timeSinceLastPong / 1e6)
                            + "s since the coprocessor last heard a pong.";

            timesyncAlert.setText(warningText);
            timesyncAlert.set(true);

            if (Timer.getFPGATimestamp() > (prevTimeSyncWarnTime + WARN_DEBOUNCE_SEC)) {
                prevTimeSyncWarnTime = Timer.getFPGATimestamp();

                DriverStation.reportWarning(
                        warningText
                                + "\n\nCheck /photonvision/.timesync/{COPROCESSOR_HOSTNAME} for more information.",
                        false);
            }
        } else {
            // Got a valid packet, reset the last time
            prevTimeSyncWarnTime = 0;
            timesyncAlert.set(false);
        }
    }

    /**
     * Returns the name of the camera. This will return the same value that was given to the
     * constructor as cameraName.
     *
     * @return The name of the camera.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns whether the camera is connected and actively returning new data. Connection status is
     * debounced.
     *
     * @return True if the camera is actively sending frame data, false otherwise.
     */
    public boolean isConnected() {
        var curHeartbeat = heartbeatSubscriber.get();
        var now = Timer.getFPGATimestamp();

        if (curHeartbeat < 0) {
            // we have never heard from the camera
            return false;
        }

        if (curHeartbeat != prevHeartbeatValue) {
            // New heartbeat value from the coprocessor
            prevHeartbeatChangeTime = now;
            prevHeartbeatValue = curHeartbeat;
        }

        return (now - prevHeartbeatChangeTime) < HEARTBEAT_DEBOUNCE_SEC;
    }

    /**
     * Gets the NetworkTable representing this camera's subtable. You probably don't ever need to call
     * this.
     */
    public final NetworkTable getCameraTable() {
        return cameraTable;
    }

    void verifyVersion() {
        if (!VERSION_CHECK_ENABLED) return;

        if ((Timer.getFPGATimestamp() - lastVersionCheckTime) < VERSION_CHECK_INTERVAL) return;
        lastVersionCheckTime = Timer.getFPGATimestamp();

        // Heartbeat entry is assumed to always be present. If it's not present, we
        // assume that a camera with that name was never connected in the first place.
        if (!heartbeatSubscriber.exists()) {
            var cameraNames = getTablesThatLookLikePhotonCameras();
            if (cameraNames.isEmpty()) {
                DriverStation.reportError(
                        "Could not find **any** PhotonVision coprocessors on NetworkTables. Double check that PhotonVision is running, and that your camera is connected!",
                        false);
            } else {
                DriverStation.reportError(
                        "PhotonVision coprocessor at path "
                                + path
                                + " not found on NetworkTables. Double check that your camera names match!",
                        true);

                var cameraNameStr = new StringBuilder();
                for (var c : cameraNames) {
                    cameraNameStr.append(" ==> ");
                    cameraNameStr.append(c);
                    cameraNameStr.append("\n");
                }

                DriverStation.reportError(
                        "Found the following PhotonVision cameras on NetworkTables:\n"
                                + cameraNameStr.toString(),
                        false);
            }
        }
        // Check for connection status. Warn if disconnected.
        else if (!isConnected()) {
            DriverStation.reportWarning(
                    "PhotonVision coprocessor at path " + path + " is not sending new data.", false);
        }

        // Check mdef UUID
        String local_uuid = PhotonPipelineResult.photonStruct.getInterfaceUUID();
        String remote_uuid = resultSubscriber.getInterfaceUUID();

        if (remote_uuid == null || remote_uuid.isEmpty()) {
            // not connected yet?
            DriverStation.reportWarning(
                    "PhotonVision coprocessor at path "
                            + path
                            + " has not reported a message interface UUID - is your coprocessor's camera started?",
                    false);
        } else if (!local_uuid.equals(remote_uuid)) {
            // Error on a verified version mismatch
            // But stay silent otherwise

            // spotless:off
            String bfw = """





                    >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
                    >>> !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                    >>>                                         \s
                    >>> You are running an incompatible version \s
                    >>> of PhotonVision on your coprocessor!    \s
                    >>>                                         \s
                    >>> This is neither tested nor supported.   \s
                    >>> You MUST update PhotonVision,           \s
                    >>> PhotonLib, or both.                     \s
                    >>>                                         \s
                    >>> Your code will now crash.               \s
                    >>> We hope your day gets better.           \s
                    >>>                                         \s
                    >>> !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                    >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
                    """;
            // spotless:on

            DriverStation.reportWarning(bfw, false);
            var versionMismatchMessage =
                    "Photon version "
                            + PhotonVersion.versionString
                            + " (message definition version "
                            + local_uuid
                            + ")"
                            + " does not match coprocessor (message definition version "
                            + remote_uuid
                            + ")"
                            + "!";
            DriverStation.reportError(versionMismatchMessage, false);
            throw new UnsupportedOperationException(versionMismatchMessage);
        }
    }

    private List<String> getTablesThatLookLikePhotonCameras() {
        return rootPhotonTable.getSubTables().stream()
                .filter(
                        it -> {
                            return rootPhotonTable.getSubTable(it).getEntry("rawBytes").exists();
                        })
                .toList();
    }
}
