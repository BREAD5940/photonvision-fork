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

package org.photonvision.vision.frame;

import org.photonvision.common.util.math.MathUtils;
import org.photonvision.vision.opencv.CVMat;
import org.photonvision.vision.opencv.Releasable;

import edu.wpi.first.util.WPIUtilJNI;

public class Frame implements Releasable {
    public final long sequenceID;
    public final long timestampNanos;
    public final long timestampNanosSystem;

    // Frame should at _least_ contain the thresholded frame, and sometimes the color image
    public final CVMat colorImage;
    public final CVMat processedImage;
    public final FrameThresholdType type;

    public final FrameStaticProperties frameStaticProperties;

    public Frame(
            long sequenceID,
            CVMat color,
            CVMat processed,
            FrameThresholdType type,
            long timestampNanos,
            long timestampNanosSystem,
            FrameStaticProperties frameStaticProperties) {
        this.sequenceID = sequenceID;
        this.colorImage = color;
        this.processedImage = processed;
        this.type = type;
        this.timestampNanos = timestampNanos;
        this.timestampNanosSystem = timestampNanosSystem;
        this.frameStaticProperties = frameStaticProperties;
    }

    public Frame(
            long sequenceID,
            CVMat color,
            CVMat processed,
            FrameThresholdType processType,
            FrameStaticProperties frameStaticProperties) {
        this(sequenceID, color, processed, processType, MathUtils.wpiNanoTime(), WPIUtilJNI.getSystemTime(), frameStaticProperties);
    }

    public Frame() {
        this(
                -1,
                new CVMat(),
                new CVMat(),
                FrameThresholdType.NONE,
                MathUtils.wpiNanoTime(),
                WPIUtilJNI.getSystemTime(),
                new FrameStaticProperties(0, 0, 0, null));
    }

    public void copyTo(Frame destFrame) {
        colorImage.getMat().copyTo(destFrame.colorImage.getMat());
        processedImage.getMat().copyTo(destFrame.processedImage.getMat());
    }

    @Override
    public void release() {
        colorImage.release();
        processedImage.release();
    }
}
