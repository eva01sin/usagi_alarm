package com.usagi.alarm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ScreenLaunchPolicyTest {
    @Test public void opensWhenAllowedAndNotVisible() {
        assertTrue(ScreenLaunchPolicy.shouldOpenDirectly(true, false));
    }

    @Test public void keepsNotificationOnlyWithoutPermission() {
        assertFalse(ScreenLaunchPolicy.shouldOpenDirectly(false, false));
        assertFalse(ScreenLaunchPolicy.shouldOpenDirectly(false, true));
    }

    @Test public void doesNotOpenTwiceWhenScreenAlreadyVisible() {
        assertFalse(ScreenLaunchPolicy.shouldOpenDirectly(true, true));
    }
}
