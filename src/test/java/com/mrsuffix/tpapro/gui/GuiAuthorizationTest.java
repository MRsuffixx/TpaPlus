package com.mrsuffix.tpapro.gui;

import com.mrsuffix.tpapro.request.RequestType;
import com.mrsuffix.tpapro.request.TeleportRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GuiAuthorizationTest {
    @Test void onlyTheRequestTargetCanAcceptOrDenyFromGui() {
        UUID sender = UUID.randomUUID(), target = UUID.randomUUID();
        TeleportRequest request = new TeleportRequest(UUID.randomUUID(), sender, target, RequestType.TPA,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Map.of());
        assertThat(GuiManager.canActOnRequest(target, request)).isTrue();
        assertThat(GuiManager.canActOnRequest(sender, request)).isFalse();
        assertThat(GuiManager.canActOnRequest(UUID.randomUUID(), request)).isFalse();
    }
}
