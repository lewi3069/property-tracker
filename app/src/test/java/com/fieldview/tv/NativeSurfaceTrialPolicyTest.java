package com.fieldview.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeSurfaceTrialPolicyTest {
    @Test public void ordinaryBuildRetainsItsExistingOutputOnTheAffectedDevice() {
        assertFalse(NativeSurfaceTrialPolicy.enabled(false, true, "Amazon", "AFTKM", 30));
    }
    @Test public void explicitTrialAllowsTheObservedFireTvProfile() {
        assertTrue(NativeSurfaceTrialPolicy.enabled(true, true, "Amazon", "AFTKM", 30));
    }
    @Test public void olderAndUntestedAndroidVersionsRetainTheirExistingOutput() {
        for (int sdk : new int[]{22, 23, 26, 29, 31, 35, 36})
            assertFalse("API " + sdk, NativeSurfaceTrialPolicy.enabled(true, true, "Amazon", "AFTKM", sdk));
    }
    @Test public void otherTvModelsAndManufacturersRetainTheirExistingOutput() {
        for (String model : new String[]{"AFTT", "AFTMM", "SHIELD Android TV", "unknown", null})
            assertFalse(NativeSurfaceTrialPolicy.enabled(true, true, "Amazon", model, 30));
        assertFalse(NativeSurfaceTrialPolicy.enabled(true, true, "NVIDIA", "AFTKM", 30));
        assertFalse(NativeSurfaceTrialPolicy.enabled(true, true, null, "AFTKM", 30));
    }
    @Test public void phoneModeCannotEnableTheNativeSurfaceExperiment() {
        assertFalse(NativeSurfaceTrialPolicy.enabled(true, false, "Amazon", "AFTKM", 30));
    }
}
