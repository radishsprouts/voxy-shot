package me.voxyshot.compat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class VoxyShotFrexConsumer implements Consumer<Function<String, Consumer<Boolean>>> {
    private static final ConcurrentHashMap<String, Consumer<Boolean>> CONTROLLERS = new ConcurrentHashMap<>();

    @Override
    public void accept(Function<String, Consumer<Boolean>> provider) {
        Consumer<Boolean> setter = provider.apply("voxyshot");
        if (setter != null) {
            CONTROLLERS.put("voxyshot", setter);
        }
    }

    public static void setFlawlessFramesActive(boolean active) {
        for (Consumer<Boolean> controller : CONTROLLERS.values()) {
            try {
                controller.accept(active);
            } catch (Exception ignored) {
            }
        }
    }
}
