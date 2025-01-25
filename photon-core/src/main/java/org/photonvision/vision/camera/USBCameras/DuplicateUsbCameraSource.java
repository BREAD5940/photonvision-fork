package org.photonvision.vision.camera.USBCameras;

import java.util.HashMap;
import java.util.function.Supplier;

import org.photonvision.common.configuration.CameraConfiguration;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.vision.camera.PVCameraInfo;
import org.photonvision.vision.camera.QuirkyCamera;
import org.photonvision.vision.frame.Frame;
import org.photonvision.vision.frame.FrameProvider;
import org.photonvision.vision.frame.FrameStaticProperties;
import org.photonvision.vision.frame.FrameThresholdType;
import org.photonvision.vision.frame.provider.CpuImageProcessor;
import org.photonvision.vision.frame.provider.USBFrameProvider;
import org.photonvision.vision.opencv.CVMat;
import org.photonvision.vision.opencv.ImageRotationMode;
import org.photonvision.vision.pipe.impl.HSVPipe.HSVParams;
import org.photonvision.vision.processes.VisionSource;
import org.photonvision.vision.processes.VisionSourceSettables;

import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.cscore.CvSink;
import edu.wpi.first.cscore.UsbCamera;
import edu.wpi.first.cscore.VideoMode;

public class DuplicateUsbCameraSource extends VisionSource {
    FrameProvider m_frameProvider;
    NullSettables m_settables;

    private static class DuplicateFrameProvider extends CpuImageProcessor {
        private CvSink m_sink;
        private Logger logger;
        private Supplier<FrameStaticProperties> m_propsSupplier;

        private DuplicateFrameProvider(UsbCamera camera, Supplier<FrameStaticProperties> propsSupplier) {
            super();

            var name = "DUPLICATE " + camera.getName();
            this.logger = new Logger(DuplicateFrameProvider.class, name, LogGroup.Camera);

            m_sink = new CvSink(name);
            m_sink.setSource(camera);
            CameraServer.addServer(m_sink);
            m_sink.setEnabled(true);

            m_propsSupplier = propsSupplier;

            onCameraConnected();
        }

        @Override
        public void release() {
            m_sink.close();
        }

        @Override
        protected CapturedFrame getInputMat() {
            var mat = new CVMat();
            // This is from wpi::Now, or WPIUtilJNI.now(). The epoch from grabFrame is uS
            // since
            // Hal::initialize was called
            // TODO - under the hood, this incurs an extra copy. We should avoid this, if we
            // can.
            long captureTimeNs = m_sink.grabFrame(mat.getMat(), 0.5) * 1000;

            if (captureTimeNs == 0) {
                var error = m_sink.getError();
                logger.error("Error grabbing image: " + error);
            }

            return new CapturedFrame(mat, m_propsSupplier.get(), captureTimeNs);
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean checkCameraConnected() {
            return true;
        }

        @Override
        public String getName() {
            return "foobar";
        }

    };

    private static class NullSettables extends VisionSourceSettables {
        private HashMap<Integer, VideoMode> m_videoModes = new HashMap<>();

        protected NullSettables(CameraConfiguration configuration, HashMap<Integer, VideoMode> modes) {
            super(configuration);
            this.m_videoModes = modes;
        }

        @Override
        public void setExposureRaw(double exposureRaw) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'setExposureRaw'");
        }

        @Override
        public double getMinExposureRaw() {
            return 0;
        }

        @Override
        public double getMaxExposureRaw() {
            return 1;
        }

        @Override
        public void setAutoExposure(boolean cameraAutoExposure) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'setAutoExposure'");
        }

        @Override
        public void setWhiteBalanceTemp(double temp) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'setWhiteBalanceTemp'");
        }

        @Override
        public void setAutoWhiteBalance(boolean autowb) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'setAutoWhiteBalance'");
        }

        @Override
        public void setBrightness(int brightness) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'setBrightness'");
        }

        @Override
        public void setGain(int gain) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'setGain'");
        }

        @Override
        public VideoMode getCurrentVideoMode() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'getCurrentVideoMode'");
        }

        @Override
        protected void setVideoModeInternal(VideoMode videoMode) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'setVideoModeInternal'");
        }

        @Override
        public HashMap<Integer, VideoMode> getAllVideoModes() {
            return m_videoModes;
        }

        @Override
        public double getMinWhiteBalanceTemp() {
            return 3;
        }

        @Override
        public double getMaxWhiteBalanceTemp() {
            return 4;
        }

    }

    public DuplicateUsbCameraSource(USBCameraSource source) {
        super(new CameraConfiguration(
                new PVCameraInfo.PVDuplicateCameraInfo(source.getCameraConfiguration().uniqueName)));
        cameraConfiguration.cameraQuirks = new QuirkyCamera("foobar", 0, 0, null, new HashMap<>());

        m_frameProvider = new DuplicateFrameProvider(source.getCamera(),
                source.getSettables()::getFrameStaticProperties);
        
        if (source.getCamera().isConnected()) {
            m_settables = new NullSettables(cameraConfiguration, 
                source.getSettables().getAllVideoModes());
        } else {
            throw new UnsupportedOperationException("Unimplemented else case");
        }
    }

    @Override
    public void release() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'release'");
    }

    @Override
    public FrameProvider getFrameProvider() {
        return m_frameProvider;
    }

    @Override
    public VisionSourceSettables getSettables() {
        return m_settables;
    }

    @Override
    public boolean isVendorCamera() {
        return false;
    }

    @Override
    public boolean hasLEDs() {
        return false;
    }

    @Override
    public void remakeSettables() {
        // nothing to do
    }
}
