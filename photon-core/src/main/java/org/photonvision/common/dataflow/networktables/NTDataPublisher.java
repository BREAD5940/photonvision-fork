/*
 * Copyright (C) Photon Vision.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.photonvision.common.dataflow.networktables;

import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTablesJNI;
import java.util.List;
import org.photonvision.common.configuration.ConfigManager;
import org.photonvision.common.dataflow.CVPipelineResultConsumer;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.common.networktables.NTTopicSet;
import org.photonvision.common.util.math.MathUtils;
import org.photonvision.targeting.PhotonPipelineResult;
import org.photonvision.vision.pipeline.result.CVPipelineResult;
import org.photonvision.vision.pipeline.result.CalibrationPipelineResult;
import org.photonvision.vision.target.TrackedTarget;

public class NTDataPublisher implements CVPipelineResultConsumer {
    private final Logger logger = new Logger(NTDataPublisher.class, LogGroup.General);

    private final NetworkTable rootTable = NetworkTablesManager.getInstance().kRootTable;

    private final NTTopicSet ts = new NTTopicSet();

    public NTDataPublisher(String cameraNickname) {
        updateCameraNickname(cameraNickname);
        ts.updateEntries();
    }

    private void removeEntries() {
        ts.removeEntries();
    }

    private void updateEntries() {
        ts.updateEntries();
    }

    public void updateCameraNickname(String newCameraNickname) {
        removeEntries();
        ts.subTable = rootTable.getSubTable(newCameraNickname);
        updateEntries();
    }

    @Override
    public void accept(CVPipelineResult result) {
        CVPipelineResult acceptedResult;
        if (result
                instanceof
                CalibrationPipelineResult) // If the data is from a calibration pipeline, override the list
            // of targets to be null to prevent the data from being sent and
            // continue to post blank/zero data to the network tables
            acceptedResult =
                    new CVPipelineResult(
                            result.sequenceID,
                            result.processingNanos,
                            result.fps,
                            List.of(),
                            result.inputAndOutputFrame);
        else acceptedResult = result;
        var now = NetworkTablesJNI.now();
        var captureMicros = MathUtils.nanosToMicros(result.getImageCaptureTimestampNanos());

        var offset = NetworkTablesManager.getInstance().getOffset();

        // Transform the metadata timestamps from the local nt::Now timebase to the Time Sync Server's
        // timebase
        var simplified =
                new PhotonPipelineResult(
                        acceptedResult.sequenceID,
                        captureMicros + offset,
                        now + offset,
                        NetworkTablesManager.getInstance().getTimeSinceLastPong(),
                        TrackedTarget.simpleFromTrackedTargets(acceptedResult.targets),
                        acceptedResult.multiTagResult);

        // random guess at size of the array
        ts.resultPublisher.set(simplified, 1024);
        if (ConfigManager.getInstance().getConfig().getNetworkConfig().shouldPublishProto) {
            ts.protoResultPublisher.set(simplified);
        }

        ts.latencyMillisEntry.set(acceptedResult.getLatencyMillis());
        ts.fpsEntry.set(acceptedResult.fps);
        ts.hasTargetEntry.set(acceptedResult.hasTargets());

        if (acceptedResult.hasTargets()) {
            var bestTarget = acceptedResult.targets.get(0);

            ts.targetPitchEntry.set(bestTarget.getPitch());
            ts.targetYawEntry.set(bestTarget.getYaw());
            ts.targetAreaEntry.set(bestTarget.getArea());
            ts.targetSkewEntry.set(bestTarget.getSkew());

            var pose = bestTarget.getBestCameraToTarget3d();
            ts.targetPoseEntry.set(pose);

            var targetOffsetPoint = bestTarget.getTargetOffsetPoint();
            ts.bestTargetPosX.set(targetOffsetPoint.x);
            ts.bestTargetPosY.set(targetOffsetPoint.y);
        } else {
            ts.targetPitchEntry.set(0);
            ts.targetYawEntry.set(0);
            ts.targetAreaEntry.set(0);
            ts.targetSkewEntry.set(0);
            ts.targetPoseEntry.set(new Transform3d());
            ts.bestTargetPosX.set(0);
            ts.bestTargetPosY.set(0);
        }

        ts.heartbeatPublisher.set(acceptedResult.sequenceID);

        // TODO...nt4... is this needed?
        rootTable.getInstance().flush();
    }
}
