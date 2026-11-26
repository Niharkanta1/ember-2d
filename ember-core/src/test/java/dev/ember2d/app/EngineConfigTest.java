package dev.ember2d.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;

class EngineConfigTest {

    @Test
    void defaultsAreValid() {
        assertThatCode(() -> new EngineConfig().validate()).doesNotThrowAnyException();
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        EngineConfig config = new EngineConfig();
        config.virtualWidth = 0;
        config.virtualHeight = -1;
        config.pixelsPerUnit = Float.NaN;
        config.fixedStep = 0.5f;
        config.maxCatchUpSteps = 0;
        config.assetManifest = " ";

        EngineConfigException e = catchThrowableOfType(EngineConfigException.class, config::validate);

        assertThat(e.problems()).containsExactly(
                "virtualWidth must be > 0 (was 0)",
                "virtualHeight must be > 0 (was -1)",
                "pixelsPerUnit must be a finite number > 0 (was NaN)",
                "fixedStep must be > 0 and <= 0.25 seconds (was 0.5)",
                "maxCatchUpSteps must be between 1 and 60 (was 0)",
                "assetManifest must be a non-empty path (was \" \")");
        assertThat(e.getMessage()).startsWith("Invalid EngineConfig:").contains("virtualWidth").contains("assetManifest");
    }

    @Test
    void boundaryValuesAreAccepted() {
        EngineConfig config = new EngineConfig();
        config.fixedStep = EngineConfig.MAX_FIXED_STEP;
        config.maxCatchUpSteps = EngineConfig.MAX_CATCH_UP_LIMIT;

        assertThatCode(config::validate).doesNotThrowAnyException();
    }

    @Test
    void infinitePixelsPerUnitAndNullManifestAreRejected() {
        EngineConfig config = new EngineConfig();
        config.pixelsPerUnit = Float.POSITIVE_INFINITY;
        config.assetManifest = null;

        assertThat(catchThrowableOfType(EngineConfigException.class, config::validate).problems()).containsExactly(
                "pixelsPerUnit must be a finite number > 0 (was Infinity)",
                "assetManifest must be a non-empty path (was null)");
    }

    @Test
    void hotReloadNeedsTheFolderItWatches() {
        EngineConfig config = new EngineConfig();
        config.devMode = true;
        config.assetWatcher = new dev.ember2d.assets.AssetWatcher() {
            @Override
            public void drainChanges(java.util.function.Consumer<String> changedPath) {
            }

            @Override
            public void close() {
            }
        };
        config.assetsDir = " ";
        EngineConfigException e = catchThrowableOfType(EngineConfigException.class, config::validate);
        assertThat(e.problems()).containsExactly("assetsDir must be null or an absolute folder (was \" \")");

        config.assetsDir = null;
        e = catchThrowableOfType(EngineConfigException.class, config::validate);
        assertThat(e.problems()).singleElement().asString().startsWith("assetWatcher needs assetsDir");

        config.devMode = false;
        assertThatCode(config::validate).as("ignored outside dev builds").doesNotThrowAnyException();
    }
}
