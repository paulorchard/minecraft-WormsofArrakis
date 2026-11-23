package com.paulorchard.islandcraft.wormsofarrakis.test;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;

/**
 * Animation controllers of the test worm. Experiment 5: the "mouth" controller plays the GeckoLib animation
 * (assets/islandcraft_wormsofarrakis/geckolib/animations/arrakis_worm_test.animation.json) that matches the spec's
 * mode: open (holds open), close (holds closed), cycle (loops open and closed). Other kinds play nothing.
 */
final class TestControllers {
    private TestControllers() {}

    static void register(TestWormEntity entity, AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<TestWormEntity>("mouth", 0, state -> {
            WormSpec spec = WormSpec.parse(state.animatable().getSpecString());
            if (spec.kind().equals("move")) {
                return state.setAndContinue(RawAnimation.begin().thenPlayAndHold(spec.p(8, 0) > 0 ? "mouth_open" : "mouth_close"));
            }
            if (!spec.kind().equals("mouth")) return PlayState.STOP;
            return switch (spec.mode()) {
                case "open" -> state.setAndContinue(RawAnimation.begin().thenPlayAndHold("mouth_open"));
                case "close" -> state.setAndContinue(RawAnimation.begin().thenPlayAndHold("mouth_close"));
                default -> state.setAndContinue(RawAnimation.begin().thenLoop("mouth_cycle"));
            };
        }));
    }
}
