package com.example.phoneapp.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAccessTest {
    @Test fun legacyStorageAccessWorksThroughAndroid12() {
        for (api in 26..32) {
            assertEquals(PhotoAccess.FULL, PhotoAccess.fromPermissions(api, false, false, true))
            assertEquals(PhotoAccess.NONE, PhotoAccess.fromPermissions(api, true, true, false))
        }
    }

    @Test fun android13RequiresImagesPermission() {
        assertEquals(PhotoAccess.FULL, PhotoAccess.fromPermissions(33, true, false, false))
        assertEquals(PhotoAccess.NONE, PhotoAccess.fromPermissions(33, false, true, true))
    }

    @Test fun selectedAccessIsNotFullAccessOnAndroid14AndLater() {
        for (api in 34..37) {
            assertEquals(PhotoAccess.SELECTED, PhotoAccess.fromPermissions(api, false, true, false))
            assertEquals(PhotoAccess.FULL, PhotoAccess.fromPermissions(api, true, true, false))
            assertEquals(PhotoAccess.NONE, PhotoAccess.fromPermissions(api, false, false, true))
        }
    }
}
